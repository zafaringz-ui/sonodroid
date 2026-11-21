package dev.sonora.core.deezer

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import dev.sonora.core.DeezerAccountSession
import dev.sonora.core.DeezerArtistTopTracks
import dev.sonora.core.PublicCatalogClient
import dev.sonora.core.SonoraCoreClient
import dev.sonora.core.model.AccountSummary
import dev.sonora.core.model.Album
import dev.sonora.core.model.Artist
import dev.sonora.core.model.AuthState
import dev.sonora.core.model.CoreEvent
import dev.sonora.core.model.DownloadItem
import dev.sonora.core.model.HomeFeed
import dev.sonora.core.model.HomeItem
import dev.sonora.core.model.HomeSection
import dev.sonora.core.model.LibrarySnapshot
import dev.sonora.core.model.Lyrics
import dev.sonora.core.lyrics.LyricsQuery
import dev.sonora.core.lyrics.LyricsRepository
import dev.sonora.core.model.NetworkKind
import dev.sonora.core.model.PlaybackState
import dev.sonora.core.model.PlanTier
import dev.sonora.core.model.PlaylistDetail
import dev.sonora.core.model.Provider
import dev.sonora.core.model.ProviderAuth
import dev.sonora.core.model.QualityPreference
import dev.sonora.core.model.RepeatMode
import dev.sonora.core.model.SearchFilter
import dev.sonora.core.model.SearchResults
import dev.sonora.core.model.StreamPurpose
import dev.sonora.core.model.StreamQuality
import dev.sonora.core.model.QualityTier
import dev.sonora.core.model.Track
import java.util.concurrent.atomic.AtomicBoolean
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
import kotlinx.serialization.json.JsonObject

