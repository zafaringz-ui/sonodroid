package dev.sonora.core.demo

import dev.sonora.core.model.Album
import dev.sonora.core.model.Artist
import dev.sonora.core.model.DownloadItem
import dev.sonora.core.model.DownloadState
import dev.sonora.core.model.HomeFeed
import dev.sonora.core.model.HomeItem
import dev.sonora.core.model.HomeSection
import dev.sonora.core.model.LibrarySnapshot
import dev.sonora.core.model.LyricLine
import dev.sonora.core.model.Lyrics
import dev.sonora.core.model.Playlist
import dev.sonora.core.model.PlaylistDetail
import dev.sonora.core.model.Provider
import dev.sonora.core.model.QualityTier
import dev.sonora.core.model.SearchFilter
import dev.sonora.core.model.SearchResults
import dev.sonora.core.model.Track
import dev.sonora.core.quality.QualityPolicy

/**
 * Development catalogue.
 *
 * Mirrors `rust/crates/mobile-core/src/fixtures.rs` so the demo and Rust builds show
 * the same library. Artwork URLs are null on purpose: the UI falls back to the
 * generated gradient tiles, so nothing here needs the network.
 */
object DemoLibrary {

    private val deezerTracks = listOf(
        track("dz-1", Provider.DEEZER, "Night Ferry", "Astrid Lind", "Harbour Lights", 214_000, "al-harbour"),
        track("dz-2", Provider.DEEZER, "Paper Lantern", "Astrid Lind", "Harbour Lights", 187_000, "al-harbour"),
        track("dz-3", Provider.DEEZER, "Slow Tide", "Kaito Mori", "Coastline", 243_000, "al-coastline"),
        track("dz-4", Provider.DEEZER, "Copper Sky", "Kaito Mori", "Coastline", 198_000, "al-coastline"),
        track("dz-5", Provider.DEEZER, "Rain on Glass", "Mireille", "Interiors", 226_000, "al-interiors"),
        track("dz-6", Provider.DEEZER, "Long Way Home", "Mireille", "Interiors", 251_000, "al-interiors"),
    )

    private val youtubeTracks = listOf(
        track("yt-1", Provider.YOUTUBE_MUSIC, "Harbour Lights (Live)", "Astrid Lind", null, 231_000, null),
        track("yt-2", Provider.YOUTUBE_MUSIC, "Coastline", "Kaito Mori", null, 205_000, null),
        track("yt-3", Provider.YOUTUBE_MUSIC, "Interiors", "Mireille", null, 219_000, null),
        track("yt-4", Provider.YOUTUBE_MUSIC, "Signal Fires", "Nnamdi Okafor", null, 262_000, null),
        track("yt-5", Provider.YOUTUBE_MUSIC, "Low Sun", "Nnamdi Okafor", null, 194_000, null),
        track("yt-6", Provider.YOUTUBE_MUSIC, "Paper Lantern (Acoustic)", "Astrid Lind", null, 178_000, null),
    )

    private fun track(
        id: String,
        provider: Provider,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        albumId: String?,
    ) = Track(
        id = id,
        provider = provider,
        title = title,
        artists = listOf(artist),
        album = album,
        albumId = albumId,
        durationMs = durationMs,
    )

    fun tracks(provider: Provider): List<Track> = when (provider) {
        Provider.DEEZER -> deezerTracks
        Provider.YOUTUBE_MUSIC -> youtubeTracks
    }

    fun albums(provider: Provider): List<Album> = when (provider) {
        Provider.DEEZER -> listOf(
            Album("al-harbour", provider, "Harbour Lights", "Astrid Lind", 2024, trackCount = 2),
            Album("al-coastline", provider, "Coastline", "Kaito Mori", 2023, trackCount = 2),
            Album("al-interiors", provider, "Interiors", "Mireille", 2025, trackCount = 2),
        )
        Provider.YOUTUBE_MUSIC -> listOf(
            Album("al-live", provider, "Live Sessions", "Astrid Lind", 2024, trackCount = 2),
            Album("al-signal", provider, "Signal Fires", "Nnamdi Okafor", 2025, trackCount = 2),
            Album("al-interiors", provider, "Interiors", "Mireille", 2025, trackCount = 2),
        )
    }

