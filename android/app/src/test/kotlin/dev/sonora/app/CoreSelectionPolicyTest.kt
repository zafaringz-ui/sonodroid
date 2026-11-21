package dev.sonora.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fallback used to be invisible: `runCatching { ... }.getOrNull()` returned
 * demo mode for a missing class, a mismatched signature and a failed JNI load
 * alike. These tests pin the decision table so each failure keeps its own reason.
 */
class CoreSelectionPolicyTest {

    @Test
    fun `default build reports demo with the flag off`() {
        val selection = CoreSelectionPolicy.decide(
            rustEnabled = false,
            adapterVisible = false,
            instantiated = false,
        )

        assertEquals(CoreMode.DEMO, selection.mode)
        assertEquals(FallbackReason.FLAG_OFF, selection.reason)
        assertEquals(CoreSelectionPolicy.LABEL_DEMO, selection.label)
        assertTrue(selection.detail.orEmpty().contains("Build flag is off"))
    }

    @Test
    fun `flag on but missing adapter reports class missing`() {
        val selection = CoreSelectionPolicy.decide(
            rustEnabled = true,
            adapterVisible = false,
            instantiated = false,
        )

        assertEquals(CoreMode.DEMO, selection.mode)
        assertEquals(FallbackReason.CLASS_MISSING, selection.reason)
        assertTrue(selection.detail.orEmpty().contains("gen-bindings"))
    }

    @Test
    fun `native load failure keeps its own reason`() {
        val selection = CoreSelectionPolicy.decide(
            rustEnabled = true,
            adapterVisible = true,
            instantiated = false,
            reason = FallbackReason.NATIVE_LOAD_FAILED,
        )

        assertEquals(CoreMode.DEMO, selection.mode)
        assertEquals(FallbackReason.NATIVE_LOAD_FAILED, selection.reason)
        assertTrue(selection.detail.orEmpty().contains("shared library"))
    }

    @Test
    fun `adapter present but not instantiated falls back to core init failed`() {
        val selection = CoreSelectionPolicy.decide(
            rustEnabled = true,
            adapterVisible = true,
            instantiated = false,
        )

        assertEquals(CoreMode.DEMO, selection.mode)
        assertEquals(FallbackReason.CORE_INIT_FAILED, selection.reason)
    }

    @Test
    fun `rust core on fixtures is labelled distinct from live`() {
        val selection = CoreSelectionPolicy.decide(
            rustEnabled = true,
            adapterVisible = true,
            instantiated = true,
            useFixtures = true,
        )

        assertEquals(CoreMode.RUST_FIXTURES, selection.mode)
        assertNull(selection.reason)
        assertEquals(CoreSelectionPolicy.LABEL_RUST_FIXTURES, selection.label)
    }

    @Test
    fun `rust core without fixtures is labelled live`() {
        val selection = CoreSelectionPolicy.decide(
            rustEnabled = true,
            adapterVisible = true,
            instantiated = true,
            useFixtures = false,
        )

        assertEquals(CoreMode.RUST_LIVE, selection.mode)
        assertNull(selection.reason)
        assertEquals(CoreSelectionPolicy.LABEL_RUST_LIVE, selection.label)
    }

    @Test
    fun `debug public mode has an explicit non-demo label`() {
        val selection = CoreSelectionPolicy.deezerPublic()

        assertEquals(CoreMode.DEEZER_PUBLIC, selection.mode)
        assertNull(selection.reason)
        assertEquals("Deezer public catalog", selection.label)
    }

    @Test
    fun `every fallback reason has a distinct non empty description`() {
        val descriptions = FallbackReason.entries.map { CoreSelectionPolicy.describe(it) }

        assertEquals(FallbackReason.entries.size, descriptions.toSet().size)
        descriptions.forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun `labels are distinct so the badge cannot be ambiguous`() {
        val labels = setOf(
            CoreSelectionPolicy.LABEL_DEMO,
            CoreSelectionPolicy.LABEL_RUST_FIXTURES,
            CoreSelectionPolicy.LABEL_RUST_LIVE,
            CoreSelectionPolicy.LABEL_DEEZER_PUBLIC,
        )

        assertEquals(4, labels.size)
    }

    @Test
    fun `successful native core never carries a fallback reason`() {
        val selection = CoreSelectionPolicy.decide(
            rustEnabled = true,
            adapterVisible = true,
            instantiated = true,
        )

        assertNull(selection.reason)
        assertNotNull(selection.detail)
    }
}
