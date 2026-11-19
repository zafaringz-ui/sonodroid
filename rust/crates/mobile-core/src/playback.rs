//! Playback state and clock.
//!
//! Phase 1 has no audio engine yet: the Oboe sink arrives in Phase 2. What exists
//! here is the part the UI depends on — a monotonic position clock, gapless and
//! crossfade policy, and the snapshot pushed to the media session. The engine will
//! drive the same state, so nothing in Kotlin changes when audio lands.

use std::time::Instant;

use crate::models::Track;
use crate::queue::RepeatMode;

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct PlaybackState {
    pub track: Option<Track>,
    pub is_playing: bool,
    /// Position at the moment the snapshot was taken.
    pub position_ms: i64,
    pub duration_ms: i64,
    pub buffered_ms: i64,
    pub repeat: RepeatMode,
    pub shuffle: bool,
    pub queue_index: i32,
    pub queue_size: i32,
    pub volume: f32,
    pub crossfade_ms: i64,
    pub output_name: String,
}

impl Default for PlaybackState {
    fn default() -> Self {
        Self {
            track: None,
            is_playing: false,
            position_ms: 0,
            duration_ms: 0,
            buffered_ms: 0,
            repeat: RepeatMode::Off,
            shuffle: false,
            queue_index: 0,
            queue_size: 0,
            volume: 1.0,
            crossfade_ms: 0,
            output_name: "Phone speaker".to_owned(),
        }
    }
}

#[derive(Debug)]
pub struct Player {
    track: Option<Track>,
    duration_ms: i64,
    position_ms: i64,
    anchor: Option<Instant>,
    is_playing: bool,
    buffered_ms: i64,
    volume: f32,
    crossfade_ms: i64,
    output_name: String,
    queue_index: i32,
    queue_size: i32,
    repeat: RepeatMode,
    shuffle: bool,
}

impl Default for Player {
    fn default() -> Self {
        Self {
            track: None,
            duration_ms: 0,
            position_ms: 0,
            anchor: None,
            is_playing: false,
            buffered_ms: 0,
            volume: 1.0,
            crossfade_ms: 0,
            output_name: "Phone speaker".to_owned(),
            queue_index: 0,
            queue_size: 0,
            repeat: RepeatMode::Off,
            shuffle: false,
        }
    }
}

impl Player {
    /// Load a track. Playback does not start; call [`Player::set_playing`].
    pub fn load(&mut self, track: Option<Track>, duration_ms: i64) {
        self.flush_clock();
        self.track = track;
        self.duration_ms = duration_ms.max(0);
        self.position_ms = 0;
        self.buffered_ms = 0;
        self.anchor = if self.is_playing {
            Some(Instant::now())
        } else {
            None
        };
    }

    pub fn clear(&mut self) {
        self.is_playing = false;
        self.anchor = None;
        self.track = None;
        self.duration_ms = 0;
        self.position_ms = 0;
        self.buffered_ms = 0;
        self.queue_index = 0;
        self.queue_size = 0;
    }

    pub fn set_queue_context(
        &mut self,
        queue_index: i32,
        queue_size: i32,
        repeat: RepeatMode,
        shuffle: bool,
    ) {
        self.queue_index = queue_index;
        self.queue_size = queue_size;
        self.repeat = repeat;
        self.shuffle = shuffle;
    }

    pub fn set_playing(&mut self, playing: bool) {
        if playing == self.is_playing {
            return;
        }
        self.flush_clock();
        self.is_playing = playing;
        self.anchor = if playing { Some(Instant::now()) } else { None };
    }

    pub fn toggle(&mut self) {
        self.set_playing(!self.is_playing);
    }

    #[must_use]
    pub fn is_playing(&self) -> bool {
        self.is_playing
    }

    /// Seek. Positions past the end clamp to the duration.
    pub fn seek(&mut self, position_ms: i64) {
        self.flush_clock();
        self.position_ms = position_ms.clamp(0, self.duration_ms.max(0));
        if self.is_playing {
            self.anchor = Some(Instant::now());
        }
    }

    pub fn set_volume(&mut self, volume: f32) {
        self.volume = volume.clamp(0.0, 1.0);
    }

    pub fn set_crossfade(&mut self, seconds: i32) {
        self.crossfade_ms = i64::from(seconds.clamp(0, 12)) * 1_000;
    }

