//! Quality policy.
//!
//! The provider account sets a ceiling, the network and the user preference pick
//! a point at or below it. This module is pure so it can be unit tested and reused
//! by the download manager, the stream cache and the settings screen.
//!
//! Ladders (from the plan, taken from `deezer/client.rs` and `youtube/playback.rs`):
//! * Deezer `FLAC_24` → `FLAC` → `MP3_320` → `MP3_128`
//! * YouTube Music: AAC 256 (Premium) → AAC 128 → HE-AAC 48

use crate::error::{CoreError, CoreResult};
use crate::models::Provider;

#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, uniffi::Enum)]
pub enum QualityTier {
    Low,
    Normal,
    High,
    Lossless,
    HiRes,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum NetworkKind {
    Wifi,
    Cellular,
    Offline,
}

/// The account's playback ceiling. Provider plans map onto this shared ladder.
#[derive(Clone, Copy, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum PlanTier {
    Free,
    Standard,
    Premium,
    HiFi,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Default, uniffi::Enum)]
pub enum QualityPreference {
    /// Pick per network: full ceiling on Wi-Fi, capped at High on mobile data.
    #[default]
    Auto,
    DataSaver,
    Balanced,
    Best,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum StreamPurpose {
    Streaming,
    Download,
}

#[derive(Clone, Debug, PartialEq, Eq, uniffi::Record)]
pub struct StreamQuality {
    pub tier: QualityTier,
    pub codec: String,
    pub bitrate_kbps: i32,
    pub sample_rate_hz: i32,
    pub bit_depth: i32,
    pub label: String,
}

/// Highest tier the account can play for this provider.
#[must_use]
pub fn ceiling(provider: Provider, plan: PlanTier) -> QualityTier {
    match (provider, plan) {
        (Provider::Deezer, PlanTier::Free) => QualityTier::Normal,
        (Provider::Deezer, PlanTier::Standard | PlanTier::Premium) => QualityTier::High,
        (Provider::Deezer, PlanTier::HiFi) => QualityTier::HiRes,
        (Provider::YouTubeMusic, PlanTier::Free | PlanTier::Standard) => QualityTier::Normal,
        (Provider::YouTubeMusic, PlanTier::Premium | PlanTier::HiFi) => QualityTier::High,
    }
}

/// Lowest tier worth requesting; Deezer has nothing below MP3 128.
fn floor(provider: Provider) -> QualityTier {
    match provider {
        Provider::Deezer => QualityTier::Normal,
        Provider::YouTubeMusic => QualityTier::Low,
    }
}

/// Resolve the quality for one stream.
///
/// Downloads always honour the plan ceiling: the user explicitly asked for them.
/// Streaming on mobile data under `Auto` is capped at High so a Hi-Fi account does
/// not silently pull 24-bit FLAC over a metered connection.
pub fn resolve(
    provider: Provider,
    plan: PlanTier,
    network: NetworkKind,
    preference: QualityPreference,
    purpose: StreamPurpose,
) -> CoreResult<StreamQuality> {
    if network == NetworkKind::Offline && purpose == StreamPurpose::Streaming {
        return Err(CoreError::Offline);
    }

    let cap = match purpose {
        StreamPurpose::Download => QualityTier::HiRes,
        StreamPurpose::Streaming => match (preference, network) {
            (QualityPreference::DataSaver, _) => QualityTier::Low,
            (QualityPreference::Balanced, _) => QualityTier::High,
            (QualityPreference::Best, _) => QualityTier::HiRes,
            (QualityPreference::Auto, NetworkKind::Cellular) => QualityTier::High,
            (QualityPreference::Auto, _) => QualityTier::HiRes,
        },
    };

    let tier = ceiling(provider, plan).min(cap).max(floor(provider));
    Ok(describe(provider, tier))
}

/// The full ladder for a provider, best first. Used by the settings screens.
#[must_use]
pub fn ladder(provider: Provider) -> Vec<StreamQuality> {
    match provider {
        Provider::Deezer => vec![
            describe(provider, QualityTier::HiRes),
            describe(provider, QualityTier::Lossless),
            describe(provider, QualityTier::High),
            describe(provider, QualityTier::Normal),
        ],
        Provider::YouTubeMusic => vec![
            describe(provider, QualityTier::High),
            describe(provider, QualityTier::Normal),
            describe(provider, QualityTier::Low),
        ],
    }
}

#[must_use]
pub fn describe(provider: Provider, tier: QualityTier) -> StreamQuality {
    match (provider, tier) {
        (Provider::Deezer, QualityTier::HiRes) => StreamQuality {
            tier,
            codec: "flac".into(),
            bitrate_kbps: 4608,
            sample_rate_hz: 96_000,
            bit_depth: 24,
            label: "FLAC 24-bit / 96 kHz".into(),
        },
        (Provider::Deezer, QualityTier::Lossless) => StreamQuality {
            tier,
            codec: "flac".into(),
            bitrate_kbps: 1411,
            sample_rate_hz: 44_100,
            bit_depth: 16,
            label: "FLAC 16-bit / 44.1 kHz".into(),
        },
        (Provider::Deezer, QualityTier::High) => StreamQuality {
            tier,
            codec: "mp3".into(),
            bitrate_kbps: 320,
            sample_rate_hz: 44_100,
            bit_depth: 0,
            label: "MP3 320 kbps".into(),
        },
        (Provider::Deezer, QualityTier::Normal | QualityTier::Low) => StreamQuality {
            tier: QualityTier::Normal,
            codec: "mp3".into(),
            bitrate_kbps: 128,
            sample_rate_hz: 44_100,
            bit_depth: 0,
            label: "MP3 128 kbps".into(),
        },
        (Provider::YouTubeMusic, QualityTier::HiRes | QualityTier::Lossless | QualityTier::High) => {
            StreamQuality {
                tier: QualityTier::High,
                codec: "aac".into(),
                bitrate_kbps: 256,
                sample_rate_hz: 44_100,
                bit_depth: 0,
                label: "AAC 256 kbps".into(),
            }
        }
        (Provider::YouTubeMusic, QualityTier::Normal) => StreamQuality {
            tier,
            codec: "aac".into(),
            bitrate_kbps: 128,
            sample_rate_hz: 44_100,
            bit_depth: 0,
            label: "AAC 128 kbps".into(),
        },
        (Provider::YouTubeMusic, QualityTier::Low) => StreamQuality {
            tier,
            codec: "he-aac".into(),
            bitrate_kbps: 48,
            sample_rate_hz: 24_000,
            bit_depth: 0,
            label: "HE-AAC 48 kbps".into(),
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn free_account_is_capped_at_128() {
        let q = resolve(
            Provider::Deezer,
            PlanTier::Free,
            NetworkKind::Wifi,
            QualityPreference::Best,
            StreamPurpose::Streaming,
        )
        .unwrap();
        assert_eq!(q.tier, QualityTier::Normal);
        assert_eq!(q.bitrate_kbps, 128);
    }

    #[test]
    fn hifi_downloads_get_the_full_ceiling() {
        let q = resolve(
            Provider::Deezer,
            PlanTier::HiFi,
            NetworkKind::Cellular,
            QualityPreference::Best,
            StreamPurpose::Download,
        )
        .unwrap();
        assert_eq!(q.tier, QualityTier::HiRes);
        assert_eq!(q.bit_depth, 24);
    }

    #[test]
    fn auto_on_cellular_drops_hires_to_high() {
        let q = resolve(
            Provider::Deezer,
            PlanTier::HiFi,
            NetworkKind::Cellular,
            QualityPreference::Auto,
            StreamPurpose::Streaming,
        )
        .unwrap();
        assert_eq!(q.tier, QualityTier::High);
    }

    #[test]
    fn auto_on_wifi_keeps_lossless() {
        let q = resolve(
            Provider::Deezer,
            PlanTier::HiFi,
            NetworkKind::Wifi,
            QualityPreference::Auto,
            StreamPurpose::Streaming,
        )
        .unwrap();
        assert_eq!(q.tier, QualityTier::HiRes);
    }

    #[test]
    fn youtube_premium_gets_256() {
        let q = resolve(
            Provider::YouTubeMusic,
            PlanTier::Premium,
            NetworkKind::Wifi,
            QualityPreference::Best,
            StreamPurpose::Streaming,
        )
        .unwrap();
        assert_eq!(q.codec, "aac");
        assert_eq!(q.bitrate_kbps, 256);
    }

    #[test]
    fn data_saver_on_youtube_uses_he_aac() {
        let q = resolve(
            Provider::YouTubeMusic,
            PlanTier::Premium,
            NetworkKind::Cellular,
            QualityPreference::DataSaver,
            StreamPurpose::Streaming,
        )
        .unwrap();
        assert_eq!(q.tier, QualityTier::Low);
        assert_eq!(q.codec, "he-aac");
    }

    #[test]
    fn deezer_never_asks_for_he_aac_floor() {
        let q = resolve(
            Provider::Deezer,
            PlanTier::HiFi,
            NetworkKind::Cellular,
            QualityPreference::DataSaver,
            StreamPurpose::Streaming,
        )
        .unwrap();
        assert_eq!(q.tier, QualityTier::Normal);
    }

    #[test]
    fn streaming_offline_is_an_error_but_downloads_are_not() {
        assert!(matches!(
            resolve(
                Provider::Deezer,
                PlanTier::HiFi,
                NetworkKind::Offline,
                QualityPreference::Auto,
                StreamPurpose::Streaming
            ),
            Err(CoreError::Offline)
        ));
        assert!(resolve(
            Provider::Deezer,
            PlanTier::Free,
            NetworkKind::Offline,
            QualityPreference::Auto,
            StreamPurpose::Download
        )
        .is_ok());
    }
}
