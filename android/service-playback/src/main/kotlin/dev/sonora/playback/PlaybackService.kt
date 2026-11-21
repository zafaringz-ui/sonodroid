package dev.sonora.playback

import android.content.Intent
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.sonora.core.Media3PlayerClient

/**
 * Foreground media service (plan §2).
 *
 * `MediaSessionService` publishes the session, builds the notification, handles
 * audio focus and exposes the queue to Android Auto, Wear OS and Assistant. The
 * player behind it is [SonoraPlayer], which reflects the Rust core.
 *
 * The manifest declares `foregroundServiceType="mediaPlayback"`, and the service is
 * exported with the Media3 action so `MediaBrowser` clients can connect.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var ownsPlayer: Boolean = false

    override fun onCreate() {
        super.onCreate()
        val core = PlaybackDependencies.core ?: return

        val sharedPlayer = (core as? Media3PlayerClient)?.media3Player
        val player = sharedPlayer ?: SonoraPlayer(core, Looper.getMainLooper()).also { ownsPlayer = true }
        session = MediaSession.Builder(this, player)
            .setId(SESSION_ID)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val playing = PlaybackDependencies.core?.playback?.value?.isPlaying == true
        if (!playing) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        session?.run {
            if (ownsPlayer) player.release()
            release()
        }
        ownsPlayer = false
        session = null
        super.onDestroy()
    }

    private companion object {
        const val SESSION_ID = "sonora-playback"
    }
}
