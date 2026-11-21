package dev.sonora.app

import android.content.Context
import dev.sonora.core.DeezerAccountSession
import dev.sonora.core.SonoraCoreClient
import dev.sonora.core.model.Provider
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
import kotlinx.coroutines.launch

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

    private val coreResult = CoreFactory(
        rustEnabled = BuildConfig.SONORA_RUST,
        debugPublicMode = BuildConfig.DEBUG,
    ).createCore(
        context = appContext,
        scope = applicationScope,
    )

    /** Debug-only badge text and fallback reason; see [CoreSelection]. */
    val coreSelection: CoreSelection = coreResult.selection

    /**
     * The core the UI talks to.
     *
     * Phase 1 ships the in-memory implementation so the app is usable before the
     * Rust `.so` exists. With `-Psonora.rust=true` the generated UniFFI adapter is
     * on the classpath and constructed reflectively: the default build never has to
     * compile against generated types, and a missing `.so` degrades to demo mode
     * instead of crashing.
     *
     * Which of those happened is recorded in [coreSelection]; the debug build shows
     * it so demo data can never be mistaken for the native core.
     */
    val core: SonoraCoreClient = coreResult.client

    val vault = SecretsVault(appContext)
    val credentials = CredentialStore(vault)
    val onboarding = OnboardingStore(appContext)

    init {
        val arl = credentials.load(Provider.DEEZER.slug)
        val accountSession = core as? DeezerAccountSession
        if (!arl.isNullOrBlank() && accountSession != null) {
            applicationScope.launch { runCatching { accountSession.restore(arl) } }
        }
    }

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
