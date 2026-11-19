package dev.sonora.playback

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.sonora.core.SonoraCoreClient
import dev.sonora.core.model.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Media3 adapter over the Sonora core.
 *
 * The session does not decode anything here: the Rust engine owns the audio path
 * (Oboe sink in Phase 2). This class projects [SonoraCoreClient.playback] into a
 * `Player.State` so the lock screen, notification, Bluetooth controls, Wear OS and
 * Android Auto can drive the same queue the UI drives.
 *
 * Every `handle*` method forwards to the core and returns immediately; the state
 * change arrives back through the flow and triggers [invalidateState].
 */
@OptIn(UnstableApi::class)
class SonoraPlayer(
    private val core: SonoraCoreClient,
    looper: Looper = Looper.getMainLooper(),
) : SimpleBasePlayer(looper) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        scope.launch {
            combine(core.playback, core.queue) { _, _ -> Unit }.collect {
                invalidateState()
            }
        }
    }

    override fun getState(): State {
        val playback = core.playback.value
        val queue = core.queue.value

        val items = queue.map { track ->
            MediaItemData.Builder(track.id)
                .setMediaItem(
                    MediaItem.Builder()
                        .setMediaId(track.id)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(track.artistLine)
                                .setAlbumTitle(track.album)
                                .setIsPlayable(true)
                                .setIsBrowsable(false)
                                .build(),
                        )
                        .build(),
                )
                .setDurationUs(track.durationMs * 1_000L)
                .build()
        }

        val builder = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(items)
            .setCurrentMediaItemIndex(
                playback.queueIndex.coerceIn(0, (items.lastIndex).coerceAtLeast(0)),
            )
            .setPlayWhenReady(
                playback.isPlaying,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            )
            .setPlaybackState(
                if (playback.track == null) Player.STATE_IDLE else Player.STATE_READY,
            )
            .setRepeatMode(playback.repeat.toPlayerRepeatMode())
            .setShuffleModeEnabled(playback.shuffle)

        if (playback.track != null) {
            builder.setContentPositionMs(playback.positionMs)
            builder.setContentBufferedPositionMs(PositionSupplier.getConstant(playback.bufferedMs))
        }
        return builder.build()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (core.playback.value.isPlaying != playWhenReady) {
            core.playPause()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        val current = core.playback.value.queueIndex
        if (mediaItemIndex != current) {
            core.playQueueIndex(mediaItemIndex)
        }
        core.seekTo(positionMs)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        core.setRepeat(
            when (repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
        )
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        core.setShuffle(shuffleModeEnabled)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        core.setVolume(volume)
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        if (core.playback.value.isPlaying) {
            core.playPause()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        scope.cancel()
        return Futures.immediateVoidFuture()
    }
}

private fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
}
