# Sonora for Android: Merged Master Plan

Merges `sonora-android-plan.md` (v1, all providers, broad) and `sonora-android-plan_v2.md` (Deezer + YouTube Music, deep). Source: github.com/sonorahq/sonora v0.50.2 (Rust workspace, 14 crates, GPL-3.0).

**How the two plans were combined**

- v2's stack, provider detail, screen list, quality targets and roadmap are the backbone, because they were verified against the Deezer, YouTube and token code.
- v1's full crate inventory, desktop-to-Android mapping, extra providers (Jellyfin, Subsonic, Local, Tidal, Spotify, Apple) and DRM analysis are kept as **later expansion tracks**, not v1 scope.
- Where they disagreed, v2 won (e.g. PoToken: v1 said "audit needed", v2 resolved it with an off-screen WebView).
- This is a native Android app (Kotlin + Rust). Lovable cannot build or run it; build in Android Studio.

---

## 1. Goal and scope

**Release 1 (store-ready):** Deezer + YouTube Music, best possible quality, on the reused Rust core.

**Expansion tracks (after Release 1):**

- Track A, low risk: Jellyfin, Subsonic/OpenSubsonic, Local files.
- Track B, high risk: Tidal (device code + Widevine), Spotify and Apple Music (Widevine/FairPlay via Android MediaDrm). These may conflict with terms of service or need certified devices. Only attempt after legal review.
- Soloist: daemon cannot run on Android; audit pairing only.

---

## 2. Stack decision

| Layer | Choice | Why |
| --- | --- | --- |
| UI | Kotlin 2.1 + Jetpack Compose (Material 3) | Native 120 Hz animation, Material You, TalkBack, foldables, Android Auto. Flutter/React Native add a second runtime and still need native code for media sessions. |
| Core | Rust (edition 2024), reused | Deezer decrypt, YouTube streaming, gapless engine, EQ, lyrics, queue already exist and are tested. Rewriting costs 6+ months. |
| Bridge | UniFFI 0.28 | Type-safe Kotlin from Rust, async to coroutines, callbacks for events. |
| Audio out | Oboe 1.9 (AAudio, OpenSL fallback) via `oboe-rs` | Low latency, float output; keeps Sonora's resampler, gapless join and EQ. ExoPlayer would bypass the Rust engine. |
| Media session | AndroidX Media3 1.5 `MediaSessionService` + custom `SimpleBasePlayer` | Lock screen, notification, Bluetooth, Wear OS, Android Auto, Assistant, all driven by the Rust engine. |
| YouTube token JS | Android System WebView | Real Chromium passes BotGuard. |

Rejected: Flutter, React Native, Kotlin Multiplatform core rewrite, ExoPlayer-only playback.

Key fact: GPUI has no Android backend, so the entire visual layer is rewritten. Everything below the UI stays in Rust behind one new `mobile-core` crate.

```text
+-------------------------------------------------+
| Jetpack Compose UI (Kotlin)                      |
|  screens / chrome / shared components / themes   |
+---------------------+---------------------------+
| ViewModels + Flow   | Media3 MediaSessionService |
+---------------------+---------------------------+
| UniFFI bindings (generated Kotlin <-> Rust)      |
+-------------------------------------------------+
| Rust core (.so: arm64-v8a, armeabi-v7a, x86_64)  |
|  providers, state, logic, router, i18n, storage, |
|  lyrics, scrobble, equalizer, spectrum           |
+-------------------------------------------------+
| Platform adapters: Oboe, WebView auth, Keystore, |
| WorkManager (MediaDrm only in Track B)           |
+-------------------------------------------------+
```

---

## 3. Inventory: what is kept, changed, removed

### Keep (Rust, compiled for Android)

