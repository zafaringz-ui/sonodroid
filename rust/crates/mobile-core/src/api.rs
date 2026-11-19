//! The UniFFI surface consumed by Kotlin.
//!
//! One `SonoraCore` object per process, owned by the Kotlin `AppGraph`. Every
//! mutating call returns promptly: network work will move onto the Tokio runtime in
//! Phase 3/4, and the audio engine owns its own real-time thread in Phase 2.

use std::collections::HashMap;
use std::sync::Arc;

use parking_lot::Mutex;

use crate::auth::{AccountSummary, AuthStore, ProviderAuth};
use crate::error::{CoreError, CoreResult};
use crate::events::{CoreEvent, CoreListener, TokenMinter};
use crate::fixtures;
use crate::models::{
    Album, LibrarySnapshot, Lyrics, PlaylistDetail, Provider, SearchFilter, SearchResults, Track,
};
use crate::playback::{PlaybackState, Player};
use crate::quality::{
    self, NetworkKind, PlanTier, QualityPreference, StreamPurpose, StreamQuality,
};
use crate::queue::{Queue, QueueSnapshot, RepeatMode};

/// Everything the core needs from Android at startup.
#[derive(Clone, Debug, uniffi::Record)]
pub struct CoreConfig {
    /// `Context.filesDir` — library cache, history, vault.
    pub data_dir: String,
    /// `Context.cacheDir` — decrypted stream scratch space.
    pub cache_dir: String,
    pub default_provider: Provider,
    /// Development only. A release build must pass `false`.
    pub use_fixtures: bool,
    pub crossfade_seconds: i32,
    pub network: NetworkKind,
}

struct Inner {
    config: CoreConfig,
    auth: AuthStore,
    queue: Queue,
    player: Player,
    network: NetworkKind,
    preferences: HashMap<Provider, QualityPreference>,
    listener: Option<Arc<dyn CoreListener>>,
    token_minter: Option<Arc<dyn TokenMinter>>,
}

impl Inner {
    fn new(config: CoreConfig) -> Self {
        let mut player = Player::default();
        player.set_crossfade(config.crossfade_seconds);
        let network = config.network;
        let default_provider = config.default_provider;
        Self {
            config,
            auth: AuthStore::new(),
            queue: Queue::default(),
            player,
            network,
            preferences: HashMap::from([(default_provider, QualityPreference::Auto)]),
            listener: None,
            token_minter: None,
        }
    }

    fn preference(&self, provider: Provider) -> QualityPreference {
        self.preferences.get(&provider).copied().unwrap_or_default()
    }

    fn plan(&self, provider: Provider) -> PlanTier {
        self.auth
            .get(provider)
            .account
            .map_or(PlanTier::Free, |account| account.plan)
    }

    fn resolved_quality(
        &self,
        provider: Provider,
        purpose: StreamPurpose,
    ) -> CoreResult<StreamQuality> {
        quality::resolve(
            provider,
            self.plan(provider),
            self.network,
            self.preference(provider),
            purpose,
        )
    }

    /// Mirror queue state into the player, loading the current track when it
    /// changed. `autoplay` is false for edits that must not restart audio.
    fn sync_player(&mut self, autoplay: bool) {
        let current = self.queue.current().cloned();
        let playing_id = self.player.state().track.map(|track| track.id);
        let current_id = current.as_ref().map(|track| track.id.clone());

        if current_id != playing_id {
            let duration = current.as_ref().map_or(0, |track| track.duration_ms);
            self.player.load(current, duration);
        }

        self.player.set_queue_context(
            self.queue.current_index(),
            i32::try_from(self.queue.len()).unwrap_or(i32::MAX),
            self.queue.repeat(),
            self.queue.shuffle(),
        );

        if autoplay && !self.queue.is_empty() {
            self.player.set_playing(true);
        }
    }

    fn transport_events(&self) -> Vec<CoreEvent> {
        vec![
            CoreEvent::PlaybackChanged {
                state: self.player.state(),
            },
            CoreEvent::QueueChanged {
                size: i32::try_from(self.queue.len()).unwrap_or(i32::MAX),
                index: self.queue.current_index(),
            },
        ]
    }
}

/// The core object. Cheap to call, thread-safe, one instance per process.
#[derive(uniffi::Object)]
pub struct SonoraCore {
    inner: Mutex<Inner>,
}

#[uniffi::export(async_runtime = "tokio")]
impl SonoraCore {
    #[uniffi::constructor]
    #[must_use]
    pub fn new(config: CoreConfig) -> Arc<Self> {
        Arc::new(Self {
            inner: Mutex::new(Inner::new(config)),
        })
    }

    #[must_use]
    pub fn version(&self) -> String {
        env!("CARGO_PKG_VERSION").to_owned()
    }

