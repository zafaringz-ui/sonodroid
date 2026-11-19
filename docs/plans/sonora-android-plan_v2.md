# Sonora for Android — Full Engineering Plan (v2)
Scope: Deezer + YouTube Music only. Goal: a finished, store-ready Android app with the best possible quality, not the simplest build.

Source: github.com/sonorahq/sonora v0.50.2. The Deezer and YouTube provider code, the token module and the dependency lists were read directly.

---

## 0. Language and stack decision (best result, not easiest)

| Layer | Choice | Why it beats the alternatives |
|---|---|---|
| UI | **Kotlin 2.1 + Jetpack Compose (Material 3)** | The native Android toolkit: 120 Hz animations, Material You colors, TalkBack, foldables, Android Auto. Flutter or React Native add a second runtime and still need native code for media sessions and DRM-free audio. |
| Core | **Rust (edition 2024), reused from Sonora** | Deezer decryption, YouTube streaming, the gapless engine, equalizer, lyrics and queue logic already exist and are tested. Rewriting them in Kotlin would add 6+ months and new bugs. |
| Bridge | **UniFFI 0.28 (Mozilla)** | Generates type-safe Kotlin from Rust, with async to coroutines and callbacks for events. Hand-written JNI is error-prone. |
| Audio out | **Oboe 1.9 (AAudio, OpenSL fallback) via `oboe-rs`** | Lowest latency, exclusive/float output, needed to keep Sonora's own resampler, gapless join and EQ. ExoPlayer would bypass the Rust engine. |
| Media session | **AndroidX Media3 1.5 `MediaSessionService` + custom `SimpleBasePlayer`** | Lock screen, notification, Bluetooth, Wear OS, Android Auto and Google Assistant, all driven by the Rust engine. |
| JS for YouTube token | **Android System WebView** | The real Chromium engine passes Google's BotGuard check. On desktop Sonora needs WebKitGTK; on Android it's built in. |

Rejected: Flutter (no reuse of the Rust audio path, weaker media integration), React Native, Kotlin Multiplatform rewrite of the core (throws away working code), ExoPlayer-only playback (loses Deezer FLAC decrypt pipeline, EQ and spectrum).

---

## 1. What gets kept, changed and removed

### Keep (Rust, compiled for Android)
- `music/deezer/*` — auth.rs, client.rs (1,711 lines), decrypt.rs, playback.rs, links.rs, wire.rs
- `music/youtube/*` — accounts, auth, client, genres, links, lyrics, playback, radio, segments, subscriptions, wire
- `music/engine.rs`, `stream.rs`, `sink.rs`, `audio.rs`, `aac.rs`, `mp4.rs`, `trim.rs`, `progress.rs`, `order.rs`
- `music/equalizer/*`, `music/spectrum/*`
- `music/lyrics/*` + `lrclib`, `musixmatch`, `netease`, `kugou` (lyrics sources)
- `music/scrobble/*` (Last.fm, ListenBrainz, Maloja)
- `music/sponsorblock.rs`, `smart.rs`, `models.rs`, `link.rs`, `cover.rs`, `artwork.rs`, `vault.rs`, `credentials.rs`, `redact.rs`, `trouble.rs`, `escape.rs`, `calendar.rs`
- `music/local/*` (optional local files tab)
- `logic` (queue, sections, station, lyrics), `router`, `i18n`, `storage`, `icons` (exported as vector drawables)
- `state` modules without UI ties: playback, queue, library, search, home, artist, genre, history, lyrics, downloads, equalizer, settings, themes, session, scrobble, sponsorblock, network, pins, profile, snapshot, song, tags, toast, usage, vault, catalog, detail, cover, mosaic, links, listening (Jam)

### Remove
- Providers: spotify, apple, tidal, jellyfin, subsonic, soloist, binimum, connect.rs, drm.rs, potoken desktop window, `widevine` crate, librespot-*, opensubsonic, oauth2 (unused after removal)
- Desktop: `sonora` crate (tray, dock, thumbbar, x11_icon, window_title, single, console), `webview` crate, `ui` crate (GPUI), `views` crate, `input/shortcuts`, `cli`, `state/remote` (MPRIS/souvlaki), `state/discord`, `state/updates`, `state/window_shape`, `state/wake`, `music/output/output_alsa.rs`, `output_hog.rs`
- Packaging: flatpak/, aur/, nix/, scripts/windows/, assets/macos, assets/linux, desktop GitHub workflows

