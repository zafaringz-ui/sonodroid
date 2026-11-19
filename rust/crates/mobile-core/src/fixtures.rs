//! Offline development catalogue.
//!
//! Phase 1 has no provider clients yet (they land in Phase 3 for Deezer and Phase 4
//! for YouTube Music). These fixtures let the Compose UI, the demo client and the
//! Rust queue be exercised end to end without credentials or network.
//!
//! They are compiled in only when `CoreConfig::use_fixtures` is set, which every
//! release build must turn off.

use crate::models::{
    Album, Artist, HomeFeed, HomeItem, HomeSection, LibrarySnapshot, LyricLine, Lyrics, Playlist,
    Provider, SearchFilter, SearchResults, Track,
};

fn deezer_track(id: &str, title: &str, artist: &str, album: &str, duration_ms: i64) -> Track {
    Track {
        id: id.into(),
        provider: Provider::Deezer,
        title: title.into(),
        artists: vec![artist.into()],
        album: Some(album.into()),
        album_id: Some(format!("al-{id}")),
        duration_ms,
        artwork_url: None,
        is_explicit: false,
        is_video: false,
        quality_label: Some("FLAC 16-bit / 44.1 kHz".into()),
    }
}

fn youtube_track(id: &str, title: &str, artist: &str, duration_ms: i64) -> Track {
    Track {
        id: id.into(),
        provider: Provider::YouTubeMusic,
        title: title.into(),
        artists: vec![artist.into()],
        album: None,
        album_id: None,
        duration_ms,
        artwork_url: None,
        is_explicit: false,
        is_video: false,
        quality_label: Some("AAC 256 kbps".into()),
    }
}

#[must_use]
pub fn tracks(provider: Provider) -> Vec<Track> {
    match provider {
        Provider::Deezer => vec![
            deezer_track(
                "dz-1",
                "Night Ferry",
                "Astrid Lind",
                "Harbour Lights",
                214_000,
            ),
            deezer_track(
                "dz-2",
                "Paper Lantern",
                "Astrid Lind",
                "Harbour Lights",
                187_000,
            ),
            deezer_track("dz-3", "Slow Tide", "Kaito Mori", "Coastline", 243_000),
            deezer_track("dz-4", "Copper Sky", "Kaito Mori", "Coastline", 198_000),
            deezer_track("dz-5", "Rain on Glass", "Mireille", "Interiors", 226_000),
            deezer_track("dz-6", "Long Way Home", "Mireille", "Interiors", 251_000),
        ],
        Provider::YouTubeMusic => vec![
            youtube_track("yt-1", "Harbour Lights (Live)", "Astrid Lind", 231_000),
            youtube_track("yt-2", "Coastline", "Kaito Mori", 205_000),
            youtube_track("yt-3", "Interiors", "Mireille", 219_000),
            youtube_track("yt-4", "Signal Fires", "Nnamdi Okafor", 262_000),
            youtube_track("yt-5", "Low Sun", "Nnamdi Okafor", 194_000),
            youtube_track("yt-6", "Paper Lantern (Acoustic)", "Astrid Lind", 178_000),
        ],
    }
}

fn release(provider: Provider, id: &str, title: &str, artist: &str, year: i32) -> Album {
    Album {
        id: id.into(),
        provider,
        title: title.into(),
        artist: artist.into(),
        year: Some(year),
        artwork_url: None,
        track_count: 2,
        quality_label: None,
    }
}

#[must_use]
pub fn albums(provider: Provider) -> Vec<Album> {
    match provider {
        Provider::Deezer => vec![
            release(
                provider,
                "al-harbour",
                "Harbour Lights",
                "Astrid Lind",
                2024,
            ),
            release(provider, "al-coastline", "Coastline", "Kaito Mori", 2023),
            release(provider, "al-interiors", "Interiors", "Mireille", 2025),
        ],
        Provider::YouTubeMusic => vec![
            release(provider, "al-live", "Live Sessions", "Astrid Lind", 2024),
            release(provider, "al-signal", "Signal Fires", "Nnamdi Okafor", 2025),
            release(provider, "al-interiors", "Interiors", "Mireille", 2025),
        ],
    }
}

#[must_use]
pub fn artists(provider: Provider) -> Vec<Artist> {
    let names: &[(&str, &str)] = match provider {
        Provider::Deezer => &[
            ("ar-astrid", "Astrid Lind"),
            ("ar-kaito", "Kaito Mori"),
            ("ar-mireille", "Mireille"),
        ],
        Provider::YouTubeMusic => &[
            ("ar-astrid", "Astrid Lind"),
            ("ar-nnamdi", "Nnamdi Okafor"),
            ("ar-mireille", "Mireille"),
        ],
    };
    names
        .iter()
        .map(|(id, name)| Artist {
            id: (*id).into(),
            provider,
            name: (*name).into(),
            artwork_url: None,
            follower_count: Some(48_120),
        })
        .collect()
}

