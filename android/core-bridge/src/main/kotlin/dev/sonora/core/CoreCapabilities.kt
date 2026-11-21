package dev.sonora.core

import androidx.media3.common.Player
import dev.sonora.core.model.Track

/** Optional provider capability without changing the stable [SonoraCoreClient] contract. */
interface DeezerArtistTopTracks {
    suspend fun artistTopTracks(artistId: String): List<Track>
}

/** Shared Media3 player exposed to the media-session service by the active account client. */
interface Media3PlayerClient {
    val media3Player: Player? get() = null
}

/** Deezer account capability used for restore, sign-in state and account-only playback. */
interface DeezerAccountSession {
    val isSignedIn: Boolean
    suspend fun signIn(arl: String)
    suspend fun restore(arl: String)
    suspend fun signOut()
}
