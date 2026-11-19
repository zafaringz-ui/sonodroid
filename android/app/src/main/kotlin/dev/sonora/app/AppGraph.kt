package dev.sonora.app

import android.content.Context
import dev.sonora.core.SonoraCoreClient
import dev.sonora.core.demo.DemoCoreClient
import dev.sonora.playback.PlaybackDependencies
import dev.sonora.secure.CredentialStore
import dev.sonora.secure.SecretsVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Manual dependency graph.
 *
 * The plan names Hilt, and this is the one deliberate deviation: with twelve Gradle
 * modules and no compiler available while the project was scaffolded, a single
 * explicit object is easier to verify than annotation processing spread across every
 * module. Moving to Hilt later is mechanical — each `val` becomes a `@Provides`.
 */
class AppGraph(context: Context) {

    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * The core the UI talks to.
     *
     * Phase 1 ships the in-memory implementation so the app is usable before the
     * Rust `.so` exists. With `-Psonora.rust=true` the generated UniFFI adapter is
     * on the classpath and constructed reflectively: the default build never has to
     * compile against generated types, and a missing `.so` degrades to demo mode
     * instead of crashing.
     */
    val core: SonoraCoreClient = createCore()

    private fun createCore(): SonoraCoreClient {
        if (!BuildConfig.SONORA_RUST) return DemoCoreClient(applicationScope)
        return runCatching {
            val bridge = Class.forName("dev.sonora.core.rust.UniffiCoreClient")
            val factory = bridge.getMethod(
                "create",
                android.content.Context::class.java,
                CoroutineScope::class.java,
            )
            factory.invoke(null, appContext, applicationScope) as? SonoraCoreClient
        }.getOrNull() ?: DemoCoreClient(applicationScope)
    }

    val vault = SecretsVault(appContext)
    val credentials = CredentialStore(vault)
    val onboarding = OnboardingStore(appContext)

    private val _deepLinks = MutableSharedFlow<DeepLink>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val deepLinks: SharedFlow<DeepLink> = _deepLinks.asSharedFlow()

    private val networkMonitor = NetworkMonitor(appContext) { kind ->
        core.setNetwork(kind)
    }

    fun start() {
        PlaybackDependencies.core = core
        networkMonitor.start()
    }

    fun handleIncomingLink(deepLink: DeepLink) {
        _deepLinks.tryEmit(deepLink)
    }
}

class OnboardingStore(context: Context) {
    private val prefs = context.getSharedPreferences("sonora.onboarding", Context.MODE_PRIVATE)

    var isOnboarded: Boolean
        get() = prefs.getBoolean(KEY_DONE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DONE, value).apply()
        }

    private companion object {
        const val KEY_DONE = "done"
    }
}
