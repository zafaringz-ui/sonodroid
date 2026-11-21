package dev.sonora.core.deezer

import android.content.Context
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DeezerApiClientTest {
    private lateinit var server: MockWebServer

    @Before fun startServer() {
        server = MockWebServer()
        server.start()
    }
    @After fun stopServer() { server.shutdown() }

    @Test fun mockWebServerSearchMapsDocumentedFieldsAndEscapesQuery() = runTest {
        server.enqueue(MockResponse().setBody(resource("/deezer/search-tracks.json")))
        val api = DeezerApiClient(baseUrl = server.url("/").toString().trimEnd('/'))
        val context: Context = RuntimeEnvironment.getApplication()
        val core = DeezerPublicCoreClient(context, backgroundScope, api)
        try {
            val results = core.search(dev.sonora.core.model.Provider.DEEZER, "daft punk & friends", dev.sonora.core.model.SearchFilter.TRACKS)
            val request = server.takeRequest()
            assertEquals("/search?q=daft%20punk%20%26%20friends&limit=25", request.path)
            assertEquals(DeezerApiClient.USER_AGENT, request.getHeader("User-Agent"))
            val track = results.tracks.single()
            assertEquals("Harder Better Faster Stronger", track.title)
            assertEquals(listOf("Daft Punk"), track.artists)
            assertEquals("Discovery", track.album)
            assertEquals("302127", track.albumId)
            assertEquals(224_000L, track.durationMs)
            assertEquals("https://cdn-preview.example/preview.mp3", track.previewUrl)
            assertNotNull(track.artworkUrl)
        } finally {
            core.close()
        }
    }

    @Test fun mapsHttp200QuotaErrorAndRetriesWithBackoff() = runTest {
        val waits = mutableListOf<Long>()
        server.enqueue(MockResponse().setBody("""{"error":{"type":"Exception","message":"Quota limit exceeded","code":4}}"""))
        server.enqueue(MockResponse().setBody("""{"error":{"type":"Exception","message":"Quota limit exceeded","code":4}}"""))
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        val api = DeezerApiClient(
            baseUrl = server.url("/").toString().trimEnd('/'),
            pause = { waits += it },
        )
        val result = api.get("search", mapOf("q" to "test"))
        assertTrue(result.array("data")!!.isEmpty())
        assertEquals(listOf(1_000L, 2_000L), waits)
        assertEquals(3, server.requestCount)
    }

    @Test fun mapsNonRateLimitHttp200ErrorJsonToApiError() = runTest {
        server.enqueue(MockResponse().setBody("""{"error":{"type":"Exception","message":"Invalid query","code":100}}"""))
        val api = DeezerApiClient(baseUrl = server.url("/").toString().trimEnd('/'))
        val error = runCatching { api.get("search", mapOf("q" to "x")) }.exceptionOrNull()
        assertTrue(error is DeezerApiException.Api)
        assertEquals(100, (error as DeezerApiException.Api).code)
    }

    @Test fun returnsRetryableRateLimitAfterBoundedBackoff() = runTest {
        repeat(3) {
            server.enqueue(MockResponse().setBody("""{"error":{"message":"Quota limit exceeded","code":4}}"""))
        }
        val waits = mutableListOf<Long>()
        val api = DeezerApiClient(
            baseUrl = server.url("/").toString().trimEnd('/'),
            pause = { waits += it },
        )
        val error = runCatching { api.get("search") }.exceptionOrNull()
        assertTrue(error is DeezerApiException.RateLimited)
        assertEquals(listOf(1_000L, 2_000L), waits)
    }

    @Test fun honorsRetryAfterHeaderWithinBound() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "3").setBody("{}"))
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        val waits = mutableListOf<Long>()
        val api = DeezerApiClient(
            baseUrl = server.url("/").toString().trimEnd('/'),
            pause = { waits += it },
        )
        api.get("chart")
        assertEquals(listOf(3_000L), waits)
    }

    private fun resource(path: String): String =
        requireNotNull(javaClass.getResource(path)).readText()
}
