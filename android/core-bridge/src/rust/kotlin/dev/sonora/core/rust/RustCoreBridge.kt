package dev.sonora.core.rust

import android.content.Context
import dev.sonora.core.SonoraCoreClient
import dev.sonora.core.ffi.AccountStatus
import dev.sonora.core.ffi.Album
import dev.sonora.core.ffi.Artist
import dev.sonora.core.ffi.CoreConfig
import dev.sonora.core.ffi.CoreError
import dev.sonora.core.ffi.CoreEvent
import dev.sonora.core.ffi.CoreListener
import dev.sonora.core.ffi.HomeItem
import dev.sonora.core.ffi.LibrarySnapshot
import dev.sonora.core.ffi.Playlist
import dev.sonora.core.ffi.ProviderAuth
import dev.sonora.core.ffi.SearchFilter
import dev.sonora.core.ffi.SonoraCore
import dev.sonora.core.ffi.StreamQuality
import dev.sonora.core.ffi.Track
import dev.sonora.core.model.Album as AlbumModel
import dev.sonora.core.model.Artist as ArtistModel
import dev.sonora.core.model.AuthState
import dev.sonora.core.model.CoreEvent as EventModel
import dev.sonora.core.model.DownloadItem
import dev.sonora.core.model.HomeFeed
import dev.sonora.core.model.HomeItem as HomeItemModel
import dev.sonora.core.model.HomeSection
import dev.sonora.core.model.LibrarySnapshot as LibraryModel
import dev.sonora.core.model.Lyrics
import dev.sonora.core.model.NetworkKind
import dev.sonora.core.model.PlaybackState
import dev.sonora.core.model.PlanTier
import dev.sonora.core.model.Playlist as PlaylistModel
import dev.sonora.core.model.PlaylistDetail
import dev.sonora.core.model.Provider
import dev.sonora.core.model.ProviderAuth as AuthModel
import dev.sonora.core.model.QualityPreference
import dev.sonora.core.model.QualityTier
import dev.sonora.core.model.RepeatMode
import dev.sonora.core.model.SearchFilter as SearchFilterModel
import dev.sonora.core.model.SearchResults
import dev.sonora.core.model.StreamPurpose
import dev.sonora.core.model.StreamQuality as QualityModel
import dev.sonora.core.model.Track as TrackModel
import dev.sonora.core.model.AccountSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * UniFFI adapter.
 *
 * Part of the `-Psonora.rust=true` source set: it references the Kotlin bindings that
 * `scripts/gen-bindings.sh` / the `Android native` CI job writes into
 * `src/generated/`, so a default build never sees it.
 *
 * This file was reconciled against uniffi 0.28.3's Kotlin backend, not guessed:
 *  * objects generate an interface plus an `open class <Name> : Disposable, AutoCloseable`,
 *    so `SonoraCore` is returned directly and closed via `AutoCloseable`;
 *  * a `with_foreign` trait generates a plain `interface`, so `object : CoreListener`
 *    is a legal implementation;
 *  * records are `data class` with `var` fields by default;
 *  * flat enums are Kotlin `enum class` with SHOUTY_SNAKE variants;
 *  * `#[uniffi(flat_error)]` generates `sealed class CoreError(message: String)` with
 *    `CoreError.NotImplemented(message)`-style subclasses, not `Exception` subclasses
 *    of the same name;
 *  * async exports become `suspend fun` that throw `CoreError`. Kotlin does not
 *    enforce checked exceptions, but the ViewModels already wrap these calls in
 *    `runCatching`; `@Throws` is kept as documentation for the JVM signature.
 */
