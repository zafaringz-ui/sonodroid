package dev.sonora.core.lyrics

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import dev.sonora.core.model.LyricLine
import dev.sonora.core.model.LyricWord
import dev.sonora.core.model.Lyrics

/** Metadata used to search independent lyrics catalogues for a provider track. */
internal data class LyricsQuery(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
)

internal data class LyricsCandidate(
    val source: String,
    val trust: Int,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    val synced: Boolean,
    val lines: List<LyricLine>,
)

internal interface LyricsProvider {
    val name: String
    suspend fun search(query: LyricsQuery): List<LyricsCandidate>
}

/** Small provider fan-out and track-match ranker; Deezer itself is not a lyrics source. */
internal class LyricsRepository(
    private val providers: List<LyricsProvider> = listOf(LrcLibProvider()),
) {
    suspend fun lyrics(query: LyricsQuery): Lyrics? {
        val candidates = mutableListOf<LyricsCandidate>()
        for (provider in providers) {
            try {
                candidates += provider.search(query)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A missing or unavailable catalogue must not prevent trying the next source.
            }
        }
        val sourceOrder = providers.map(LyricsProvider::name)
        val best = LyricsRanker.rank(query, candidates, sourceOrder).firstOrNull() ?: return null
        return Lyrics(
            trackId = query.trackId,
            source = best.source,
            synced = best.synced,
            lines = best.lines,
        )
    }
}

