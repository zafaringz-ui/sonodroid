package dev.sonora.app

/**
 * Which implementation of `SonoraCoreClient` the app is running on, and why.
 *
 * This is the one thing that has been ambiguous so far: a build without the Rust
 * `.so` silently degrades to [DemoCoreClient], which looks identical to a working
 * native core. Recording the outcome as a value makes the choice testable without
 * an Android runtime, and lets the debug UI state it plainly.
 *
 * Deliberately free of Android types so it stays a plain-JVM unit test target.
 */
enum class CoreMode {
    /** `DemoCoreClient`: in-memory library, no native code involved. */
    DEMO,

    /** `UniffiCoreClient` backed by the Rust core, still serving development fixtures. */
    RUST_FIXTURES,

    /** `UniffiCoreClient` backed by the Rust core answering from real providers. */
    RUST_LIVE,

    /** Public Deezer catalog with optional authenticated full-track playback. */
    DEEZER_PUBLIC,
}

/**
 * Why the app is not on the native core. Ordered from "expected" to "broken":
 * [FLAG_OFF] is the normal state of a default build, the rest mean something that
 * should have loaded did not.
 */
enum class FallbackReason {
    /** `-Psonora.rust=true` was not passed, so the adapter is not on the classpath. */
    FLAG_OFF,

    /** The flag was on, but `UniffiCoreClient` is absent from the APK. */
    CLASS_MISSING,

    /** The adapter class exists, but its `create(Context, CoroutineScope)` entry point is not callable. */
    FACTORY_INCOMPATIBLE,

    /** The adapter started, but `System.loadLibrary` / JNI could not bind the `.so`. */
    NATIVE_LOAD_FAILED,

    /** The adapter threw while constructing the core object itself. */
    CORE_INIT_FAILED,
}

/**
 * What [CoreMode] and [FallbackReason] together should say on screen.
 *
 * @property mode the implementation actually constructed.
 * @property reason why it fell back, or `null` when the Rust core was used.
 * @property label short, stable text for the debug badge.
 * @property detail human-readable elaboration for logs and the settings row.
 */
data class CoreSelection(
    val mode: CoreMode,
    val reason: FallbackReason?,
    val label: String,
    val detail: String?,
)

/**
 * Pure decision table. Every branch an Android build can take is expressed here so
 * the fallback can be asserted in a JVM unit test rather than discovered on a device.
 */
object CoreSelectionPolicy {

    const val LABEL_DEMO = "Demo core"
    const val LABEL_RUST_FIXTURES = "Rust core (fixtures)"
    const val LABEL_RUST_LIVE = "Rust core (live)"
    const val LABEL_DEEZER_PUBLIC = "Deezer public (30s previews)"

    fun deezerPublic(): CoreSelection = CoreSelection(
        mode = CoreMode.DEEZER_PUBLIC,
        reason = null,
        label = LABEL_DEEZER_PUBLIC,
        detail = "Public Deezer catalog; sign-in is optional and unlocks authenticated full tracks.",
    )

    /**
     * @param rustEnabled the `SONORA_RUST` build flag.
     * @param adapterVisible whether `dev.sonora.core.rust.UniffiCoreClient` resolved.
     * @param instantiated whether the reflective `create(...)` call returned a client.
     * @param useFixtures the `use_fixtures` value the adapter was configured with.
     * @param reason the specific failure, when one was caught.
     */
    fun decide(
        rustEnabled: Boolean,
        adapterVisible: Boolean,
        instantiated: Boolean,
        useFixtures: Boolean = false,
        reason: FallbackReason? = null,
    ): CoreSelection {
        if (!rustEnabled || !adapterVisible || !instantiated) {
            val effective = reason ?: when {
                !rustEnabled -> FallbackReason.FLAG_OFF
                !adapterVisible -> FallbackReason.CLASS_MISSING
                else -> FallbackReason.CORE_INIT_FAILED
            }
            return CoreSelection(
                mode = CoreMode.DEMO,
                reason = effective,
                label = LABEL_DEMO,
                detail = describe(effective),
            )
        }

        return if (useFixtures) {
            CoreSelection(
                mode = CoreMode.RUST_FIXTURES,
                reason = null,
                label = LABEL_RUST_FIXTURES,
                detail = "Native core is loaded but serving development fixtures.",
            )
        } else {
            CoreSelection(
                mode = CoreMode.RUST_LIVE,
                reason = null,
                label = LABEL_RUST_LIVE,
                detail = "Native core is answering from the provider clients.",
            )
        }
    }

    /** One line, no secrets: only our own vocabulary and no exception text. */
    fun describe(reason: FallbackReason): String = when (reason) {
        FallbackReason.FLAG_OFF ->
            "Build flag is off (-Psonora.rust=true not set); the native adapter was never compiled in."
        FallbackReason.CLASS_MISSING ->
            "Build flag is on but the native adapter class is not in the APK; run scripts/gen-bindings.sh."
        FallbackReason.FACTORY_INCOMPATIBLE ->
            "Native adapter is present but its create(Context, CoroutineScope) entry point does not match."
        FallbackReason.NATIVE_LOAD_FAILED ->
            "Native adapter is present but the shared library failed to load; check the ABI in jniLibs."
        FallbackReason.CORE_INIT_FAILED ->
            "Native adapter loaded but the core object could not be constructed."
    }
}
