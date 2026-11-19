package dev.sonora.playback

import dev.sonora.core.SonoraCoreClient

/**
 * Bridge between `:app` and the media service.
 *
 * A `MediaSessionService` is created by the system, not by us, so it cannot take
 * constructor injection. The Application sets this once at startup; after Phase 2
 * the service will host the Rust engine itself and this becomes read-only.
 */
object PlaybackDependencies {
    @Volatile
    var core: SonoraCoreClient? = null

    /** True when the service has something to publish. */
    val isReady: Boolean get() = core != null
}
