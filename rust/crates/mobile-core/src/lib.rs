//! Sonora Android core.
//!
//! Everything below the Compose UI lives here: providers, queue, playback state,
//! auth/session bookkeeping and quality policy. The Kotlin app talks to this crate
//! exclusively through the UniFFI interface in [`api`].
//!
//! Conventions that matter for the FFI boundary:
//! * durations and positions are `i64` milliseconds (Kotlin `Long`)
//! * indices and counts are `i32` (Kotlin `Int`)
//! * every fallible operation returns [`error::CoreError`], which UniFFI turns into
//!   a Kotlin checked exception
#![warn(clippy::all, clippy::pedantic)]

mod api;
mod auth;
mod error;
mod events;
mod fixtures;
mod models;
mod playback;
mod quality;
mod queue;

pub use api::{CoreConfig, SonoraCore};
pub use auth::{AccountStatus, AccountSummary, ProviderAuth};
pub use error::CoreError;
pub use events::{CoreEvent, CoreListener, TokenMinter};
pub use models::{
    Album, Artist, DownloadItem, DownloadState, HomeFeed, HomeItem, HomeSection, LibrarySnapshot,
    LyricLine, Lyrics, Playlist, PlaylistDetail, Provider, SearchFilter, SearchResults, Track,
};
pub use playback::PlaybackState;
pub use quality::{
    NetworkKind, PlanTier, QualityPreference, QualityTier, StreamPurpose, StreamQuality,
};
pub use queue::{QueueSnapshot, RepeatMode};

// Generates the scaffolding the Kotlin bindings are built against.
uniffi::setup_scaffolding!();