    fun artists(provider: Provider): List<Artist> {
        val names = when (provider) {
            Provider.DEEZER -> listOf("ar-astrid" to "Astrid Lind", "ar-kaito" to "Kaito Mori", "ar-mireille" to "Mireille")
            Provider.YOUTUBE_MUSIC -> listOf("ar-astrid" to "Astrid Lind", "ar-nnamdi" to "Nnamdi Okafor", "ar-mireille" to "Mireille")
        }
        return names.map { (id, name) -> Artist(id, provider, name, followerCount = 48_120) }
    }

    fun playlists(provider: Provider): List<Playlist> = listOf(
        Playlist("pl-flow", provider, "Flow", "Endless mix from your listening", trackCount = 6, isOwned = true),
        Playlist("pl-late", provider, "Late Night Drive", null, trackCount = 4, isOwned = true),
        Playlist("pl-saved", provider, "Saved this week", null, trackCount = 3),
    )

    fun home(provider: Provider): HomeFeed {
        val tracks = tracks(provider)
        val quickPicks = tracks.take(4).map { HomeItem.TrackItem(it) }
        val releases = albums(provider).map { HomeItem.AlbumItem(it) }
        val mixes = playlists(provider).take(2).map { HomeItem.PlaylistItem(it) }
        return HomeFeed(
            provider = provider,
            greeting = "Good evening",
            sections = listOf(
                HomeSection("flow", "Flow", "Built from your listening", quickPicks),
                HomeSection("new", "New releases", null, releases),
                HomeSection("mixes", "Made for you", null, mixes),
            ),
        )
    }

    fun library(provider: Provider) = LibrarySnapshot(
        provider = provider,
        tracks = tracks(provider),
        albums = albums(provider),
        artists = artists(provider),
        playlists = playlists(provider),
        downloads = downloads(),
    )

    fun album(provider: Provider, albumId: String): Album =
        albums(provider).firstOrNull { it.id == albumId }
            ?: error("Unknown album $albumId for ${provider.label}")

    fun playlist(provider: Provider, playlistId: String): PlaylistDetail {
        val playlist = playlists(provider).firstOrNull { it.id == playlistId }
            ?: error("Unknown playlist $playlistId for ${provider.label}")
        return PlaylistDetail(playlist, tracks(provider).take(playlist.trackCount))
    }

    fun search(provider: Provider, query: String, filter: SearchFilter): SearchResults {
        val needle = query.trim()
        fun matches(value: String) = needle.isEmpty() || value.contains(needle, ignoreCase = true)
        fun wants(candidate: SearchFilter) = filter == SearchFilter.ALL || filter == candidate

        return SearchResults(
            provider = provider,
            query = query,
            tracks = if (wants(SearchFilter.TRACKS)) {
                tracks(provider).filter { matches(it.title) || matches(it.artistLine) }
            } else {
                emptyList()
            },
            albums = if (wants(SearchFilter.ALBUMS)) {
                albums(provider).filter { matches(it.title) || matches(it.artist) }
            } else {
                emptyList()
            },
            artists = if (wants(SearchFilter.ARTISTS)) {
                artists(provider).filter { matches(it.name) }
            } else {
                emptyList()
            },
            playlists = if (wants(SearchFilter.PLAYLISTS)) {
                playlists(provider).filter { matches(it.title) }
            } else {
                emptyList()
            },
        )
    }

    fun lyrics(trackId: String): Lyrics = Lyrics(
        trackId = trackId,
        source = "demo",
        synced = true,
        lines = listOf(
            LyricLine(0, ""),
            LyricLine(4_000, "The ferry leaves the harbour slow"),
            LyricLine(9_500, "and the lights come on along the row"),
            LyricLine(15_000, "I keep your voice inside the radio"),
            LyricLine(21_000, ""),
            LyricLine(24_000, "Night ferry, carry me home"),
        ),
    )

    fun downloads(): List<DownloadItem> = listOf(
        DownloadItem(
            trackId = "dz-1",
            title = "Night Ferry",
            artist = "Astrid Lind",
            state = DownloadState.DONE,
            progress = 1f,
            bytesOnDisk = 41_800_000,
            quality = QualityPolicy.describe(Provider.DEEZER, QualityTier.LOSSLESS),
        ),
        DownloadItem(
            trackId = "dz-3",
            title = "Slow Tide",
            artist = "Kaito Mori",
            state = DownloadState.RUNNING,
            progress = 0.42f,
            bytesOnDisk = 18_200_000,
            quality = QualityPolicy.describe(Provider.DEEZER, QualityTier.LOSSLESS),
        ),
    )
}