- `music/deezer/*` (auth, client, decrypt, playback, links, wire)
- `music/youtube/*` (accounts, auth, client, genres, links, lyrics, playback, radio, segments, subscriptions, wire)
- Engine: `engine`, `stream`, `sink`, `audio`, `aac`, `mp4`, `trim`, `progress`, `order`
- `equalizer/*`, `spectrum/*`
- Lyrics: `lyrics/*` + LRCLIB, Musixmatch, NetEase, KuGou, TTML/LRC, romanisation
- Scrobbling: Last.fm, ListenBrainz, Maloja
- Misc: `sponsorblock`, `smart`, `models`, `link`, `cover`, `artwork`, `vault`, `credentials`, `redact`, `trouble`, `escape`, `calendar`
- `music/local/*` (kept for Track A)
- Crates: `logic` (queue, sections, station, lyrics), `router`, `i18n`, `storage`, `icons` (export as vector drawables)
- `state` modules without UI ties: playback, queue, library, search, home, artist, genre, history, lyrics, downloads, equalizer, settings, themes, session, scrobble, sponsorblock, network, pins, profile, snapshot, song, tags, toast, usage, vault, catalog, detail, cover, mosaic, links, listening (Jam)
- Assets reused directly: 19 languages (.ftl), 18 theme JSONs, Inter fonts, icon sets

### Change

- `output_host.rs` becomes a new `output_oboe.rs`
- `vault.rs` + `credentials.rs` use Android Keystore through a Kotlin callback (AES-GCM + EncryptedFile)
- `state/potoken.rs` becomes a Kotlin `PoTokenMinter` using WebView
- `dirs` paths become `Context.filesDir`, `cacheDir`, `getExternalFilesDir(MUSIC)`
- `state/network.rs` fed by `ConnectivityManager.NetworkCallback`
- `state/devices.rs`, `outputs.rs` use `AudioManager.getDevices()` + Media3 routing
- `state/remote` (MPRIS/souvlaki) is replaced by MediaSession
- Local files: MediaStore + Storage Access Framework instead of directory scanning (id3/tags/m3u code stays)

### Remove from Release 1

- Providers: spotify, apple, tidal, jellyfin, subsonic, soloist, binimum, `connect.rs`, `drm.rs`, desktop potoken window, `widevine` crate, librespot-\*, opensubsonic, oauth2 (restored only if Track A/B happen)
- Desktop: `sonora` app shell (tray, dock, thumbbar, x11\_icon, window\_title, single-instance, console), `webview` crate, `ui` (GPUI), `views`, `input/shortcuts` (hardware keyboards only), `cli`, `state/discord` (no local IPC socket on Android), `state/updates` (Play Store/F-Droid; the GitHub build keeps a lightweight update check), `state/window_shape`, `state/wake`, `output_alsa.rs`, `output_hog.rs`
- Packaging: flatpak, aur, nix, Windows installer, macOS plist, desktop workflows
- Rust crates dropped: alsa, x11rb, windows, gpui, clap, desktop-specific cpal code

---

## 4. Deezer

**Endpoints (keep as-is):** `www.deezer.com/ajax/gw-light.php` (private gateway: library, playlists, favourites, flow), `api.deezer.com` (catalogue), `pipe.deezer.com/api` (GraphQL home/recs), `media.deezer.com/v1/get_url` (signed stream URLs), `auth.deezer.com/login/arl?jo=p&rto=c&i=c` (session from ARL), `upload.deezer.com/v2/playlist/picture` (cover upload).

**Sign-in**

1. Full-screen WebView at `https://www.deezer.com/login`.
2. After login read the `arl` cookie with `CookieManager`.
3. Validate with `deezer::auth::arl()`, store encrypted (Keystore AES-GCM + EncryptedFile).
4. Fallback: "Paste ARL" (advanced), same validation.
5. Clear WebView cookies after capture.

**Quality ladder:** FLAC\_24 (Hi-Res), FLAC, MP3\_320, MP3\_128. Account plan sets the ceiling. Separate choices for Wi-Fi, mobile data, downloads.

