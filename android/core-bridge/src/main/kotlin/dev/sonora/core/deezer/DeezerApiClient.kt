package dev.sonora.core.deezer

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.parseToJsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Public, unauthenticated Deezer API transport. Never logs request URLs or query text. */
class DeezerApiClient internal constructor(
    private val http: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = API_ROOT,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(path: String, query: Map<String, String> = emptyMap()): JsonObject {
        val url = buildUrl(path, query)
        var attempt = 0
        while (true) {
            val wire = try {
                withContext(Dispatchers.IO) {
                    http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build())
                        .execute()
                        .use { response ->
                            WireResponse(
                                status = response.code,
                                retryAfter = response.header("Retry-After"),
                                body = response.body?.string().orEmpty(),
                            )
                        }
                }
            } catch (error: IOException) {
                throw DeezerApiException.Network(error)
            }
            val status = wire.status
            val retryHeader = wire.retryAfter?.toLongOrNull()?.times(1_000L)
            val payload = try {
                if (wire.body.isBlank() && status == 429) JsonObject(emptyMap())
                else json.parseToJsonElement(wire.body) as? JsonObject
                    ?: throw DeezerApiException.Malformed("Deezer returned a non-object response")
            } catch (error: DeezerApiException) {
                if (status !in 200..299) JsonObject(emptyMap()) else throw error
            } catch (error: Exception) {
                if (status !in 200..299) JsonObject(emptyMap())
                else throw DeezerApiException.Malformed("Deezer returned invalid JSON", error)
            }
            val apiError = payload["error"] as? JsonObject
            val code = apiError?.long("code")?.toInt()
            val message = apiError?.string("message")
            val limited = status == 429 || code == RATE_LIMIT_CODE ||
                (message?.contains("quota limit exceeded", ignoreCase = true) == true)
            if (limited) {
                if (attempt < MAX_RATE_LIMIT_RETRIES) {
                    pause(retryDelayMs(attempt, retryHeader))
                    attempt += 1
                    continue
                }
                throw DeezerApiException.RateLimited(retryDelayMs(attempt - 1, retryHeader))
            }
            if (status !in 200..299) {
                throw DeezerApiException.Http(status, message ?: "Deezer request failed")
            }
            if (apiError != null) {
                throw DeezerApiException.Api(code, message ?: "Deezer API error")
            }
            return payload
        }
    }

    private fun buildUrl(path: String, query: Map<String, String>): HttpUrl {
        val normalizedBase = baseUrl.trimEnd('/') + "/"
        val url = normalizedBase.toHttpUrlOrNullCompat()
            ?: throw DeezerApiException.Malformed("Invalid Deezer API base URL")
        val builder = url.newBuilder().addPathSegments(path.trimStart('/'))
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    companion object {
        const val API_ROOT = "https://api.deezer.com"
        const val USER_AGENT = "SonoraAndroid/0.1 (public catalog; Android)"
        const val RATE_LIMIT_CODE = 4
        const val MAX_RATE_LIMIT_RETRIES = 2
        const val BASE_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 8_000L

        fun retryDelayMs(attempt: Int, retryAfterMs: Long? = null): Long {
            val exponential = (BASE_BACKOFF_MS shl attempt.coerceIn(0, 3))
                .coerceAtMost(MAX_BACKOFF_MS)
            return (retryAfterMs ?: exponential).coerceIn(250L, MAX_BACKOFF_MS)
        }

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

private data class WireResponse(val status: Int, val retryAfter: String?, val body: String)

sealed class DeezerApiException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class Network(cause: IOException) : DeezerApiException("Network unavailable. Check your connection and try again.", cause)
    class Malformed(message: String, cause: Throwable? = null) : DeezerApiException(message, cause)
    class Http(val status: Int, message: String) : DeezerApiException("Deezer request failed ($status): $message")
    class Api(val code: Int?, message: String) : DeezerApiException("Deezer API error${code?.let { " $it" } ?: ""}: $message")
    class RateLimited(val retryAfterMs: Long) : DeezerApiException(
        "Deezer is busy. Wait about ${(retryAfterMs + 999L) / 1_000L} seconds, then try again.",
    )
}

internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNullCompat()

internal fun JsonObject.long(key: String): Long? =
    (this[key] as? JsonPrimitive)?.longOrNull

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

internal fun JsonObject.bool(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull

private fun String.toHttpUrlOrNullCompat(): HttpUrl? = try {
    toHttpUrl()
} catch (_: IllegalArgumentException) {
    null
}

private fun JsonPrimitive.contentOrNullCompat(): String? = if (isString || content != "null") content else null