### Change
- `music/output/output_host.rs` → new `output_oboe.rs`
- `music/vault.rs` + `credentials.rs` → Android Keystore through a Kotlin callback
- `state/potoken.rs` → Kotlin `PoTokenMinter` using WebView
- `dirs` paths → `Context.filesDir`, `cacheDir`, `getExternalFilesDir(MUSIC)`
- `state/network.rs` → feeds from `ConnectivityManager.NetworkCallback` (Wi-Fi vs mobile data quality)
- `state/devices.rs`, `outputs.rs` → `AudioManager.getDevices()` + Media3 route

---

## 2. Deezer — every detail

**Endpoints used by Sonora (keep as-is):**
- `https://www.deezer.com/ajax/gw-light.php` — private gateway (library, playlists, favourites, flow, user data)
- `https://api.deezer.com` — public catalogue (search, albums, artists, charts)
- `https://pipe.deezer.com/api` — GraphQL (home, recommendations)
- `https://media.deezer.com/v1/get_url` — signed stream URLs
- `https://auth.deezer.com/login/arl?jo=p&rto=c&i=c` — session from ARL
- `https://upload.deezer.com/v2/playlist/picture` — playlist cover upload

**Sign-in on Android:**
1. "Sign in with Deezer" opens a full-screen Android WebView at `https://www.deezer.com/login`.
2. When the user is signed in, read the `arl` cookie with `CookieManager.getCookie("https://www.deezer.com")`.
3. Validate it with the existing `deezer::auth::arl()` and store it encrypted (Keystore AES-GCM key + EncryptedFile).
4. Fallback: "Paste ARL" field (advanced), same validation.
5. Clear the WebView's cookies after capture.

**Quality ladder (from client.rs `FORMATS`):** FLAC_24 (Hi-Res) → FLAC (lossless) → MP3_320 → MP3_128. The account plan decides the top. Settings offer separate choices for Wi-Fi, mobile data and downloads.