**Pipeline**

```text
get_url (signed) -> ranged HTTPS fetch (reqwest/rustls) -> Blowfish CBC stripe decrypt
(every 3rd 2048-byte chunk) -> symphonia FLAC/MP3 -> EQ -> loudness -> resampler -> Oboe
```

- The decrypt secret is scraped from the web-player bundle (`fetch_secret`); cache it and refresh on failure.
- Offline downloads stay **encrypted**; no decrypted export (project anti-piracy policy).

**Features:** Flow, home channels, search (podcasts excluded at v1), favourites, followed artists, playlists (create, rename, reorder, cover upload, privacy), album/artist pages, track radio/mix, lyrics, history, offline.

**Risk:** ARL login and the web gateway are not a public API and can change. Mitigation: quick release channel, remote config kill-switch.

---

## 5. YouTube Music

**Library:** Sonora's `ytmusic` fork over InnerTube with browser cookies.

**Sign-in**

1. WebView opens `SIGN_IN_URL` (accounts.google.com to music.youtube.com).
2. On landing, capture cookies (`SAPISID`, `__Secure-3PAPISID`, `SID`, `HSID`, `SSID`, `APISID`, `LOGIN_INFO`).
3. Pass to `youtube::auth::header()`; the client refreshes rotated cookies (`keep_fresh`) and writes them back encrypted.
4. Multiple accounts/brand channels via `accounts.rs` picker sheet.
5. Google blocks embedded-WebView sign-in for some accounts. Fallback: Custom Tabs plus a cookie-handoff screen. **Test in Phase 0; it is a blocker.**

**Quality ladder:** High = AAC 256 (Premium) / 128, Normal = AAC 128, Low = HE-AAC 48. Opus optional via symphonia-libopus.

**Pipeline**

```text
InnerTube player -> stream URL -> ranged download (resumes from stalled byte)
-> DASH fMP4 + sidx seeking -> AAC decode -> normalise to -14 LUFS
-> trim silence -> EQ -> Oboe
```

Timeouts: 15 s per step, 2 attempts (keep).

**PoToken**

- Kotlin `PoTokenMinter`: off-screen WebView loads `youtube.com/embed/jNQXAC9IVRw`, injects `potoken.js`, reads cookie `SONORA_POT`.
- Rust asks via UniFFI callback `TokenMinter.mint(binding)`; Kotlin answers async.
- Linger briefly after the last request, then destroy (about 270 MB).
- Only while the app or playback service is alive.

**Features:** home shelves, moods & genres, search with filters, library (songs, albums, artists, playlists, subscriptions), likes, history, radio/start mix, up-next autoplay, lyrics, SponsorBlock, audio-only mode for music videos, playlist edit.

**Not shipped:** YouTube audio downloads (ripping; README forbids it). Offline = bounded LRU stream cache, cleared on sign-out.

**Risk:** InnerTube/BotGuard change often; Google may block the app. Mitigation: pin `ytmusic`, update weekly, remote kill-switch, F-Droid/GitHub fallback.

---

## 6. Shared features

- Unified queue mixing Deezer and YouTube tracks; shuffle, repeat, gapless, crossfade (0 to 12 s), next-track preload.
- Lyrics order: provider, then LRCLIB, Musixmatch, NetEase, KuGou; TTML word-by-word; Japanese romanisation (kakasi).
- Equalizer: parametric EQ, presets, AutoEQ/APO import, per-output profiles (headphones, speaker, car). Touch-curve UI.
- Spectrum visualizer (rustfft) streamed to Compose at 60 fps; drawn on Canvas or AGSL shaders (replaces WGSL waves/lattice shaders).
- Scrobbling queued offline in WorkManager.
- Jam (group listening): host on local Wi-Fi with NSD, foreground only; QR join code.
- Smart playlists, pins, history, play stats.
- Link handling: paste or open deezer.com / deezer.page.link / music.youtube.com / youtu.be links; share-target intent.
- Themes: all 18 JSON themes mapped to Material 3 color schemes + Material You dynamic color.
- i18n: Fluent `.ftl` through the Rust `i18n` crate; Weblate for translations.
- Cast (optional, Media3 Cast); output picker covers phone, Bluetooth, wired, Cast.

