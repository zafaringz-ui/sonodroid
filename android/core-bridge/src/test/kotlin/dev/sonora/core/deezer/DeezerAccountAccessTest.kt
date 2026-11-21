package dev.sonora.core.deezer

import android.content.Context
import dev.sonora.core.model.Provider
import dev.sonora.core.model.SearchFilter
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DeezerAccountAccessTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun catalogRequestsRequireValidatedSignIn() = runTest {
        val context: Context = RuntimeEnvironment.getApplication()
        val core = DeezerAccountCoreClient(context, backgroundScope, DeezerApiClient(baseUrl = server.url("/").toString().trimEnd('/')))
        try {
            val failure = runCatching {
                core.search(Provider.DEEZER, "test", SearchFilter.TRACKS)
            }.exceptionOrNull()

            assertTrue(failure is AccountFeatureUnavailableException)
            assertTrue(failure?.message.orEmpty().contains("Sign in to Deezer"))
            assertEquals(0, server.requestCount)
        } finally {
            core.close()
        }
    }
}
