package dev.sonora.core.model

/**
 * App-level models.
 *
 * These are deliberately independent of the UniFFI-generated types: the Rust
 * bridge maps generated records onto these, so the UI never imports `ffi.*` and
 * the demo client can implement the same contract without a `.so`.
 */

enum class Provider(val slug: String, val label: String) {
    DEEZER("deezer", "Deezer"),
    YOUTUBE_MUSIC("youtube", "YouTube Music"),
    ;

    companion object {
        fun fromSlug(slug: String): Provider =
            entries.firstOrNull { it.slug.equals(slug, ignoreCase = true) } ?: DEEZER
    }
}

data class Track(
    val id: String,
    val provider: Provider,
    val title: String,
    val artists: List<String>,
    val album: String? = null,
    val albumId: String? = null,
    val durationMs: Long,
    val artworkUrl: String? = null,
    val isExplicit: Boolean = false,
    val isVideo: Boolean = false,
    val qualityLabel: String? = null,
) {
    val artistLine: String get() = artists.joinToString(", ")
}

data class Album(
    val id: String,
    val provider: Provider,
    val title: String,
    val artist: String,
    val year: Int? = null,
    val artworkUrl: String? = null,
    val trackCount: Int = 0,
    val qualityLabel: String? = null,
    /** Populated on an album-detail request when public tracks are available. */
    val tracks: List<Track> = emptyList(),
)

data class Artist(
    val id: String,
    val provider: Provider,
    val name: String,
    val artworkUrl: String? = null,
    val followerCount: Long? = null,
)

data class Playlist(
    val id: String,
    val provider: Provider,
    val title: String,
    val description: String? = null,
    val trackCount: Int = 0,
    val artworkUrl: String? = null,
    val isOwned: Boolean = false,
)

data class PlaylistDetail(
    val playlist: Playlist,
    val tracks: List<Track>,
)

/** One shelf on Home. */
data class HomeSection(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val items: List<HomeItem>,
)

sealed interface HomeItem {
    data class TrackItem(val track: Track) : HomeItem
    data class AlbumItem(val album: Album) : HomeItem
    data class PlaylistItem(val playlist: Playlist) : HomeItem
    data class ArtistItem(val artist: Artist) : HomeItem
}

data class HomeFeed(
    val provider: Provider,
    val greeting: String,
    val sections: List<HomeSection>,
)

enum class SearchFilter(val label: String) {
    ALL("All"),
    TRACKS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists"),
    VIDEOS("Videos"),
}

data class SearchResults(
    val provider: Provider,
    val query: String,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
) {
    val isEmpty: Boolean
        get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}

data class LibrarySnapshot(
    val provider: Provider,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val downloads: List<DownloadItem> = emptyList(),
)

data class LyricWord(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
) {
    fun activeWordIndex(positionMs: Long): Int =
        words.indexOfLast { it.startMs <= positionMs }
}

data class Lyrics(
    val trackId: String,
    val source: String,
    val synced: Boolean,
    val lines: List<LyricLine>,
    val romanized: Boolean = false,
) {
    fun activeLineIndex(positionMs: Long): Int =
        lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
}

enum class DownloadState { QUEUED, RUNNING, DONE, FAILED }

data class DownloadItem(
    val trackId: String,
    val title: String,
    val artist: String,
    val state: DownloadState,
    val progress: Float,
    val bytesOnDisk: Long,
    val quality: StreamQuality,
)

enum class RepeatMode { OFF, ALL, ONE }

data class PlaybackState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val repeat: RepeatMode = RepeatMode.OFF,
    val shuffle: Boolean = false,
    val queueIndex: Int = 0,
    val queueSize: Int = 0,
    val volume: Float = 1f,
    val crossfadeSeconds: Int = 0,
    val outputName: String = "Phone speaker",
    /** Visible playback failure, e.g. a Deezer preview that expired. */
    val error: String? = null,
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

// ---- quality -------------------------------------------------------------

enum class QualityTier { LOW, NORMAL, HIGH, LOSSLESS, HIRES }

enum class NetworkKind { WIFI, CELLULAR, OFFLINE }

enum class PlanTier(val label: String) {
    FREE("Free"),
    STANDARD("Standard"),
    PREMIUM("Premium"),
    HIFI("Hi-Fi"),
}

enum class QualityPreference(val label: String) {
    AUTO("Auto"),
    DATA_SAVER("Data saver"),
    BALANCED("Balanced"),
    BEST("Best available"),
}

enum class StreamPurpose { STREAMING, DOWNLOAD }

data class StreamQuality(
    val tier: QualityTier,
    val codec: String,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val bitDepth: Int,
    val label: String,
)

// ---- accounts ------------------------------------------------------------

enum class AuthState { SIGNED_OUT, CONNECTING, SIGNED_IN, NEEDS_ATTENTION }

data class AccountSummary(
    val id: String,
    val displayName: String,
    val plan: PlanTier,
    val isLosslessCapable: Boolean,
)

data class ProviderAuth(
    val provider: Provider,
    val state: AuthState = AuthState.SIGNED_OUT,
    val account: AccountSummary? = null,
    val detail: String? = null,
)

// ---- events --------------------------------------------------------------

sealed interface CoreEvent {
    data class PlaybackChanged(val state: PlaybackState) : CoreEvent
    data class QueueChanged(val size: Int, val index: Int) : CoreEvent
    data class AuthChanged(val auth: ProviderAuth) : CoreEvent
    data class TrackChanged(val track: Track) : CoreEvent
    data class Message(val text: String) : CoreEvent
    data class Failure(val message: String) : CoreEvent
}