---

## 7. Screens (Compose)

Navigation: bottom bar (Home, Search, Library, Settings), mini-player above it, swipe-up Now Playing. Dropped desktop chrome: title bar, toolbar, swipe cue, keybinds page.

| # | Screen | Content | Desktop source |
| --- | --- | --- | --- |
| 1 | Splash | Logo, core load | none |
| 2 | Welcome / provider picker | Deezer, YouTube Music, both | screens/login |
| 3 | Deezer sign-in | WebView + Paste ARL | popups/cookie |
| 4 | YouTube sign-in | WebView, account/brand picker | popups/accounts |
| 5 | Home | Per-provider tabs, shelves, Flow, quick picks, pins | screens/home |
| 6 | Search | Field, recents, filters, provider switch | screens/search |
| 7 | Library | Playlists, Albums, Artists, Songs, Downloads; sort/filter; grid/list | screens/library |
| 8 | Album | Hero art, tracks, more by artist | screens/detail |
| 9 | Playlist | Header, edit, reorder, cover, privacy | detail + playlist\_editor |
| 10 | Artist | Hero, top songs, discography, related, follow | screens/artist |
| 11 | Song info | Credits, tags, codec/quality badge | screens/song |
| 12 | Genre / Mood | Grid and playlists | screens/genre |
| 13 | History | By day | screens/history |
| 14 | Profile | Account, stats | screens/user |
| 15 | Mini-player | Art, title, play/pause, progress; swipe to skip | chrome/player\_bar |
| 16 | Now Playing | Art, ambient background, scrubber, controls, like, output, lyrics peek | shells/fullscreen |
| 17 | Lyrics full | Synced, tap-to-seek, translation/romanised | lyrics |
| 18 | Queue | Drag reorder, swipe remove, "playing from" | sidebar\_right |
| 19 | Equalizer | Touch curve, bands, presets, import/export | screens/equalizer |
| 20 | Output picker | Phone, Bluetooth, wired, Cast | chrome/output\_picker |
| 21 | Jam | Host/join, members, QR | chrome/jam, members |
| 22 | Downloads | Progress, storage use (Deezer only) | chrome/downloads |
| 23 | Settings hub | Sections below | screens/settings |
| 24 to 35 | Settings pages | Accounts and devices, Playback (crossfade, gapless, normalise), Audio quality (Wi-Fi/mobile/download per provider), Sound, Lyrics, Appearance, Themes, Now Playing, Scrobbling, Storage/cache, Language, About/licenses | settings/\* |
| 36 | Dialogs and sheets | Track menu, add-to-playlist, smart playlist builder, tag editor, cover viewer, confirm, error | shared/\* |
| 37 | Widgets | 2x2 and 4x2 home-screen players (Glance) | tray/dock replacement |
| 38 | Android Auto | Browse tree: Home, Library, Recent, Search | none |

Layouts: portrait, landscape, tablet/foldable (two-pane + NavigationRail) via WindowSizeClass. Track lists keep sort/filter (`shared/tracks`). Gestures (swipe, long-press, drag reorder) map to Compose gestures.

---

## 8. Architecture and modules

```text
android/
  app/                 Activity, nav graph, DI
  core-bridge/         UniFFI Kotlin + jniLibs (.so)
  feature-home/ search/ library/ player/ lyrics/ equalizer/ settings/ auth/ jam/ downloads/
  service-playback/    MediaSessionService, SimpleBasePlayer adapter, notification
  platform-audio/      Oboe glue, audio focus, noisy receiver
  platform-webview/    Sign-in WebViews, PoTokenMinter
  platform-secure/     Keystore, EncryptedFile
  design-system/       Theme, typography, components, motion
  widget/  auto/
rust/
  crates/mobile-core/  UniFFI interface, event bus
  crates/music, logic, state, router, i18n, storage (trimmed)
```

