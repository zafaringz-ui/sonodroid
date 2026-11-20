package dev.sonora.core

import androidx.media3.common.Player
import dev.sonora.core.model.Track

/** Optional provider capability without changing the stable [SonoraCoreClient] contract. */
interface DeezerArtistTopTracks {
    suspend fun artistTopTracks(artistId: String): List<Track>
}

/** True for clients that intentionally expose only public, unauthenticated catalog data. */
interface PublicCatalogClient {
    /** The shared Media3 player used by the catalog client and media session, when present. */
    val media3Player: Player? get() = null
}