class UniffiCoreClient(
    private val core: SonoraCore,
    private val scope: CoroutineScope,
) : SonoraCoreClient {

    private val _playback = MutableStateFlow(core.playbackState().toModel())
    override val playback: StateFlow<PlaybackState> = _playback.asStateFlow()

    private val _queue = MutableStateFlow(core.queueSnapshot().tracks.map(Track::toModel))
    override val queue: StateFlow<List<TrackModel>> = _queue.asStateFlow()

    private val _auth = MutableStateFlow(core.authSnapshot().map(ProviderAuth::toModel))
    override val auth: StateFlow<List<AuthModel>> = _auth.asStateFlow()

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    override val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val _events = MutableSharedFlow<EventModel>(extraBufferCapacity = 64)
    override val events: SharedFlow<EventModel> = _events.asSharedFlow()

    override val isDemoMode: Boolean = false

    private var ticker: Job? = null

    init {
        core.setListener(
            object : CoreListener {
                override fun onCoreEvent(event: CoreEvent) {
                    when (event) {
                        is CoreEvent.PlaybackChanged ->
                            _playback.value = event.state.toModel()
                        is CoreEvent.QueueChanged -> {
                            _queue.value = core.queueSnapshot().tracks.map(Track::toModel)
                        }
                        is CoreEvent.AuthChanged -> {
                            _auth.value = core.authSnapshot().map(ProviderAuth::toModel)
                        }
                        is CoreEvent.Message -> _events.tryEmit(EventModel.Message(event.text))
                        is CoreEvent.Failed -> _events.tryEmit(EventModel.Failure(event.message))
                    }
                }
            },
        )
        startTicker()
    }

    /**
     * Rust computes the position on demand from its monotonic clock; there is no
     * event per millisecond, so the UI polls while something is playing.
     */
    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(POSITION_TICK_MS)
                val state = core.playbackState()
                _playback.value = state.toModel()
            }
        }
    }

    // ---- catalogue ----------------------------------------------------------

    @Throws(CoreError::class)
    override suspend fun home(provider: Provider): HomeFeed =
        core.home(provider.toFfi()).toModel()

    @Throws(CoreError::class)
    override suspend fun library(provider: Provider): LibraryModel =
        core.library(provider.toFfi()).toModel().also { snapshot ->
            _downloads.value = snapshot.downloads
        }

    @Throws(CoreError::class)
    override suspend fun search(
        provider: Provider,
        query: String,
        filter: SearchFilterModel,
    ): SearchResults = core.search(provider.toFfi(), query, filter.toFfi()).toModel()

    @Throws(CoreError::class)
    override suspend fun album(provider: Provider, albumId: String): AlbumModel =
        core.album(provider.toFfi(), albumId).toModel()

    @Throws(CoreError::class)
    override suspend fun playlist(provider: Provider, playlistId: String): PlaylistDetail {
        val detail = core.playlist(provider.toFfi(), playlistId)
        return PlaylistDetail(detail.playlist.toModel(), detail.tracks.map(Track::toModel))
    }

    @Throws(CoreError::class)
    override suspend fun lyrics(trackId: String): Lyrics {
        val lyrics = core.lyrics(trackId)
        return Lyrics(
            trackId = lyrics.trackId,
            source = lyrics.source,
            synced = lyrics.synced,
            romanized = lyrics.romanized,
            lines = lyrics.lines.map { dev.sonora.core.model.LyricLine(it.timeMs, it.text) },
        )
    }

    // ---- accounts -----------------------------------------------------------

    override fun beginSignIn(provider: Provider) = core.signInStarted(provider.toFfi())

    override suspend fun completeSignIn(
        provider: Provider,
        accountLabel: String,
        plan: PlanTier,
    ): Result<Unit> = runCatching {
        // Validation of the stored ARL/cookies happens inside the Rust provider
        // client once Phase 3/4 land; today this marks the session.
        //
        // `slug` only exists on the Rust `Provider` enum, so it must be called on
        // the FFI value; `model.Provider.slug` is a different, unrelated property.
        val ffiProvider = provider.toFfi()
        core.markSignedIn(
            ffiProvider,
            accountId = ffiProvider.slug(),
            displayName = accountLabel,
            plan = plan.toFfi(),
        )
    }

    override suspend fun signOut(provider: Provider) = core.markSignedOut(provider.toFfi())

    // ---- quality ------------------------------------------------------------

    override fun setNetwork(network: NetworkKind) = core.setNetwork(network.toFfi())

    override fun setQualityPreference(provider: Provider, preference: QualityPreference) =
        core.setQualityPreference(provider.toFfi(), preference.toFfi())

    override fun qualityPreference(provider: Provider): QualityPreference =
        core.qualityPreference(provider.toFfi()).toModel()

    override fun qualityLadder(provider: Provider): List<QualityModel> =
        core.qualityLadder(provider.toFfi()).map(StreamQuality::toModel)

    @Throws(CoreError::class)
    override fun resolvedQuality(provider: Provider, purpose: StreamPurpose): QualityModel? =
        runCatching {
            core.resolvedQuality(provider.toFfi(), purpose.toFfi()).toModel()
        }.getOrNull()

    // ---- transport ----------------------------------------------------------

    override fun playTracks(tracks: List<TrackModel>, startIndex: Int) =
        core.playTracks(tracks.map { it.toFfi() }, startIndex)

    override fun playQueueIndex(index: Int) = core.playQueueIndex(index)

    override fun playPause() = core.playPause()

    override fun next() = core.nextTrack()

    override fun previous() = core.previousTrack()

    override fun seekTo(positionMs: Long) = core.seek(positionMs)

    override fun setRepeat(mode: RepeatMode) = core.setRepeat(mode.toFfi())

    override fun setShuffle(enabled: Boolean) = core.setShuffle(enabled)

    // Rust returns false when either index is out of range; the client interface
    // has no place to report that, so the boolean is deliberately ignored.
    override fun moveInQueue(from: Int, to: Int) {
        core.moveInQueue(from, to)
    }

    override fun removeFromQueue(index: Int) = core.removeFromQueue(index)

    override fun addToQueue(track: TrackModel, playNext: Boolean) =
        core.addToQueue(track.toFfi(), playNext)

    override fun setVolume(volume: Float) = core.setVolume(volume)

    override fun setCrossfade(seconds: Int) = core.setCrossfade(seconds)

    override fun close() {
        ticker?.cancel()
        ticker = null
        core.close()
    }

    companion object {
        private const val POSITION_TICK_MS = 500L

        /**
         * Entry point used by `AppGraph` through reflection, so the default build
         * (without generated bindings) still compiles.
         */
        @JvmStatic
        fun create(context: Context, scope: CoroutineScope): SonoraCoreClient {
            // `useFixtures` must stay true until the catalogue clients exist:
            // `require_online()` in api.rs returns NotImplemented for every
            // catalogue call when it is false, so Home/Library/Search would fail
            // immediately on a correctly loaded core. Keep this in step with
            // `CoreFactory.ADAPTER_USES_FIXTURES` in the app module, which decides
            // whether the debug badge says "fixtures" or "live".
            val config = CoreConfig(
                dataDir = context.filesDir.absolutePath,
                cacheDir = context.cacheDir.absolutePath,
                defaultProvider = dev.sonora.core.ffi.Provider.DEEZER,
                useFixtures = true,
                crossfadeSeconds = 0,
                network = dev.sonora.core.ffi.NetworkKind.WIFI,
            )
            return UniffiCoreClient(SonoraCore(config), scope)
        }
    }
}