- Pattern: MVI. Compose screen, ViewModel (StateFlow), Repository, Rust core.
- Rust events (position, track change, errors, download progress) via UniFFI callback to `SharedFlow`.
- Rust owns a Tokio runtime (2 to 4 workers); the audio callback thread is real-time with no allocation.
- Persistence: Rust SQLite (rusqlite, bundled) for library cache, history, vault; Kotlin DataStore for UI prefs only.
- Platform code sits behind cargo feature flags so desktop builds still work.

---

## 9. Materials and tools

**Android/Kotlin:** Kotlin 2.1, AGP 8.7+, Gradle 8.10 (Kotlin DSL, version catalog), JDK 17; compileSdk/targetSdk 35, minSdk 26; Compose BOM 2024.12+, Material 3, extended icons, adaptive layouts; Navigation Compose (type-safe); Hilt 2.52 + KSP; Coroutines 1.9, kotlinx.serialization; Media3 1.5 (session, common, cast, ui-compose); Car App 1.7; Glance 1.1; WorkManager 2.10; DataStore; Security Crypto/Tink; `androidx.webkit`; Coil 3 + Palette; Baseline Profiles + Macrobenchmark; Splashscreen API; Oboe 1.9; opt-in crash reporting (Sentry or Crashlytics; ACRA for F-Droid).

**Testing:** cargo tests, JUnit5, MockK, Turbine, Compose UI tests, Robolectric, Paparazzi, Espresso (WebView), Firebase Test Lab.

**Rust:** toolchain from `rust-toolchain.toml` + targets aarch64/armv7/x86\_64-linux-android; `cargo-ndk` 3.x; NDK r27c; `uniffi` 0.28; `oboe` crate (or cpal AAudio); `jni`, `ndk-context`, `android_logger`. Keep tokio, reqwest (rustls only), symphonia, blowfish, cbc, md-5, sha1, ring, rusqlite, rustfft, rtrb, lofty, kakasi, serde, ytmusic, fluent-bundle, image. Release profile: `lto="fat"`, `codegen-units=1`, `panic="abort"`, `strip=true`, `opt-level=3`.

**Design:** Figma (Material 3 kit), 18 themes to M3 schemes, Inter font, icons to vector drawables, optional Lottie.

**Infrastructure:** GitHub Actions (Rust matrix to AAR to AAB/APK; ktlint, detekt, clippy, cargo-deny, tests); fastlane for Play upload; Play App Signing; reproducible F-Droid build without proprietary parts; remote config as static JSON on GitHub Pages (kill-switches, no backend); Weblate.

**Test devices:** Android 8 low-end (2 GB), Android 12 mid-range, Pixel 8 (Android 15), Samsung (One UI), Xiaomi (battery killer), foldable, tablet, Android Auto Desktop Head Unit, Bluetooth headphones (LDAC/AAC), USB-C DAC for Hi-Res FLAC.

---

## 10. Manifest and permissions

- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS` (Android 13+), `WAKE_LOCK`
- `CHANGE_WIFI_MULTICAST_STATE`, `NEARBY_WIFI_DEVICES` (Jam discovery)
- `BLUETOOTH_CONNECT` (output names)
- `READ_MEDIA_AUDIO` only if Local files (Track A) ships
- `PlaybackService` exported for MediaBrowser, `foregroundServiceType="mediaPlayback"`
- Intent filters for Deezer and YouTube links; `ACTION_SEND` text share target
- `usesCleartextTraffic="false"`, network security config pinned to system CAs

---

## 11. Quality targets

- Cold start under 1.2 s (mid-range); first audio under 1.5 s on Wi-Fi
- Gapless 0 ms gap; seek under 300 ms
- Battery under 4% per hour of screen-off playback
- Under 30 MB per ABI (AAB splits)
- Crash-free sessions above 99.5%; no ANRs
- TalkBack labels everywhere, 48 dp touch targets, font scaling to 200%

---

## 12. Security and privacy

- ARL and Google cookies encrypted at rest (AES-256-GCM, Keystore, StrongBox when available); never logged (`redact.rs` applied to Android logs)
- WebView: JS bridge only on sign-in and token pages, file access off, cookies cleared after capture
- No analytics by default; opt-in crash reports
- Privacy policy adapted from PRIVACY.md; Play Data Safety form
- GPL-3.0: publish all source including the Android code

---

## 13. Roadmap

| Phase | Weeks | Output |
| --- | --- | --- |
| 0. Risk spikes and discovery | 2 | Proofs: Deezer ARL from WebView, Google sign-in in WebView, PoToken minted, Rust core cross-compiled, Oboe plays FLAC. Line-by-line audit of `state`, `music`, `potoken`. Legal check (ToS, GPL). Figma UX started. |
| 1. Core trim + bridge | 3 | `mobile-core`, providers removed, feature flags, UniFFI API, Kotlin smoke tests |
| 2. Playback service | 3 | Oboe sink, MediaSession, notification, focus, Bluetooth, gapless, normalise |
| 3. Deezer | 3 | Sign-in, browse, search, library, playlists, all qualities, downloads |
| 4. YouTube Music | 4 | Sign-in, accounts, browse, search, library, radio, PoToken, SponsorBlock |
| 5. UI (parallel with 2 to 4) | 8 | All 38 screens, themes, i18n, tablets, motion |
| 6. Extras | 3 | Lyrics, EQ, visualizer, scrobbling, Jam, widgets, Android Auto, link sharing |
| 7. Hardening | 3 | Battery/Doze, OEM killers, memory, baseline profiles, a11y audit, security review |
| 8. Beta + release | 3 | Closed beta, Play listing, F-Droid, GitHub releases |

**Release 1: about 26 to 30 weeks (6 to 7 months) with the team below.**

**Expansion (optional, after Release 1):** Track A Jellyfin/Subsonic/Local about 4 to 6 weeks; Track B Tidal/Spotify/Apple through MediaDrm about 6+ weeks each, gated by legal review.

**Team:** 1 senior Rust engineer, 2 senior Android engineers (Compose + Media3), 1 designer, 1 QA, part-time legal. **Solo or small-team path:** do Phase 0, then Phases 1, 2, 3 in order and ship Deezer-only first; add YouTube Music second (it carries the most breakage risk); keep UI to the 15 core screens (1 to 7, 15 to 18, settings basics) and defer Jam, Auto, widgets and Cast. Expect roughly 2 to 3 times the calendar time.

---

## 14. Risks

1. Google blocking WebView sign-in or the PoToken flow (biggest; tested in Phase 0).
2. Play Store may reject an unofficial Deezer/YouTube client. Plan B: F-Droid + GitHub releases with in-app update check.
3. Private APIs changing: weekly dependency watch, remote kill-switch.
4. GPL-3.0 requires publishing all source.
5. OEM background killing (Xiaomi, Huawei, Samsung): guidance screen + dontkillmyapp checks. Android background limits can also stop Jam and discovery.
6. DRM services (Track B) may block third-party clients or need certified devices.
7. The full UI rewrite is the largest cost.
8. Legal/ToS exposure of ARL login and Deezer decryption: get review before any public release.

---

## 15. First steps

1. Do the Phase 0 spikes; stop and re-plan if Google sign-in or PoToken fails.
2. Create the `mobile-core` crate and get one Rust function callable from Kotlin.
3. Get Oboe playing a decrypted Deezer track through the Rust engine.
4. Only then start the screens.
