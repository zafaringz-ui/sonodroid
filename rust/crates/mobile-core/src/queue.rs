//! Playback queue.
//!
//! The queue keeps the user's original order (`items`, the "playing from" order)
//! separate from the playback order (`order`). Shuffle rearranges only `order`, so
//! turning shuffle off restores the album exactly as it was.
//!
//! Determinism matters for tests and for "shuffle the same playlist differently on
//! each visit": the RNG is a seeded xorshift64 with no external dependency.

use crate::models::Track;

#[derive(Clone, Copy, Debug, PartialEq, Eq, Default, uniffi::Enum)]
pub enum RepeatMode {
    /// Stop at the end of the queue.
    #[default]
    Off,
    /// Wrap around to the top.
    All,
    /// Repeat the current track; `next` is a no-op.
    One,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct QueueSnapshot {
    /// Tracks in playback order, which is what the queue screen shows.
    pub tracks: Vec<Track>,
    pub current_index: i32,
    pub repeat: RepeatMode,
    pub shuffle: bool,
}

#[derive(Clone, Debug)]
pub struct Queue {
    items: Vec<Track>,
    /// Permutation of `0..items.len()`; position in this vector is queue position.
    order: Vec<usize>,
    cursor: usize,
    repeat: RepeatMode,
    shuffle: bool,
    rng: u64,
    /// Track indices previously played, most recent last.
    history: Vec<usize>,
}

impl Default for Queue {
    fn default() -> Self {
        Self::with_seed(0x5EED_5EED_5EED_5EED)
    }
}

impl Queue {
    #[must_use]
    pub fn with_seed(seed: u64) -> Self {
        Self {
            items: Vec::new(),
            order: Vec::new(),
            cursor: 0,
            repeat: RepeatMode::Off,
            shuffle: false,
            rng: seed | 1,
            history: Vec::new(),
        }
    }

    #[must_use]
    pub fn is_empty(&self) -> bool {
        self.order.is_empty()
    }

    #[must_use]
    pub fn len(&self) -> usize {
        self.order.len()
    }

    #[must_use]
    pub fn repeat(&self) -> RepeatMode {
        self.repeat
    }

    #[must_use]
    pub fn shuffle(&self) -> bool {
        self.shuffle
    }

    #[must_use]
    pub fn current(&self) -> Option<&Track> {
        self.order
            .get(self.cursor)
            .and_then(|index| self.items.get(*index))
    }

    #[must_use]
    pub fn current_index(&self) -> i32 {
        if self.is_empty() {
            0
        } else {
            i32::try_from(self.cursor).unwrap_or(0)
        }
    }

    fn next_random(&mut self) -> u64 {
        let mut x = self.rng;
        x ^= x << 13;
        x ^= x >> 7;
        x ^= x << 17;
        self.rng = x;
        x
    }

    /// Replace the queue and start at `start`. Used by "play album", "play playlist"
    /// and by restoring the queue after process death.
    pub fn replace(&mut self, items: Vec<Track>, start: usize) {
        let start = if items.is_empty() {
            0
        } else {
            start.min(items.len() - 1)
        };
        self.items = items;
        self.order = (0..self.items.len()).collect();
        self.cursor = start;
        self.history.clear();
        if self.shuffle {
            self.shuffle_keeping_current();
        }
    }

    /// Append to the end without disturbing playback.
    pub fn append(&mut self, tracks: &[Track]) {
        for track in tracks {
            let index = self.items.len();
            self.items.push(track.clone());
            self.order.push(index);
        }
    }

    /// Insert directly after the current track ("play next").
    pub fn insert_next(&mut self, track: Track) {
        let index = self.items.len();
        self.items.push(track);
        if self.order.is_empty() {
            self.order.push(index);
            self.cursor = 0;
            return;
        }
        let at = (self.cursor + 1).min(self.order.len());
        self.order.insert(at, index);
    }

    /// Move one queue position to another, keeping the current track playing.
    /// Positions are what the queue screen shows (playback order).
    pub fn move_item(&mut self, from: usize, to: usize) -> bool {
        if from >= self.order.len() || to >= self.order.len() || from == to {
            return false;
        }
        let playing = self.order[self.cursor];
        let moved = self.order.remove(from);
        self.order.insert(to, moved);
        self.cursor = self
            .order
            .iter()
            .position(|&index| index == playing)
            .unwrap_or(0);
        true
    }

