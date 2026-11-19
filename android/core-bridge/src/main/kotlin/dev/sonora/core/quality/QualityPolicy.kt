package dev.sonora.core.quality

import dev.sonora.core.model.NetworkKind
import dev.sonora.core.model.PlanTier
import dev.sonora.core.model.Provider
import dev.sonora.core.model.QualityPreference
import dev.sonora.core.model.QualityTier
import dev.sonora.core.model.StreamPurpose
import dev.sonora.core.model.StreamQuality

/**
 * Mirror of `rust/crates/mobile-core/src/quality.rs`.
 *
 * The Rust implementation is authoritative once the bridge is live; this copy keeps
 * the demo build honest and doubles as an executable description of the policy.
 * `QualityPolicyTest` pins the same cases as the Rust tests, so a drift between the
 * two shows up immediately.
 */
object QualityPolicy {

    fun ceiling(provider: Provider, plan: PlanTier): QualityTier = when (provider) {
        Provider.DEEZER -> when (plan) {
            PlanTier.FREE -> QualityTier.NORMAL
            PlanTier.STANDARD, PlanTier.PREMIUM -> QualityTier.HIGH
            PlanTier.HIFI -> QualityTier.HIRES
        }
        Provider.YOUTUBE_MUSIC -> when (plan) {
            PlanTier.FREE, PlanTier.STANDARD -> QualityTier.NORMAL
            PlanTier.PREMIUM, PlanTier.HIFI -> QualityTier.HIGH
        }
    }

    private fun floor(provider: Provider): QualityTier = when (provider) {
        Provider.DEEZER -> QualityTier.NORMAL
        Provider.YOUTUBE_MUSIC -> QualityTier.LOW
    }

    /**
     * @return the quality to request, or null when streaming offline.
     */
    fun resolve(
        provider: Provider,
        plan: PlanTier,
        network: NetworkKind,
        preference: QualityPreference,
        purpose: StreamPurpose,
    ): StreamQuality? {
        if (network == NetworkKind.OFFLINE && purpose == StreamPurpose.STREAMING) return null

        val cap = when (purpose) {
            StreamPurpose.DOWNLOAD -> QualityTier.HIRES
            StreamPurpose.STREAMING -> when (preference) {
                QualityPreference.DATA_SAVER -> QualityTier.LOW
                QualityPreference.BALANCED -> QualityTier.HIGH
                QualityPreference.BEST -> QualityTier.HIRES
                QualityPreference.AUTO ->
                    if (network == NetworkKind.CELLULAR) QualityTier.HIGH else QualityTier.HIRES
            }
        }

        val tier = minOf(ceiling(provider, plan), cap).coerceAtLeast(floor(provider))
        return describe(provider, tier)
    }

    fun ladder(provider: Provider): List<StreamQuality> = when (provider) {
        Provider.DEEZER -> listOf(
            describe(provider, QualityTier.HIRES),
            describe(provider, QualityTier.LOSSLESS),
            describe(provider, QualityTier.HIGH),
            describe(provider, QualityTier.NORMAL),
        )
        Provider.YOUTUBE_MUSIC -> listOf(
            describe(provider, QualityTier.HIGH),
            describe(provider, QualityTier.NORMAL),
            describe(provider, QualityTier.LOW),
        )
    }

    fun describe(provider: Provider, tier: QualityTier): StreamQuality = when (provider) {
        Provider.DEEZER -> when (tier) {
            QualityTier.HIRES -> StreamQuality(tier, "flac", 4608, 96_000, 24, "FLAC 24-bit / 96 kHz")
            QualityTier.LOSSLESS -> StreamQuality(tier, "flac", 1411, 44_100, 16, "FLAC 16-bit / 44.1 kHz")
            QualityTier.HIGH -> StreamQuality(tier, "mp3", 320, 44_100, 0, "MP3 320 kbps")
            QualityTier.NORMAL, QualityTier.LOW ->
                StreamQuality(QualityTier.NORMAL, "mp3", 128, 44_100, 0, "MP3 128 kbps")
        }
        Provider.YOUTUBE_MUSIC -> when (tier) {
            QualityTier.HIRES, QualityTier.LOSSLESS, QualityTier.HIGH ->
                StreamQuality(QualityTier.HIGH, "aac", 256, 44_100, 0, "AAC 256 kbps")
            QualityTier.NORMAL -> StreamQuality(tier, "aac", 128, 44_100, 0, "AAC 128 kbps")
            QualityTier.LOW -> StreamQuality(tier, "he-aac", 48, 24_000, 0, "HE-AAC 48 kbps")
        }
    }
}
