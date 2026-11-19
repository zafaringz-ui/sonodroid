//! Events pushed from Rust to Kotlin.
//!
//! Kotlin registers one listener at startup; a thin adapter fans these out into a
//! `SharedFlow` that ViewModels collect. Events are fire-and-forget: a slow
//! listener must not block the audio thread, so the API emits from the caller's
//! thread and the Kotlin side posts to its own dispatcher.

use crate::auth::ProviderAuth;
use crate::playback::PlaybackState;

#[derive(Clone, Debug, uniffi::Enum)]
pub enum CoreEvent {
    PlaybackChanged { state: PlaybackState },
    QueueChanged { size: i32, index: i32 },
    AuthChanged { auth: ProviderAuth },
    Message { text: String },
    Failed { message: String },
}

/// Implemented in Kotlin, called from Rust.
#[uniffi::export(with_foreign)]
pub trait CoreListener: Send + Sync {
    fn on_core_event(&self, event: CoreEvent);
}

/// Proof-of-Origin token minter, implemented in Kotlin on top of an off-screen
/// WebView (see `platform-webview/PoTokenMinter.kt`).
///
/// `binding` is the InnerTube visitor binding string; the answer is the `poToken`
/// or `None` when the page could not produce one.
#[uniffi::export(with_foreign)]
#[async_trait::async_trait]
pub trait TokenMinter: Send + Sync {
    async fn mint(&self, binding: String) -> Option<String>;
}