#[must_use]
pub fn playlists(provider: Provider) -> Vec<Playlist> {
    vec![
        Playlist {
            id: "pl-flow".into(),
            provider,
            title: "Flow".into(),
            description: Some("Endless mix from your listening".into()),
            track_count: 6,
            artwork_url: None,
            is_owned: true,
        },
        Playlist {
            id: "pl-late".into(),
            provider,
            title: "Late Night Drive".into(),
            description: None,
            track_count: 4,
            artwork_url: None,
            is_owned: true,
        },
        Playlist {
            id: "pl-saved".into(),
            provider,
            title: "Saved this week".into(),
            description: None,
            track_count: 3,
            artwork_url: None,
            is_owned: false,
        },
    ]
}

#[must_use]
pub fn home(provider: Provider) -> HomeFeed {
    let all = tracks(provider);
    let section =
        |id: &str, title: &str, subtitle: Option<&str>, items: Vec<HomeItem>| HomeSection {
            id: id.into(),
            title: title.into(),
            subtitle: subtitle.map(Into::into),
            items,
        };

    let quick_picks = all
        .iter()
        .take(4)
        .map(|track| HomeItem::TrackItem {
            track: track.clone(),
        })
        .collect();
    let new_releases = albums(provider)
        .into_iter()
        .map(|album| HomeItem::AlbumItem { album })
        .collect();
    let mixes = playlists(provider)
        .into_iter()
        .take(2)
        .map(|playlist| HomeItem::PlaylistItem { playlist })
        .collect();

    HomeFeed {
        provider,
        greeting: match provider {
            Provider::Deezer => "Good evening".into(),
            Provider::YouTubeMusic => "Good evening".into(),
        },
        sections: vec![
            section(
                "flow",
                "Flow",
                Some("Built from your listening"),
                quick_picks,
            ),
            section("new", "New releases", None, new_releases),
            section("mixes", "Made for you", None, mixes),
        ],
    }
}

#[must_use]
pub fn library(provider: Provider) -> LibrarySnapshot {
    LibrarySnapshot {
        provider,
        tracks: tracks(provider),
        albums: albums(provider),
        artists: artists(provider),
        playlists: playlists(provider),
        downloads: Vec::new(),
    }
}

#[must_use]
pub fn search(provider: Provider, query: &str, filter: SearchFilter) -> SearchResults {
    let needle = query.trim().to_lowercase();
    let matches = |haystack: &str| needle.is_empty() || haystack.to_lowercase().contains(&needle);
    let wants =
        |candidate: SearchFilter| matches!(filter, SearchFilter::All) || filter == candidate;

    let tracks = if wants(SearchFilter::Tracks) || wants(SearchFilter::Videos) {
        tracks(provider)
            .into_iter()
            .filter(|track| matches(&track.title) || matches(&track.artist_line()))
            .collect()
    } else {
        Vec::new()
    };
    let albums = if wants(SearchFilter::Albums) {
        albums(provider)
            .into_iter()
            .filter(|album| matches(&album.title) || matches(&album.artist))
            .collect()
    } else {
        Vec::new()
    };
    let artists = if wants(SearchFilter::Artists) {
        artists(provider)
            .into_iter()
            .filter(|artist| matches(&artist.name))
            .collect()
    } else {
        Vec::new()
    };
    let playlists = if wants(SearchFilter::Playlists) {
        playlists(provider)
            .into_iter()
            .filter(|playlist| matches(&playlist.title))
            .collect()
    } else {
        Vec::new()
    };

    SearchResults {
        provider,
        query: query.to_owned(),
        tracks,
        albums,
        artists,
        playlists,
    }
}

/// Lyrics fixture shaped like a synced provider response.
#[must_use]
pub fn lyrics(track_id: &str) -> Lyrics {
    let lines = vec![
        (0, ""),
        (4_000, "The ferry leaves the harbour slow"),
        (9_500, "and the lights come on along the row"),
        (15_000, "I keep your voice inside the radio"),
        (21_000, ""),
        (24_000, "Night ferry, carry me home"),
    ];
    Lyrics {
        track_id: track_id.to_owned(),
        source: "fixture".into(),
        synced: true,
        romanized: false,
        lines: lines
            .into_iter()
            .map(|(time_ms, text)| LyricLine {
                time_ms,
                text: text.to_owned(),
            })
            .collect(),
    }
}

#[must_use]
pub fn playlist_detail(provider: Provider, id: &str) -> Option<crate::models::PlaylistDetail> {
    let playlist = playlists(provider)
        .into_iter()
        .find(|playlist| playlist.id == id)?;
    let tracks = tracks(provider)
        .into_iter()
        .take(playlist.track_count.max(0) as usize)
        .collect();
    Some(crate::models::PlaylistDetail { playlist, tracks })
}
