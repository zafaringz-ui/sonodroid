package dev.sonora.core

import dev.sonora.core.model.Album
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
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The single seam between Compose and the Rust core.
 *
 * Two implementations exist:
 *  * [dev.sonora.core.demo.DemoCoreClient] — in-memory library and transport, used
 *    until the Rust `.so` is wired up and for Compose previews.
 *  * `dev.sonora.core.rust.UniffiCoreClient` — generated bindings, compiled only
 *    with `-Psonora.rust=true`.
 *
 * ViewModels only ever see this interface, so swapping the implementation is a
 * one-line change in `AppGraph`.
 */
interface SonoraCoreClient {

    val playback: StateFlow<PlaybackState>
    val queue: StateFlow<List<Track>>
    val auth: StateFlow<List<ProviderAuth>>
    val downloads: StateFlow<List<DownloadItem>>
    val events: SharedFlow<CoreEvent>

    /** True while running on fixtures instead of the Rust core. Surfaced in Settings. */
    val isDemoMode: Boolean

    // Catalogue ---------------------------------------------------------------

    suspend fun home(provider: Provider): HomeFeed
    suspend fun library(provider: Provider): LibrarySnapshot
    suspend fun search(provider: Provider, query: String, filter: SearchFilter): SearchResults
    suspend fun album(provider: Provider, albumId: String): Album
    suspend fun playlist(provider: Provider, playlistId: String): PlaylistDetail
    suspend fun lyrics(trackId: String): Lyrics?

    // Accounts ----------------------------------------------------------------

    /** WebView is about to open; shows a spinner and records intent. */
    fun beginSignIn(provider: Provider)

    /** Called once a cookie/ARL was captured and validated. */
    suspend fun completeSignIn(
        provider: Provider,
        accountLabel: String,
        plan: PlanTier,
    ): Result<Unit>

    suspend fun signOut(provider: Provider)

    // Quality -----------------------------------------------------------------

    fun setNetwork(network: NetworkKind)
    fun setQualityPreference(provider: Provider, preference: QualityPreference)
    fun qualityPreference(provider: Provider): QualityPreference

    /** Best first; drives the quality settings screen. */
    fun qualityLadder(provider: Provider): List<StreamQuality>

    /** What would be requested right now, or null when offline. */
    fun resolvedQuality(provider: Provider, purpose: StreamPurpose): StreamQuality?

    // Transport ---------------------------------------------------------------

    fun playTracks(tracks: List<Track>, startIndex: Int = 0)

    /** Jump to an existing queue position (tapped a row in the queue). */
    fun playQueueIndex(index: Int)
    fun playPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun setRepeat(mode: RepeatMode)
    fun setShuffle(enabled: Boolean)
    fun moveInQueue(from: Int, to: Int)
    fun removeFromQueue(index: Int)
    fun addToQueue(track: Track, playNext: Boolean = false)
    fun setVolume(volume: Float)
    fun setCrossfade(seconds: Int)

    fun close()
}
