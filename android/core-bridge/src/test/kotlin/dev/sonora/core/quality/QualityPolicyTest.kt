package dev.sonora.core.quality

import dev.sonora.core.model.NetworkKind
import dev.sonora.core.model.PlanTier
import dev.sonora.core.model.Provider
import dev.sonora.core.model.QualityPreference
import dev.sonora.core.model.QualityTier
import dev.sonora.core.model.StreamPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Mirrors `rust/crates/mobile-core/src/quality.rs` tests. If these two disagree,
 * the demo build and the native core would show different quality labels.
 */
class QualityPolicyTest {

    @Test
    fun `free account on deezer is capped at 128`() {
        val quality = QualityPolicy.resolve(
            Provider.DEEZER,
            PlanTier.FREE,
            NetworkKind.WIFI,
            QualityPreference.BEST,
            StreamPurpose.STREAMING,
        )
        assertEquals(QualityTier.NORMAL, quality?.tier)
        assertEquals(128, quality?.bitrateKbps)
    }

    @Test
    fun `hifi downloads get the full ceiling`() {
        val quality = QualityPolicy.resolve(
            Provider.DEEZER,
            PlanTier.HIFI,
            NetworkKind.CELLULAR,
            QualityPreference.BEST,
            StreamPurpose.DOWNLOAD,
        )
        assertEquals(QualityTier.HIRES, quality?.tier)
        assertEquals(24, quality?.bitDepth)
    }

    @Test
    fun `auto on cellular drops hires to high`() {
        val quality = QualityPolicy.resolve(
            Provider.DEEZER,
            PlanTier.HIFI,
            NetworkKind.CELLULAR,
            QualityPreference.AUTO,
            StreamPurpose.STREAMING,
        )
        assertEquals(QualityTier.HIGH, quality?.tier)
    }

    @Test
    fun `auto on wifi keeps the ceiling`() {
        val quality = QualityPolicy.resolve(
            Provider.DEEZER,
            PlanTier.HIFI,
            NetworkKind.WIFI,
            QualityPreference.AUTO,
            StreamPurpose.STREAMING,
        )
        assertEquals(QualityTier.HIRES, quality?.tier)
    }

    @Test
    fun `youtube premium gets aac 256`() {
        val quality = QualityPolicy.resolve(
            Provider.YOUTUBE_MUSIC,
            PlanTier.PREMIUM,
            NetworkKind.WIFI,
            QualityPreference.BEST,
            StreamPurpose.STREAMING,
        )
        assertEquals("aac", quality?.codec)
        assertEquals(256, quality?.bitrateKbps)
    }

    @Test
    fun `data saver on youtube falls back to he aac`() {
        val quality = QualityPolicy.resolve(
            Provider.YOUTUBE_MUSIC,
            PlanTier.PREMIUM,
            NetworkKind.CELLULAR,
            QualityPreference.DATA_SAVER,
            StreamPurpose.STREAMING,
        )
        assertEquals(QualityTier.LOW, quality?.tier)
        assertEquals("he-aac", quality?.codec)
    }

    @Test
    fun `deezer never drops below mp3 128`() {
        val quality = QualityPolicy.resolve(
            Provider.DEEZER,
            PlanTier.HIFI,
            NetworkKind.CELLULAR,
            QualityPreference.DATA_SAVER,
            StreamPurpose.STREAMING,
        )
        assertEquals(QualityTier.NORMAL, quality?.tier)
    }

    @Test
    fun `streaming offline yields no quality but downloads still resolve`() {
        assertNull(
            QualityPolicy.resolve(
                Provider.DEEZER,
                PlanTier.HIFI,
                NetworkKind.OFFLINE,
                QualityPreference.AUTO,
                StreamPurpose.STREAMING,
            ),
        )
        assertEquals(
            QualityTier.HIRES,
            QualityPolicy.resolve(
                Provider.DEEZER,
                PlanTier.HIFI,
                NetworkKind.OFFLINE,
                QualityPreference.AUTO,
                StreamPurpose.DOWNLOAD,
            )?.tier,
        )
    }

    @Test
    fun `ladders are ordered best first`() {
        val deezer = QualityPolicy.ladder(Provider.DEEZER)
        assertEquals(QualityTier.HIRES, deezer.first().tier)
        assertEquals(QualityTier.NORMAL, deezer.last().tier)

        val youtube = QualityPolicy.ladder(Provider.YOUTUBE_MUSIC)
        assertEquals(QualityTier.HIGH, youtube.first().tier)
        assertEquals(QualityTier.LOW, youtube.last().tier)
    }
}