**Playback pipeline:**
```text
get_url (signed) -> HTTPS ranged fetch (reqwest/rustls) -> Blowfish CBC stripe decrypt
(decrypt.rs: every 3rd 2048-byte chunk, key from track id + secret) -> symphonia FLAC/MP3
decode -> EQ -> loudness -> resampler -> Oboe stream
```
- The decrypt secret is scraped from the web-player bundle (`fetch_secret`). Cache it in storage and refresh on failure.
- Offline downloads keep the file **still encrypted**, as Sonora already does (no decrypted export, per the project's anti-piracy policy).

**Features to ship:** Flow, home channels, search (tracks/albums/artists/playlists/podcasts excluded at v1), favourites, followed artists, playlists (create, rename, reorder, cover upload, privacy), album/artist pages, track radio/mix, lyrics (Deezer synced lyrics + fallbacks), history, offline.

**Risks:** ARL login and the web gateway aren't a public API; Deezer can change them. Mitigation: server-free design, quick release channel, remote config flag to disable features.

---

## 3. YouTube Music — every detail

**Library:** the `ytmusic` crate (Sonora's fork), which talks to the InnerTube API with the browser's cookies.

**Sign-in on Android:**
1. WebView opens Sonora's `SIGN_IN_URL` (accounts.google.com → music.youtube.com).
2. On landing at `music.youtube.com`, capture the cookies (`SAPISID`, `__Secure-3PAPISID`, `SID`, `HSID`, `SSID`, `APISID`, `LOGIN_INFO`) with CookieManager.
3. Pass them to `youtube::auth::header()`; the client keeps them fresh as Google rotates them (`keep_fresh`) and writes them back to the encrypted store.
4. Multiple Google accounts / brand channels: `youtube/accounts.rs` picker sheet.
5. Note: Google blocks sign-in in embedded WebViews for some accounts. Fallback: open Custom Tabs to music.youtube.com and guide the user through a cookie-handoff screen; test early (Phase 1 blocker).

**Quality ladder (playback.rs `QUALITIES`):** High = AAC 256 kbps (Premium) / 128 kbps, Normal = AAC 128, Low = HE-AAC 48. Opus streams can be added through symphonia-libopus.

**Playback pipeline:**
```text
InnerTube player -> stream URL -> ranged download (resumes from the stalled byte)
-> DASH fMP4, sidx index (segments.rs) for seeking -> AAC decode -> loudness normalise
to -14 LUFS (REFERENCE_LUFS) -> trim silence (trim.rs) -> EQ -> Oboe
```
- Timeouts: 15 s per step (`PATIENCE`), 2 attempts (`ATTEMPTS`) — keep.

**Proof-of-origin token (PoToken):**
- Kotlin `PoTokenMinter`: an off-screen WebView loads `https://www.youtube.com/embed/jNQXAC9IVRw`, injects `potoken.js`, and reads the answer from cookie `SONORA_POT`.
- Rust asks through a UniFFI callback interface `TokenMinter.mint(binding)`; Kotlin answers asynchronously.
- Keep the page open for a short linger time after the last request, then destroy it (it costs ~270 MB).
- Only runs while the app or the playback service is alive.

**Features to ship:** home shelves, moods & genres (`genres.rs`), search with filters, library (songs, albums, artists, playlists, subscriptions), liked songs, history, radio / "start mix" (`radio.rs`), up-next autoplay, lyrics (`youtube/lyrics.rs`), SponsorBlock skip of non-music parts of music videos, music video audio-only mode, playlist edit.

**Not shipped:** downloading YouTube audio for offline use. That's ripping, and Sonora's README forbids it; offline for YouTube is stream caching only (bounded LRU cache, cleared on sign-out).

**Risks:** InnerTube and BotGuard change often. Google may block the app. Mitigation: pin `ytmusic` and update weekly; remote kill-switch; F-Droid / GitHub releases in case the Play Store refuses the app.

---

## 4. Shared music features (both providers)
- Unified queue with mixed Deezer and YouTube tracks; shuffle (`order.rs`), repeat, gapless, crossfade (0–12 s), preload of next track.
- Lyrics: provider lyrics first, then LRCLIB, Musixmatch, NetEase, KuGou; TTML word-by-word sync; Japanese romanisation (kakasi).
- Equalizer: parametric EQ (`equalizer/design.rs`, `stage.rs`), presets, AutoEQ/APO import (`apo.rs`), per-output-device profiles (headphones vs speaker vs car).
- Spectrum visualizer from `spectrum/analyzer.rs` (rustfft) streamed to Compose at 60 fps.
- Scrobbling: Last.fm, ListenBrainz, Maloja, queued offline.
- Jam (group listening, `state/listening`): host on local Wi-Fi with Network Service Discovery; foreground-only.
- Smart playlists (`smart.rs`), pins, history, play stats (`usage.rs`).
- Cross-provider links: paste a Deezer or YouTube link to open it (`link.rs`, `links.rs`) + Android share-target intent.

---

## 5. Every screen (Compose)

| # | Screen | Content | Source in Sonora |
|---|---|---|---|
| 1 | Splash | Logo, core load | — |
| 2 | Welcome | Choose Deezer / YouTube Music / both | screens/login.rs |
| 3 | Deezer sign-in | WebView + Paste ARL | popups/cookie.rs |
| 4 | YouTube sign-in | WebView, account/brand picker | popups/accounts.rs |
| 5 | Home | Per-provider tabs, shelves, Flow, quick picks, pins | screens/home |
| 6 | Search | Field, recent, filters (song/album/artist/playlist/video), provider switch | screens/search |
| 7 | Library | Tabs: Playlists, Albums, Artists, Songs, Downloads; sort/filter; grid/list | screens/library |
| 8 | Album | Hero art, tracks, more by artist | screens/detail |
| 9 | Playlist | Header, edit, reorder, cover change, privacy | detail + playlist_editor |
| 10 | Artist | Hero, top songs, discography, related, follow | screens/artist |
| 11 | Song info | Credits, tags, codec/quality badge | screens/song |
| 12 | Genre / Mood | Grid and playlists | screens/genre |
| 13 | History | By day | screens/history |
| 14 | Profile | Account, stats | screens/user |
| 15 | Mini-player | Art, title, play/pause, progress line; swipe to skip | chrome/player_bar |
| 16 | Now Playing | Big art or canvas, ambient color bg, scrubber, controls, like, output button, lyrics peek | shells/fullscreen |
| 17 | Lyrics full | Synced, tap-to-seek, translation/romanised | lyrics |
| 18 | Queue | Drag reorder, swipe remove, "playing from" | sidebar_right |
| 19 | Equalizer | Touch curve, bands, presets, import | screens/equalizer |
| 20 | Output picker | Phone, Bluetooth, wired, Cast | chrome/output_picker |
| 21 | Jam | Host/join, members, QR join code | chrome/jam, members |
| 22 | Downloads | Progress, storage use (Deezer only) | chrome/downloads |
| 23 | Settings hub | Sections below | screens/settings |
| 24–35 | Settings pages | Accounts, Playback (crossfade, gapless, normalise), Audio quality (Wi-Fi/mobile/download per provider), Sound, Lyrics, Appearance, Themes (18 + Material You), Now Playing, Scrobbling, Storage/cache, Language, About/licenses | settings/* |
| 36 | Dialogs/sheets | Track menu, add-to-playlist, smart playlist builder, tag editor, cover viewer, confirm, error ("trouble") | shared/* |
| 37 | Widgets | 2×2 and 4×2 home screen players (Glance) | — |
| 38 | Android Auto | Browse tree: Home, Library, Recent, Search | — |

Layouts: phone portrait, landscape, tablet/foldable (two-pane with NavigationRail) via WindowSizeClass.

---

## 6. App architecture and modules

```text
android/
  app/                      Activity, navigation graph, DI setup
  core-bridge/              UniFFI-generated Kotlin + jniLibs (.so)
  feature-home/ feature-search/ feature-library/ feature-player/
  feature-lyrics/ feature-equalizer/ feature-settings/ feature-auth/
  feature-jam/ feature-downloads/
  service-playback/         MediaSessionService, SimpleBasePlayer adapter, notification
  platform-audio/           Oboe glue (C++/Rust), audio focus, noisy receiver
  platform-webview/         Sign-in WebViews, PoTokenMinter
  platform-secure/          Keystore, EncryptedFile
  design-system/            Theme, typography, components, motion
  widget/ auto/
rust/
  crates/mobile-core/       UniFFI interface (.udl or proc-macros), event bus
  crates/music, logic, state, router, i18n, storage (trimmed)
```

- Pattern: MVI — Compose screen → ViewModel (StateFlow) → Repository → Rust core.
- Events from Rust (position, track change, errors, download progress) via a UniFFI callback → `SharedFlow`.
- Threads: Rust owns a Tokio runtime (multi-thread, 2–4 workers); audio callback thread has real-time priority and no allocation.
- Persistence: Rust keeps its own SQLite (rusqlite, bundled) for library cache, history and vault; Kotlin uses DataStore only for UI prefs.

---

## 7. Full materials list (dependencies and tools)

**Android / Kotlin**
- Kotlin 2.1, AGP 8.7+, Gradle 8.10 (Kotlin DSL, version catalog), JDK 17
- compileSdk 35, targetSdk 35, minSdk 26 (Android 8.0)
- Compose BOM 2024.12+, Material 3, material-icons-extended, Compose Animation, adaptive (WindowSizeClass, ListDetailPaneScaffold)
- Navigation Compose (type-safe routes), Lifecycle ViewModel / runtime-compose
- Hilt 2.52 (DI), KSP
- Kotlin Coroutines 1.9, kotlinx.serialization
- Media3 1.5: session, common (SimpleBasePlayer), cast (optional), ui-compose
- AndroidX Car App 1.7 (Android Auto)
- Glance 1.1 (widgets)
- WorkManager 2.10 (downloads, scrobble retries, cache cleanup)
- DataStore Preferences
- AndroidX Security Crypto / Tink (Keystore-backed encryption)
- WebKit (`androidx.webkit` for cookie and JS bridge control)
- Coil 3 (artwork, with palette extraction), androidx.palette
- Baseline Profiles + Macrobenchmark, Startup
- Splashscreen API
- Firebase Crashlytics or Sentry (opt-in only, per PRIVACY.md); or ACRA for the F-Droid build
- Testing: JUnit5, MockK, Turbine, Compose UI test, Robolectric, Paparazzi (screenshots), Espresso for WebView
- Oboe 1.9 (prefab AAR)

**Rust**
- Toolchain from `rust-toolchain.toml` + targets `aarch64-linux-android`, `armv7-linux-androideabi`, `x86_64-linux-android`
- `cargo-ndk` 3.x, Android NDK r27c
- `uniffi` 0.28, `oboe` crate or cpal's AAudio backend, `jni`, `ndk-context`, `android_logger`
- Keep: tokio, reqwest (rustls-tls, no OpenSSL), symphonia, rodio parts, blowfish, cbc, md-5, sha1, ring, rusqlite (bundled), rustfft, rtrb, lofty, kakasi, serde, ytmusic, fluent-bundle, image
- Release profile: `lto = "fat"`, `codegen-units = 1`, `panic = "abort"`, `strip = true`, `opt-level = 3`

**Design**
- Figma (Material 3 kit), the 18 Sonora theme JSONs converted to M3 color schemes, Inter font (from assets/fonts), Lucide/Remix icons converted to vector drawables, Lottie for micro-animations (optional)

**Infrastructure**
- GitHub Actions: Rust build matrix → AAR → AAB/APK; ktlint, detekt, clippy, cargo-deny (licenses), cargo test, Gradle tests
- Firebase Test Lab (or a self-hosted emulator matrix)
- fastlane for Play Console upload; Play App Signing; F-Droid metadata (reproducible build)
- Remote config: a static JSON on GitHub Pages (feature kill-switches) — no own backend needed
- Weblate for translations (19 languages already in .ftl)

**Devices for testing**
- Android 8 low-end (2 GB RAM), Android 12 mid-range, Pixel 8 (Android 15), Samsung S-series (One UI), Xiaomi (aggressive battery killer), a foldable, a tablet, Android Auto Desktop Head Unit, Bluetooth headphones with LDAC/AAC, a USB-C DAC for Hi-Res FLAC

---

## 8. Android manifest and permissions
- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- `POST_NOTIFICATIONS` (Android 13+)
- `WAKE_LOCK`
- `CHANGE_WIFI_MULTICAST_STATE`, `NEARBY_WIFI_DEVICES` (Jam discovery)
- `READ_MEDIA_AUDIO` (only if the local-files tab is kept)
- `BLUETOOTH_CONNECT` (output names)
- Services: `PlaybackService` (exported for MediaBrowser, `foregroundServiceType="mediaPlayback"`)
- Intent filters: deezer.com / deezer.page.link / music.youtube.com / youtu.be links; `ACTION_SEND` text share target
- `android:usesCleartextTraffic="false"`, network security config pinned to system CAs

---

## 9. Quality targets
- Cold start < 1.2 s on mid-range; first audio < 1.5 s on Wi-Fi
- Gapless with 0 ms gap; seek < 300 ms
- Battery: < 4% per hour of screen-off playback
- APK size < 30 MB per ABI (split by ABI in the AAB)
- Crash-free sessions > 99.5%; no ANRs
- Accessibility: TalkBack labels on every control, 48 dp touch targets, font scaling to 200%

---

## 10. Security and privacy
- ARL and Google cookies encrypted at rest (AES-256-GCM, key in Android Keystore, StrongBox when available)
- Never logged (`redact.rs` kept and applied to Android logs)
- WebView: JS bridge only on the sign-in and token pages, file access disabled, cookies cleared after capture
- No analytics by default; opt-in crash reports
- Privacy policy adapted from PRIVACY.md; Play Data Safety form

---

## 11. Phased roadmap

| Phase | Weeks | Output |
|---|---|---|
| 0. Risk spikes | 2 | Proof: Deezer ARL from WebView, Google sign-in in WebView, PoToken minted in WebView, Rust core cross-compiled, Oboe plays a FLAC |
| 1. Core trim + bridge | 3 | `mobile-core`, providers removed, UniFFI API, Kotlin smoke tests |
| 2. Playback service | 3 | Oboe sink, MediaSession, notification, focus, Bluetooth, gapless, normalise |
| 3. Deezer | 3 | Sign-in, browse, search, library, playlists, streaming all qualities, downloads |
| 4. YouTube Music | 4 | Sign-in, accounts, browse, search, library, radio, PoToken, SponsorBlock |
| 5. UI (parallel 2–4) | 8 | All 38 screens, themes, tablets, motion |
| 6. Extras | 3 | Lyrics, EQ, visualizer, scrobbling, Jam, widgets, Android Auto, link sharing |
| 7. Hardening | 3 | Battery, OEM battery killers, memory, baseline profiles, a11y audit, security review |
| 8. Beta + release | 3 | Closed beta, Play listing, F-Droid, GitHub releases |

Total ≈ 26–30 weeks. Team: 1 senior Rust engineer, 2 senior Android (Compose + Media3) engineers, 1 product designer, 1 QA, part-time legal review.

---

## 12. Risks
1. Google blocking WebView sign-in or the PoToken flow — biggest risk; tested in Phase 0.
2. Play Store may reject an unofficial Deezer/YouTube client (policy on API terms). Plan B: F-Droid + GitHub releases with in-app update check.
3. Private APIs changing — weekly dependency watch, remote kill-switch.
4. GPL-3.0: all source must be published, including the Android code.
5. OEM background killing (Xiaomi, Huawei, Samsung) — guidance screen + dontkillmyapp checks.