    /// Remove a queue position. Returns the removed track.
    pub fn remove_at(&mut self, position: usize) -> Option<Track> {
        if position >= self.order.len() {
            return None;
        }
        let item_index = self.order.remove(position);
        let track = self.items.remove(item_index);

        for index in &mut self.order {
            if *index > item_index {
                *index -= 1;
            }
        }
        for index in &mut self.history {
            if *index > item_index {
                *index -= 1;
            }
        }
        self.history.retain(|&index| index != item_index);

        if self.order.is_empty() {
            self.cursor = 0;
        } else if position < self.cursor {
            self.cursor -= 1;
        } else if self.cursor >= self.order.len() {
            self.cursor = self.order.len() - 1;
        }
        Some(track)
    }

    /// Jump to a queue position (tap a row).
    pub fn seek_to(&mut self, position: usize) -> Option<&Track> {
        if position >= self.order.len() {
            return None;
        }
        self.history.push(self.order[self.cursor]);
        self.cursor = position;
        self.current()
    }

    /// Advance to the next track, honouring repeat mode.
    pub fn advance(&mut self) -> Option<&Track> {
        if self.is_empty() {
            return None;
        }
        if self.repeat == RepeatMode::One {
            return self.current();
        }
        self.history.push(self.order[self.cursor]);
        if self.cursor + 1 < self.order.len() {
            self.cursor += 1;
        } else if self.repeat == RepeatMode::All {
            self.cursor = 0;
            self.history.clear();
        } else {
            let _ = self.history.pop();
            return None;
        }
        self.current()
    }

    /// Go back. Falls back to restarting the current track, like every music app.
    pub fn back(&mut self) -> Option<&Track> {
        if self.is_empty() {
            return None;
        }
        while let Some(previous) = self.history.pop() {
            if let Some(position) = self.order.iter().position(|&index| index == previous) {
                self.cursor = position;
                return self.current();
            }
        }
        if self.repeat == RepeatMode::All {
            self.cursor = self.order.len() - 1;
        }
        self.current()
    }

    pub fn set_repeat(&mut self, mode: RepeatMode) {
        self.repeat = mode;
    }

    pub fn set_shuffle(&mut self, enabled: bool) {
        if enabled == self.shuffle {
            return;
        }
        let playing = self.current_track_index();
        self.shuffle = enabled;
        if enabled {
            self.shuffle_keeping_current();
        } else {
            self.order = (0..self.items.len()).collect();
            self.cursor = playing
                .and_then(|index| self.order.iter().position(|&candidate| candidate == index))
                .unwrap_or(0);
        }
    }

    fn current_track_index(&self) -> Option<usize> {
        self.order.get(self.cursor).copied()
    }

    fn shuffle_keeping_current(&mut self) {
        if self.order.len() < 2 {
            self.cursor = 0;
            return;
        }
        let playing = self.current_track_index().unwrap_or(0);
        // Fisher-Yates, then move the playing track to the front.
        for i in (1..self.order.len()).rev() {
            let j = usize::try_from(self.next_random() % (i as u64 + 1)).unwrap_or(0);
            self.order.swap(i, j);
        }
        if let Some(position) = self.order.iter().position(|&index| index == playing) {
            self.order.swap(0, position);
        }
        self.cursor = 0;
    }

    /// The next `limit` tracks, excluding the current one. Used for preloading.
    #[must_use]
    pub fn upcoming(&self, limit: usize) -> Vec<Track> {
        self.order
            .iter()
            .skip(self.cursor + 1)
            .take(limit)
            .filter_map(|index| self.items.get(*index).cloned())
            .collect()
    }