    /// Kotlin registers its event bridge once, at process start.
    pub fn set_listener(&self, listener: Box<dyn CoreListener>) {
        self.inner.lock().listener = Some(Arc::from(listener));
    }

    /// The WebView-backed Proof-of-Origin minter (Phase 4).
    pub fn set_token_minter(&self, minter: Box<dyn TokenMinter>) {
        self.inner.lock().token_minter = Some(Arc::from(minter));
    }

    #[must_use]
    pub fn providers(&self) -> Vec<Provider> {
        vec![Provider::Deezer, Provider::YouTubeMusic]
    }

    // ---- auth -----------------------------------------------------------

    #[must_use]
    pub fn auth_snapshot(&self) -> Vec<ProviderAuth> {
        self.inner.lock().auth.snapshot()
    }

    /// The WebView is open; show a spinner.
    pub fn sign_in_started(&self, provider: Provider) {
        let auth = {
            let mut inner = self.inner.lock();
            inner.auth.set_connecting(provider);
            inner.auth.get(provider)
        };
        self.emit(vec![CoreEvent::AuthChanged { auth }]);
    }

    /// Called after the cookie/ARL was captured and validated against the provider.
    pub fn mark_signed_in(
        &self,
        provider: Provider,
        account_id: String,
        display_name: String,
        plan: PlanTier,
    ) {
        let auth = {
            let mut inner = self.inner.lock();
            inner.auth.set_signed_in(
                provider,
                AccountSummary {
                    id: account_id,
                    display_name,
                    plan,
                    is_lossless_capable: matches!(plan, PlanTier::HiFi | PlanTier::Premium),
                },
            );
            inner.auth.get(provider)
        };
        self.emit(vec![CoreEvent::AuthChanged { auth }]);
    }

    pub fn mark_signed_out(&self, provider: Provider) {
        let auth = {
            let mut inner = self.inner.lock();
            inner.auth.set_signed_out(provider);
            inner.auth.get(provider)
        };
        self.emit(vec![CoreEvent::AuthChanged { auth }]);
    }

    /// Cookies expired or Google asked for a re-auth. The reason is redacted.
    pub fn mark_needs_attention(&self, provider: Provider, reason: String) {
        let auth = {
            let mut inner = self.inner.lock();
            inner.auth.needs_attention(provider, &reason);
            inner.auth.get(provider)
        };
        self.emit(vec![CoreEvent::AuthChanged { auth }]);
    }

    pub fn set_plan(&self, provider: Provider, plan: PlanTier) {
        let auth = {
            let mut inner = self.inner.lock();
            inner.auth.set_plan(provider, plan)
        };
        if let Some(auth) = auth {
            self.emit(vec![CoreEvent::AuthChanged { auth }]);
        }
    }

    // ---- quality and network -------------------------------------------

    pub fn set_network(&self, network: NetworkKind) {
        let mut inner = self.inner.lock();
        inner.network = network;
        // Losing the network pauses playback; the sink resumes on reconnect.
        let keep_playing = inner.player.is_playing() && network != NetworkKind::Offline;
        inner.player.set_playing(keep_playing);
    }

    #[must_use]
    pub fn network(&self) -> NetworkKind {
        self.inner.lock().network
    }

    pub fn set_quality_preference(&self, provider: Provider, preference: QualityPreference) {
        self.inner.lock().preferences.insert(provider, preference);
    }

    #[must_use]
    pub fn quality_preference(&self, provider: Provider) -> QualityPreference {
        self.inner.lock().preference(provider)
    }

    /// Best first, for the "Audio quality" settings screen.
    #[must_use]
    pub fn quality_ladder(&self, provider: Provider) -> Vec<StreamQuality> {
        quality::ladder(provider)
    }

    /// What the app would request right now for this provider.
    pub fn resolved_quality(
        &self,
        provider: Provider,
        purpose: StreamPurpose,
    ) -> CoreResult<StreamQuality> {
        self.inner.lock().resolved_quality(provider, purpose)
    }

    // ---- catalogue ------------------------------------------------------
    //
    // Until the provider clients land, these serve the development fixtures. The
    // signatures are the final ones, so the Kotlin layer does not change.

    pub fn home(&self, provider: Provider) -> CoreResult<crate::models::HomeFeed> {
        let inner = self.inner.lock();
        Self::require_online(&inner, provider)?;
        Ok(fixtures::home(provider))
    }

    pub fn library(&self, provider: Provider) -> CoreResult<LibrarySnapshot> {
        let inner = self.inner.lock();
        Self::require_online(&inner, provider)?;
        Ok(fixtures::library(provider))
    }

    pub fn search(
        &self,
        provider: Provider,
        query: String,
        filter: SearchFilter,
    ) -> CoreResult<SearchResults> {
        let inner = self.inner.lock();
        Self::require_online(&inner, provider)?;
        Ok(fixtures::search(provider, &query, filter))
    }

