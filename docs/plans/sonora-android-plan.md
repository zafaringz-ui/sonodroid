# Sonora: Desktop to Android Conversion Plan

Source reviewed: github.com/sonorahq/sonora (v0.50.2, 1,031 files, Rust workspace with 14 crates, GPL-3.0). I listed the whole file tree and read the manifests and README. I didn't open every source file one by one; a few spots marked "audit" below still need to be read line by line.

Note: this is a native Android app (Kotlin + Rust). Lovable can't build or run it. This plan is meant to be carried out in Android Studio.

---

## 1. What exists today (inventory)

| Layer | Crate | Contents | Android fate |
|---|---|---|---|
| App shell | `sonora` | main, tray (SNI/native), dock, thumbbar, x11_icon, window_title, single-instance, gestures, console, http, memory | Rewrite as an Android Activity and Service |
| UI toolkit | `ui` | GPUI widgets: button, card, menu, modal, popover, scrubber, slider, tabs, table, toast, tooltip, glass, waves/lattice WGSL shaders, visualizer, qr, otp, swipe, drag | Rewrite in Jetpack Compose |
| Screens | `views` | screens, chrome, shared, shells (see section 3) | Rewrite in Compose |
| State | `state` | ~60 modules: playback, queue, library, search, home, artist, genre, history, lyrics, downloads, devices, equalizer, settings, themes, session, scrobble, discord, listening (jam server), remote (MPRIS/souvlaki), updates, vault, potoken, sponsorblock, network, wake | Keep most of it in Rust; replace the OS-specific parts |
| Domain logic | `logic` | queue, sections, station, lyrics | Keep as-is |
| Providers | `music` | Spotify, YouTube Music, Apple Music, Tidal, Deezer, Jellyfin, Subsonic, Local files, Soloist; lyrics (LRCLIB, Musixmatch, NetEase, KuGou, TTML/LRC, romanization); scrobbling (Last.fm, ListenBrainz, Maloja); engine, sink, equalizer, spectrum, aac/mp4, drm | Keep. Replace audio output and DRM |
| Audio output | `music/output` | ALSA, cpal host, exclusive mode (hog), resampler | Replace with Oboe/AAudio |
| DRM | `widevine` | C++ CDM shim, CENC | Replace with Android MediaDrm |
| Webview | `webview` | Linux/macOS/Windows login webviews | Replace with Android WebView or Custom Tabs |
| Other crates | `router`, `input`, `i18n`, `icons`, `embed`, `storage`, `cli` | navigation, shortcuts, Fluent i18n, icons, cache | router/i18n/storage kept; input remapped; cli dropped |
| Assets | `assets/` | 19 languages (.ftl), 18 themes (JSON), Inter fonts, icon sets | Reused directly |
| Packaging | flatpak, aur, nix, Windows installer, macOS plist, GitHub workflows | | Replaced by Gradle, signing and Play Store |

---

## 2. Target architecture

```text
+-------------------------------------------------+
| Jetpack Compose UI (Kotlin)                      |
|  screens / chrome / shared components / themes   |
+---------------------+---------------------------+
| ViewModels + Flow   |  Media3 MediaSessionService|
+---------------------+---------------------------+
| UniFFI bindings (generated Kotlin <-> Rust)      |
+-------------------------------------------------+
| Rust core (.so for arm64-v8a, armeabi-v7a, x86_64)
|  music providers, state, logic, router, i18n,    |
|  storage, lyrics, scrobble, equalizer, spectrum  |
+-------------------------------------------------+
| Platform adapters: Oboe audio, MediaDrm,         |
| WebView auth, Keystore vault, WorkManager        |
+-------------------------------------------------+
```

Key decision: GPUI has no Android backend, so the whole visual layer gets rewritten. Everything below the UI stays in Rust and is exposed through one new `mobile-core` crate using UniFFI.

---

## 3. Every page and how it maps to Android

Navigation: bottom bar (Home, Search, Library, Settings), a mini-player above it, and a full-screen Now Playing sheet you swipe up.

| Desktop file | Android screen |
|---|---|
| screens/login.rs + popups (accounts, cookie, credentials) + device_code | Onboarding, provider picker, sign-in (WebView, device code, QR) |
| screens/home | Home feed with shelves, picks and pins |
| screens/search | Search with filters and recent searches |
| screens/library (albums, artists, playlists) | Library tabs and a grid/list toggle |
| screens/artist | Artist page (hero, top tracks, discography) |
| screens/detail | Album and playlist detail |
| screens/song | Song details and credits |
| screens/genre | Genre browsing |
| screens/history | Listening history |
| screens/user | User profile |
| screens/equalizer (graph, meter, rows, rules, transfer) | Equalizer screen: touch graph, presets, import/export |
| settings/general, appearance, themes, layout | Settings: general, appearance, themes |
| settings/accounts, sessions, connect | Accounts, devices, Spotify Connect |
| settings/playback, sound, now_playing, player_bar | Playback, sound and Now Playing settings |
| settings/lyrics, scrobbling, discord, storage, about | Matching settings pages |
| settings/keybinds | Dropped (kept only for hardware keyboards) |
| shells/fullscreen | Now Playing full screen: lyrics, visualizer, ambient background |
| shells/workspace | Main layout with bottom navigation |
| chrome/player_bar, timeline | Mini-player and seek bar |
| chrome/sidebar_left / right / aside | Navigation drawer, queue sheet, lyrics sheet |
| chrome/jam, members, session, sessions_button | Group-listening (Jam) screen |
| chrome/downloads | Downloads screen |
| chrome/output_picker | Output device picker (Bluetooth, speaker, Cast) |
| chrome/title_bar, toolbar, swipe_cue | Dropped; replaced by the top app bar |
| shared: playlist_editor, smart_playlist, tag_editor, cover_viewer, confirm, menus | Bottom sheets and dialogs |
| shared: tracks (columns, sieve, sort) | Track list with sort and filter |