    #[must_use]
    pub fn snapshot(&self) -> QueueSnapshot {
        QueueSnapshot {
            tracks: self
                .order
                .iter()
                .filter_map(|index| self.items.get(*index).cloned())
                .collect(),
            current_index: self.current_index(),
            repeat: self.repeat,
            shuffle: self.shuffle,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::Provider;

    fn track(id: &str) -> Track {
        Track {
            id: id.into(),
            provider: Provider::Deezer,
            title: format!("Track {id}"),
            artists: vec!["Artist".into()],
            album: None,
            album_id: None,
            duration_ms: 180_000,
            artwork_url: None,
            is_explicit: false,
            is_video: false,
            quality_label: None,
        }
    }

    fn queue_of(ids: &[&str]) -> Queue {
        let mut queue = Queue::default();
        queue.replace(ids.iter().map(|id| track(id)).collect(), 0);
        queue
    }

    fn ids(queue: &Queue) -> Vec<String> {
        queue
            .snapshot()
            .tracks
            .into_iter()
            .map(|track| track.id)
            .collect()
    }

    #[test]
    fn starts_at_the_requested_track_and_advances() {
        let mut queue = queue_of(&["a", "b", "c"]);
        queue.seek_to(1);
        assert_eq!(queue.current().unwrap().id, "b");
        assert_eq!(queue.advance().unwrap().id, "c");
        assert!(queue.advance().is_none());
    }

    #[test]
    fn repeat_all_wraps_and_repeat_one_stays() {
        let mut queue = queue_of(&["a", "b"]);
        queue.set_repeat(RepeatMode::All);
        queue.seek_to(1);
        assert_eq!(queue.advance().unwrap().id, "a");

        queue.set_repeat(RepeatMode::One);
        assert_eq!(queue.advance().unwrap().id, "a");
        assert_eq!(queue.advance().unwrap().id, "a");
    }

    #[test]
    fn back_walks_history_then_restarts() {
        let mut queue = queue_of(&["a", "b", "c"]);
        queue.advance();
        assert_eq!(queue.current().unwrap().id, "b");
        assert_eq!(queue.back().unwrap().id, "a");
        assert_eq!(queue.back().unwrap().id, "a");
    }

    #[test]
    fn shuffle_keeps_the_current_track_and_is_reversible() {
        let mut queue = queue_of(&["a", "b", "c", "d", "e"]);
        queue.seek_to(2);
        queue.set_shuffle(true);
        assert_eq!(queue.current().unwrap().id, "c");
        assert_eq!(queue.current_index(), 0);
        let mut shuffled = ids(&queue);
        shuffled.sort();
        assert_eq!(shuffled, vec!["a", "b", "c", "d", "e"]);

        queue.set_shuffle(false);
        assert_eq!(ids(&queue), vec!["a", "b", "c", "d", "e"]);
        assert_eq!(queue.current().unwrap().id, "c");
        assert_eq!(queue.current_index(), 2);
    }

    #[test]
    fn move_keeps_the_playing_track() {
        let mut queue = queue_of(&["a", "b", "c", "d"]);
        queue.seek_to(1);
        assert!(queue.move_item(1, 3));
        assert_eq!(ids(&queue), vec!["a", "c", "d", "b"]);
        assert_eq!(queue.current().unwrap().id, "b");
        assert_eq!(queue.current_index(), 3);
    }

    #[test]
    fn remove_before_the_cursor_shifts_the_cursor() {
        let mut queue = queue_of(&["a", "b", "c", "d"]);
        queue.seek_to(2);
        let removed = queue.remove_at(0).unwrap();
        assert_eq!(removed.id, "a");
        assert_eq!(ids(&queue), vec!["b", "c", "d"]);
        assert_eq!(queue.current().unwrap().id, "c");
        assert_eq!(queue.current_index(), 1);
    }

    #[test]
    fn removing_the_last_track_empties_the_queue() {
        let mut queue = queue_of(&["a"]);
        assert!(queue.remove_at(0).is_some());
        assert!(queue.is_empty());
        assert!(queue.current().is_none());
    }

    #[test]
    fn insert_next_plays_after_the_current_track() {
        let mut queue = queue_of(&["a", "b"]);
        queue.insert_next(track("x"));
        assert_eq!(ids(&queue), vec!["a", "x", "b"]);
        assert_eq!(queue.advance().unwrap().id, "x");

        let mut empty = Queue::default();
        empty.insert_next(track("only"));
        assert_eq!(empty.current().unwrap().id, "only");
    }

    #[test]
    fn upcoming_skips_the_current_track() {
        let queue = queue_of(&["a", "b", "c"]);
        let upcoming: Vec<_> = queue.upcoming(5).into_iter().map(|t| t.id).collect();
        assert_eq!(upcoming, vec!["b", "c"]);
    }

    #[test]
    fn seeding_is_deterministic() {
        let build = || {
            let mut queue = Queue::with_seed(42);
            queue.replace((0..10).map(|i| track(&i.to_string())).collect(), 0);
            queue.set_shuffle(true);
            ids(&queue)
        };
        assert_eq!(build(), build());
    }
}