    pub fn album(&self, provider: Provider, album_id: String) -> CoreResult<Album> {
        let inner = self.inner.lock();
        Self::require_online(&inner, provider)?;
        fixtures::albums(provider)
            .into_iter()
            .find(|album| album.id == album_id)
            .ok_or_else(|| CoreError::InvalidArgument(format!("unknown album {album_id}")))
    }

    pub fn playlist(&self, provider: Provider, playlist_id: String) -> CoreResult<PlaylistDetail> {
        let inner = self.inner.lock();
        Self::require_online(&inner, provider)?;
        fixtures::playlist_detail(provider, &playlist_id)
            .ok_or_else(|| CoreError::InvalidArgument(format!("unknown playlist {playlist_id}")))
    }

    pub fn lyrics(&self, track_id: String) -> CoreResult<Lyrics> {
        Ok(fixtures::lyrics(&track_id))
    }

    // ---- transport ------------------------------------------------------

    #[must_use]
    pub fn playback_state(&self) -> PlaybackState {
        self.inner.lock().player.state()
    }

    #[must_use]
    pub fn queue_snapshot(&self) -> QueueSnapshot {
        self.inner.lock().queue.snapshot()
    }

    pub fn play_tracks(&self, tracks: Vec<Track>, start_index: i32) {
        self.transport(|inner| {
            let start = usize::try_from(start_index.max(0)).unwrap_or(0);
            inner.queue.replace(tracks, start);
            inner.sync_player(true);
        });
    }

    pub fn add_to_queue(&self, track: Track, play_next: bool) {
        self.transport(|inner| {
            if play_next {
                inner.queue.insert_next(track);
            } else {
                inner.queue.append(&[track]);
            }
        });
    }

    pub fn play_queue_index(&self, index: i32) {
        self.transport(|inner| {
            let position = usize::try_from(index.max(0)).unwrap_or(0);
            if inner.queue.seek_to(position).is_some() {
                inner.sync_player(true);
            }
        });
    }

    pub fn play_pause(&self) {
        self.transport(|inner| {
            if inner.player.state().track.is_some() {
                inner.player.toggle();
            }
        });
    }

    pub fn pause(&self) {
        self.transport(|inner| inner.player.set_playing(false));
    }

    pub fn resume(&self) {
        self.transport(|inner| {
            if inner.player.state().track.is_some() {
                inner.player.set_playing(true);
            }
        });
    }

    pub fn next_track(&self) {
        self.transport(|inner| {
            if inner.queue.advance().is_some() {
                inner.sync_player(true);
            } else {
                inner.player.set_playing(false);
            }
        });
    }

    pub fn previous_track(&self) {
        self.transport(|inner| {
            if inner.queue.back().is_some() {
                inner.sync_player(true);
            }
        });
    }

    pub fn seek(&self, position_ms: i64) {
        self.transport(|inner| inner.player.seek(position_ms));
    }

    pub fn set_repeat(&self, mode: RepeatMode) {
        self.transport(|inner| {
            inner.queue.set_repeat(mode);
            let was_playing = inner.player.is_playing();
            inner.sync_player(was_playing);
        });
    }

    pub fn set_shuffle(&self, enabled: bool) {
        self.transport(|inner| {
            inner.queue.set_shuffle(enabled);
            inner.sync_player(false);
        });
    }

    /// Returns false when either position is out of range.
    pub fn move_in_queue(&self, from: i32, to: i32) -> bool {
        let (moved, events) = {
            let mut inner = self.inner.lock();
            let from = usize::try_from(from).unwrap_or(usize::MAX);
            let to = usize::try_from(to).unwrap_or(usize::MAX);
            let moved = inner.queue.move_item(from, to);
            if moved {
                let was_playing = inner.player.is_playing();
                inner.sync_player(was_playing);
            }
            (moved, inner.transport_events())
        };
        if moved {
            self.emit(events);
        }
        moved
    }

    pub fn remove_from_queue(&self, position: i32) {
        self.transport(|inner| {
            let position = usize::try_from(position).unwrap_or(usize::MAX);
            let was_playing = inner.player.is_playing();
            if inner.queue.remove_at(position).is_some() {
                inner.sync_player(was_playing);
                if inner.queue.is_empty() {
                    inner.player.clear();
                }
            }
        });
    }

    pub fn set_volume(&self, volume: f32) {
        self.transport(|inner| inner.player.set_volume(volume));
    }

    pub fn set_crossfade(&self, seconds: i32) {
        self.transport(|inner| inner.player.set_crossfade(seconds));
    }

    pub fn set_output(&self, name: Option<String>) {
        self.transport(|inner| inner.player.set_output(name));
    }

