package dev.sonora.core.deezer

import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** Session and full-track transport ported from Sonora's Deezer client. Never logs secrets or URLs. */
internal class DeezerPrivateSession(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build(),
    private val gatewayRoot: String = GATEWAY_URL,
    private val mediaUrl: String = MEDIA_URL,
    private val webPlayerUrl: String = WEB_PLAYER_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    @Volatile private var active: Session? = null
    @Volatile private var streamSecret: ByteArray? = null

    data class Account(val id: String, val displayName: String, val plan: PublicPlan)
    enum class PublicPlan { FREE, PREMIUM, HIFI }

    suspend fun connect(arl: String): Account = mutex.withLock {
        val normalized = arl.trim()
        require(normalized.matches(Regex("(?i)[0-9a-f]{32,}"))) {
            "The Deezer session cookie is invalid. Sign in again."
        }
        val cookies = linkedMapOf("arl" to normalized)
        val ping = request(gatewayUrl("deezer.ping", ""), cookies, null, isGet = true)
        adoptCookies(cookies, ping.setCookies)
        val results = ping.body.sessionObj("results")
            ?: throw DeezerApiException.Api(null, "Deezer did not return a session.")
        val sid = results.sessionText("SESSION")?.takeIf(String::isNotBlank)
            ?: throw DeezerApiException.Api(null, "Deezer did not establish a session.")
        cookies["sid"] = sid
        val accountData = request(
            gatewayUrl("deezer.getUserData", ""),
            cookies,
            "{}",
            isGet = false,
        )
        adoptCookies(cookies, accountData.setCookies)
        val userData = accountData.body.sessionObj("results")
            ?: throw DeezerApiException.Api(null, "Deezer returned no account details.")
        val user = userData.sessionObj("USER")
            ?: throw DeezerApiException.Api(null, "Deezer refused the session. Sign in again.")
        val id = user.sessionText("USER_ID")?.takeIf(String::isNotBlank)
            ?: throw DeezerApiException.Api(null, "Deezer refused the session. Sign in again.")
        val apiToken = userData.sessionText("checkForm")?.takeIf(String::isNotBlank)
            ?: throw DeezerApiException.Api(null, "Deezer did not provide a session token.")
        val options = user.sessionObj("OPTIONS")
        val license = options?.sessionText("license_token")?.takeIf(String::isNotBlank)
            ?: throw DeezerApiException.Api(null, "This Deezer account cannot authorize full-track playback.")
        val plan = when {
            options?.sessionBool("web_lossless") == true || options?.sessionBool("mobile_lossless") == true -> PublicPlan.HIFI
            options?.sessionBool("web_hq") == true || options?.sessionBool("mobile_hq") == true -> PublicPlan.PREMIUM
            else -> PublicPlan.FREE
        }
        val displayName = user.sessionText("BLOG_NAME")?.takeIf(String::isNotBlank)
            ?: user.sessionText("NAME")?.takeIf(String::isNotBlank)
            ?: "Deezer listener"
        active = Session(normalized, cookies, apiToken, license, id, displayName, plan)
        Account(id, displayName, plan)
    }

    suspend fun disconnect() = mutex.withLock { active = null }

    fun isConnected(): Boolean = active != null

    suspend fun downloadTrack(trackId: String): ByteArray {
        val trackData = gateway(
            "song.getData",
            buildJsonObject { put("SNG_ID", trackId) },
        ).let { it.sessionObj("FALLBACK") ?: it }
        val effectiveId = trackData.sessionText("SNG_ID")?.takeIf(String::isNotBlank)
            ?: trackId
        val token = trackData.sessionText("TRACK_TOKEN")?.takeIf(String::isNotBlank)
            ?: throw DeezerApiException.Api(null, "This Deezer track is not available for playback.")
        val session = active ?: throw DeezerApiException.Api(null, "Sign in to Deezer to play full tracks.")
        val mediaRequest = buildJsonObject {
            put("license_token", session.licenseToken)
            put("media", buildJsonArray {
                add(buildJsonObject {
                    put("type", "FULL")
                    put("formats", buildJsonArray {
                        add(buildJsonObject {
                            put("cipher", "BF_CBC_STRIPE")
                            put("format", "MP3_128")
                        })
                    })
                })
            })
            put("track_tokens", buildJsonArray { add(JsonPrimitive(token)) })
        }
        val media = requestJson(mediaUrl, mediaRequest.toString(), emptyMap(), "application/json")
        val item = media.sessionArray("data")?.firstOrNull()?.sessionAsObject()
            ?: throw DeezerApiException.Api(null, "Deezer did not provide an audio stream.")
        val errors = item.sessionArray("errors")
        if (errors != null && errors.isNotEmpty()) {
            throw DeezerApiException.Api(null, "Deezer could not authorize this track for playback.")
        }
        val url = item.sessionArray("media")?.firstOrNull()?.sessionAsObject()
            ?.sessionArray("sources")?.firstOrNull()?.sessionAsObject()?.sessionText("url")
            ?: throw DeezerApiException.Api(null, "Deezer did not return a playable audio source.")
        val encrypted = fetchBytes(url)
        val secret = streamSecret ?: fetchStreamSecret().also { streamSecret = it }
        val key = trackKey(effectiveId, secret)
        return decryptStriped(encrypted, key)
    }

    private suspend fun gateway(method: String, body: JsonObject): JsonObject = mutex.withLock {
        var session = active ?: throw DeezerApiException.Api(null, "Sign in to Deezer to play full tracks.")
        val response = try {
            request(gatewayUrl(method, session.apiToken), session.cookies, body.toString(), false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            val refreshed = refresh(session)
            session = refreshed
            active = refreshed
            request(gatewayUrl(method, session.apiToken), session.cookies, body.toString(), false)
        }
        adoptCookies(session.cookies, response.setCookies)
        val updated = session.copy(cookies = session.cookies.toMutableMap())
        active = updated
        response.body.sessionObj("results") ?: throw DeezerApiException.Malformed("Deezer returned an invalid gateway response.")
    }

    private suspend fun refresh(previous: Session): Session {
        val response = request(gatewayUrl("deezer.getUserData", ""), previous.cookies, "{}", false)
        adoptCookies(previous.cookies, response.setCookies)
        val result = response.body.sessionObj("results") ?: throw DeezerApiException.Api(null, "Deezer session expired. Sign in again.")
        val user = result.sessionObj("USER") ?: throw DeezerApiException.Api(null, "Deezer session expired. Sign in again.")
        val token = result.sessionText("checkForm") ?: throw DeezerApiException.Api(null, "Deezer session expired. Sign in again.")
        val license = user.sessionObj("OPTIONS")?.sessionText("license_token")
            ?: throw DeezerApiException.Api(null, "Deezer session expired. Sign in again.")
        return previous.copy(cookies = previous.cookies.toMutableMap(), apiToken = token, licenseToken = license)
    }

    private suspend fun requestJson(
        url: String,
        body: String,
        cookies: Map<String, String>,
        contentType: String = GATEWAY_MEDIA_TYPE,
    ): JsonObject = request(url, cookies, body, false, contentType).body

    private suspend fun request(
        url: String,
        cookies: Map<String, String>,
        body: String?,
        isGet: Boolean,
        contentType: String = GATEWAY_MEDIA_TYPE,
    ): Wire = withContext(Dispatchers.IO) {
        try {
            val cookieHeader = cookies.entries.joinToString("; ") { (key, value) -> "$key=$value" }
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DeezerApiClient.USER_AGENT)
                .apply { if (cookieHeader.isNotEmpty()) header("Cookie", cookieHeader) }
                .apply {
                    if (isGet) get() else post((body ?: "{}").toRequestBody(contentType.toMediaType()))
                }
                .build()
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val payload = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
                    ?: throw DeezerApiException.Malformed("Deezer returned invalid JSON.")
                if (!response.isSuccessful) {
                    throw DeezerApiException.Http(response.code, "Deezer request failed.")
                }
                val apiError = payload.sessionObj("error")
                if (apiError != null) {
                    val message = apiError.sessionText("message")?.takeIf(String::isNotBlank)
                        ?: "Deezer rejected the request."
                    throw DeezerApiException.Api(apiError.sessionNumber("code")?.toInt(), message)
                }
                Wire(payload, response.headers.values("Set-Cookie"))
            }
        } catch (error: IOException) {
            if (error is DeezerApiException) throw error
            throw DeezerApiException.Network(error)
        }
    }

    private suspend fun fetchBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        // Do not include this signed, short-lived URL in exceptions or logs.
        try {
            http.newCall(Request.Builder().url(url).header("User-Agent", DeezerApiClient.USER_AGENT).get().build())
                .execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Deezer audio source refused playback.")
                    response.body?.bytes() ?: throw IOException("Deezer returned an empty audio stream.")
                }
        } catch (error: IOException) {
            throw DeezerApiException.Network(error)
        }
    }

    private suspend fun fetchStreamSecret(): ByteArray = withContext(Dispatchers.IO) {
        runCatching {
            val html = fetchText(webPlayerUrl)
            val bundle = findBundle(html) ?: error("Missing player bundle")
            val source = fetchText(bundle)
            val first = findHalf(source, 0x61, 0x67) ?: error("Missing first secret half")
            val second = findHalf(source, 0x31, 0x34) ?: error("Missing second secret half")
            val secret = ByteArray(16) { index -> if (index % 2 == 0) first[index / 2] else second[index / 2] }
            val hash = MessageDigest.getInstance("MD5").digest(secret)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            check(hash == SECRET_MD5) { "Secret validation failed" }
            secret
        }.getOrElse { FALLBACK_SECRET.copyOf() }
    }

    private fun fetchText(url: String): String =
        http.newCall(Request.Builder().url(url).header("User-Agent", DeezerApiClient.USER_AGENT).get().build())
            .execute().use { response ->
                if (!response.isSuccessful) throw IOException("Deezer player bundle request failed.")
                response.body?.string() ?: throw IOException("Deezer player bundle was empty.")
            }

    private fun gatewayUrl(method: String, token: String): String =
        gatewayRoot.toHttpUrl().newBuilder()
            .addQueryParameter("method", method)
            .addQueryParameter("input", "3")
            .addQueryParameter("api_version", "1.0")
            .addQueryParameter("api_token", token)
            .addQueryParameter("cid", System.nanoTime().toString())
            .build().toString()

    private fun adoptCookies(cookies: MutableMap<String, String>, setCookies: List<String>) {
        for (header in setCookies) {
            val pair = header.substringBefore(';').split('=', limit = 2)
            if (pair.size != 2) continue
            val name = pair[0].trim()
            val value = pair[1].trim()
            if (name.isEmpty()) continue
            if (value.isEmpty() || value.equals("deleted", true)) cookies.remove(name)
            else cookies[name] = value
        }
    }

    private data class Session(
        val arl: String,
        val cookies: MutableMap<String, String>,
        val apiToken: String,
        val licenseToken: String,
        val accountId: String,
        val displayName: String,
        val plan: PublicPlan,
    )

    private data class Wire(val body: JsonObject, val setCookies: List<String>)

    companion object {
        const val GATEWAY_URL = "https://www.deezer.com/ajax/gw-light.php"
        const val MEDIA_URL = "https://media.deezer.com/v1/get_url"
        const val WEB_PLAYER_URL = "https://www.deezer.com/en/channels/explore/"
        const val SECRET_MD5 = "7ebf40da848f4a0fb3cc56ddbe6c2d09"
        val FALLBACK_SECRET = "g4el58wc0zvf9na1".toByteArray(Charsets.US_ASCII)
        val IV = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        internal const val STRIPE_BLOCK_BYTES = 2048
        const val GATEWAY_MEDIA_TYPE = "text/plain;charset=UTF-8"
    }
}