/** Public-catalog client used in debug builds; optional sign-in adds full-track playback. */
class DeezerPublicCoreClient(
    context: Context,
    private val scope: CoroutineScope,
    private val api: DeezerApiClient = DeezerApiClient(),
) : SonoraCoreClient, DeezerArtistTopTracks, PublicCatalogClient, DeezerAccountSession {
    private val account = DeezerPrivateSession()
    private val lyricsRepository = LyricsRepository()
    override val media3Player: Player get() = player
    private val player = ExoPlayer.Builder(context.applicationContext)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                DeezerRoutingDataSourceFactory(
                    DefaultHttpDataSource.Factory()
                        .setUserAgent(DeezerApiClient.USER_AGENT)
                        .setConnectTimeoutMs(10_000)
                        .setReadTimeoutMs(15_000),
                    account,
                ),
            ),
        )
        .build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
    }
    private val closed = AtomicBoolean(false)
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    override val queue: StateFlow<List<Track>> = _queue.asStateFlow()
    private val _playback = MutableStateFlow(PlaybackState())
    override val playback: StateFlow<PlaybackState> = _playback.asStateFlow()
    private val _auth = MutableStateFlow(
        listOf(ProviderAuth(Provider.DEEZER), ProviderAuth(Provider.YOUTUBE_MUSIC)),
    )
    override val auth: StateFlow<List<ProviderAuth>> = _auth.asStateFlow()
    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    override val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()
    private val _events = MutableSharedFlow<CoreEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<CoreEvent> = _events.asSharedFlow()
    override val isDemoMode: Boolean = false

    private val preferences = mutableMapOf<Provider, QualityPreference>()
    private var network = NetworkKind.WIFI
    private var ticker: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = syncPlayback()
            override fun onPlaybackStateChanged(playbackState: Int) = syncPlayback()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = syncPlayback()
            override fun onPlayerError(error: PlaybackException) {
                val track = _playback.value.track
                _playback.value = _playback.value.copy(
                    isPlaying = false,
                    error = when {
                        network == NetworkKind.OFFLINE -> "You are offline. Reconnect and try again."
                        !account.isConnected() && track?.previewUrl.isNullOrBlank() -> "This track has no 30-second preview. Sign in to try full-track playback."
                        account.isConnected() -> "The full track could not be played. Your Deezer session may have expired; sign in again."
                        else -> "The preview could not be played. It may have expired; try again."
                    },
                )
                emit(CoreEvent.Failure(_playback.value.error.orEmpty()))
                emit(CoreEvent.PlaybackChanged(_playback.value))
            }
        })
        ticker = scope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                syncPlayback()
                delay(250)
            }
        }
    }

    override suspend fun home(provider: Provider): HomeFeed {
        requireDeezer(provider)
        val root = api.get("chart")
        val tracks = root.obj("tracks")?.array("data").orEmpty().mapNotNull { it.asObject()?.toTrack() }
        val albums = root.obj("albums")?.array("data").orEmpty().mapNotNull { it.asObject()?.toAlbum() }
        val artists = root.obj("artists")?.array("data").orEmpty().mapNotNull { it.asObject()?.toArtist() }
        return HomeFeed(
            provider = Provider.DEEZER,
            greeting = "Deezer charts",
            sections = listOfNotNull(
                tracks.takeIf { it.isNotEmpty() }?.let {
                    HomeSection("deezer-chart-tracks", "Top tracks", items = it.map { track -> HomeItem.TrackItem(track) })
                },
                albums.takeIf { it.isNotEmpty() }?.let {
                    HomeSection("deezer-chart-albums", "Popular albums", items = it.map { album -> HomeItem.AlbumItem(album) })
                },
                artists.takeIf { it.isNotEmpty() }?.let {
                    HomeSection("deezer-chart-artists", "Popular artists", items = it.map { artist -> HomeItem.ArtistItem(artist) })
                },
            ),
        )
    }

    override suspend fun search(provider: Provider, query: String, filter: SearchFilter): SearchResults {
        requireDeezer(provider)
        val term = query.trim()
        if (term.isEmpty()) return SearchResults(Provider.DEEZER, term)
        if (filter == SearchFilter.PLAYLISTS || filter == SearchFilter.VIDEOS) {
            throw PublicModeUnavailableException("Playlist and video search are not available in public mode.")
        }
        val includeTracks = filter == SearchFilter.ALL || filter == SearchFilter.TRACKS
        val includeAlbums = filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS
        val includeArtists = filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS
        val tracks = if (includeTracks) {
            api.get("search", mapOf("q" to term, "limit" to SEARCH_LIMIT.toString()))
                .array("data").orEmpty().mapNotNull { it.asObject()?.toTrack() }
        } else emptyList()
        val albums = if (includeAlbums) {
            api.get("search/album", mapOf("q" to term, "limit" to SEARCH_LIMIT.toString()))
                .array("data").orEmpty().mapNotNull { it.asObject()?.toAlbum() }
        } else emptyList()
        val artists = if (includeArtists) {
            api.get("search/artist", mapOf("q" to term, "limit" to SEARCH_LIMIT.toString()))
                .array("data").orEmpty().mapNotNull { it.asObject()?.toArtist() }
        } else emptyList()
        return SearchResults(Provider.DEEZER, term, tracks, albums, artists)
    }

    override suspend fun album(provider: Provider, albumId: String): Album {
        requireDeezer(provider)
        requireValidId(albumId)
        val details = api.get("album/$albumId")
        val tracks = api.get("album/$albumId/tracks", mapOf("limit" to "100"))
            .array("data").orEmpty().mapNotNull {
                it.asObject()?.toTrack(albumId, details.string("title"), details.bestImage("cover_xl", "cover_big", "cover"))
            }
        return details.toAlbum(tracks)
            ?: throw DeezerApiException.Malformed("Deezer album response is missing required fields")
    }

    /** Available for callers that add an artist-detail screen; the current UI has no such route. */
    override suspend fun artistTopTracks(artistId: String): List<Track> {
        requireValidId(artistId)
        return api.get("artist/$artistId/top", mapOf("limit" to "20"))
            .array("data").orEmpty().mapNotNull { it.asObject()?.toTrack() }
    }

    override suspend fun library(provider: Provider): LibrarySnapshot =
        throw PublicModeUnavailableException("Library and downloads are not available in public mode.")
    override suspend fun playlist(provider: Provider, playlistId: String): PlaylistDetail = unavailable("Playlists")
    override suspend fun lyrics(trackId: String): Lyrics? {
        val track = _queue.value.firstOrNull { it.id == trackId } ?: return null
        if (track.provider != Provider.DEEZER) return null
        return lyricsRepository.lyrics(
            LyricsQuery(
                trackId = track.id,
                title = track.title,
                artist = track.artistLine,
                album = track.album,
                durationMs = track.durationMs,
            ),
        )
    }

    override fun beginSignIn(provider: Provider) {
        emit(CoreEvent.Message(if (provider == Provider.DEEZER) "Continue Deezer sign-in in the secure WebView." else "Sign-in is not available in public mode."))
    }

    override suspend fun signIn(arl: String) {
        val accountInfo = account.connect(arl)
        val plan = when (accountInfo.plan) {
            DeezerPrivateSession.PublicPlan.FREE -> PlanTier.FREE
            DeezerPrivateSession.PublicPlan.PREMIUM -> PlanTier.PREMIUM
            DeezerPrivateSession.PublicPlan.HIFI -> PlanTier.HIFI
        }
        val auth = ProviderAuth(
            provider = Provider.DEEZER,
            state = AuthState.SIGNED_IN,
            account = AccountSummary(accountInfo.id, accountInfo.displayName, plan, plan == PlanTier.HIFI),
        )
        _auth.value = _auth.value.map { if (it.provider == Provider.DEEZER) auth else it }
        emit(CoreEvent.AuthChanged(auth))
    }

    override suspend fun restore(arl: String) = signIn(arl)

    override suspend fun signOut() {
        account.disconnect()
        player.pause()
        player.clearMediaItems()
        _queue.value = emptyList()
        _playback.value = PlaybackState()
        val auth = ProviderAuth(Provider.DEEZER)
        _auth.value = _auth.value.map { if (it.provider == Provider.DEEZER) auth else it }
        emit(CoreEvent.AuthChanged(auth))
    }

    override suspend fun completeSignIn(
        provider: Provider,
        accountLabel: String,
        plan: PlanTier,
    ): Result<Unit> = Result.failure(PublicModeUnavailableException("Sign-in is not available in public mode."))

    override suspend fun signOut(provider: Provider) {
        if (provider == Provider.DEEZER) signOut()
    }

    override fun setNetwork(network: NetworkKind) {
        this.network = network
        if (network == NetworkKind.OFFLINE) {
            player.pause()
            _playback.value = _playback.value.copy(isPlaying = false, error = "You are offline. Reconnect and try again.")
        }
    }

    override fun setQualityPreference(provider: Provider, preference: QualityPreference) {
        preferences[provider] = preference
    }

    override fun qualityPreference(provider: Provider): QualityPreference =
        preferences[provider] ?: QualityPreference.AUTO

    override fun qualityLadder(provider: Provider): List<StreamQuality> =
        if (provider == Provider.DEEZER) listOf(if (account.isConnected()) FULL_QUALITY else PREVIEW_QUALITY) else emptyList()

    override fun resolvedQuality(provider: Provider, purpose: StreamPurpose): StreamQuality? =
        when {
            provider != Provider.DEEZER || purpose != StreamPurpose.STREAMING -> null
            account.isConnected() -> FULL_QUALITY
            else -> PREVIEW_QUALITY
        }

    override fun playTracks(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val selected = tracks.getOrNull(startIndex.coerceIn(tracks.indices)) ?: tracks.first()
        val useFullTracks = account.isConnected()
        if (!useFullTracks && selected.previewUrl.isNullOrBlank()) {
            player.pause()
            player.clearMediaItems()
            _queue.value = emptyList()
            _playback.value = PlaybackState(
                track = selected,
                queueIndex = 0,
                queueSize = 0,
                durationMs = selected.durationMs,
                error = "This track has no 30-second preview. Choose another track.",
            )
            emit(CoreEvent.PlaybackChanged(_playback.value))
            return
        }
        val playable = tracks.filter { useFullTracks || !it.previewUrl.isNullOrBlank() }
        val currentIndex = playable.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
        val queueTracks = if (useFullTracks) playable.map { it.copy(previewUrl = null) } else playable
        _queue.value = queueTracks
        val items = playable.map { track -> buildMediaItem(track, useFullTracks) }
        player.setMediaItems(items, currentIndex, 0L)
        player.prepare()
        if (network == NetworkKind.OFFLINE) {
            _playback.value = PlaybackState(track = selected, queueIndex = currentIndex, queueSize = playable.size, error = "You are offline. Reconnect and try again.")
        } else {
            player.play()
        }
        syncPlayback()
        emit(CoreEvent.QueueChanged(playable.size, currentIndex))
    }

    override fun playQueueIndex(index: Int) {
        if (index !in _queue.value.indices || index !in 0 until player.mediaItemCount) return
        _playback.value = _playback.value.copy(error = null)
        player.seekTo(index, 0L)
        player.prepare()
        player.play()
    }

    override fun playPause() {
        if (_playback.value.track == null || player.mediaItemCount == 0) return
        _playback.value = _playback.value.copy(error = null)
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED || player.playerError != null) player.prepare()
            player.play()
        }
    }

    override fun next() = player.seekToNextMediaItem()
    override fun previous() = if (player.currentPosition > 3_000L) player.seekTo(0L) else player.seekToPreviousMediaItem()
    override fun seekTo(positionMs: Long) = player.seekTo(positionMs.coerceAtLeast(0L))
    override fun setRepeat(mode: RepeatMode) {
        player.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        syncPlayback()
    }
    override fun setShuffle(enabled: Boolean) {
        player.shuffleModeEnabled = enabled
        syncPlayback()
    }
    override fun moveInQueue(from: Int, to: Int) {
        val list = _queue.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val currentId = playback.value.track?.id
        list.add(to, list.removeAt(from))
        _queue.value = list
        if (player.mediaItemCount > 0) player.moveMediaItem(from, to)
        _playback.value = _playback.value.copy(queueIndex = list.indexOfFirst { it.id == currentId }, queueSize = list.size)
    }
    override fun removeFromQueue(index: Int) {
        if (index !in _queue.value.indices) return
        val list = _queue.value.toMutableList().also { it.removeAt(index) }
        _queue.value = list
        if (index in 0 until player.mediaItemCount) player.removeMediaItem(index)
        if (list.isEmpty()) _playback.value = PlaybackState() else syncPlayback()
    }
    override fun addToQueue(track: Track, playNext: Boolean) {
        val useFullTracks = account.isConnected()
        if (!useFullTracks && track.previewUrl.isNullOrBlank()) {
            _playback.value = _playback.value.copy(error = "This track has no 30-second preview. Sign in for full tracks.")
            return
        }
        val at = if (playNext) (player.currentMediaItemIndex + 1).coerceAtLeast(0) else player.mediaItemCount
        val queuedTrack = if (useFullTracks) track.copy(previewUrl = null) else track
        _queue.value = _queue.value.toMutableList().apply { add(at.coerceAtMost(size), queuedTrack) }
        player.addMediaItem(at, buildMediaItem(track, useFullTracks))
        syncPlayback()
    }
    override fun setVolume(volume: Float) { player.volume = volume.coerceIn(0f, 1f) }
    override fun setCrossfade(seconds: Int) = Unit

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            ticker?.cancel()
            player.release()
        }
    }

    private fun syncPlayback() {
        val index = player.currentMediaItemIndex
        val track = _queue.value.getOrNull(index)
        if (track == null) {
            val previous = _playback.value.track
            if (player.mediaItemCount == 0 && previous?.previewUrl.isNullOrBlank()) return
            if (previous != null) _playback.value = PlaybackState()
            return
        }
        val repeat = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }
        _playback.value = _playback.value.copy(
            track = track,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0)
                ?: if (account.isConnected()) track.durationMs else PREVIEW_DURATION_MS,
            bufferedMs = player.bufferedPosition.coerceAtLeast(0),
            repeat = repeat,
            shuffle = player.shuffleModeEnabled,
            queueIndex = index.coerceAtLeast(0),
            queueSize = _queue.value.size,
            volume = player.volume,
            error = if (player.playerError == null) null else _playback.value.error,
        )
        emit(CoreEvent.PlaybackChanged(_playback.value))
    }

    private fun requireDeezer(provider: Provider) {
        if (provider != Provider.DEEZER) throw PublicModeUnavailableException("${provider.label} is not available in public mode.")
    }

    private fun requireValidId(id: String) {
        require(id.isNotBlank() && id.all(Char::isDigit)) { "Invalid Deezer identifier." }
    }

    private suspend fun <T> unavailable(feature: String): T =
        throw PublicModeUnavailableException("$feature are not available in public mode.")

    private fun emit(event: CoreEvent) { _events.tryEmit(event) }

    private companion object {
        const val SEARCH_LIMIT = 25
        const val PREVIEW_DURATION_MS = 30_000L
        val PREVIEW_QUALITY = StreamQuality(
            tier = QualityTier.NORMAL,
            codec = "MP3",
            bitrateKbps = 128,
            sampleRateHz = 44_100,
            bitDepth = 16,
            label = "Deezer preview · 30 s",
        )
        val FULL_QUALITY = StreamQuality(
            tier = QualityTier.NORMAL,
            codec = "MP3",
            bitrateKbps = 128,
            sampleRateHz = 44_100,
            bitDepth = 16,
            label = "Deezer full track · MP3 128",
        )
    }
}