    // ---- token minting --------------------------------------------------

    /// Ask Kotlin's off-screen WebView for a Proof-of-Origin token. Returns `None`
    /// when no minter is registered or the page failed to produce one.
    pub async fn mint_token(&self, binding: String) -> Option<String> {
        let minter = self.inner.lock().token_minter.clone();
        match minter {
            Some(minter) => minter.mint(binding).await,
            None => None,
        }
    }
}

impl SonoraCore {
    fn emit(&self, events: Vec<CoreEvent>) {
        if events.is_empty() {
            return;
        }
        let listener = self.inner.lock().listener.clone();
        if let Some(listener) = listener {
            for event in events {
                listener.on_core_event(event);
            }
        }
    }

    fn transport(&self, mutate: impl FnOnce(&mut Inner)) {
        let events = {
            let mut inner = self.inner.lock();
            mutate(&mut inner);
            inner.transport_events()
        };
        self.emit(events);
    }

    fn require_online(inner: &Inner, provider: Provider) -> CoreResult<()> {
        if inner.network == NetworkKind::Offline {
            return Err(CoreError::Offline);
        }
        if !inner.config.use_fixtures {
            return Err(CoreError::NotImplemented(format!(
                "{} catalogue client",
                provider.display_name()
            )));
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn core() -> Arc<SonoraCore> {
        SonoraCore::new(CoreConfig {
            data_dir: "/tmp/sonora".into(),
            cache_dir: "/tmp/sonora/cache".into(),
            default_provider: Provider::Deezer,
            use_fixtures: true,
            crossfade_seconds: 0,
            network: NetworkKind::Wifi,
        })
    }

    #[test]
    fn playing_tracks_loads_the_first_track_and_starts() {
        let core = core();
        let tracks = fixtures::tracks(Provider::Deezer);
        core.play_tracks(tracks, 0);

        let state = core.playback_state();
        assert!(state.is_playing);
        assert_eq!(state.track.unwrap().id, "dz-1");
        assert_eq!(state.queue_size, 6);
        assert_eq!(state.queue_index, 0);
    }

    #[test]
    fn next_advances_and_previous_goes_back() {
        let core = core();
        core.play_tracks(fixtures::tracks(Provider::Deezer), 0);
        core.next_track();
        assert_eq!(core.playback_state().track.unwrap().id, "dz-2");
        core.previous_track();
        assert_eq!(core.playback_state().track.unwrap().id, "dz-1");
    }

    #[test]
    fn queue_edits_keep_the_current_track() {
        let core = core();
        core.play_tracks(fixtures::tracks(Provider::Deezer), 1);
        assert_eq!(core.playback_state().track.unwrap().id, "dz-2");

        assert!(core.move_in_queue(1, 3));
        assert_eq!(core.playback_state().track.unwrap().id, "dz-2");

        core.remove_from_queue(0);
        assert_eq!(core.playback_state().track.unwrap().id, "dz-2");
        assert_eq!(core.queue_snapshot().tracks.len(), 5);
    }

    #[test]
    fn empty_queue_play_pause_is_a_no_op() {
        let core = core();
        core.play_pause();
        assert!(!core.playback_state().is_playing);
        core.seek(5_000);
        assert_eq!(core.playback_state().position_ms, 0);
    }

    #[test]
    fn offline_blocks_catalogue_calls_but_not_local_state() {
        let core = core();
        core.set_network(NetworkKind::Offline);
        assert!(matches!(
            core.home(Provider::Deezer),
            Err(CoreError::Offline)
        ));
        assert!(core.auth_snapshot().len() == 2);
    }

    #[test]
    fn quality_follows_the_plan_of_the_signed_in_account() {
        let core = core();
        core.mark_signed_in(
            Provider::Deezer,
            "42".into(),
            "Listener".into(),
            PlanTier::HiFi,
        );
        let quality = core
            .resolved_quality(Provider::Deezer, StreamPurpose::Streaming)
            .unwrap();
        assert_eq!(quality.tier, quality::QualityTier::HiRes);
    }

    #[test]
    fn unknown_album_is_an_invalid_argument() {
        let core = core();
        assert!(matches!(
            core.album(Provider::Deezer, "nope".into()),
            Err(CoreError::InvalidArgument(_))
        ));
    }

    #[test]
    fn fixtures_disabled_reports_not_implemented() {
        let core = SonoraCore::new(CoreConfig {
            data_dir: String::new(),
            cache_dir: String::new(),
            default_provider: Provider::Deezer,
            use_fixtures: false,
            crossfade_seconds: 0,
            network: NetworkKind::Wifi,
        });
        assert!(matches!(
            core.home(Provider::Deezer),
            Err(CoreError::NotImplemented(_))
        ));
    }
}
