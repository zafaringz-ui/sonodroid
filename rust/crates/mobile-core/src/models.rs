//! Domain records shared by both providers.
//!
//! These types intentionally mirror what Compose needs and nothing more: no raw
//! provider JSON, no tokens, no file paths.

use crate::quality::StreamQuality;

/// A music service. Release 1 ships the first two; the rest are expansion tracks.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord, uniffi::Enum)]
pub enum Provider {
    Deezer,
    YouTubeMusic,
}

impl Provider {
    #[must_use]
    pub fn slug(self) -> &'static str {
        match self {
            Self::Deezer => "deezer",
            Self::YouTubeMusic => "youtube",
        }
    }

    #[must_use]
    pub fn display_name(self) -> &'static str {
        match self {
            Self::Deezer => "Deezer",
            Self::YouTubeMusic => "YouTube Music",
        }
    }
}

/// A playable item. YouTube music videos play in audio-only mode unless disabled.
#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct Track {
    pub id: String,
    pub provider: Provider,
    pub title: String,
    pub artists: Vec<String>,
    pub album: Option<String>,
    pub album_id: Option<String>,
    pub duration_ms: i64,
    pub artwork_url: Option<String>,
    pub is_explicit: bool,
    pub is_video: bool,
    /// Filled in by the quality policy when the track was enqueued.
    pub quality_label: Option<String>,
}

impl Track {
    /// Display line used by rows and the media notification.
    #[must_use]
    pub fn artist_line(&self) -> String {
        self.artists.join(", ")
    }
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct Album {
    pub id: String,
    pub provider: Provider,
    pub title: String,
    pub artist: String,
    pub year: Option<i32>,
    pub artwork_url: Option<String>,
    pub track_count: i32,
    pub quality_label: Option<String>,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct Artist {
    pub id: String,
    pub provider: Provider,
    pub name: String,
    pub artwork_url: Option<String>,
    pub follower_count: Option<i64>,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct Playlist {
    pub id: String,
    pub provider: Provider,
    pub title: String,
    pub description: Option<String>,
    pub track_count: i32,
    pub artwork_url: Option<String>,
    pub is_owned: bool,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct PlaylistDetail {
    pub playlist: Playlist,
    pub tracks: Vec<Track>,
}

/// One shelf on the Home screen (Flow, quick picks, recently played, ...).
#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct HomeSection {
    pub id: String,
    pub title: String,
    pub subtitle: Option<String>,
    pub items: Vec<HomeItem>,
}

#[derive(Clone, Debug, PartialEq, uniffi::Enum)]
pub enum HomeItem {
    TrackItem { track: Track },
    AlbumItem { album: Album },
    PlaylistItem { playlist: Playlist },
    ArtistItem { artist: Artist },
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct HomeFeed {
    pub provider: Provider,
    pub greeting: String,
    pub sections: Vec<HomeSection>,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Default, uniffi::Enum)]
pub enum SearchFilter {
    #[default]
    All,
    Tracks,
    Albums,
    Artists,
    Playlists,
    Videos,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct SearchResults {
    pub provider: Provider,
    pub query: String,
    pub tracks: Vec<Track>,
    pub albums: Vec<Album>,
    pub artists: Vec<Artist>,
    pub playlists: Vec<Playlist>,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct LyricLine {
    pub time_ms: i64,
    pub text: String,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct Lyrics {
    pub track_id: String,
    pub source: String,
    pub synced: bool,
    pub romanized: bool,
    pub lines: Vec<LyricLine>,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum DownloadState {
    Queued,
    Running,
    Done,
    Failed,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct DownloadItem {
    pub track_id: String,
    pub title: String,
    pub artist: String,
    pub state: DownloadState,
    /// 0.0 to 1.0.
    pub progress: f32,
    pub bytes_on_disk: i64,
    pub quality: StreamQuality,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct LibrarySnapshot {
    pub provider: Provider,
    pub tracks: Vec<Track>,
    pub albums: Vec<Album>,
    pub artists: Vec<Artist>,
    pub playlists: Vec<Playlist>,
    pub downloads: Vec<DownloadItem>,
}