    pub fn set_output(&mut self, name: Option<String>) {
        self.output_name = name.unwrap_or_else(|| "Phone speaker".to_owned());
    }

    pub fn set_buffered(&mut self, buffered_ms: i64) {
        self.buffered_ms = buffered_ms.max(0);
    }

    #[must_use]
    pub fn position_ms(&self) -> i64 {
        self.position_at(self.elapsed_ms())
    }

    fn elapsed_ms(&self) -> i64 {
        self.anchor.map_or(0, |anchor| {
            i64::try_from(anchor.elapsed().as_millis()).unwrap_or(i64::MAX)
        })
    }

    fn flush_clock(&mut self) {
        self.position_ms = self.position_ms();
    }

    fn position_at(&self, elapsed_ms: i64) -> i64 {
        let position = if self.is_playing {
            self.position_ms.saturating_add(elapsed_ms)
        } else {
            self.position_ms
        };
        if self.duration_ms > 0 {
            position.min(self.duration_ms)
        } else {
            position
        }
    }

    /// Gapless: the next track must be preloaded when the current one ends. The
    /// engine calls this from `sink.rs`; the UI only needs it to show the badge.
    #[must_use]
    pub fn should_crossfade(&self) -> bool {
        if self.crossfade_ms == 0 || self.duration_ms == 0 || !self.is_playing {
            return false;
        }
        self.duration_ms.saturating_sub(self.position_ms()) <= self.crossfade_ms
    }

    #[must_use]
    pub fn state(&self) -> PlaybackState {
        PlaybackState {
            track: self.track.clone(),
            is_playing: self.is_playing,
            position_ms: self.position_ms(),
            duration_ms: self.duration_ms,
            buffered_ms: self.buffered_ms,
            repeat: self.repeat,
            shuffle: self.shuffle,
            queue_index: self.queue_index,
            queue_size: self.queue_size,
            volume: self.volume,
            crossfade_ms: self.crossfade_ms,
            output_name: self.output_name.clone(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::Provider;

    fn track() -> Track {
        Track {
            id: "t1".into(),
            provider: Provider::Deezer,
            title: "Song".into(),
            artists: vec!["Artist".into()],
            album: None,
            album_id: None,
            duration_ms: 10_000,
            artwork_url: None,
            is_explicit: false,
            is_video: false,
            quality_label: None,
        }
    }

    #[test]
    fn position_advances_only_while_playing() {
        let mut player = Player::default();
        player.load(Some(track()), 10_000);
        assert_eq!(player.position_at(1_000), 0);
        player.set_playing(true);
        assert_eq!(player.position_at(1_000), 1_000);
        player.set_playing(false);
        let paused_at = player.position_ms();
        // Time no longer moves once paused, no matter how long the caller waits.
        assert_eq!(player.position_at(1_000), paused_at);
    }

    #[test]
    fn seek_clamps_to_the_track_bounds() {
        let mut player = Player::default();
        player.load(Some(track()), 10_000);
        player.seek(-5);
        assert_eq!(player.position_ms(), 0);
        player.seek(60_000);
        assert_eq!(player.position_ms(), 10_000);
    }

    #[test]
    fn new_track_resets_position() {
        let mut player = Player::default();
        player.load(Some(track()), 10_000);
        player.seek(4_000);
        player.load(Some(track()), 20_000);
        assert_eq!(player.position_ms(), 0);
        assert_eq!(player.state().duration_ms, 20_000);
    }

    #[test]
    fn crossfade_needs_a_window_and_playback() {
        let mut player = Player::default();
        player.load(Some(track()), 10_000);
        player.set_crossfade(5);
        assert!(!player.should_crossfade(), "not playing yet");
        player.set_playing(true);
        assert!(!player.should_crossfade());
        player.seek(6_000);
        assert!(player.should_crossfade());
        player.set_crossfade(0);
        assert!(!player.should_crossfade());
    }

    #[test]
    fn volume_and_crossfade_are_clamped() {
        let mut player = Player::default();
        player.set_volume(4.0);
        assert!((player.state().volume - 1.0).abs() < f32::EPSILON);
        player.set_crossfade(30);
        assert_eq!(player.state().crossfade_ms, 12_000);
    }
}