private fun buildMediaItem(track: Track, fullTrack: Boolean): MediaItem {
    val uri = if (fullTrack) {
        Uri.Builder().scheme("sonora").authority("deezer").appendPath("full").appendPath("${track.id}.mp3").build()
    } else {
        Uri.parse(track.previewUrl.orEmpty())
    }
    val builder = MediaItem.Builder()
        .setMediaId(track.id)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artistLine)
                .setAlbumTitle(track.album)
                .setArtworkUri(track.artworkUrl?.let(Uri::parse))
                .setIsPlayable(true)
                .build(),
        )
    if (fullTrack) builder.setMimeType(MimeTypes.AUDIO_MPEG)
    return builder.build()
}

class PublicModeUnavailableException(message: String) : IllegalStateException(message)

private fun JsonObject.toTrack(
    albumIdOverride: String? = null,
    albumTitleOverride: String? = null,
    albumArtworkOverride: String? = null,
): Track? {
    val id = string("id") ?: return null
    val artist = obj("artist")
    val album = obj("album")
    val title = string("title") ?: return null
    val duration = long("duration") ?: 0L
    return Track(
        id = id,
        provider = Provider.DEEZER,
        title = title,
        artists = listOfNotNull(artist?.string("name")),
        album = albumTitleOverride ?: album?.string("title"),
        albumId = albumIdOverride ?: album?.string("id"),
        durationMs = duration * 1_000L,
        artworkUrl = album?.bestImage("cover_xl", "cover_big", "cover")
            ?: albumArtworkOverride
            ?: artist?.bestImage("picture_xl", "picture_big", "picture"),
        isExplicit = bool("explicit_lyrics") ?: false,
        previewUrl = string("preview")?.takeIf(String::isNotBlank),
    )
}

private fun JsonObject.toAlbum(tracks: List<Track> = emptyList()): Album? {
    val id = string("id") ?: return null
    val artist = obj("artist")
    return Album(
        id = id,
        provider = Provider.DEEZER,
        title = string("title") ?: return null,
        artist = artist?.string("name") ?: "Unknown artist",
        year = string("release_date")?.take(4)?.toIntOrNull(),
        artworkUrl = bestImage("cover_xl", "cover_big", "cover"),
        trackCount = (long("nb_tracks") ?: tracks.size.toLong()).toInt(),
        tracks = tracks,
    )
}

private fun JsonObject.toArtist(): Artist? {
    val id = string("id") ?: return null
    val name = string("name") ?: return null
    return Artist(
        id = id,
        provider = Provider.DEEZER,
        name = name,
        artworkUrl = bestImage("picture_xl", "picture_big", "picture"),
        followerCount = long("nb_fan"),
    )
}

private fun JsonObject.bestImage(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key -> string(key)?.takeIf(String::isNotBlank) }
