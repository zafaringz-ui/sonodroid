package dev.sonora.core.demo

import dev.sonora.core.SonoraCoreClient
import dev.sonora.core.model.AccountSummary
import dev.sonora.core.model.Album
import dev.sonora.core.model.AuthState
import dev.sonora.core.model.CoreEvent
import dev.sonora.core.model.DownloadItem
import dev.sonora.core.model.HomeFeed
import dev.sonora.core.model.LibrarySnapshot
import dev.sonora.core.model.Lyrics
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
import dev.sonora.core.model.Track
import dev.sonora.core.quality.QualityPolicy
import kotlinx.coroutines.CoroutineScope
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
 * In-memory implementation of [SonoraCoreClient].
 *
 * It exists for two reasons: the app must be demoable before the Rust `.so` is
 * generated, and every Compose screen needs data for previews and UI tests. The
 * transport logic mirrors the Rust queue closely enough that the UI code is not
 * throwaway.
 */
class DemoCoreClient(
    private val scope: CoroutineScope,
) : SonoraCoreClient {

    private val _playback = MutableStateFlow(PlaybackState())
    override val playback: StateFlow<PlaybackState> = _playback.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    override val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _auth = MutableStateFlow(
        listOf(
            ProviderAuth(Provider.DEEZER),
            ProviderAuth(Provider.YOUTUBE_MUSIC),
        ),
    )
    override val auth: StateFlow<List<ProviderAuth>> = _auth.asStateFlow()

    private val _downloads = MutableStateFlow(DemoLibrary.downloads())
    override val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val _events = MutableSharedFlow<CoreEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<CoreEvent> = _events.asSharedFlow()

    override val isDemoMode: Boolean = true

    private val preferences = mutableMapOf(
        Provider.DEEZER to QualityPreference.AUTO,
        Provider.YOUTUBE_MUSIC to QualityPreference.AUTO,
    )
    private var network: NetworkKind = NetworkKind.WIFI

    private val ticker = scope.launch {
        while (isActive) {
            delay(TICK_MS)
            tick()
        }
    }

    // ---- catalogue ----------------------------------------------------------

    override suspend fun home(provider: Provider): HomeFeed {
        delay(FAKE_LATENCY_MS)
        return DemoLibrary.home(provider)
    }

    override suspend fun library(provider: Provider): LibrarySnapshot {
        delay(FAKE_LATENCY_MS)
        return DemoLibrary.library(provider)
    }

    override suspend fun search(
        provider: Provider,
        query: String,
        filter: SearchFilter,
    ): SearchResults {
        delay(FAKE_LATENCY_MS)
        return DemoLibrary.search(provider, query, filter)
    }

    override suspend fun album(provider: Provider, albumId: String): Album {
        delay(FAKE_LATENCY_MS)
        return DemoLibrary.album(provider, albumId)
    }

    override suspend fun playlist(provider: Provider, playlistId: String): PlaylistDetail {
        delay(FAKE_LATENCY_MS)
        return DemoLibrary.playlist(provider, playlistId)
    }

    override suspend fun lyrics(trackId: String): Lyrics {
        delay(FAKE_LATENCY_MS)
        return DemoLibrary.lyrics(trackId)
    }

    // ---- accounts -----------------------------------------------------------

    override fun beginSignIn(provider: Provider) {
        updateAuth(provider) { it.copy(state = AuthState.CONNECTING) }
    }

    override suspend fun completeSignIn(
        provider: Provider,
        accountLabel: String,
        plan: PlanTier,
    ): Result<Unit> {
        delay(400)
        val auth = ProviderAuth(
            provider = provider,
            state = AuthState.SIGNED_IN,
            account = AccountSummary(
                id = "demo-${provider.slug}",
                displayName = accountLabel,
                plan = plan,
                isLosslessCapable = plan == PlanTier.HIFI || plan == PlanTier.PREMIUM,
            ),
        )
        updateAuth(provider) { auth }
        emit(CoreEvent.AuthChanged(auth))
        return Result.success(Unit)
    }

    override suspend fun signOut(provider: Provider) {
        val auth = ProviderAuth(provider)
        updateAuth(provider) { auth }
        emit(CoreEvent.AuthChanged(auth))
    }

    private fun updateAuth(provider: Provider, transform: (ProviderAuth) -> ProviderAuth) {
        _auth.value = _auth.value.map { if (it.provider == provider) transform(it) else it }
    }

    // ---- quality ------------------------------------------------------------

    override fun setNetwork(network: NetworkKind) {
        this.network = network
        if (network == NetworkKind.OFFLINE && _playback.value.isPlaying) {
            _playback.value = _playback.value.copy(isPlaying = false)
        }
    }

    override fun setQualityPreference(provider: Provider, preference: QualityPreference) {
        preferences[provider] = preference
    }

    override fun qualityPreference(provider: Provider): QualityPreference =
        preferences[provider] ?: QualityPreference.AUTO

    override fun qualityLadder(provider: Provider): List<StreamQuality> =
        QualityPolicy.ladder(provider)

    override fun resolvedQuality(provider: Provider, purpose: StreamPurpose): StreamQuality? {
        val plan = _auth.value.firstOrNull { it.provider == provider }?.account?.plan
            ?: PlanTier.FREE
        return QualityPolicy.resolve(
            provider = provider,
            plan = plan,
            network = network,
            preference = qualityPreference(provider),
            purpose = purpose,
        )
    }

    // ---- transport ----------------------------------------------------------

    override fun playTracks(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        _queue.value = tracks.toList()
        playAt(startIndex.coerceIn(0, tracks.lastIndex), autoplay = true)
        emit(CoreEvent.QueueChanged(_queue.value.size, _playback.value.queueIndex))
    }

    override fun playQueueIndex(index: Int) {
        if (index !in _queue.value.indices) return
        playAt(index, autoplay = true)
    }

    override fun playPause() {
        val state = _playback.value
        if (state.track == null) return
        _playback.value = state.copy(isPlaying = !state.isPlaying)
        emit(CoreEvent.PlaybackChanged(_playback.value))
    }

    override fun next() = skip(forward = true)

    override fun previous() = skip(forward = false)

    override fun seekTo(positionMs: Long) {
        val state = _playback.value
        if (state.track == null) return
        _playback.value = state.copy(positionMs = positionMs.coerceIn(0, state.durationMs))
    }

    override fun setRepeat(mode: RepeatMode) {
        _playback.value = _playback.value.copy(repeat = mode)
    }

    override fun setShuffle(enabled: Boolean) {
        val state = _playback.value
        if (state.shuffle == enabled) return
        val current = state.track
        val queue = _queue.value
        val reordered = if (enabled && current != null) {
            listOf(current) + queue.filterNot { it.id == current.id }.shuffled()
        } else {
            queue
        }
        _queue.value = reordered
        val index = reordered.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
        _playback.value = state.copy(
            shuffle = enabled,
            queueIndex = index,
            queueSize = reordered.size,
        )
    }

    override fun moveInQueue(from: Int, to: Int) {
        val list = _queue.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val currentId = _playback.value.track?.id
        list.add(to, list.removeAt(from))
        _queue.value = list
        val index = list.indexOfFirst { it.id == currentId }
        if (index >= 0) {
            _playback.value = _playback.value.copy(queueIndex = index, queueSize = list.size)
        }
        emit(CoreEvent.QueueChanged(list.size, _playback.value.queueIndex))
    }

    override fun removeFromQueue(index: Int) {
        val list = _queue.value.toMutableList()
        if (index !in list.indices) return
        val state = _playback.value
        val removingCurrent = index == state.queueIndex
        list.removeAt(index)
        _queue.value = list

        when {
            list.isEmpty() -> _playback.value = PlaybackState()
            removingCurrent -> playAt(
                index.coerceAtMost(list.lastIndex),
                autoplay = state.isPlaying,
            )
            index < state.queueIndex -> _playback.value =
                state.copy(queueIndex = state.queueIndex - 1, queueSize = list.size)
            else -> _playback.value = state.copy(queueSize = list.size)
        }
        emit(CoreEvent.QueueChanged(list.size, _playback.value.queueIndex))
    }

    override fun addToQueue(track: Track, playNext: Boolean) {
        val list = _queue.value.toMutableList()
        val at = if (playNext) {
            (_playback.value.queueIndex + 1).coerceIn(0, list.size)
        } else {
            list.size
        }
        list.add(at, track)
        _queue.value = list
        if (_playback.value.track == null) {
            playAt(at, autoplay = false)
        } else {
            _playback.value = _playback.value.copy(queueSize = list.size)
        }
        emit(CoreEvent.QueueChanged(list.size, _playback.value.queueIndex))
    }

    override fun setVolume(volume: Float) {
        _playback.value = _playback.value.copy(volume = volume.coerceIn(0f, 1f))
    }

    override fun setCrossfade(seconds: Int) {
        _playback.value = _playback.value.copy(crossfadeSeconds = seconds.coerceIn(0, 12))
    }

    override fun close() {
        ticker.cancel()
    }

    // ---- internals ----------------------------------------------------------

    private fun tick() {
        val state = _playback.value
        if (!state.isPlaying || state.track == null) return

        val next = state.positionMs + TICK_MS
        if (state.durationMs > 0 && next >= state.durationMs) {
            skip(forward = true, auto = true)
        } else {
            _playback.value = state.copy(
                positionMs = next,
                bufferedMs = minOf(state.durationMs, next + BUFFER_AHEAD_MS),
            )
        }
    }

    private fun skip(forward: Boolean, auto: Boolean = false) {
        val state = _playback.value
        val queue = _queue.value
        if (queue.isEmpty()) return

        if (forward && state.repeat == RepeatMode.ONE) {
            _playback.value = state.copy(positionMs = 0, bufferedMs = 0)
            return
        }

        val candidate = state.queueIndex + if (forward) 1 else -1
        val target = when {
            candidate in queue.indices -> candidate
            candidate > queue.lastIndex && state.repeat == RepeatMode.ALL -> 0
            candidate < 0 && state.repeat == RepeatMode.ALL -> queue.lastIndex
            else -> -1
        }

        if (target == -1) {
            // End of queue with repeat off: park on the last track, paused.
            _playback.value = state.copy(
                isPlaying = false,
                positionMs = if (forward) state.durationMs else 0,
            )
            return
        }
        playAt(target, autoplay = state.isPlaying || auto)
    }

    private fun playAt(index: Int, autoplay: Boolean) {
        val queue = _queue.value
        val track = queue.getOrNull(index) ?: return
        val quality = resolvedQuality(track.provider, StreamPurpose.STREAMING)
        val enriched = track.copy(qualityLabel = quality?.label)

        _playback.value = _playback.value.copy(
            track = enriched,
            isPlaying = autoplay,
            positionMs = 0,
            durationMs = enriched.durationMs,
            bufferedMs = minOf(enriched.durationMs, BUFFER_AHEAD_MS),
            queueIndex = index,
            queueSize = queue.size,
        )
        emit(CoreEvent.TrackChanged(enriched))
        emit(CoreEvent.PlaybackChanged(_playback.value))
    }

    private fun emit(event: CoreEvent) {
        _events.tryEmit(event)
    }

    private companion object {
        const val TICK_MS = 500L
        const val BUFFER_AHEAD_MS = 20_000L
        const val FAKE_LATENCY_MS = 120L
    }
}