// ---- mapping ---------------------------------------------------------------

private fun Provider.toFfi(): dev.sonora.core.ffi.Provider = when (this) {
    Provider.DEEZER -> dev.sonora.core.ffi.Provider.DEEZER
    Provider.YOUTUBE_MUSIC -> dev.sonora.core.ffi.Provider.YOUTUBE_MUSIC
}

private fun dev.sonora.core.ffi.Provider.toModel(): Provider = when (this) {
    dev.sonora.core.ffi.Provider.DEEZER -> Provider.DEEZER
    dev.sonora.core.ffi.Provider.YOUTUBE_MUSIC -> Provider.YOUTUBE_MUSIC
}

private fun Track.toModel(): TrackModel = TrackModel(
    id = id,
    provider = provider.toModel(),
    title = title,
    artists = artists,
    album = album,
    albumId = albumId,
    durationMs = durationMs,
    artworkUrl = artworkUrl,
    isExplicit = isExplicit,
    isVideo = isVideo,
    qualityLabel = qualityLabel,
)

private fun TrackModel.toFfi(): Track = Track(
    id = id,
    provider = provider.toFfi(),
    title = title,
    artists = artists,
    album = album,
    albumId = albumId,
    durationMs = durationMs,
    artworkUrl = artworkUrl,
    isExplicit = isExplicit,
    isVideo = isVideo,
    qualityLabel = qualityLabel,
)

private fun Album.toModel(): AlbumModel = AlbumModel(
    id = id,
    provider = provider.toModel(),
    title = title,
    artist = artist,
    year = year,
    artworkUrl = artworkUrl,
    trackCount = trackCount,
    qualityLabel = qualityLabel,
)

