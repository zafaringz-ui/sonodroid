package dev.sonora.core

import androidx.media3.common.Player
import dev.sonora.core.model.Track

/** Optional provider capability without changing the stable [SonoraCoreClient] contract. */
interface DeezerArtistTopTracks {
    suspend fun artistTopTracks(artistId: String): List<Track>
}

/** True for clients whose catalog works without an account; account capabilities may be optional. */
interface PublicCatalogClient {
    /** The shared Media3 player used by the catalog client and media session, when present. */
    val media3Player: Player? get() = null
}

/** Optional account path for a client that can upgrade public catalog playback to full tracks. */
interface DeezerAccountSession {
    suspend fun signIn(arl: String)
    suspend fun restore(arl: String)
    suspend fun signOut()
}
