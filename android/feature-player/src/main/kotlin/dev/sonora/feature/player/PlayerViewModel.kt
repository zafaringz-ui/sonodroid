package dev.sonora.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sonora.core.SonoraCoreClient
import dev.sonora.core.model.Lyrics
import dev.sonora.core.model.PlaybackState
import dev.sonora.core.model.RepeatMode
import dev.sonora.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlayerUiState(
    val playback: PlaybackState = PlaybackState(),
    val queue: List<Track> = emptyList(),
    val lyrics: Lyrics? = null,
    val lyricsLoading: Boolean = false,
)

/**
 * Transport and lyrics state for the now-playing sheet, the queue and the lyrics
 * screen. All three can instantiate this — the truth lives in the core, not here.
 */
class PlayerViewModel(
    private val core: SonoraCoreClient,
) : ViewModel() {

    private val lyrics = MutableStateFlow<Lyrics?>(null)
    private val lyricsLoading = MutableStateFlow(false)
    private var lyricsRequestedFor: String? = null

    val state: StateFlow<PlayerUiState> = combine(
        core.playback,
        core.queue,
        lyrics,
        lyricsLoading,
    ) { playback, queue, currentLyrics, loading ->
        PlayerUiState(playback, queue, currentLyrics, loading)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlayerUiState(),
    )

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        core.playTracks(tracks, startIndex)
    }

    fun playQueueIndex(index: Int) = core.playQueueIndex(index)

    fun togglePlayPause() = core.playPause()

    fun retryCurrent() {
        val index = core.playback.value.queueIndex
        if (core.queue.value.getOrNull(index) != null) core.playQueueIndex(index)
    }

    fun next() = core.next()

    fun previous() = core.previous()

    fun seekTo(positionMs: Long) = core.seekTo(positionMs)

    fun toggleRepeat() {
        val next = when (core.playback.value.repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        core.setRepeat(next)
    }

    fun toggleShuffle() = core.setShuffle(!core.playback.value.shuffle)

    fun moveInQueue(from: Int, to: Int) = core.moveInQueue(from, to)

    fun removeFromQueue(index: Int) = core.removeFromQueue(index)

    fun addToQueue(track: Track, playNext: Boolean = false) =
        core.addToQueue(track, playNext)

    /** Loads lyrics for the current track once; no-op while already loaded or loading. */
    fun ensureLyrics() {
        val trackId = core.playback.value.track?.id ?: return
        if (lyricsRequestedFor == trackId) return
        lyricsRequestedFor = trackId
        viewModelScope.launch {
            lyricsLoading.value = true
            lyrics.value = runCatching { core.lyrics(trackId) }.getOrNull()
            lyricsLoading.value = false
        }
    }

    fun setCrossfade(seconds: Int) = core.setCrossfade(seconds)

    override fun onCleared() {
        lyricsRequestedFor = null
        super.onCleared()
    }

    companion object {
        fun factory(core: SonoraCoreClient): ViewModelProvider.Factory = viewModelFactory {
            initializer { PlayerViewModel(core) }
        }
    }
}