private fun Artist.toModel(): ArtistModel = ArtistModel(
    id = id,
    provider = provider.toModel(),
    name = name,
    artworkUrl = artworkUrl,
    followerCount = followerCount,
)

private fun Playlist.toModel(): PlaylistModel = PlaylistModel(
    id = id,
    provider = provider.toModel(),
    title = title,
    description = description,
    trackCount = trackCount,
    artworkUrl = artworkUrl,
    isOwned = isOwned,
)

private fun HomeItem.toModel(): HomeItemModel = when (this) {
    is HomeItem.TrackItem -> HomeItemModel.TrackItem(track.toModel())
    is HomeItem.AlbumItem -> HomeItemModel.AlbumItem(album.toModel())
    is HomeItem.PlaylistItem -> HomeItemModel.PlaylistItem(playlist.toModel())
    is HomeItem.ArtistItem -> HomeItemModel.ArtistItem(artist.toModel())
}

private fun dev.sonora.core.ffi.HomeFeed.toModel(): HomeFeed = HomeFeed(
    provider = provider.toModel(),
    greeting = greeting,
    sections = sections.map { section ->
        HomeSection(
            id = section.id,
            title = section.title,
            subtitle = section.subtitle,
            items = section.items.map(HomeItem::toModel),
        )
    },
)

private fun LibrarySnapshot.toModel(): LibraryModel = LibraryModel(
    provider = provider.toModel(),
    tracks = tracks.map(Track::toModel),
    albums = albums.map(Album::toModel),
    artists = artists.map(Artist::toModel),
    playlists = playlists.map(Playlist::toModel),
    downloads = downloads.map { download ->
        DownloadItem(
            trackId = download.trackId,
            title = download.title,
            artist = download.artist,
            state = when (download.state) {
                dev.sonora.core.ffi.DownloadState.QUEUED -> dev.sonora.core.model.DownloadState.QUEUED
                dev.sonora.core.ffi.DownloadState.RUNNING -> dev.sonora.core.model.DownloadState.RUNNING
                dev.sonora.core.ffi.DownloadState.DONE -> dev.sonora.core.model.DownloadState.DONE
                dev.sonora.core.ffi.DownloadState.FAILED -> dev.sonora.core.model.DownloadState.FAILED
            },
            progress = download.progress,
            bytesOnDisk = download.bytesOnDisk,
            quality = download.quality.toModel(),
        )
    },
)

private fun dev.sonora.core.ffi.SearchResults.toModel(): SearchResults = SearchResults(
    provider = provider.toModel(),
    query = query,
    tracks = tracks.map(Track::toModel),
    albums = albums.map(Album::toModel),
    artists = artists.map(Artist::toModel),
    playlists = playlists.map(Playlist::toModel),
)

private fun dev.sonora.core.ffi.PlaybackState.toModel(): PlaybackState = PlaybackState(
    track = track?.toModel(),
    isPlaying = isPlaying,
    positionMs = positionMs,
    durationMs = durationMs,
    bufferedMs = bufferedMs,
    repeat = when (repeat) {
        dev.sonora.core.ffi.RepeatMode.OFF -> RepeatMode.OFF
        dev.sonora.core.ffi.RepeatMode.ALL -> RepeatMode.ALL
        dev.sonora.core.ffi.RepeatMode.ONE -> RepeatMode.ONE
    },
    shuffle = shuffle,
    queueIndex = queueIndex,
    queueSize = queueSize,
    volume = volume,
    crossfadeSeconds = (crossfadeMs / 1_000).toInt(),
    outputName = outputName,
)

private fun ProviderAuth.toModel(): AuthModel = AuthModel(
    provider = provider.toModel(),
    state = when (status) {
        AccountStatus.SIGNED_OUT -> AuthState.SIGNED_OUT
        AccountStatus.CONNECTING -> AuthState.CONNECTING
        AccountStatus.SIGNED_IN -> AuthState.SIGNED_IN
        AccountStatus.NEEDS_ATTENTION -> AuthState.NEEDS_ATTENTION
    },
    account = account?.let {
        AccountSummary(
            id = it.id,
            displayName = it.displayName,
            plan = it.plan.toModel(),
            isLosslessCapable = it.isLosslessCapable,
        )
    },
    detail = detail,
)

