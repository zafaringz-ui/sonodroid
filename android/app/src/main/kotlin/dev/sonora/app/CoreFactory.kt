package dev.sonora.app

import android.content.Context
import android.util.Log
import dev.sonora.core.SonoraCoreClient
import dev.sonora.core.demo.DemoCoreClient
import kotlinx.coroutines.CoroutineScope

/**
 * Builds the active `SonoraCoreClient` and reports which one it is.
 *
 * Split out of `AppGraph` so the decision is a single unit with a single return
 * value: `AppGraph.core` is just [createCore]'s `client`, and the debug badge reads
 * [createCore]'s `selection`. No silent `getOrNull()` — every fallback carries a
 * [FallbackReason] and a `Log.e` with only the exception class and message.
 */
class CoreFactory(
    /** `BuildConfig.SONORA_RUST`; passed in so this file stays testable off-device. */
    private val rustEnabled: Boolean,
    /** The adapter the app should use, or `null` when the flag is off. */
    private val adapterClassName: String? = ADAPTER_CLASS,
) {

    data class Result(
        val client: SonoraCoreClient,
        val selection: CoreSelection,
    )

    /**
     * @param fallback used when the Rust core is unavailable; injected so unit tests
     *   can avoid `DemoCoreClient`'s coroutines if they ever need to.
     */
    fun createCore(
        context: Context,
        scope: CoroutineScope,
        fallback: (CoroutineScope) -> SonoraCoreClient = { DemoCoreClient(it) },
    ): Result {
        if (!rustEnabled || adapterClassName == null) {
            val selection = CoreSelectionPolicy.decide(
                rustEnabled = false,
                adapterVisible = false,
                instantiated = false,
            )
            Log.i(TAG, "Using demo core: ${selection.detail}")
            return Result(fallback(scope), selection)
        }

        val adapter = runCatching { Class.forName(adapterClassName) }.getOrElse { error ->
            // Only the class name and exception type: no user data is involved here,
            // but keeping logcat to fixed strings makes support reports comparable.
            Log.e(TAG, "Native adapter $adapterClassName missing (${error.javaClass.name}: ${error.message})")
            val selection = CoreSelectionPolicy.decide(
                rustEnabled = true,
                adapterVisible = false,
                instantiated = false,
                reason = FallbackReason.CLASS_MISSING,
            )
            return Result(fallback(scope), selection)
        }

        val factory = runCatching {
            adapter.getMethod("create", Context::class.java, CoroutineScope::class.java)
        }.getOrElse { error ->
            Log.e(
                TAG,
                "Native adapter has no create(Context, CoroutineScope) " +
                    "(${error.javaClass.name}: ${error.message})",
            )
            val selection = CoreSelectionPolicy.decide(
                rustEnabled = true,
                adapterVisible = true,
                instantiated = false,
                reason = FallbackReason.FACTORY_INCOMPATIBLE,
            )
            return Result(fallback(scope), selection)
        }

        val client = runCatching {
            factory.invoke(null, context, scope) as? SonoraCoreClient
        }.getOrElse { error ->
            val cause = error.cause ?: error
            Log.e(
                TAG,
                "Native adapter create() failed (${cause.javaClass.name}: ${cause.message})",
                error,
            )
            val selection = CoreSelectionPolicy.decide(
                rustEnabled = true,
                adapterVisible = true,
                instantiated = false,
                reason = classifyInitFailure(cause),
            )
            return Result(fallback(scope), selection)
        }

        if (client == null) {
            Log.e(TAG, "Native adapter create() returned null or an unexpected type")
            val selection = CoreSelectionPolicy.decide(
                rustEnabled = true,
                adapterVisible = true,
                instantiated = false,
                reason = FallbackReason.CORE_INIT_FAILED,
            )
            return Result(fallback(scope), selection)
        }

        val selection = CoreSelectionPolicy.decide(
            rustEnabled = true,
            adapterVisible = true,
            instantiated = true,
            useFixtures = ADAPTER_USES_FIXTURES,
        )
        Log.i(TAG, "Using ${selection.label}: ${selection.detail}")
        return Result(client, selection)
    }

    /**
     * `UnsatisfiedLinkError` and friends mean the `.so` never bound; anything else
     * is the core object rejecting construction. Classified by type rather than
     * message text, which is stable across ABIs and locales.
     */
    private fun classifyInitFailure(error: Throwable): FallbackReason {
        var current: Throwable? = error
        while (current != null) {
            if (current is UnsatisfiedLinkError || current is NoClassDefFoundError) {
                return FallbackReason.NATIVE_LOAD_FAILED
            }
            current = current.cause
        }
        return FallbackReason.CORE_INIT_FAILED
    }

    companion object {
        private const val TAG = "SonoraCore"

        /** Reflective entry point; kept as a string so the default build never links it. */
        const val ADAPTER_CLASS = "dev.sonora.core.rust.UniffiCoreClient"

        /**
         * Mirrors `useFixtures` in `UniffiCoreClient.create`, which in turn maps to
         * `CoreConfig.use_fixtures` in `rust/crates/mobile-core/src/api.rs`. Change
         * both together: this constant only labels the badge, it does not configure
         * the core.
         */
        const val ADAPTER_USES_FIXTURES = true
    }
}
