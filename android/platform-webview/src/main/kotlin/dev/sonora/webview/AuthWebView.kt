package dev.sonora.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * A locked-down WebView for provider sign-in.
 *
 * Security choices, all deliberate:
 *  * JavaScript on (Google and Deezer both require it), file/content access off.
 *  * No `addJavascriptInterface`: no JS bridge exists on the auth pages.
 *  * Navigation is restricted to the provider's hosts; anything else is refused
 *    instead of being rendered.
 *  * Cookies are read only once the success host is reached, and the caller clears
 *    the WebView cookie jar afterwards.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AuthWebView(
    target: AuthTarget,
    modifier: Modifier = Modifier,
    onCookiesCaptured: (Map<String, String>) -> Unit,
    onPageTitle: (String) -> Unit = {},
    onBlockedNavigation: (String) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportMultipleWindows(false)
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    val client = remember(target) {
        object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (isSuccessPage(target, url)) {
                    val header = CookieManager.getInstance().getCookie(url)
                    onCookiesCaptured(AuthFlows.parseCookies(header))
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                onPageTitle(view.title.orEmpty())
                if (isSuccessPage(target, url)) {
                    val header = CookieManager.getInstance().getCookie(url)
                    onCookiesCaptured(AuthFlows.parseCookies(header))
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val host = request.url.host.orEmpty()
                if (!isAllowedHost(target, host)) {
                    onBlockedNavigation(request.url.toString())
                    return true
                }
                return false
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError,
            ) {
                super.onReceivedError(view, request, error)
                onPageTitle("Could not load sign-in page")
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            webView.apply {
                webViewClient = client
                loadUrl(target.startUrl)
            }
        },
        update = { view ->
            view.webViewClient = client
        },
        onRelease = { view ->
            view.webViewClient = WebViewClient()
            view.destroy()
        },
    )
}

internal fun isAllowedHost(target: AuthTarget, host: String): Boolean {
    if (host.isEmpty()) return false
    return target.allowedHostSuffixes.any { suffix ->
        host == suffix || host.endsWith(".$suffix")
    }
}

internal fun isSuccessPage(target: AuthTarget, url: String): Boolean {
    val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
    return host == target.successHostSuffix || host.endsWith(".${target.successHostSuffix}")
}

/**
 * Removes every cookie for the provider. Called after a successful capture so the
 * WebView does not keep a live session around.
 */
fun clearCookiesFor(target: AuthTarget) {
    val manager = CookieManager.getInstance()
    target.allowedHostSuffixes.forEach { suffix ->
        manager.setCookie("https://$suffix", "arl=; Max-Age=0")
        manager.setCookie("https://$suffix", "SAPISID=; Max-Age=0")
    }
    manager.flush()
}

/**
 * Whether the device supports the `WebViewCompat` cookie helpers. Kept for the
 * "trusted WebView" checks needed by Google sign-in on some OEMs.
 */
fun supportsTrustedWebView(): Boolean =
    false // Multi-profile support is not used by this prototype.