private fun StreamQuality.toModel(): QualityModel = QualityModel(
    tier = when (tier) {
        dev.sonora.core.ffi.QualityTier.LOW -> QualityTier.LOW
        dev.sonora.core.ffi.QualityTier.NORMAL -> QualityTier.NORMAL
        dev.sonora.core.ffi.QualityTier.HIGH -> QualityTier.HIGH
        dev.sonora.core.ffi.QualityTier.LOSSLESS -> QualityTier.LOSSLESS
        dev.sonora.core.ffi.QualityTier.HIRES -> QualityTier.HIRES
    },
    codec = codec,
    bitrateKbps = bitrateKbps,
    sampleRateHz = sampleRateHz,
    bitDepth = bitDepth,
    label = label,
)

private fun PlanTier.toFfi(): dev.sonora.core.ffi.PlanTier = when (this) {
    PlanTier.FREE -> dev.sonora.core.ffi.PlanTier.FREE
    PlanTier.STANDARD -> dev.sonora.core.ffi.PlanTier.STANDARD
    PlanTier.PREMIUM -> dev.sonora.core.ffi.PlanTier.PREMIUM
    PlanTier.HIFI -> dev.sonora.core.ffi.PlanTier.HIFI
}

private fun dev.sonora.core.ffi.PlanTier.toModel(): PlanTier = when (this) {
    dev.sonora.core.ffi.PlanTier.FREE -> PlanTier.FREE
    dev.sonora.core.ffi.PlanTier.STANDARD -> PlanTier.STANDARD
    dev.sonora.core.ffi.PlanTier.PREMIUM -> PlanTier.PREMIUM
    dev.sonora.core.ffi.PlanTier.HIFI -> PlanTier.HIFI
}

private fun NetworkKind.toFfi(): dev.sonora.core.ffi.NetworkKind = when (this) {
    NetworkKind.WIFI -> dev.sonora.core.ffi.NetworkKind.WIFI
    NetworkKind.CELLULAR -> dev.sonora.core.ffi.NetworkKind.CELLULAR
    NetworkKind.OFFLINE -> dev.sonora.core.ffi.NetworkKind.OFFLINE
}

private fun QualityPreference.toFfi(): dev.sonora.core.ffi.QualityPreference = when (this) {
    QualityPreference.AUTO -> dev.sonora.core.ffi.QualityPreference.AUTO
    QualityPreference.DATA_SAVER -> dev.sonora.core.ffi.QualityPreference.DATA_SAVER
    QualityPreference.BALANCED -> dev.sonora.core.ffi.QualityPreference.BALANCED
    QualityPreference.BEST -> dev.sonora.core.ffi.QualityPreference.BEST
}

private fun dev.sonora.core.ffi.QualityPreference.toModel(): QualityPreference = when (this) {
    dev.sonora.core.ffi.QualityPreference.AUTO -> QualityPreference.AUTO
    dev.sonora.core.ffi.QualityPreference.DATA_SAVER -> QualityPreference.DATA_SAVER
    dev.sonora.core.ffi.QualityPreference.BALANCED -> QualityPreference.BALANCED
    dev.sonora.core.ffi.QualityPreference.BEST -> QualityPreference.BEST
}

private fun StreamPurpose.toFfi(): dev.sonora.core.ffi.StreamPurpose = when (this) {
    StreamPurpose.STREAMING -> dev.sonora.core.ffi.StreamPurpose.STREAMING
    StreamPurpose.DOWNLOAD -> dev.sonora.core.ffi.StreamPurpose.DOWNLOAD
}

private fun RepeatMode.toFfi(): dev.sonora.core.ffi.RepeatMode = when (this) {
    RepeatMode.OFF -> dev.sonora.core.ffi.RepeatMode.OFF
    RepeatMode.ALL -> dev.sonora.core.ffi.RepeatMode.ALL
    RepeatMode.ONE -> dev.sonora.core.ffi.RepeatMode.ONE
}

private fun SearchFilterModel.toFfi(): SearchFilter = when (this) {
    SearchFilterModel.ALL -> SearchFilter.ALL
    SearchFilterModel.TRACKS -> SearchFilter.TRACKS
    SearchFilterModel.ALBUMS -> SearchFilter.ALBUMS
    SearchFilterModel.ARTISTS -> SearchFilter.ARTISTS
    SearchFilterModel.PLAYLISTS -> SearchFilter.PLAYLISTS
    SearchFilterModel.VIDEOS -> SearchFilter.VIDEOS
}
