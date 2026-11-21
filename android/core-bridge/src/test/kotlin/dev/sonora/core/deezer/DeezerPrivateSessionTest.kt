package dev.sonora.core.deezer

import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeezerPrivateSessionTest {
    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer()
        server.start()
    }

    @After fun stop() = server.shutdown()

    @Test fun validatesPubliclyCapturedArlAndEstablishesGatewaySession() = runTest {
        server.enqueue(
            MockResponse()
                .addHeader("Set-Cookie", "sid=session-id; Path=/")
                .setBody("""{"results":{"SESSION":"session-id"}}"""),
        )
        server.enqueue(
            MockResponse().setBody(
                """{"results":{"checkForm":"form-token","USER":{"USER_ID":"42","BLOG_NAME":"Listener","OPTIONS":{"license_token":"license-token","web_hq":true}}}}""",
            ),
        )
        val session = DeezerPrivateSession(
            http = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build(),
            gatewayRoot = server.url("/ajax/gw-light.php").toString(),
        )

        val account = session.connect("a".repeat(64))

        assertEquals("42", account.id)
        assertEquals("Listener", account.displayName)
        assertEquals(DeezerPrivateSession.PublicPlan.PREMIUM, account.plan)
        assertTrue(session.isConnected())
        val ping = server.takeRequest()
        assertEquals("GET", ping.method)
        assertEquals("deezer.ping", ping.requestUrl?.queryParameter("method"))
        assertEquals("arl=${"a".repeat(64)}", ping.getHeader("Cookie"))
        val userData = server.takeRequest()
        assertEquals("POST", userData.method)
        assertEquals("deezer.getUserData", userData.requestUrl?.queryParameter("method"))
        assertEquals("", userData.requestUrl?.queryParameter("api_token"))
        assertTrue(userData.getHeader("Cookie").orEmpty().contains("sid=session-id"))
        assertTrue(userData.getHeader("Cookie").orEmpty().contains("arl=${"a".repeat(64)}"))
        assertEquals("{}", userData.body.readUtf8())

        session.disconnect()
        assertFalse(session.isConnected())
    }

    @Test fun rejectsMalformedCredentialBeforeMakingAnyRequest() = runTest {
        val session = DeezerPrivateSession(gatewayRoot = server.url("/ajax/gw-light.php").toString())
        val error = runCatching { session.connect("not-a-cookie") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(0, server.requestCount)
    }

    @Test fun decryptsEveryThirdWholeStripeAndLeavesPartialTailUntouched() {
        val key = ByteArray(16) { (it * 7 + 3).toByte() }
        val clear = ByteArray(4 * DeezerPrivateSession.STRIPE_BLOCK_BYTES + 19) { (it * 31).toByte() }
        val encrypted = clear.copyOf()
        encryptBlock(encrypted, 0, key)
        encryptBlock(encrypted, 3, key)

        assertArrayEquals(clear, decryptStriped(encrypted, key))
    }

    private fun encryptBlock(bytes: ByteArray, index: Int, key: ByteArray) {
        val offset = index * DeezerPrivateSession.STRIPE_BLOCK_BYTES
        val cipher = Cipher.getInstance("Blowfish/CBC/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "Blowfish"),
            IvParameterSpec(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)),
        )
        cipher.doFinal(bytes, offset, DeezerPrivateSession.STRIPE_BLOCK_BYTES).copyInto(bytes, offset)
    }
}