/** LRCLIB search implementation, matching Sonora's synced-before-plain precedence. */
internal class LrcLibProvider(
    private val http: OkHttpClient = defaultHttpClient(),
    private val endpoint: String = ENDPOINT,
) : LyricsProvider {
    override val name: String = SOURCE
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: LyricsQuery): List<LyricsCandidate> = withContext(Dispatchers.IO) {
        val url = endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("track_name", query.title)
            .addQueryParameter("artist_name", query.artist)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        try {
            http.newCall(request).execute().use { response ->
                if (response.code == 404) return@withContext emptyList()
                if (!response.isSuccessful) throw IOException("LRCLIB returned HTTP ${response.code}.")
                val body = response.body?.string().orEmpty()
                val entries = json.parseToJsonElement(body) as? JsonArray
                    ?: throw IOException("LRCLIB returned an invalid search response.")
                entries.mapNotNull { it.asObject()?.toCandidate() }
            }
        } catch (error: IOException) {
            throw error
        }
    }

    private fun JsonObject.toCandidate(): LyricsCandidate? {
        val title = string("trackName").orEmpty()
        val artist = string("artistName").orEmpty()
        val album = string("albumName")?.takeIf(String::isNotBlank)
        val duration = number("duration")?.let { (it * 1_000.0).toLong() }
        val syncedLines = string("syncedLyrics")?.let(::parseLrc).orEmpty()
        val timingDuration = duration?.let { (it * 1_000.0).toLong() } ?: query.durationMs
        val wordedLines = estimateWordTimings(syncedLines, timingDuration)
        val plainLines = string("plainLyrics")?.lineSequence()
            ?.map(String::trimEnd)
            ?.filter(String::isNotBlank)
            ?.map { LyricLine(timeMs = 0L, text = it) }
            ?.toList()
            .orEmpty()
        val lines = wordedLines.ifEmpty { plainLines }
        if (lines.isEmpty()) return null
        return LyricsCandidate(
            source = SOURCE,
            trust = 0,
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            synced = syncedLines.isNotEmpty(),
            lines = lines,
        )
    }

    private companion object {
        const val SOURCE = "LrcLib"
        const val ENDPOINT = "https://lrclib.net/api/search"
        const val USER_AGENT = "SonoraAndroid/0.1 (https://github.com/zafaringz-ui/sonodroid)"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

/** First-pass port of Sonora's provider-independent match/rank rules. */
internal object LyricsRanker {
    private const val CLOSE_ENOUGH_MS = 3_000L
    private const val WAY_OFF_MS = 10_000L
    private const val TRUNCATED_PENALTY = 500

    fun rank(
        query: LyricsQuery,
        candidates: List<LyricsCandidate>,
        priority: List<String> = emptyList(),
    ): List<LyricsCandidate> = candidates
        .filter { candidate -> matches(query, candidate) && candidate.lines.any { it.text.isNotBlank() } }
        .sortedWith(
            compareByDescending<LyricsCandidate> { grade(query, it) }
                .thenBy { priority.indexOf(it.source).let { index -> if (index < 0) priority.size else index } }
                .thenByDescending { score(query, it) }
                .thenBy { it.source }
                .thenBy { it.title },
        )
        .distinctBy(::fingerprint)

    private fun matches(query: LyricsQuery, candidate: LyricsCandidate): Boolean {
        if (!alike(query.title, candidate.title) || !artistsAlike(query.artist, candidate.artist)) return false
        val candidateDuration = candidate.durationMs ?: return true
        return query.durationMs <= 0L || kotlin.math.abs(candidateDuration - query.durationMs) <= WAY_OFF_MS
    }

    private fun grade(query: LyricsQuery, candidate: LyricsCandidate): Int = when {
        truncated(query, candidate) -> 0
        candidate.synced -> 2
        else -> 1
    }

    private fun score(query: LyricsQuery, candidate: LyricsCandidate): Int {
        var score = candidate.trust
        candidate.durationMs?.let { duration ->
            val drift = kotlin.math.abs(duration - query.durationMs)
            if (drift <= CLOSE_ENOUGH_MS) score += 100 - (drift / 100L).toInt() * 10
        }
        if (alike(query.title, candidate.title)) score += 40
        if (artistsAlike(query.artist, candidate.artist)) score += 30
        if (query.album != null && candidate.album != null && alike(query.album, candidate.album)) score += 15
        if (candidate.synced) score += 200
        if (truncated(query, candidate)) score -= TRUNCATED_PENALTY
        return score
    }

    private fun truncated(query: LyricsQuery, candidate: LyricsCandidate): Boolean {
        if (!candidate.synced || query.durationMs <= 0L) return false
        val span = candidate.lines.maxOfOrNull(LyricLine::timeMs) ?: return false
        return span < query.durationMs * 0.6
    }

    private fun fingerprint(candidate: LyricsCandidate): String =
        (if (candidate.synced) "synced:" else "plain:") + candidate.lines
            .asSequence()
            .map(LyricLine::text)
            .joinToString("")
            .filter(Char::isLetterOrDigit)
            .lowercase()

    private fun alike(left: String, right: String): Boolean {
        val a = undecorated(left)
        val b = undecorated(right)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val short = if (a.length <= b.length) a else b
        val long = if (a.length <= b.length) b else a
        return long.contains(short) && short.length * 2 >= long.length
    }

    private fun artistsAlike(left: String, right: String): Boolean =
        alike(left, right) || left.split(',', '&', ';').any { a ->
            right.split(',', '&', ';').any { b -> alike(a.trim(), b.trim()) }
        }

    private fun undecorated(value: String): String {
        val head = value.substringBefore(" - ")
        var depth = 0
        return head.asSequence()
            .filter { char ->
                when (char) {
                    '(', '[' -> { depth++; false }
                    ')', ']' -> { if (depth > 0) depth--; false }
                    else -> depth == 0
                }
            }
            .filter(Char::isLetterOrDigit)
            .joinToString("")
            .lowercase()
    }
}

/** LRC timestamps, multiple stamps, offsets, trailing end stamps, and inline word tags. */
internal fun parseLrc(input: String): List<LyricLine> {
    var offsetMs = 0L
    val lines = mutableListOf<LyricLine>()
    for (raw in input.lineSequence()) {
        var rest = raw.trim()
        val offset = OFFSET.matchEntire(rest)
        if (offset != null) {
            offsetMs += offset.groupValues[1].toLongOrNull() ?: 0L
            continue
        }
        val stamps = mutableListOf<Long>()
        while (rest.startsWith('[')) {
            val close = rest.indexOf(']')
            if (close < 0) break
            val stamp = parseLrcTimestamp(rest.substring(1, close)) ?: break
            stamps += stamp
            rest = rest.substring(close + 1).trimStart()
        }
        if (stamps.isEmpty()) continue
        rest = removeTrailingEndStamp(rest)
        val parsed = parseWordTimedText(rest)
        val text = parsed.first.trim()
        if (text.isEmpty()) continue
        for (stamp in stamps) {
            val lineTime = (stamp - offsetMs).coerceAtLeast(0L)
            val words = parsed.second.map { word ->
                word.copy(
                    startMs = (word.startMs - offsetMs).coerceAtLeast(0L),
                    endMs = (word.endMs - offsetMs).coerceAtLeast(0L),
                )
            }.let { wordList ->
                val drift = wordList.firstOrNull()?.startMs?.let { first -> (lineTime - first).coerceAtLeast(0L) } ?: 0L
                if (drift == 0L) wordList else wordList.map { word ->
                    word.copy(startMs = (word.startMs + drift).coerceAtLeast(0L), endMs = (word.endMs + drift).coerceAtLeast(0L))
                }
            }
            lines += LyricLine(timeMs = lineTime, text = text, words = words)
        }
    }
    return lines.sortedBy(LyricLine::timeMs)
}

/**
 * Preserve source word stamps when present. For ordinary line-synced LRC, spread each
 * line's interval across its words by character count, matching Sonora's shape fallback.
 */
internal fun estimateWordTimings(lines: List<LyricLine>, durationMs: Long): List<LyricLine> =
    lines.mapIndexed { index, line ->
        if (line.words.isNotEmpty()) return@mapIndexed line
        val matches = Regex("\\S+").findAll(line.text).toList()
        if (matches.isEmpty()) return@mapIndexed line
        val nextLineTime = lines.getOrNull(index + 1)?.timeMs
        val endMs = nextLineTime?.takeIf { it > line.timeMs }
            ?: durationMs.takeIf { it > line.timeMs }
            ?: return@mapIndexed line
        val span = endMs - line.timeMs
        val weights = matches.map { match -> match.value.codePointCount(0, match.value.length).coerceAtLeast(1) }
        val total = weights.sum().toFloat()
        var passed = 0
        val words = matches.mapIndexed { wordIndex, match ->
            val start = line.timeMs + (span * (passed / total)).toLong()
            passed += weights[wordIndex]
            val end = if (wordIndex == matches.lastIndex) endMs
                else line.timeMs + (span * (passed / total)).toLong()
            val textEnd = matches.getOrNull(wordIndex + 1)?.range?.first ?: line.text.length
            LyricWord(start, maxOf(start, end), line.text.substring(match.range.first, textEnd))
        }
        line.copy(words = words)
    }

private val OFFSET = Regex("^\\[offset:([+-]?\\d+)]$", RegexOption.IGNORE_CASE)
private val END_STAMP = Regex("\\[([^]]+)]$")

private fun removeTrailingEndStamp(text: String): String {
    val match = END_STAMP.find(text.trimEnd()) ?: return text
    if (parseLrcTimestamp(match.groupValues[1]) == null) return text
    return text.substring(0, match.range.first).trimEnd()
}

/** Parse Sonora's word-timed LRC form: [line] <word-start>word <next-start>word. */
private fun parseWordTimedText(source: String): Pair<String, List<LyricWord>> {
    data class Segment(val atMs: Long?, val text: StringBuilder = StringBuilder())

    val segments = mutableListOf(Segment(atMs = null))
    var cursor = 0
    while (cursor < source.length) {
        val open = source.indexOf('<', cursor)
        if (open < 0) {
            segments.last().text.append(source.substring(cursor))
            break
        }
        val close = source.indexOf('>', open + 1)
        if (close < 0) {
            segments.last().text.append(source.substring(cursor))
            break
        }
        val stamp = parseLrcTimestamp(source.substring(open + 1, close))
        if (stamp == null) {
            segments.last().text.append(source.substring(cursor, close + 1))
        } else {
            segments.last().text.append(source.substring(cursor, open))
            segments += Segment(atMs = stamp)
        }
        cursor = close + 1
    }

    val plain = segments.joinToString(separator = "") { it.text.toString() }
    val timed = segments.filter { it.atMs != null }
    if (timed.isEmpty()) return plain.trimEnd() to emptyList()

    val words = mutableListOf<LyricWord>()
    timed.forEachIndexed { index, segment ->
        val start = segment.atMs ?: return@forEachIndexed
        val next = timed.getOrNull(index + 1)?.atMs
        val piece = segment.text.toString()
        if (piece.isEmpty()) {
            if (words.isNotEmpty()) {
                val last = words.last()
                words[words.lastIndex] = last.copy(endMs = maxOf(last.startMs, start))
            }
        } else {
            words += LyricWord(
                startMs = start,
                endMs = next?.takeIf { it > start } ?: start,
                text = piece,
            )
        }
    }
    return plain.trimEnd() to words
}

private fun JsonElement.asObject(): JsonObject? = this as? JsonObject

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.number(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull

private fun parseLrcTimestamp(value: String): Long? {
    val parts = value.replace(',', '.').split(':')
    val seconds = when (parts.size) {
        2 -> {
            val minutes = parts[0].toLongOrNull() ?: return null
            val seconds = parts[1].toDoubleOrNull() ?: return null
            minutes * 60.0 + seconds
        }
        3 -> {
            if (parts[2].contains('.')) {
                val hours = parts[0].toLongOrNull() ?: return null
                val minutes = parts[1].toLongOrNull() ?: return null
                val seconds = parts[2].toDoubleOrNull() ?: return null
                hours * 3600.0 + minutes * 60.0 + seconds
            } else {
                val minutes = parts[0].toLongOrNull() ?: return null
                val wholeSeconds = parts[1].toLongOrNull() ?: return null
                val fraction = parts[2].takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
                    ?.let { "0.$it".toDoubleOrNull() } ?: return null
                minutes * 60.0 + wholeSeconds + fraction
            }
        }
        else -> return null
    }
    if (!seconds.isFinite() || seconds < 0.0) return null
    return (seconds * 1_000.0).toLong()
}
