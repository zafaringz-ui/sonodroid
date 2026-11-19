package dev.sonora.app

import android.content.Intent
import android.net.Uri
import dev.sonora.core.model.Provider

/**
 * Link handling for deezer.com, deezer.page.link, music.youtube.com and youtu.be.
 *
 * Resolution (turning a link into a playable track) needs the provider clients, so
 * this only recognises and routes links today. The intent filters are already in the
 * manifest, including the `ACTION_SEND` text share target.
 */
data class DeepLink(
    val provider: Provider,
    val url: String,
    val id: String,
    val kind: Kind,
) {
    enum class Kind { TRACK, ALBUM, PLAYLIST, ARTIST, UNKNOWN }

    val description: String
        get() = when (kind) {
            Kind.TRACK -> "track"
            Kind.ALBUM -> "album"
            Kind.PLAYLIST -> "playlist"
            Kind.ARTIST -> "artist"
            Kind.UNKNOWN -> "link"
        }
}

object DeepLinks {

    fun fromIntent(intent: Intent?): DeepLink? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { parse(it.toString()) }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
                parse(text.trim())
            }
            else -> null
        }
    }

    fun parse(raw: String): DeepLink? {
        val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return null
        if (uri.scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null

        return when {
            (host == "deezer.com" || host.endsWith(".deezer.com")) || host == "deezer.page.link" -> parseDeezer(uri)
            (host == "youtube.com" || host.endsWith(".youtube.com")) || host == "youtu.be" -> parseYouTube(uri)
            else -> null
        }
    }

    private fun parseDeezer(uri: Uri): DeepLink {
        val segments = uri.pathSegments
        val kind = when {
            segments.contains("album") -> DeepLink.Kind.ALBUM
            segments.contains("playlist") -> DeepLink.Kind.PLAYLIST
            segments.contains("artist") -> DeepLink.Kind.ARTIST
            segments.contains("track") -> DeepLink.Kind.TRACK
            else -> DeepLink.Kind.UNKNOWN
        }
        val id = segments.lastOrNull { it.isNotBlank() } ?: uri.toString()
        return DeepLink(Provider.DEEZER, uri.toString(), id, kind)
    }

    private fun parseYouTube(uri: Uri): DeepLink {
        val segments = uri.pathSegments
        val kind = when {
            segments.contains("playlist") -> DeepLink.Kind.PLAYLIST
            segments.contains("browse") || segments.contains("channel") -> DeepLink.Kind.ARTIST
            else -> DeepLink.Kind.TRACK
        }
        val id = uri.getQueryParameter("v")
            ?: uri.getQueryParameter("list")
            ?: segments.lastOrNull { it.isNotBlank() }
            ?: uri.toString()
        return DeepLink(Provider.YOUTUBE_MUSIC, uri.toString(), id, kind)
    }
}
