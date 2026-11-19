package dev.sonora.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Proof-of-Origin token minter (plan §5, YouTube Music).
 *
 * Google's BotGuard challenge only passes inside a real Chromium. On Android that
 * means an off-screen `WebView`:
 *
 *  1. load the embed page (`youtube.com/embed/...`) — the same origin InnerTube uses
 *  2. inject the BotGuard bundle and ask it for a token bound to `binding`
 *  3. read the answer back from a cookie the page writes (`SONORA_POT`)
 *  4. keep the page alive briefly (it costs ~270 MB) and destroy it after the linger
 *
 * Phase 4 supplies the actual BotGuard bundle from the `ytmusic` crate; this class
 * owns the lifecycle, the isolation and the timeout so that piece is a drop-in.
 * The generated `TokenMinter` adapter (`dev.sonora.core.ffi.TokenMinter`) delegates
 * here once the Rust bridge is enabled.
 */
class PoTokenMinter(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private var webView: WebView? = null
    private var destroyJob: Job? = null
    private var lastUsedAt: Long = 0

    /**
     * Ask for a token. Returns null on timeout, which the Rust side treats as
     * "continue without PoToken" (YouTube then answers with the limited stream set).
     */
    suspend fun mint(binding: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String? =
        withContext(Dispatchers.Main) {
            val view = ensureWebView()
            destroyJob?.cancel()
            view.loadUrl(pageUrl(binding))
            injectBridge(view)

            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            while (SystemClock.elapsedRealtime() < deadline) {
                readToken()?.let { token ->
                    lastUsedAt = SystemClock.elapsedRealtime()
                    scheduleDestroy()
                    return@withContext token
                }
                delay(POLL_INTERVAL_MS)
            }
            scheduleDestroy()
            null
        }

    /** Called when sign-out or process teardown happens. */
    fun release() {
        destroyJob?.cancel()
        destroyJob = null
        webView?.let { view ->
            view.stopLoading()
            view.loadUrl("about:blank")
            view.destroy()
        }
        webView = null
    }

    private fun scheduleDestroy() {
        destroyJob?.cancel()
        destroyJob = scope.launch(Dispatchers.Main) {
            delay(LINGER_MS)
            // Only tear down if nothing asked for a token in the meantime.
            if (SystemClock.elapsedRealtime() - lastUsedAt >= LINGER_MS) {
                release()
            }
        }
    }

    private fun readToken(): String? {
        val header = CookieManager.getInstance().getCookie(EMBED_ORIGIN) ?: return null
        return AuthFlows.parseCookies(header)[COOKIE_NAME]?.takeIf { it.isNotBlank() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(): WebView = webView ?: WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mediaPlaybackRequiresUserGesture = true
        settings.userAgentString = settings.userAgentString.replace("; wv", "")
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webView = this
        Log.d(TAG, "PoToken WebView created")
    }

    private fun injectBridge(view: WebView) {
        val script = runCatching {
            context.assets.open(BRIDGE_ASSET).bufferedReader().use { it.readText() }
        }.getOrElse { return }
        view.evaluateJavascript(script, null)
    }

    private fun pageUrl(binding: String): String =
        "https://www.youtube.com/embed/$EMBED_VIDEO_ID?autoplay=0&origin=https%3A%2F%2Fmusic.youtube.com" +
            "&po_binding=${UriEncoder.encode(binding)}"

    private object UriEncoder {
        fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
    }

    private companion object {
        const val TAG = "PoTokenMinter"
        const val BRIDGE_ASSET = "potoken_loader.js"
        const val COOKIE_NAME = "SONORA_POT"
        const val EMBED_ORIGIN = "https://www.youtube.com"
        const val EMBED_VIDEO_ID = "jNQXAC9IVRw"
        const val POLL_INTERVAL_MS = 250L
        const val LINGER_MS = 30_000L
        const val DEFAULT_TIMEOUT_MS = 15_000L
    }
}