internal fun decryptStriped(source: ByteArray, key: ByteArray): ByteArray {
    val output = source.copyOf()
    var block = 0
    var offset = 0
    while (offset + DeezerPrivateSession.STRIPE_BLOCK_BYTES <= output.size) {
        if (block % 3 == 0) {
            val cipher = javax.crypto.Cipher.getInstance("Blowfish/CBC/NoPadding")
            cipher.init(
                javax.crypto.Cipher.DECRYPT_MODE,
                javax.crypto.spec.SecretKeySpec(key, "Blowfish"),
                javax.crypto.spec.IvParameterSpec(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)),
            )
            val plain = cipher.doFinal(output, offset, DeezerPrivateSession.STRIPE_BLOCK_BYTES)
            plain.copyInto(output, offset)
        }
        offset += DeezerPrivateSession.STRIPE_BLOCK_BYTES
        block++
    }
    return output
}

internal fun trackKey(trackId: String, secret: ByteArray): ByteArray {
    val hex = MessageDigest.getInstance("MD5").digest(trackId.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        .toByteArray(Charsets.US_ASCII)
    return ByteArray(16) { index -> (hex[index].toInt() xor hex[index + 16].toInt() xor secret[index].toInt()).toByte() }
}

private fun findBundle(html: String): String? {
    var rest = html
    while (true) {
        val at = rest.indexOf("app-web")
        if (at < 0) return null
        val from = rest.substring(0, at).lastIndexOf("https://")
        if (from >= 0) {
            val tail = rest.substring(from)
            val end = tail.indexOf('"')
            if (end >= 0 && tail.substring(0, end).endsWith(".js")) return tail.substring(0, end)
        }
        rest = rest.substring((at + 7).coerceAtMost(rest.length))
    }
}

private fun findHalf(source: String, first: Int, last: Int): ByteArray? {
    val needle = "0x%02x%%2C".format(first)
    var rest = source
    while (true) {
        val at = rest.indexOf(needle)
        if (at < 0) return null
        val candidate = rest.substring(at)
        val bytes = parseHalf(candidate, "0x%02x".format(last))
        if (bytes != null) return bytes
        rest = candidate.substring(needle.length.coerceAtMost(candidate.length))
    }
}

private fun parseHalf(candidate: String, terminator: String): ByteArray? {
    val endAt = candidate.indexOf(terminator)
    if (endAt < 0) return null
    val end = endAt + terminator.length
    if (end > 8 * "0xNN%2C".length) return null
    val parts = candidate.substring(0, end).split("%2C")
    if (parts.size != 8) return null
    val bytes = parts.map { part ->
        val hex = part.removePrefix("0x")
        if (hex == part) return null
        val value = hex.toIntOrNull(16)?.takeIf { it in 0..255 } ?: return null
        value.toByte()
    }
    return bytes.reversed().toByteArray()
}

private fun JsonObject.sessionObj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.sessionArray(key: String): JsonArray? = this[key] as? JsonArray
private fun JsonObject.sessionText(key: String): String? = (this[key] as? JsonPrimitive)?.content
private fun JsonObject.sessionNumber(key: String): Long? = (this[key] as? JsonPrimitive)?.content?.toLongOrNull()
private fun JsonObject.sessionBool(key: String): Boolean? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return primitive.booleanOrNull ?: when (primitive.content.lowercase()) {
        "1", "yes" -> true
        "0", "no" -> false
        else -> null
    }
}
private fun JsonElement.sessionAsObject(): JsonObject? = this as? JsonObject