---

## 4. Features and their Android equivalents

- Playback and queue: Media3 `MediaSessionService` running as a foreground service with a notification, lock screen controls, Bluetooth/headset buttons and Android Auto. The Rust engine keeps decoding.
- MPRIS / souvlaki remote: replaced by MediaSession.
- System tray, dock, thumbbar: replaced by the media notification and a home screen widget (Glance).
- Spotify Connect and Jellyfin remote: keep the Rust code; add network permissions and multicast (mDNS) lock.
- Jam / listening server: keep, using a local network socket while in the foreground.
- Offline downloads: WorkManager plus app-specific storage. Encrypted content only, following the project's anti-piracy rules.
- Local files: MediaStore and Storage Access Framework instead of directory scanning; the id3/tags/m3u code stays.
- Lyrics: all providers kept; synced lyrics view in Compose.
- Equalizer and spectrum: kept in Rust DSP; the visualizer is drawn on a Compose Canvas or AGSL shaders, which replace the WGSL shaders.
- Scrobbling: kept; queued in WorkManager for offline retries.
- Discord rich presence: dropped (no local IPC socket on Android).
- Self-updater (`state/updates`): dropped; updates come through the Play Store or F-Droid.
- Themes: all 18 JSON themes loaded into Material 3 color schemes, plus Material You dynamic color.
- i18n: Fluent .ftl files kept through the Rust `i18n` crate.
- Gestures: swipe, long-press and drag reorder mapped to Compose gestures.
- PoToken (YouTube, `potoken.js`): runs in a hidden WebView or a QuickJS engine. Audit needed.
- SponsorBlock: kept.

---

## 5. Backend and providers

No server of our own: the app talks directly to each music service, as the desktop version does.

| Provider | Auth on Android | Playback risk |
|---|---|---|
| Spotify | OAuth through Custom Tabs | High: Widevine needs MediaDrm L1/L3, and may conflict with the terms of service |
| YouTube Music | WebView cookie login | Medium: PoToken |
| Apple Music | WebView token | High: FairPlay/Widevine |
| Tidal | Device code flow | Medium: Widevine |
| Deezer | Login plus decrypt | Medium |
| Jellyfin / Subsonic | Server URL and password | Low |
| Local files | MediaStore | Low |
| Soloist | Daemon pairing; the daemon itself can't run on Android | Audit |

Credentials (`vault.rs`, `credentials.rs`) move to Android Keystore with EncryptedSharedPreferences. Storage and cache use app-specific directories in place of `dirs`.

---

## 6. Engineering resources

- Languages: Rust (stable, edition 2024), Kotlin 2.x, a small amount of C++ (Oboe).
- Tools: Android Studio, Gradle (Kotlin DSL), NDK r27+, `cargo-ndk`, UniFFI, `rustup` targets aarch64/armv7/x86_64-linux-android.
- Libraries: Jetpack Compose, Material 3, Navigation Compose, Media3 (ExoPlayer for the HTTP fallback, MediaSession), Oboe, Coil (images), DataStore, WorkManager, Hilt, Kotlin Coroutines/Flow, Glance, AndroidX Car App (Android Auto), Media3 Cast (optional).
- Rust crate changes: drop alsa, x11rb, windows, gpui, cpal-host-specific code, clap; add `jni`/`ndk`, `uniffi`, `oboe-rs` or a cpal Android backend.
- Testing: cargo tests for the core, JUnit and Turbine for ViewModels, Compose UI tests, Macrobenchmark, Firebase Test Lab device matrix.
- CI/CD: GitHub Actions to build the .so files, build the AAB, sign it, and upload through Play Console or fastlane. An F-Droid build without proprietary parts.
- People: 1 Rust engineer, 2 Android engineers, 1 designer, 1 QA, part-time legal/licensing review.
- Devices: low-end phone (Android 8, API 26), mid-range, Pixel, Samsung, a tablet, an Android Auto head unit or the desktop head unit emulator.

---

## 7. Phased roadmap to finished product

1. Discovery (2 weeks): line-by-line audit of `state`, `music`, `widevine`, `potoken`, `soloist`; legal check on DRM and GPL; mobile UX design in Figma.
2. Core extraction (4 weeks): create the `mobile-core` crate, put platform code behind feature flags, define the UniFFI interface, and cross-compile for the NDK.
3. Audio and playback (4 weeks): Oboe sink, MediaSessionService, notification, audio focus, Bluetooth, gapless playback, equalizer.
4. Providers (6 weeks): Jellyfin, Subsonic and Local first; then YouTube and Deezer; then Tidal, Spotify and Apple through MediaDrm.
5. UI (8 weeks, alongside steps 3 and 4): every screen in section 3, themes, i18n, accessibility (TalkBack), tablet layouts.
6. Extras (3 weeks): lyrics, Jam, Connect, downloads, scrobbling, widget, Android Auto.
7. Hardening (3 weeks): battery and Doze behavior, memory, crash reporting, performance benchmarks, security review.
8. Release (2 weeks): Play Store listing, privacy policy (adapted from PRIVACY.md), F-Droid, beta channel.

Estimated total: about 6 to 8 months with the team in section 6.

---

## 8. Risks

- DRM services (Spotify, Apple, Tidal) may block third-party Android clients or need certified devices.
- GPL-3.0 makes Play Store distribution possible, but the source must be published.
- Background limits on Android can stop the Jam server and Connect discovery.
- The whole UI rewrite is the biggest cost.
