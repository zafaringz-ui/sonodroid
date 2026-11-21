package dev.sonora.core.lyrics

import dev.sonora.core.model.LyricLine
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsRepositoryTest {
    @Test fun parsesWordTimedLrcWithoutLosingWhitespaceOrTiming() {
        val line = parseLrc("[00:01.00]<00:01.10>Hello <00:01.50>world").single()

        assertEquals("Hello world", line.text)
        assertEquals(2, line.words.size)
        assertEquals("Hello ", line.words[0].text)
        assertEquals(1_100L, line.words[0].startMs)
        assertEquals(1_500L, line.words[0].endMs)
        assertEquals("world", line.words[1].text)
        assertEquals(1_500L, line.words[1].startMs)
    }

    @Test fun keepsTextBeforeFirstInlineWordTimestamp() {
        val line = parseLrc("[00:01.00]Hello <00:02.00>world").single()

        assertEquals("Hello world", line.text)
        assertEquals(line.text, line.words.joinToString("") { it.text })
        assertEquals("Hello world", line.words.single().text)
    }

    @Test fun estimatesWordCadenceAcrossLineTimedLyrics() {
        val words = estimateWordTimings(
            listOf(LyricLine(1_000L, "one two"), LyricLine(3_000L, "three")),
            durationMs = 5_000L,
        ).first().words

        assertEquals(listOf("one ", "two"), words.map { it.text })
        assertEquals(1_000L, words.first().startMs)
        assertEquals(3_000L, words.last().endMs)
    }

    @Test fun estimatesFinalLineWithinTrackDurationFromLrcLibResponse() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setBody(
                    """[{"trackName":"Song","artistName":"Artist","duration":180,"syncedLyrics":"[00:10.00]one two\n[02:50.00]three four"}]""",
                ),
            )
            val candidate = LrcLibProvider(endpoint = server.url("/api/search").toString())
                .search(LyricsQuery("id", "Song", "Artist", null, 180_000L))
                .single()

            val finalWords = candidate.lines.last().words
            assertEquals(170_000L, finalWords.first().startMs)
            assertEquals(180_000L, finalWords.last().endMs)
        } finally {
            server.shutdown()
        }
    }

    @Test fun appliesLrcOffsetToLineAndWordTimes() {
        val line = parseLrc("[offset:100]\n[00:01.00]<00:01.10>word").single()

        assertEquals(900L, line.timeMs)
        assertEquals(1_000L, line.words.single().startMs)
    }
}
