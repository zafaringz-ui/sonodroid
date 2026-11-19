package dev.sonora.webview

/**
 * Provider-specific details for the sign-in WebViews.
 *
 * The capture rules come from the desktop clients:
 *  * Deezer — sign in on www.deezer.com, keep the `arl` cookie, validate it with
 *    `deezer::auth::arl()` on the Rust side.
 *  * YouTube Music — sign in through accounts.google.com, keep the SAPISID family of
 *    cookies plus SID/HSID/SSID/APISID/LOGIN_INFO, hand them to `youtube::auth`.
 *
 * Nothing in this file logs a cookie value.
 */
enum class AuthProvider(val slug: String) {
    DEEZER("deezer"),
    YOUTUBE("youtube"),
}

data class AuthTarget(
    val provider: AuthProvider,
    /** Where the WebView starts. */
    val startUrl: String,
    /** Hosts allowed to stay inside the WebView; anything else is blocked. */
    val allowedHostSuffixes: List<String>,
    /** Page that means "the user is signed in, read the cookies now". */
    val successHostSuffix: String,
    /** Cookies that must be present for the session to be usable. */
    val requiredCookies: List<String>,
    val label: String,
)

object AuthFlows {

    val deezer = AuthTarget(
        provider = AuthProvider.DEEZER,
        startUrl = "https://www.deezer.com/login",
        allowedHostSuffixes = listOf("deezer.com", "deezer.page.link", "google.com", "gstatic.com"),
        successHostSuffix = "deezer.com",
        requiredCookies = listOf("arl"),
        label = "Deezer",
    )

    val youtube = AuthTarget(
        provider = AuthProvider.YOUTUBE,
        startUrl = "https://accounts.google.com/ServiceLogin" +
            "?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com",
        allowedHostSuffixes = listOf(
            "google.com",
            "gstatic.com",
            "googleusercontent.com",
            "youtube.com",
            "ytimg.com",
        ),
        successHostSuffix = "music.youtube.com",
        requiredCookies = listOf("SAPISID", "SID", "HSID", "SSID", "APISID"),
        label = "YouTube Music",
    )

    fun forSlug(slug: String): AuthTarget =
        if (slug == AuthProvider.DEEZER.slug) deezer else youtube

    /**
     * Parse a `CookieManager` header (`a=1; b=2`) into a map. Values are returned
     * verbatim; callers must store them encrypted and never log them.
     */
    fun parseCookies(header: String?): Map<String, String> {
        if (header.isNullOrBlank()) return emptyMap()
        return header.split(';')
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val name = part.substring(0, separator).trim()
                val value = part.substring(separator + 1).trim()
                if (name.isEmpty() || value.isEmpty()) null else name to value
            }
            .toMap()
    }

    /** True when every required cookie for this provider is present. */
    fun hasRequiredCookies(target: AuthTarget, cookies: Map<String, String>): Boolean =
        target.requiredCookies.all { required ->
            cookies.keys.any { it.equals(required, ignoreCase = true) }
        }

    /**
     * The value the Rust provider layer needs.
     *
     * Deezer wants the ARL; YouTube wants the whole cookie jar rebuilt as a header.
     */
    fun credentialFor(target: AuthTarget, cookies: Map<String, String>): String? = when (target.provider) {
        AuthProvider.DEEZER -> cookies.entries
            .firstOrNull { it.key.equals("arl", ignoreCase = true) }
            ?.value
        AuthProvider.YOUTUBE -> cookies.entries
            .joinToString("; ") { "${it.key}=${it.value}" }
            .takeIf { it.isNotBlank() }
    }

    /** A redacted description safe for Settings and logs. */
    fun describeCredential(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return "«empty»"
        return "${trimmed.take(4)}…(${trimmed.length} chars)"
    }
}
