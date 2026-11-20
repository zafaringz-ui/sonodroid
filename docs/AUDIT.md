# Upstream audit: Sonora `v0.50.2` for Release 1

Read-only analysis of [sonorahq/sonora](https://github.com/sonorahq/sonora) at tag `v0.50.2`,
commit `c0b501cf4c138e55670a390240fadb7577c3913a`. No application code, Android module or
workflow was changed by this PR.

**Method.** The source was cloned at the pinned tag (`git clone --depth 1 --branch v0.50.2`)
into a scratch directory outside the repository, so the 21 MB working tree and its 89 MB history
never enter this repo or its CI. Every path and line range below was read in that clone. Anything
I could not open is marked **not verified** and collected in [§9](#9-not-verified).

Line numbers are 1-based and refer to files at that commit. Where a number mattered I re-checked
it with `grep -n`; where it came from a `sed` window I say so.

---

## 0. How upstream is pinned

**Superseded by §11: upstream is now vendored in-tree.** This section described the submodule
pin that was in place when this audit was written, and it is kept because the reasoning still
explains *why* the submodule came first. It no longer describes the tree: `vendor/sonora` and
`.gitmodules` were removed after `rust/vendor/sonora/` took over.

*What was true at the time of writing.* `.gitmodules` declared `vendor/sonora` →
`https://github.com/sonorahq/sonora.git`, and the tree pointed it at
`c0b501cf4c138e55670a390240fadb7577c3913a` (`git ls-tree HEAD vendor/` → `160000 commit c0b501c…`),
which is exactly `v0.50.2`. All three CI jobs used `actions/checkout@v4` **without**
`submodules: true`, so they checked out an empty `vendor/sonora` and no CI job read it. The pin
could not break CI because nothing consumed it.

*Why that was the right first step.* The checkout stayed a pointer, so the repository was not
inflated by 21 MB of GPL source and review diffs contained no upstream files. It was also a real
limitation: `vendor/sonora` was empty in a fresh clone until `git submodule update --init` ran, so
no command could read it without that step.

*Why it was replaced.* A submodule cannot be a workspace member. Building `music` requires the
source in the tree, at a path Cargo can resolve, under version control with the rest of the
workspace — so PR #14 copied the three crates to `rust/vendor/sonora/crates/` and pinned the
compiler to upstream's 1.98.1.

*Why the submodule was then deleted rather than kept.* Keeping both pins means two things that can
drift, and the submodule's only job — recording provenance — is already done better by
`rust/vendor/UPSTREAM.md`, which states the repo, tag, commit and copied paths next to the files
that are actually compiled. It was also still checked out empty by every CI job, so it never
verified anything.

*Licensing consequence of the change.* This one is real and matters: a submodule is a reference,
whereas vendoring is **distribution**. Upstream code is now in this repository, and once a built
`.so` containing it ships in an APK, GPL-3.0 §6 corresponding-source obligations attach to our own
tree too — see [§1a](#1a-license-obligations) item 1, which anticipated exactly this.

---

## 1a. License obligations

### What upstream is

- Root manifest declares the license: `Cargo.toml` line 9, `license = "GPL-3.0-or-later"`.
- Full GPL-3.0 text: `COPYING` (674 lines), version 3, 29 June 2007 (`COPYING` lines 1–2).
- The whole workspace inherits it: `[workspace.package]` carries
  `license = "GPL-3.0-or-later"` at `Cargo.toml` line 9, and each crate opts in with
  `license.workspace = true` (e.g. `crates/music/Cargo.toml` line 5).

So: **GPL-3.0-or-later**, not "GPL-3.0 only". The "or later" matters because it makes the code
compatible with a future GPL-4 if one appears, and it is the reason the combined work below can be
GPL-3.0.

### What that requires of us when we ship an APK

This is an engineering summary of the obligations, not legal advice.

1. **Corresponding source for the whole combined work.** An Android APK that statically links
   upstream `music` code is a single combined work. On distribution we must offer the complete
   corresponding source: our own Rust crates *and* upstream's, at the exact revision we built,
   plus the build scripts needed to produce the same binary. GPL-3.0 §6 (`COPYING`, the "Conveying
   Non-Source Forms" section) is the operative clause.
2. **A pin is not enough on its own, which is why vendoring changed this obligation.** A
   submodule pointing at a public tag satisfied "where to get it" only while that tag exists. The
   code is now in-tree (§11), so the corresponding source is this repository at the released
   commit — which is a release-process step (tag the build), not a separate fork to publish. As soon
   as we patch the vendored tree — and [§8](#1h-proposed-phase-b-task-list) says we will — those
   patches are part of what we must ship, which is exactly what an in-tree copy makes straightforward.
3. **Notices and license text in the app.** GPL-3.0 §5 requires prominent notices of modification
   and the license. We must ship `COPYING` and our third-party notices in an in-app screen; the
   existing `THIRD-PARTY.md` (137 KB, generated by `scripts/generate-notices.py`) is the obvious
   content source.
4. **No additional restrictions.** GPL-3.0 §7 forbids adding terms that restrict the user's GPL
   rights. A proprietary EULA wrapped around the APK would conflict; the Play Store distribution
   agreement itself is generally tolerated, but nothing may forbid redistribution of the source.
5. **Anti-Tivoization.** GPL-3.0 §6 "Installation Information" applies to *User Products*. A phone
   the user owns is their User Product, so if we ship signed builds we must not lock the installed
   binary in a way that blocks a modified version — practically: we cannot rely on Play Integrity
   or a locked bootloader check to refuse to run at all. Nothing in the current code does this;
   flagging it for the release checklist.
6. **Network use is not "distribution"** (that is AGPL §13, which upstream is not), so running the
   app against Deezer does not by itself trigger source publication. Shipping the APK does.

### Third-party licenses we would pull in for Release 1

Upstream's own generated inventory is `THIRD-PARTY.md`; its `## Summary` block
(lines 30–45) counts **809 license entries**. The relevant part for us:

| License | Count | Relevance |
| --- | --- | --- |
| MIT | 687 | Compatible |
| Apache-2.0 | 37 | Compatible (GPL-3.0-compatible, no extra conditions that bite here) |
| Unicode License v3 | 21 | Compatible |
| MPL-2.0 | 19 | Compatible, **file-level** copyleft: if we *modify* an MPL file we must publish that file. We do not plan to. |
| GPL-3.0-or-later | 15 | The upstream crates themselves + `ytmusic` |
| BSD-3-Clause / 2-Clause, ISC, 0BSD, Zlib, CC0, CDLA-Permissive-2.0, Unlicense | 26 | Compatible |
| **GPL-3.0 only** | **1** | `kakasi 0.1.0` — see below |

The counts are the 14 bullets under `THIRD-PARTY.md` line 30 `## Summary`, lines 32–45; they
sum to **809 entries, not 809 distinct crates** — dual-licensed crates are listed under each
license they offer, and `grep -o '^- \[[a-z0-9_-]*'` shows `ashpd`, `aws-lc-sys`, `base64`,
`cbc`, `cipher` and others appearing twice. The unique crate count is therefore below 809 and this
file does not state it. **Not verified** as a distinct total.

Section locations I actually checked: MPL list opens at `THIRD-PARTY.md` line 1077 with
`mpris-server 0.11.0`; the GPL-3.0-only section opens at line 2410 and holds exactly one entry,
`kakasi 0.1.0`, at line 2414.

**Nothing in that list is incompatible with GPL-3.0.** The two items worth naming:

- **`kakasi` is GPL-3.0-only, not "or later"** (`THIRD-PARTY.md` line 2414, in the section that
  opens at line 2410). Combining it with GPL-3.0-or-later code is fine — the combination is
  GPL-3.0 — but it removes our option to ever move the project to GPL-4 alone. It is pulled by
  `crates/music/Cargo.toml` line 35 (`kakasi.workspace = true`) and used from
  `crates/music/src/lyrics/japanese.rs`, not from `romanize.rs` as I first assumed. Keeping it
  means accepting GPL-3.0 as the project's effective ceiling.
- **19 MPL-2.0 crates, 15 of them Symphonia 0.5.5** (the 19 entries beginning at
  `THIRD-PARTY.md` line 1077).
  Symphonia is our decoder, so it ships. MPL-2.0 requires that *modified* MPL files be published;
  using them unmodified, as we do, only requires the notice. Upstream relies on the
  Apache-2.0 option for `self_cell` and MPL-2.0 for `priority-queue` (`THIRD-PARTY.md`, the
  paragraph beginning "Where a dependency offers a choice").

**Non-GPL-compatible dependencies: none found.** The specific things to worry about are absent:

- **No OpenSSL.** `grep 'name = "openssl"' Cargo.lock` finds nothing. `openssl-sys` and
  `native-tls` are likewise absent.
- **TLS is rustls-only.** reqwest is declared at `Cargo.toml` lines 158–164 with
  `default-features = false` and features `blocking, form, json, query, rustls-no-provider` —
  explicitly *not* `native-tls`. `rustls 0.23.46` and `rustls-platform-verifier 0.7.1` are in the
  lockfile, and `rustls-platform-verifier-android 0.2.0` is too, which is what makes certificate
  verification work on Android without shipping a CA bundle.
- **`aws-lc-rs 1.19.0`** is in the graph and is ISC/Apache-2.0-licensed but **builds C and
  assembly** — a build concern, not a licensing one. The path is `opensubsonic 0.7.0 → reqwest 0.13
  "rustls"`, confirmed by the real `Cargo.lock` after vendoring; the earlier guess of
  `quinn-proto`/`rustls-webpki`/`tokio-tungstenite` was wrong because those edges are optional and
  nothing enables them. See §11.
- **Widevine** (`crates/widevine`, 2,889 LoC) is an integration shim around Google's CDM. It is
  dropped for Release 1 anyway: `crates/music/Cargo.toml` lines 7–10 define the `widevine` feature
  as `["widevine/cdm"]` and it is off by default.

**Font and icon assets** carry their own licenses and *are* shipped in the APK if we reuse them:
Inter (OFL-1.1), Lucide (ISC), Iconoir (MIT), Remix Icon (Apache-2.0), Solar (CC BY 4.0 — needs
attribution), Simple Icons (CC0). Source: `THIRD-PARTY.md` lines 5–13. **CC BY 4.0 requires an
attribution we must actually display**; that is an obligation the app UI has to satisfy, not just a
file in the repo. PLAN.md §3 wants these assets reused, so this is live.

### Obligations checklist for the release

- [ ] Publish corresponding source, tagged to the build. Vendoring (PR #14) means the tagged
      source **is** this repository now, so this is a release-process step, not a fork to push.
- [ ] Ship `COPYING` in the APK and show it in an about screen.
- [ ] Ship `THIRD-PARTY.md` content, including the CC BY 4.0 attribution for Solar icons.
- [ ] Keep `kakasi`'s GPL-3.0-only notice; do not claim "GPL-3.0-or-later" for the app if it stays.
- [ ] No EULA or integrity check that restricts running a modified build.

---

## 1b. Workspace map

`crates/*` are the workspace members (`Cargo.toml` line 3: `members = ["crates/*"]`,
`exclude = ["vendor"]`), 14 crates, Rust **edition 2024** (`Cargo.toml` line 8). LoC is
`find crates/<n>/src -name '*.rs' | xargs wc -l`.

| Crate | LoC | What it actually is | Release 1 |
| --- | --- | --- | --- |
| `music` | 68,321 | The whole non-UI core: providers, engine, stream, decoder glue, vault, EQ, lyrics, scrobbling, PoToken handoff. `crates/music/src/lib.rs` opens with `mod aac; pub mod apple;` | **Keep, heavily cut and split** |
| `state` | 37,832 | GPUI-`Entity` application state: catalogue, playback, queue, library, settings, downloads, potoken minter, remote controls | **Change: port the logic, drop the GPUI shell** |
| `views` | 51,707 | Every screen, in GPUI | **Remove** (Compose replaces it) |
| `ui` | 26,540 | GPUI widget library (70 modules: `button.rs`, `card.rs`, …) | **Remove** |
| `widevine` | 2,889 | Widevine CDM host over a C++ shim (`cdm.rs`, `shim.rs`, `cenc.rs`) | **Remove** |
| `sonora` | 4,372 | Desktop binary: tray, dock, `single.rs`, `x11_icon.rs`, `window_title.rs`, `thumbbar.rs` | **Remove** |
| `cli` | 2,000 | Headless command surface, `clap`-based (`crates/cli/Cargo.toml` pulls `clap`) | **Remove** |
| `webview` | 1,831 | Native WebView per OS; on Android `supported()` returns `false` (`crates/webview/src/unsupported.rs:7`) | **Remove** (Kotlin WebView replaces it) |
| `input` | 796 | GPUI keybindings (`KeyBinding`, 107 references in `lib.rs`) | **Remove** |
| `router` | 607 | GPUI navigation (`use gpui::{…}` at `lib.rs:6`) | **Remove** |
| `i18n` | 449 | Fluent `.ftl` loader; imports `gpui::SharedString` at `src/lib.rs:10` | **Change: drop the one GPUI import** |
| `storage` | 294 | SQLite (`rusqlite`) + `dirs` for app state, plays, cache | **Keep, inject paths** |
| `icons` | 143 | Icon packs as GPUI `AssetSource` (`src/lib.rs:5`) | **Change: export vector assets, not a crate** |
| `embed` | 58 | **Build** helper for `build.rs`, prints `cargo:rerun-if-changed` (`src/lib.rs:9`) | **Keep as-is** |

### Deviations from PLAN.md §3

PLAN.md §3 lists things that **do not exist** at `v0.50.2`. Anyone budgeting from that section
will be wrong:

1. **`logic` crate — does not exist.** PLAN.md §3 keeps "Crates: `logic` (queue, sections, station,
   lyrics)". There is no `crates/logic` in the workspace (`ls crates/` → cli, embed, i18n, icons,
   input, music, router, sonora, state, storage, ui, views, webview, widevine). Queue lives in
   `crates/state/src/queue.rs` + `crates/state/src/queue/view.rs`; the closest thing to "station" is
   a private `struct Station` at `crates/state/src/playback.rs:483`. Both are GPUI `Entity` code
   (`crates/state/src/playback.rs` imports `gpui`), so they are **not** a UI-free library we can
   keep — they are part of the porting work.
2. **`state/*` is not mostly UI-free — 40 of its 44 modules import `gpui`.** PLAN.md §3 keeps a
   long list of `state` modules described as "without UI ties". Counting `grep -c gpui` over
   `crates/state/src/*.rs`, only **four** files mention it zero times: `catalog.rs`, `logging.rs`,
   `sheets.rs`, `snapshot.rs`. Every module PLAN.md names — `playback`, `queue`, `library`,
   `search`, `home`, `artist`, `equalizer`, `downloads`, `settings`, `session`, `scrobble`,
   `network`, `pins`, `profile`, `song`, `tags`, `toast`, `usage`, `vault`, `catalog`, `detail`,
   `cover`, `mosaic`, `links` — imports GPUI. Concrete examples: `crates/state/src/network.rs:3`
   (`use gpui::{App, Context, Entity, EventEmitter, Task};`) and
   `crates/state/src/outputs.rs:4` (same shape). The crate also pulls `ui`, `views`-adjacent types,
   `cli`, `webview`, `discord-rich-presence`, `moka`, `notify` and `rusqlite`
   (`crates/state/Cargo.toml` `[dependencies]`). So PLAN.md's "keep" list is really "port the logic
   out of `state` into a new UI-free crate": that is the single largest Phase B item, and PLAN.md
   does not cost it.
3. **`state/remote` exists and uses `souvlaki`, and it compiles on Android.** PLAN.md §3 says
   `state/remote` "is replaced by MediaSession", implying it is removable. It is, but it is not
   inert: `crates/state/Cargo.toml` pulls `souvlaki` under
   `cfg(not(any(target_os = "linux", target_os = "freebsd")))` — which **includes Android** — and
   `crates/state/src/remote/souvlaki.rs` imports it. Since we drop `state` wholesale this does not
   block us, but it would block anyone who tries to keep `state` as-is.
4. **`music` is one crate, and PLAN.md treats it as many.** PLAN.md §3 keeps `music/deezer/*`,
   `music/youtube/*`, the engine, EQ, lyrics backends and more from a single 68 kLoC crate whose
   `Cargo.toml` has no per-provider features. Trimming it is not a matter of disabling features; it
   is deleting modules and the `pub mod` lines in `crates/music/src/lib.rs`. See §1c and §8.
5. **`crates/sonora` turns Widevine on.** The desktop binary depends on
   `music = { workspace = true, features = ["widevine"] }`, so the CDM shim builds for the desktop
   app even though `music` alone leaves it off (`crates/music/Cargo.toml:7-10`). Since we drop both
   `sonora` and `widevine`, we never build it — worth knowing, because it is the one place a C++
   toolchain would otherwise be required.

---

## 1c. Android blockers

Nothing in this section was **compiled** for Android. There is no NDK or Android Rust target in
this sandbox, so each item is classified from source and manifest evidence:
**blocks** (will not build), **works** (cfg-gated away or supported), **verify** (plausible but
unproven — see §9).

### Will not compile as-is

1. **`souvlaki` is pulled for every non-Linux target, Android included.**
   `crates/state/Cargo.toml` has `souvlaki.workspace = true` under
   `cfg(not(any(target_os = "linux", target_os = "freebsd")))`. Android satisfies that predicate.
   `crates/state/src/remote/souvlaki.rs:7` imports it. In the lockfile `souvlaki 0.9.3` depends on
   `core-graphics`, `objc`, `dispatch`, `dbus`, `dbus-crossroads` and `windows 0.62.2` (Cargo.lock
   lines 7594–7608), i.e. every desktop platform's native bindings and none for Android.
   *Status: blocks the `state` crate.* Moot for us because `state` is dropped, but it is the reason
   WP-1 cannot be "just trim a few modules" — see §8.

2. **`alsa` and `pulseaudio` — gated correctly, so not blockers, but they constrain `music`.**
   `crates/music/Cargo.toml` lists both only under `[target.'cfg(target_os = "linux")'.dependencies]`
   (the `alsa.workspace = true` line sits directly under that header, next to `pulseaudio`).
   Android is `target_os = "android"`, so neither is built. However `crates/music/src/output/output_host.rs`
   imports `pulseaudio::…` and `cpal::HostId::PulseAudio` **unconditionally at file scope**
   (lines 13–19), and it is only excluded because `crates/music/src/output/mod.rs:27-28` gates the
   module with `#[cfg(target_os = "linux")]`. Any edit that drops that `cfg` breaks Android. This is
   fragile-but-correct today.

3. **`cpal` builds on Android, and brings more than we want.** `cpal` is an *unconditional*
   dependency (`crates/music/Cargo.toml`, the `cpal.workspace = true` line before the linux section),
   and `cpal 0.18.2` in the lockfile lists an Android backend (`jni`, `ndk`, `ndk-context` — Cargo.lock
   lines 1550–1555) *and* the desktop ones (`alsa`, `pulseaudio`, `coreaudio-rs`, `windows`), which
   Cargo resolves per-target. So it should compile; the cost is a second audio stack in the binary
   alongside Oboe. **verify**: no Android build has been attempted.

4. **`webview` compiles to a no-op on Android.** `crates/webview/src/lib.rs:40-45` selects the real
   backends only for macos/windows/linux and routes everything else to `platform::unsupported`; the
   stub's `supported()` returns `false` (`crates/webview/src/unsupported.rs:7-9`). So the crate is
   harmless, but it also means **PoToken minting cannot work through it on Android** — which is
   exactly what `crates/state/src/potoken.rs:118-121` logs ("no browser engine here, the stream goes
   out on a cold start token"). Kotlin must supply the minter. See §1e.

5. **`widevine` needs a C++ shim** (`crates/widevine/src/shim.rs`, `cdm.rs`) and is behind the
   non-default `widevine/cdm` feature (`crates/music/Cargo.toml:7-10`). It is off for `music`, but
   **on** for the desktop binary: `crates/sonora/Cargo.toml` declares
   `music = { workspace = true, features = ["widevine"] }`. Dropping `sonora` and `widevine` removes
   the only C++ requirement in the workspace.

### Desktop-only code inside `music` (kept crate, must be edited)

These are all `#[cfg]`-gated, so they compile away — but they are in files we keep, so every edit to
those files must preserve the gates:

- `crates/music/src/output/mod.rs:704-760` — `#[cfg(windows)]` COM apartment handling
  (`windows::Win32::System::Com::CoInitializeEx`, `CoUninitialize`).
- `crates/music/src/output/output_host.rs` — PulseAudio host/device listing, whole file Linux-only.
- `crates/music/src/output/output_alsa.rs`, `output_hog.rs` — the latter 363 LoC of device
  exclusive-access logic; both Linux-only via `mod` gates at `output/mod.rs:24-28`.
- `crates/music/src/output/output_rate.rs:29-32` — `#[cfg(target_os = "linux")]` rate table and
  `#[cfg(any(target_os = "linux", target_os = "macos"))]` import.

`x11rb`, `wayland`, `gpui` appear in the workspace manifest (`Cargo.toml` lines 54–59 for `gpui`
and `gpui_platform`) but **not** in `crates/music/Cargo.toml`, so they are not transitive into the
core. `clap` is only in `crates/cli/Cargo.toml`. `windows` is only listed under
`cfg(windows)` in `music` and in `souvlaki`/`state`.

### Unnecessary weight that will compile but should not ship

`music/Cargo.toml` declares all of these **unconditionally**, and they are only reachable from
providers we drop:

| Dependency | Where it is actually used | Verdict |
| --- | --- | --- |
| `librespot-core`, `librespot-playback`, `librespot-protocol` | Spotify only. Inside kept files they appear only in **comments** (`engine.rs:11`, `sink.rs:562`, `lib.rs:615`) | **delete** — the only cost is editing three doc comments |
| `open` | `jellyfin/mod.rs:272`, `scrobble/audioscrobbler.rs:87`, `spotify/auth.rs:145`, `tidal/auth.rs:194` | delete with those providers |
| `gethostname` | `jellyfin/auth.rs:31`, `spotify/connect.rs:1701` | delete with those providers |
| `oauth2` | Spotify/Tidal auth | delete |
| `opensubsonic`, `protobuf` | Subsonic / Soloist | delete |
| `image`, `lofty` | artwork, local tag reading | **keep** if artwork/local playback ship |
| `rusqlite` (`bundled`) | cache/DB | **keep**, but note `bundled` compiles SQLite from C — cross-compiles via NDK, adds build time |
| `tokio-tungstenite` | Jam / realtime | keep if Jam ships, else delete |
| `kakasi` | `lyrics/japanese.rs` | keep (see §1a licensing caveat) |

### TLS, crypto and native code that must be checked per-ABI

- **`ring 0.17.14`** (Cargo.lock line 6727) builds C and per-arch assembly. It has upstream Android
  support for all three of our ABIs; the NDK toolchain already builds it in `Android native` today —
  but for the *current* crate set, which does not include `ring`'s use here. **verify** on a real
  cross-compile.
- **`aws-lc-rs 1.19.0` / `aws-lc-sys 0.47.0`** enter through **`opensubsonic 0.7.0`**, whose
  `reqwest = { features = ["json", "rustls", "stream"] }` selects reqwest 0.13's
  provider-carrying `rustls` feature. The originally recorded path — `quinn-proto`,
  `rustls-webpki`, `tokio-tungstenite` — was wrong: all three declare `aws-lc-rs` as optional and
  enable `ring` instead, and `quinn` is only reachable from reqwest's `http3` feature, which nothing
  turns on. Corrected from the real `Cargo.lock` after vendoring (§11).
  `aws-lc-sys` is a **CMake/C** build, the most likely Android build failure in the whole graph.
  Mitigation already present in the manifest: reqwest is `rustls-no-provider`
  (`rust/Cargo.toml`), so no crypto provider is chosen for us, and `ring` is a direct dependency —
  installing `ring` as the process-wide provider avoids initialising `aws-lc`, but **it does not
  stop `aws-lc-sys` from being compiled**, because feature unification builds it regardless.
  **verify** that `aws-lc-sys` is not pulled in unavoidably.
- **No OpenSSL anywhere** (`grep 'name = "openssl"' Cargo.lock` → no match), and no `native-tls`,
  so the "move to rustls only" part of the task is already done upstream. What remains is stopping
  `aws-lc` from being the provider actually used.

### One-line summary

The blockers are concentrated in `state` (souvlaki/gpui), not in the providers we want. `music`
should cross-compile once the unused unconditional dependencies are deleted and the `cfg` gates
above are respected. **No claim here is a substitute for an actual `cargo ndk` build** — that is
WP-1 in §8.

---

## 1d. Deezer

Six files, 3,015 LoC: `auth.rs` (69), `client.rs` (1,711), `decrypt.rs` (236), `links.rs` (36),
`mod.rs` (214), `playback.rs` (195), `wire.rs` (554).

### Public API surface

What `deezer/mod.rs` exports (`mod.rs:23-28`) is deliberately tiny:

- `pub use auth::arl;` → `fn arl(input: &str) -> Result<String>` — cookie/ARL parsing + validation.
- `pub use client::DeezerClient;` — the actual client.
- `pub use decrypt::Striped;` — the streaming decryptor, used as `Stream = stream::Stream<Striped>`.
- `pub struct DeezerProvider` (`mod.rs:39`) — the `MusicProvider` implementation that wires it into
  the app: sign-in, restore-from-disk, and the `Capabilities`/`Shape` metadata (`mod.rs:96-108`).

`DeezerClient`'s own public methods are only these six (`client.rs`):
`connect(arl, profile)` (241), `offline(arl)` (262), `arl()` (350), `profiles()` (357),
`switch(id)` (395), `served(quality)` (422), `open_stream(track_id, quality)` (941),
`track_key(effective_id)` (992). **Everything else is the `MusicApi` trait**: 78 trait methods
(`crates/music/src/lib.rs`, `pub trait MusicApi` at line 124) of which `DeezerClient` overrides 38
(`client.rs`, `impl MusicApi for DeezerClient` at line 1028). The rest fall back to trait defaults
that return `NotImplemented`-style errors. For Release 1 that gap is the integration surface: our
mobile-core currently exposes ~25 coarse methods, so **most of the 78 are irrelevant to us** and
only the ones §1g maps matter.

### Endpoints used

Declared as constants in `client.rs`:

| Constant | Line | URL | Used for |
| --- | --- | --- | --- |
| `GATEWAY` | 26 | `https://www.deezer.com/ajax/gw-light.php` | private web gateway: profile, favourites, playlists, Flow |
| `PUBLIC` | 27 | `https://api.deezer.com` | public catalogue |
| `MEDIA` | 28 | `https://media.deezer.com/v1/get_url` | signed stream URLs |
| `ARL_LOGIN` | 32 | `https://auth.deezer.com/login/arl?jo=p&rto=c&i=c` | ARL → short-lived JWT |
| `PICTURE_UPLOAD` | 34 | `https://upload.deezer.com/v2/playlist/picture` | playlist cover upload |
| `PIPE` | 36 | `https://pipe.deezer.com/api` | GraphQL home/recs |

Plus two more that are not constants: the web-player page
`https://www.deezer.com/en/channels/explore/` (`decrypt.rs:22`, `WEB_PLAYER`) and the sign-in page
`https://www.deezer.com/login` (`mod.rs:30`, `SIGN_IN_URL`). Image CDN pattern is
`https://cdn-images.dzcdn.net/images/{kind}/{md5}/{size}x{size}-000000-80-0-0.jpg` (`wire.rs:114`).

**These match PLAN.md §4's endpoint list exactly**, with one addition: PLAN.md does not mention the
web-player bundle fetch, which is where the decrypt secret comes from (below).

### How the ARL is validated

Two distinct layers, and only the first is local:

1. **Shape check, offline.** `auth::arl(input)` (`auth.rs:30-48`) accepts a full `Cookie` header, a
   lone `arl=…` pair, or a bare token. It splits on `;`, matches the `arl` name case-insensitively,
   and then requires `valid()`: **at least 32 characters, all ASCII hex** (`auth.rs:51-53`). An
   `arl` that fails the shape check is rejected with "the arl cookie does not look like one".
2. **Live check, at connect.** `DeezerClient::connect` (`client.rs:241-258`) runs `ping()`,
   `refresh()`, and then **bails if `session.profile.id` is empty** — the comment at `client.rs:236-238`
   states that an invalid or expired arl answers a user id of 0. `arl()` (line 350) returns the raw
   cookie back for storage.

So Kotlin's job is: capture the cookie, hand it to `arl()`, and treat a failed `connect` as "session
expired, sign in again". `restore_stored` (`mod.rs:76-92`) already implements the second half,
distinguishing *offline* failures (`trouble::offline`) from *dead session* ones, which is the
behaviour the app should mirror rather than reimplement.

### How the stream decrypt secret is fetched and cached

- **Fetch.** `decrypt::secret(http)` (`decrypt.rs:127-137`) calls `fetch_secret`, which GETs
  `WEB_PLAYER`, finds the `app-web…js` bundle URL in the HTML (`find_bundle`, ~line 181), GETs the
  bundle, and extracts two 8-byte hex halves — searched with `find_half(&source, 0x61, 0x67)` and
  `find_half(&source, 0x31, 0x34)`, i.e. the byte values of `"ag"` and `"14"` — then interleaves
  them into 16 bytes (`decrypt.rs:166-175`).
- **Validation.** The result must MD5 to `7ebf40da848f4a0fb3cc56ddbe6c2d09` (`SECRET_MD5`,
  `decrypt.rs:24`); otherwise it is rejected (`decrypt.rs:175-178`).
- **Fallback.** On any failure it logs a warning and returns `FALLBACK`, the constant
  `*b"g4el58wc0zvf9na1"` (`decrypt.rs:28`). So playback degrades rather than dies when Deezer
  redesigns the bundle.
- **Caching.** Once per client, at connect time, into a `OnceLock<decrypt::Secret>` field
  (`client.rs:210`, set at `client.rs:254-256`). `offline()` sets the same lock to `FALLBACK`
  (`client.rs:264`) so a downloaded track's key can be derived with no network.
- **Per-track key.** `track_key(track_id, secret)` (`decrypt.rs:38-48`): MD5-hex the track id, then
  XOR the first 16 hex chars against the last 16 and against the secret. `open_stream` uses the
  gateway's `SNG_ID` (`effective_id`) rather than the requested id, because Deezer may substitute a
  different release of the same recording (`client.rs:944-947`, `track_key` call at 993-994).

**Caching caveat worth planning around:** the secret is cached **per client process, in memory
only**. It is not persisted, so every app start re-scrapes the web-player bundle — two HTTP requests
before the first note plays. PLAN.md §4 says "cache it and refresh on failure", which upstream does
*not* do across launches. That is a deliberate deviation to implement (see WP-2 in §8) or to accept.

### Stream resolution and decryption

`open_stream` (`client.rs:941-990`): fetch track data from the gateway for `TRACK_TOKEN` and
`SNG_ID`; take `license_token` from the session; walk the formats best-first and POST to
`media.deezer.com/v1/get_url` with `cipher: "BF_CBC_STRIPE"` (`client.rs:998-1024`); on success GET
the signed URL and wrap it in `Striped`. Formats are `["FLAC_24", "FLAC", "MP3_320", "MP3_128"]`
(`FORMATS`, `client.rs:51`) and the account's plan picks the ceiling via `served()` (`client.rs:422-426`).
User-uploaded tracks (`effective_id` starting with `-`) are always `MP3_MISC` (`client.rs:957-960`).

`decrypt.rs` documents and implements the scheme: 2,048-byte blocks (`BLOCK`, line 16), every third
encrypted (`STRIPE`, line 19), fixed IV `00..07` (line 22), Blowfish-CBC reset per block.

### What Kotlin must inject

| Concern | Upstream does it | Why we cannot |
| --- | --- | --- |
| Credential storage | `auth::store`/`load` → JSON file under `credentials::dir("deezer")`, i.e. `dirs::data_local_dir()/sonora/accounts/deezer/credentials.json`, mode 0600 (`auth.rs:55-69`, `credentials.rs`) | `dirs` has no Android backend (see §1g). 0600 on `/data/data/<pkg>` is not Keystore-backed encryption, and PLAN.md §4 requires Keystore AES-GCM + EncryptedFile |
| HTTP | `reqwest::Client` built inline in `unopened()` with a **hard-coded Firefox 142 Linux user-agent** (`client.rs:270-273`) | Works on Android, but the UA says Linux — worth overriding, and the client should be process-wide (connection pool) rather than per-`DeezerClient` |
| Time | `Instant::now()` for the slot throttle, `jiff` for dates | `Instant` is fine on Android; no clock injection needed for Deezer specifically |
| Sign-in UI | `WebSignIn` prompt + `webview` crate (`mod.rs:31-36`, `SIGN_IN_URL`, `COOKIE_DOMAIN`) | `webview` is a no-op on Android (§1c); Kotlin must own the WebView and hand back the `arl` cookie |
| Family profile choice | `PromptSink`/`InputSource` round-trip through the app (`mod.rs:122-140`) | Needs a Kotlin prompt, or defer profiles to a later release |
| Secret cache | in-memory `OnceLock` only | Inject or persist if we want PLAN.md's "cache and refresh" |

### `playback.rs` (195 LoC)

Only what the engine needs from Deezer: a `Factory` implementing `PlaybackFactory`
(`playback.rs:41-56`) that calls `engine::start` with a `Deezer` fetch implementation, plus
`drain`/decode glue. It is not a player; the threads and buffering are the engine's (§1f).

---

## 1e. YouTube Music

Twelve files, 2,657 LoC in `crates/music/src/youtube/`: `accounts.rs` (57), `auth.rs` (48),
`client.rs` (654), `genres.rs` (406), `links.rs` (41), `lyrics.rs` (105), `mod.rs` (432),
`playback.rs` (354), `radio.rs` (65), `segments.rs` (140), `subscriptions.rs` (101), `wire.rs` (254).

### The `ytmusic` dependency

```toml
ytmusic = { git = "https://github.com/sonorahq/ytmusic-rs", rev = "99f7d14" }
```

`Cargo.toml` line 204 (workspace), consumed at `crates/music/Cargo.toml` line 71. It is **a Git
dependency, not a crates.io release**, so:

- it is pinned only by a **short rev** (`99f7d14`), which must be resolved against the remote and is
  not content-addressed in the way a crates.io checksum is — `Cargo.lock` does record a full commit
  for it, so reproducible builds work, but a compromised upstream repo could rewrite the branch;
- `THIRD-PARTY.md` lists it as `ytmusic 0.1.0` under **GPL-3.0-or-later** (line ~1432). So it is
  upstream's own fork under the same license, not a third-party crate. That is what makes
  "pin `ytmusic`, update weekly" (PLAN.md §5) feasible at all.
- its transitive dependency tree is **not** audited here. `THIRD-PARTY.md` covers it because it is
  in the lockfile, but I did not open its `Cargo.toml`. **not verified.**

The surface `music` uses from it: `YtMusic`, `AudioStream`, `Identity`, `Minter`, plus builder
methods `with_cookies`, `as_user`, `as_page`, `persist_cookies`, `cache_resolutions`, `cache_player`,
`mint_po_tokens`, `rotate_cookies`, `anonymous` (`youtube/mod.rs:124-145`).

### Cookie and auth handling

- **Validation.** `auth::header(input)` (`youtube/auth.rs:7-20`) normalises a `Cookie` header to
  `name=value` pairs joined by `"; "`, dropping pairs that contain whitespace, and **requires at
  least one of `SAPISID` or `__Secure-3PAPISID`** (`PROOF`, line 4) or it bails. This is the only
  local check; there is no offline expiry check.
- **Account discovery.** `accounts::list(&cookies)` (`mod.rs:199`, `accounts.rs`) asks ytmusic for the
  identities behind the header, capped at `LIMIT = 8` (`accounts.rs:6`). Zero results →
  "cookies were not accepted; sign in to the browser first" (`mod.rs:201`).
- **Persistence.** Credentials are a tagged enum `Saved::Cookies { cookies, authuser, page_id }` or
  `Saved::Guest` (`mod.rs:45-56`), serialised to a JSON file (`save`, `mod.rs:302-305`) through
  `credentials::write` — the same 0600 file path as Deezer. `COOKIE_DOMAIN` is `youtube.com`
  (`mod.rs:38`), `LANDING` is `music.youtube.com` (37), and `SIGN_IN_URL` (line 35) is Google's
  ServiceLogin with `continue=` back through `youtube.com/signin` to music.youtube.com — i.e. the
  same URL YouTube Music's own Sign-in button opens. That matters for Android: PLAN.md §5 flags
  embedded-WebView sign-in as a possible Google block, so the URL has to be exactly right.
- **`keep_fresh`** (`mod.rs:282-300`): a `tokio::spawn`ed loop, `Weak<YtMusic>` so it dies with the
  client. Wakes every `ROTATION_CHECK` = 60 s (`mod.rs:43`), and every `ROTATION` = 10 minutes
  (`mod.rs:41`) calls `api.rotate_cookies()`, logging and continuing on error. The start time is
  `rotated_at()` or now (`mod.rs:148`), so a session restored from disk rotates on the stored
  schedule. `persist_cookies(self.cookies.clone())` (`mod.rs:131`) is what writes the rotated
  cookies back to disk — on Android that file must be the encrypted store instead (see §1g).
- **Multi-account.** `authuser` index plus optional `page_id` for brand channels
  (`accounts.rs:12-19`); the picker is a prompt round-trip like Deezer's family profiles.

### DASH / sidx streaming

`playback.rs:1-7` is explicit: the track streams through `crate::stream`, fed by ytmusic's **ranged
download that re-asks from the byte a stalled connection stopped at**. The payload is "DASH fMP4, so
the decoder opens it unseekable and a seek splices the header onto the segment the `sidx` names".
`segments.rs:1-7` describes the layout it parses: `ftyp` + `moov`, then a `sidx` listing every
`moof`/`mdat` pair with size and duration, then the pairs; the index sits in the first kilobyte.
`Segments::read` (`segments.rs`, used at `playback.rs:326`) is called on the stream front
(`front(stream)` at `playback.rs:322`) along with `trim::from_mp4` for silence trimming.

Practically: seeking is **index-splicing, not HTTP range arithmetic on the file**, and the decoder
is fed a synthetic head+segment stream. Any Android player that assumes `Content-Length` and
`Range` on a plain file will not reproduce this — which is why a Media3 `DataSource` around this
code is more work than it looks (WP-4 in §8).

Timeouts: `PATIENCE` 15 s per step, `ATTEMPTS` 2 (`playback.rs:33-36`). Qualities:
High = AAC 256/128, Normal = AAC 128, Low = HE-AAC 48 (`playback.rs:38-46`).

### PoToken: what a `TokenMinter` must provide

This is a three-layer design and the split is the important part.

**Layer 1 — `crates/music/src/potoken.rs` (139 LoC), provider-agnostic, no browser knowledge.**
A process-global `Mutex<Shared>` behind a `OnceLock` (`potoken.rs:47-50`). Public API:

| Function | Line | Contract |
| --- | --- | --- |
| `token(binding) -> Option<String>` | 56 | Returns a fresh token or **records the ask** and returns `None` immediately. A track must never wait on a browser |
| `minted(binding) -> Option<String>` | 69 | Reads only; never records an ask |
| `wanted() -> Option<String>` | 75 | **Takes** the pending ask (one ask → one attempt) |
| `warm() -> bool` / `set_warm(bool)` | 81 / 86 | Whether the minter has an attested page open, so a caller may wait ~1 s |
| `give(binding, token)` | 94 | Files a token, valid `KEEP` = 6 h (line 19), and resets failure state |
| `failed()` | 112 | Records a failure; next attempt held `SOONEST` = 5 s (line 27), doubling to `SLOWEST` = 5 min (`hold` at 136–140) |

Failure backoff is deliberate: "a browser Google will not attest fails every time", so doubling the
hold stops a window opening every few seconds for the life of the run (`potoken.rs:22-32`).

**Layer 2 — `crates/music/src/youtube/mod.rs:66-86`, the `ytmusic::Minter` adapter.**
`mint(binding)` = return `potoken::token(binding)` if present; else if `!warm()` return `None`
at once; else poll `potoken::minted` every `WARM_POLL` for up to `WARM_WAIT` = 2 s
(`mod.rs:59` and `61`) and give up. So the **only** latency a stream will tolerate is 2 s, and only when
the minter claims to be warm.

**Layer 3 — `crates/state/src/potoken.rs` (245 LoC) + `potoken.js` (161 LoC), the browser side.**
A poll loop (`tick`, `state/src/potoken.rs:98-106`) that opens a `webview::Page` at
`{PAGE}?binding=…` with cookie proof, injects `potoken.js`, and reads the answer out of the
`SONORA_POT` cookie. On success: `music::potoken::give(&binding, token)` and
`set_warm(true)` (lines 213-214). On failure: `music::potoken::failed()` (118, 143, 218). If
`webview::supported()` is false — always on Android — it logs and calls `failed()`
(lines 118-121), so the app degrades to cold-start tokens.

`potoken.js` does not implement BotGuard. It waits for YouTube's own page to expose its minting
client and calls it, "the way yt-dlp's `wpc` provider does" (`potoken.js:1-11`): it looks for
`holder[SLOT].wpc` under `HOLDER = "havuokmhhs-0"`, `SLOT = "bevasrs"` (lines 26-27), falls back to
searching for the client "wherever it has moved" (lines 87-97), waits up to `PATIENCE` = 30 s
(line 29), and answers in a cookie as `binding:token`, with `!` prefixing a failure (lines 19-28).

**What Kotlin's `TokenMinter` must therefore provide:**

1. A **real browser engine** (Android WebView) that Google will attest. No JS interpreter, no HTTP
   replay: the attestation is of the engine itself (`potoken.js:1-11`).
2. A page at `music.youtube.com` (or the embed page) with the **signed-in cookie** in the WebView's
   cookie jar, because the minting client has to run under an account for the token to be accepted.
3. The `potoken.js` payload injected, and a way to call `window.sonoraPoToken(binding)` for each
   subsequent ask.
4. **Cookie read-back** of `SONORA_POT`, parsed as `binding` + `:` + value, where a leading `!` is a
   failure. That is the whole transport — no JS bridge return values.
5. **Warm-state reporting**: call the equivalent of `set_warm(true)` once attested, `false` when the
   page closes. Without it every `mint` returns `None` and streams use cold-start tokens.
6. **Failure reporting** into `failed()`, so the exponential hold works. A minter that reports every
   attempt as a failure but keeps opening windows would defeat the backoff.
7. **Bounded lifetime**: the page is expensive (PLAN.md §5 says ~270 MB) and upstream keeps it only
   while asks keep arriving (`LINGER` = 20 s, `state/src/potoken.rs:46`; the close check is at 155–165).

The bridge already declared in `mobile-core` is the right shape: `TokenMinter` with
`mint(binding)`, and `SonoraCore::mint_token` (`rust/crates/mobile-core/src/api.rs:467`). What is
missing is the *cache* and *backoff* half of layer 1 — our core currently just forwards the call.

### Sign-in block risk, restated

Google blocks embedded-WebView sign-in for many accounts. Upstream's own answer is a native WebView
with a real engine, which on Android is still a WebView. PLAN.md §5's Custom Tabs fallback is not
something upstream provides; up to and including "capture cookies from a Custom Tab" there is **no
upstream code to reuse**, because `webview` returns `supported() = false` on Android and the cookie
capture path (`COOKIE_DOMAIN`, `mod.rs:38`) belongs to the desktop backend. **not verified** whether
any Android flow works; this is the single biggest unknown in Release 1.

---

## 1f. Audio

### The pipeline as built

`crates/music/src/engine.rs` (1,632 LoC) is the engine for every provider whose track is "a file to
decode". Its header states the design (`engine.rs:1-18`): **two threads**, a tokio thread that
fetches and one audio thread that decodes and feeds `sink::Paced`; "Neither the audio callback nor
the runtime ever waits on the network". A provider supplies `Fetch`; the engine owns the command
queue, preload, gapless join, position reporting and device-loss handling.

Layer by layer:

| Stage | File | Notes |
| --- | --- | --- |
| Engine thread | `engine.rs:185-187` | `std::thread::Builder::new().name("{provider}-playback")`, one per provider session |
| Buffering / pacing | `sink.rs` (653 LoC) | `QUEUED_CHUNKS = 26` packets, `DRAIN_POLL` 10 ms, `SPARES = 28` recycled buffers (`sink.rs:22-31`) |
| Chain (EQ, volume) | `audio.rs` (380 LoC) | `Chain { volume, analyzer, equalizer, spectrum, meter }` (`audio.rs:135-142`) |
| EQ | `equalizer/` (3,036 LoC) | `FilterStage` inside `Chained` (`audio.rs:161`) |
| Resampling | `output/output_rate.rs` (194 LoC) | every packet resampled to the **device** rate with `ardftsrc`, so nothing downstream converts (`output_rate.rs:1-10`) |
| Mixer + device | `output/mod.rs` (1,659 LoC) | rodio `Player`/`MixerDeviceSink`; `Output::open` at `output/mod.rs:814-827` |
| Stream open | `output/mod.rs:1582-1625` | `DeviceSinkBuilder` → `open_stream()`, `BufferSize::Fixed(frames)` |
| Linux device enumeration | `output_host.rs` (200), `output_alsa.rs` (174), `output_hog.rs` (363) | PulseAudio/ALSA only |

Sample format handling: `output/mod.rs:813-825` picks integer vs float clamping from the device's
format, and `audio.rs` holds integer samples within full scale (`FULL_SCALE`, `audio.rs:13`), because
a 24-bit device wraps on `1.0`.

### Where an Oboe sink plugs in

Two seams exist, and they are not equally good.

**Seam A — replace `device`/`stream` inside `output`, keep everything above it (recommended).**
The code above `Output` consumes a rodio `Source` (`chain.apply(source, format, integer)` at
`output/mod.rs:821`) and knows nothing about cpal. So an `output_oboe.rs` needs to:

1. own an `oboe::AudioStream` built with `Format::F32` (or I16), the device's native rate and stereo;
2. provide the `Source` that `Chain::apply` wraps — i.e. a `rodio::Player`'s receiving end;
3. implement the same surface `Output` exposes today: `open`, `sink`, `play`, `pause`, `set_volume`
   (`output/mod.rs:806-900`), plus `DeviceInfo`/`DeviceOption` facts that `state/outputs.rs` reads.

The resampler then keeps working: `output_rate.rs` asks the device for its rate and resamples to it,
which on Android means the Oboe stream's sample rate. This is the smallest diff and keeps the EQ,
loudness, trim, gapless join and spectrum untouched. It is what PLAN.md §3 means by "`output_host.rs`
becomes a new `output_oboe.rs`", though strictly the new file replaces `output`'s device layer, not
only the PulseAudio listing.

**Seam B — replace `sink::Paced` entirely.** `sink.rs` is where packet pacing lives: `QUEUED_CHUNKS`,
the `Cue`/`Admit` state machine, spares recycling (`sink.rs:22-31`, `46-60`, `195-225`). A native
Oboe callback could drain this queue directly and skip rodio's mixer, removing one copy and one
thread. Bigger diff, better latency, and it destroys the "one shared stream for every output"
property (`Output::open` joins a shared stream, `output/mod.rs:814`), which we do not need on a phone.

Recommendation: **Seam A first**, measured, then Seam B only if latency demands it.

### Real-time constraints — what the callback does today

The code is already careful, and this is worth preserving rather than rewriting:

- **No allocation in steady state.** Returned sample buffers are recycled through `Spares`
  (`sink.rs:195-225`): the list is `Vec::with_capacity(SPARES)` up front, `take` reuses a buffer,
  `keep` pushes only when the list is under `SPARES`. The doc comment says "steady playback reuses
  the same few buffers instead of allocating one per packet". Note `take` does `buffer.reserve(wanted)`
  — that *can* allocate if a spare is smaller than the packet, so it is "usually no allocation", not
  "never".
- **No blocking lock on the audio thread.** `Spares::keep` uses `try_lock` and gives up when busy
  (`sink.rs:218`), explicitly so "the output callback only ever tries the lock, so it never waits on
  the decoder". This is the right pattern and must survive an Oboe port.
- **Atomics for hot state.** Volume is an `AtomicU32` of the gain's bits (`audio.rs:17-31`); the cue
  rides an `AtomicU8` (`sink.rs:33-37`); `playing` is an `AtomicBool` (`output/mod.rs:807`).
- **Unbounded channels only one way.** Engine→output is `tokio::sync::mpsc::UnboundedSender`
  (`output/mod.rs:41`), and the callback never sends back synchronously.

What an Oboe port must **not** introduce: a mutex the callback blocks on, `log::` calls in the
callback path, JNI calls per buffer, or `String` formatting. `open_stream`'s error callback already
logs (`output/mod.rs:1609-1623`) but that is the error path, not the audio path — acceptable today
because cpal guarantees it is not the audio callback; Oboe error callbacks differ and need the same
care.

### What the engine gives us for free, and what it costs

Free: gapless join, preload, crossfade, loudness normalisation from a provider-reported LUFS
(`engine.rs:18-20`), position reporting, device-loss recovery (`spawn_watcher`,
`output/mod.rs:1628-1657`, polls `DEVICE_POLL` and reopens), and the whole `Chain`.

Cost: it is **rodio + cpal**, so an Oboe port either keeps rodio as the mixer behind an Oboe device
(Seam A) or reimplements `sink::Paced` (Seam B). PLAN.md §3's "`output_host.rs` becomes
`output_oboe.rs`" makes it sound like a file swap; it is a device-layer swap with a mixer decision
behind it, and it should be estimated as such (WP-3 in §8).

---

## 1g. State and logic crates: mapping to `mobile-core`

Our core is `rust/crates/mobile-core`, 2,546 LoC across ten modules (`api.rs` 623,
`queue.rs` 452, `quality.rs` 321, `fixtures.rs` 320, `playback.rs` 301, `auth.rs` 246,
`models.rs` 184, `events.rs` 35, `error.rs` 25, `lib.rs` 39). It exposes ~45 methods on
`SonoraCore` (`api.rs`), two foreign traits (`CoreListener`, `TokenMinter` — `events.rs:22-35`),
and a model set in `models.rs`. It is entirely fixture-backed (`fixtures.rs`; `CoreFactory.kt` sets
`ADAPTER_USES_FIXTURES = true`).

### Where each mobile-core concern must come from

| `mobile-core` surface (`api.rs`) | Upstream source at `v0.50.2` | Verdict |
| --- | --- | --- |
| `version`, `providers`, `network`, `set_network` | none — ours | keep ours; `network` should be fed from `ConnectivityManager` (PLAN.md §3) because `state/network.rs` is GPUI-bound (`network.rs:3`) |
| `auth_snapshot`, `sign_in_started`, `mark_signed_in/out`, `mark_needs_attention`, `set_plan` | no direct equivalent. Closest is `MusicProvider` + `ProviderSession` (`deezer/mod.rs:94-110`: `profile`, `api`, `playback`, `shape`, `authenticated`, `capabilities`) | **replace**: upstream's sign-in *is* the session object, not a state machine. Our auth bookkeeping is a Kotlin-facing convenience and should stay Ours, fed from real `ProviderSession` results |
| `quality_ladder`, `quality_preference`, `resolved_quality` | `client::QUALITIES` (`deezer/client.rs:53-85`) and `Capabilities.qualities` (`deezer/mod.rs:104`); per-track resolution in `served()`/`formats_from` (`client.rs:422-426`) | **take upstream's ladder**; our `quality.rs` (321 LoC of fixtures) should be replaced by the real `Offer`/`Encoding` data |
| `home`, `library`, `search`, `album`, `playlist`, `lyrics` | `MusicApi` methods (`crates/music/src/lib.rs:124+`), 38 implemented by Deezer; the *state* wrappers live in `state/{home,library,search,detail,artist,lyrics}.rs` and are GPUI-bound | **replace**: call `MusicApi` directly from a new UI-free layer. The `state` wrappers cannot be reused (see below) |
| `playback_state`, `play_tracks`, `play_pause`, `next/previous`, `seek`, `set_repeat`, `set_shuffle`, `set_volume`, `set_crossfade`, `play_queue_index`, `add_to_queue`, `remove_from_queue`, `move_in_queue`, `queue_snapshot` | `engine.rs` + `sink.rs` + `output/`; queue logic in `state/queue.rs` and `state/playback.rs` (both GPUI `Entity`, 1 `gpui` reference each) | **port**: the engine is UI-free and reusable; the queue *policy* must be lifted out of `state/queue.rs` into our crate |
| `set_output(name)` | `output/mod.rs` `OutputId`/`DeviceOption`; `state/outputs.rs` (GPUI) | **replace** with Media3/`AudioManager.getDevices()` on Android |
| `mint_token(binding)` | `music::potoken` layer 1 + `ytmusic::Minter` layer 2 | **port layer 1 verbatim**, back Kotlin's `TokenMinter` with it (§1e) |

### Modules that must be replaced, and why

- **`vault`** (`crates/music/src/vault.rs`) — ChaCha20-Poly1305 sealed downloads. The `music` half is
  portable and takes the key from its caller: `Vault::unlock(&master)` (line 239) and
  `new_master()` (line 230). The **key custody lives in `state`**, not `music`: `state/src/vault.rs`
  reads and writes the master key through GPUI's `cx.read_credentials("sonora://kept-tracks")` /
  `cx.write_credentials(...)` (`state/src/vault.rs:20,64,113`), i.e. whatever the GPUI platform
  provides — on Linux that means `ashpd`/Secret Service over D-Bus (`crates/state/Cargo.toml:37`),
  with a 0600 key file as fallback (`state/src/vault.rs:3-12`). **There is no `keyring` crate in the
  workspace** — I grepped both manifests and sources. So on Android the key source must be Kotlin
  (Keystore), and it must be injected because both GPUI credential calls disappear with `state`.
  The sealing format itself is portable and worth keeping.
- **`credentials`** (`crates/music/src/credentials.rs`) — `dirs::cache_dir()` and
  `dirs::data_local_dir()` (`root()` and `accounts()`), owner-only permissions via
  `std::os::unix::fs::PermissionsExt`, `fs::rename` atomic replace. All of that "works" on Android
  against `/data/data/<pkg>`, but `dirs` has no Android backend at all, and 0600 is not Keystore
  encryption. This is the single most-touched seam: **37 references across 10 files in `music`
  alone** (`grep -rn 'credentials::'` → `apple/auth.rs`, `deezer/auth.rs`, `jellyfin/auth.rs`,
  `soloist/mod.rs`, `spotify/auth.rs`, `spotify/pathfinder/hashes.rs`, `subsonic/auth.rs`,
  `tidal/auth.rs`, `vault.rs`, `youtube/mod.rs`), plus `state`'s own stores. Most are in providers
  we drop, but `vault.rs`, `deezer/auth.rs` and `youtube/mod.rs` are ones we keep.
- **`dirs`** (crate) — `grep -rl 'dirs::' crates/music/src` → exactly 7 files: `credentials.rs`,
  `local/client.rs`, `musixmatch/mod.rs`, `redact.rs`, `soloist/binary.rs`, `stream.rs`, `vault.rs`.
  It is also used by `storage` and `state`. Android has no `dirs` backend; PLAN.md §3 wants
  `Context.filesDir`/`cacheDir`/`getExternalFilesDir(MUSIC)`. Three of those seven (`credentials.rs`,
  `stream.rs`, `vault.rs`) are in code we keep, so the injection is not confined to dropped
  providers.
- **`network`** — `state/network.rs` imports GPUI (`network.rs:3`) and models connectivity as an
  `Entity`. Replace with a plain `NetworkKind` setter from Kotlin, which is what our
  `set_network`/`network` already is.
- **`devices`** — `state/devices.rs` (GPUI, `HashMap`/`HashSet` of cached device facts) plus
  `output_host.rs` enumeration. On Android the truth is `AudioManager.getDevices()`; Media3 routing
  replaces it.
- **`remote`** — `state/src/remote/{mod,mpris,souvlaki}.rs`, MPRIS on Linux and `souvlaki`
  elsewhere *including Android*. Replaced by MediaSession (PLAN.md §3). **This is also a build
  blocker if `state` is kept** (§1c).
- **`potoken` (state side)** — `state/src/potoken.rs` + `potoken.js` cannot run (no WebView backend).
  Kotlin replaces the *browser* half; the Rust cache/backoff half moves into `mobile-core`.

### What is reusable as-is (the good news)

- `engine.rs`, `sink.rs`, `audio.rs`, `stream.rs`, `equalizer/`, `spectrum/`, `trim.rs`,
  `progress.rs`, `order.rs` — no UI types, no `dirs`, no keychain. This is the bulk of the value.
- `deezer/*` and `youtube/*` — provider logic, as analysed in §1d/§1e.
- `storage` (294 LoC) — SQLite + `dirs`; only the path input needs injecting.
- `music/models.rs` — the wire models our `models.rs` maps to.
- `embed`, and `i18n` once its single `gpui::SharedString` import (`i18n/src/lib.rs:10`) is replaced
  with `Cow<'static, str>` or `String`.

### Structural note on `state`

`state` is **not** a logic crate with some UI on top. 40 of its 44 modules import `gpui` (§1b
deviation 2), and its dependencies include `ui`, `cli`, `webview`, `discord-rich-presence`.
Treating "keep the `state` modules listed in PLAN.md §3" as a task will fail at `cargo check` on the
first `gpui` import. The realistic plan is a **new** `mobile-core` module tree that calls `music`
directly, with queue/playback policy copied across from `state/playback.rs` and `state/queue.rs`
rather than compiled from them.

---

## 1h. Proposed Phase B task list

Ordered by risk: each item's unknown comes first, and the riskiest items are ones where **we cannot
tell from source alone whether it works**. Verification is always a CI step, never "it compiles
here" — this sandbox has no Android toolchain, which is the same constraint that shaped PRs #10/#11.

Suggested landing order is the numbering; items 1–3 are the ones that can cancel the release.

### WP-1 — Prove `music` cross-compiles for three Android ABIs (riskiest)

*Unknown:* whether `aws-lc-sys` (C/CMake), `ring` (C+asm) and `rusqlite --features bundled` (C)
build for `aarch64/armv7/x86_64-linux-android` with NDK r27c; and whether `souvlaki`/`gpui` are
truly gone.

*Files touched (fork):* new `crates/mobile-core-android/` or a `music` feature split;
`crates/music/Cargo.toml` (delete `librespot-*`, `open`, `gethostname`, `oauth2`, `opensubsonic`,
`protobuf`, `tokio-tungstenite` if unused); `crates/music/src/lib.rs` (`pub mod` list);
`crates/music/src/engine.rs:11`, `sink.rs:562`, `lib.rs:615` (comment-only librespot mentions).

*CI verification:* a `cargo ndk` job for all three targets that builds **only** the trimmed
`music` + our crate, with `CARGO_PROFILE_RELEASE_STRIP=none` (else UniFFI metadata dies — see the
lesson in PR #10) and `--no-strip`. Fail on any missing `.so`. Publish `Cargo.lock` so the graph is
reviewable. **Gate:** if `aws-lc-sys` cannot be built or excluded, record the workaround before
proceeding.

### WP-2 — Credential and directory injection (blocks any real account)

*Unknown:* whether Keystore-backed storage can be injected through UniFFI without putting a callback
on a hot path, and whether cookie rotation can write back asynchronously from `keep_fresh`.

*Files touched:* `music/src/credentials.rs` (whole file), `music/src/deezer/auth.rs:24-69`,
`music/src/youtube/mod.rs:103,208-230,302`, `music/src/vault.rs` (key source only),
`mobile-core/src/api.rs` (new `CredentialStore` foreign trait), `events.rs`.

*CI verification:* a unit test that the injected store round-trips and that **no** `dirs::` call is
reachable: `grep -rn "dirs::" crates/` must return nothing in the Android build, enforced by a CI
step that fails on a match. Plus an instrumented test asserting a credential written and read back
through the Kotlin implementation.

### WP-3 — Oboe sink behind `Output` (Seam A)

*Unknown:* latency and glitch behaviour of the rodio-mixer-behind-Oboe shape; whether the resampler's
device-rate assumption holds when Oboe reports 48 kHz while the track is 44.1 kHz.

*Files touched:* new `music/src/output/output_oboe.rs`; `output/mod.rs:806-830` (`Output::open`
device construction), `1628-1657` (watcher); `Cargo.toml` (+`oboe` crate).

*CI verification:* unit tests for the resample path already exist; add a host-side test that the
`Chain` still produces identical samples when driven by a fake device sink. On-device: an
instrumented test that opens the stream and plays 2 s of a synthetic buffer, asserting no underrun
callback. Cannot be fully verified device-free — say so.

### WP-4 — YouTube playback: DASH/sidx seeking over a Kotlin-owned stream

*Unknown:* whether seeking-by-index can be implemented against a Media3 `DataSource`, given upstream
splices the header onto a chosen segment rather than issuing range requests per seek.

*Files touched:* `music/src/youtube/playback.rs:295-340`, `music/src/youtube/segments.rs`,
`music/src/stream.rs` (`Reader`), `music/src/mp4.rs`.

*CI verification:* pure-Rust unit tests over a captured `sidx` fixture — assert the segment offsets a
seek maps to. No network, deterministic, runs in the existing `Rust core` job.

### WP-5 — PoToken: move layer 1 into `mobile-core`, Kotlin supplies the browser

*Unknown:* whether an Android WebView passes BotGuard at all (PLAN.md §5 flags this as a blocker).

*Files touched:* `mobile-core/src/events.rs` (extend `TokenMinter` to report warm/failure),
`mobile-core/src/api.rs:467` (`mint_token`), port of `music/src/potoken.rs` semantics.

*CI verification:* Rust unit tests for the cache and backoff state machine (give/token/failed/wanted
semantics, 6 h expiry, doubling hold) with a fake clock — deterministic and device-free. The
WebView half can only be proven on a device.

### WP-6 — Port queue/playback policy out of `state`

*Unknown:* how much of `state/playback.rs`/`queue.rs` is policy versus GPUI plumbing.

*Files touched:* `state/playback.rs`, `state/queue.rs`, `state/song.rs`, `state/session.rs` →
new modules in `mobile-core`.

*CI verification:* port the existing `state` tests; add parity tests that our `QueueSnapshot`
ordering matches `state/queue.rs` behaviour for play/next/previous/shuffle/repeat.

### WP-7 — Deezer sign-in end to end

*Files touched:* `mobile-core/src/auth.rs`, `music/src/deezer/mod.rs:43-92` (replace the
`PromptSink`/`webview` flow), Kotlin WebView + `CookieManager`.

*CI verification:* the ARL parser is already pure — port `music/src/deezer/auth.rs:30-53` tests and
add cases for the full-cookie, `arl=`-pair and bare-token shapes. Live sign-in is manual.

### WP-8 — Trim `state`, `views`, `ui`, `cli`, `webview`, `widevine`, `sonora` from the fork

*Files touched:* `Cargo.toml` (`members`), delete the crates.

*CI verification:* `cargo metadata` assertion that the workspace has exactly the kept members, and
that `gpui`, `souvlaki`, `alsa`, `windows`, `x11rb` appear in **no** dependency tree
(`cargo tree -e normal | grep` fails on a match).

### WP-9 — Notices and license surface in the app

*Files touched:* new about-screen asset; `docs/` copy of `COPYING` and `THIRD-PARTY.md`.

*CI verification:* a step asserting the APK contains the license text and the CC BY 4.0 attribution
string for the Solar icons — a zip-entry + `strings` check, which is exactly the kind of thing the
existing smoke check in `Android native` already does.

### What does *not* belong in Phase B

`local/` (Track A), `soloist`, `binimum`, Jam/NSD, casting, scrobbling backends, and the Spotify /
Apple / Tidal / Subsonic / Jellyfin providers. PLAN.md §3 removes them from Release 1; keeping their
dependencies compiled in is what makes WP-1 hard, so they should be deleted before it is attempted.

---

## 9. Not verified

Everything below is either unread, or read but not executed. Nothing here should be quoted as fact.

**Not opened at all**

1. The `ytmusic` crate's own source and manifest (`github.com/sonorahq/ytmusic-rs` at `99f7d14`).
   Its license is *inferred* from `THIRD-PARTY.md` listing it under GPL-3.0-or-later; its
   transitive dependency tree is not audited. This matters because it is the YouTube client.
2. `crates/music/src/apple/`, `spotify/`, `tidal/`, `jellyfin/`, `subsonic/`, `soloist/`,
   `binimum/`, `local/`, `connect.rs`, `drm.rs` — only grepped for dependency usage, never read.
3. Any `crates/views/*` or `crates/ui/*` file. Their LoC and dependency counts are from
   `wc`/`grep` only.
4. `crates/state/src/discord/`, `updates`, `wake.rs`, `window_shape.rs` — out of scope, not read.
5. `scripts/generate-notices.py`, so how `THIRD-PARTY.md` derives its license assignments is
   **unknown**; the counts are taken at face value.
6. `Cargo.lock` in full. Dependency edges quoted above were read from specific `[[package]]` blocks
   (reqwest, cpal, souvlaki, ring, rustls*, aws-lc*, quinn-proto, open), not from a resolved tree.

**Read but not executed**

7. **No Android compile of anything.** No `cargo ndk`, no Gradle build against upstream. Every
   "will compile"/"blocks" judgement in §1c is static analysis.
8. **No runtime of any kind.** Zero calls were made to Deezer or YouTube. The endpoint list, the ARL
   validation path, the secret extraction and the PoToken flow are described from source, not from
   observed traffic.
9. **PoToken effectiveness on Android WebView is unknown.** Whether Google attests an Android
   WebView running `potoken.js` cannot be determined from source.
10. YouTube behaviour at `v0.50.2` in general — the report cannot say whether these endpoints and
    client versions still work today (2026-11-20), only that this is what the pinned code does.
11. Whether `aws-lc-sys` is unavoidable in the dependency graph, or whether installing `ring` as the
    rustls provider removes it. Needs an actual resolved tree per target.
12. Unique crate count in `THIRD-PARTY.md` — the 809 figure is entries, with duplicates (§1a).
13. Line numbers into upstream were correct as of `c0b501c`; they refer to
    `rust/vendor/sonora/crates/` since vendoring, and drift on any upstream bump.
    Re-grep before relying on one.
14. The cause of the CI breakage described in §10. The facts and the fix candidates are verified;
    the mechanism is my inference.

---

## 10. CI breakage on `main`: the `jna-5.14.0.aar` failure, and its fix

`main` at `fb03ba0` failed `Android native` → **Build the Rust-backed debug APK**:

```
org.gradle.internal.resolve.ArtifactNotFoundException:
  Could not find jna-5.14.0.aar (net.java.dev.jna:jna:5.14.0).
```

This section records the investigation, because the first explanation was wrong and the wrong
explanation is the kind that gets re-applied later.

### What was not the cause

- **The artifact.** `net.java.dev.jna:jna:5.14.0` publishes an `.aar` on Maven Central —
  517,894 bytes, dated 2023-12-10, listed in the repository index next to the `.jar`. It is also
  not a repository-ordering problem: the failure named one `Searched:` URL only.
- **The declaration.** There is exactly one JNA declaration in the whole tree,
  `android/core-bridge/build.gradle.kts:64`, and it already carried `@aar`
  (`implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")`). No module has its own
  `repositories {}` block, so `FAIL_ON_PROJECT_REPOS` was never being worked around.
- **A one-off runner glitch.** The failure reproduced across reruns and across cache states.

### What it was

A restored `GRADLE_USER_HOME` carrying a record that `jna-5.14.0.aar` is resolved when its bytes are
gone. Gradle trusts that record and only discovers otherwise inside the AAR transform:

```
TransformException: Failed to transform jna-5.14.0.aar (net.java.dev.jna:jna:5.14.0)
  to match attributes {artifactType=android-aar-metadata}
  caused by ArtifactNotFoundException: Could not find jna-5.14.0.aar
```

The `TransformException` wrapper is why this reads as "the artifact does not exist" when it does.

Evidence, in the order it was gathered (each row is one CI run of the same job):

| Change | Run | Result |
| --- | --- | --- |
| `cache-disabled: true` **and** `--refresh-dependencies` | `44731681737` | **pass** — 23,735,582-byte APK, `libjnidispatch.so` present |
| Restore the cache, drop `--refresh-dependencies` | `44732675888` | fail — the `TransformException` above appears |
| Purge `modules-2/files-2.1/net.java.dev.jna` only | `44733329353` | fail |
| Also purge the transform caches (`metadata-*/transforms`, `8.*/transforms`, `transforms-*`) | `44733717656` | fail |

The two-variable first row proves nothing on its own, which is the trap: `--refresh-dependencies`
hides the poisoned entry by re-resolving everything, so a green run under it is not evidence.
The targeted purges not clearing it says the stale record lives somewhere those paths do not reach,
and chasing that further is not worth it — the cache is disposable.

### The fix, as applied

- `.github/workflows/build.yml` (`Android native` only): `cache-disabled: true` on `setup-gradle`,
  so the job resolves from a clean Gradle home. `--refresh-dependencies` is **not** used, so the job
  demonstrates the fix rather than masking the bug.
- The same step's build now passes `--no-configuration-cache`, so a resolution failure is reported
  as a resolution failure instead of being wrapped in a configuration-cache report.
- `android/settings.gradle.kts`: `exclusiveContent { forRepository { mavenCentral() }
  filter { includeGroup("net.java.dev.jna") } }`, which pins the group to the only repository that
  serves it. This was not the bug, but it stops the group from ever being resolved from, or cached
  as missing by, another repository.
- `android/gradle.properties`: `org.gradle.configuration-cache=false`.
- A failure-only `dependencyInsight` step, so the next occurrence reports which repository and
  variant Gradle settled on instead of needing another bisect.

Verified: run `44734323404`, all three jobs green with no `--refresh-dependencies` anywhere in the
native job; artifacts `sonora-debug-rust-apk` (23,735,582 B) and `sonora-debug-apk` (19,411,767 B);
the `Explain the JNA resolution` step `skipped`, as designed; the `libjnidispatch.so` smoke check
passed.

### The cost, stated plainly

The `Android native` job no longer restores a Gradle dependency cache, so it re-downloads its
dependencies on every run. That was accepted rather than accepted silently: the alternative for
this PR was a build that only passes when a flag that hides the problem is set. Re-enabling the
cache is worth doing once the old cache entry has aged out of the repository's cache list — the
diagnostic step is there to say immediately whether it is back.

### What this does and does not say about §1c

§1c named the likely Android build blocker as `aws-lc-sys`, pulled in through `quinn-proto`,
`rustls-webpki` and `tokio-tungstenite`. The failure above is unrelated to it, and the green
cross-compile steps in run `44734323404` (**Cross-compile the core for three ABIs**, **Assert the
three shared libraries exist**, **Assert UniFFI metadata symbols survived the release link**) say
nothing about it either: those steps build `-p mobile-core`, whose dependencies today are
`uniffi`, `thiserror`, `serde`, `serde_json`, `parking_lot`, `tracing`, `once_cell`, `async-trait`
and `tokio` (`rust/crates/mobile-core/Cargo.toml:26-34`). No `music`, no `state`, no TLS stack, so no
`aws-lc-sys`.

So: the APK step failing was a Gradle cache problem and should not have been read as a Rust-side
signal — but §1c's `aws-lc-sys` risk is still **untested**, not refuted. It gets exercised the first
time `music` is actually pulled into `mobile-core`, which is the point of WP-1, and which is why WP-1
is the riskiest item on the list.

---

## 11. Vendoring landed: what changed, and the numbers it moved

PR #14 merged at `0215f3c`. `crates/music`, `crates/storage` and `crates/widevine` now live in
`rust/vendor/sonora/crates/` as workspace members at `v0.50.2` (`c0b501c`), and the toolchain is
**1.98.1** rather than 1.85.0 — the compiler upstream resolves and builds against. `mobile-core`
still does not depend on `music`.

### Two claims in this document are now settled by a real resolution

`rust/Cargo.lock` is Cargo's own output, not a hand-merge, so the graph below is no longer inferred
from lockfile blocks plus cloned manifests:

- **`aws-lc-sys 0.47.0` is in the graph**, `ring 0.17.14` alongside it, `rusqlite 0.43.0` with
  `libsqlite3-sys 0.40.0` (C amalgamation), `rquickjs 0.12.2`, `opusic-sys 0.7.7`,
  `opensubsonic 0.7.0`. §1c's concern is real, not theoretical.
- The **§1c attribution was wrong** and is corrected in §10: `aws-lc-rs` does not arrive via
  `quinn-proto`/`rustls-webpki`/`tokio-tungstenite`; those are optional edges that nothing enables.
  It arrives through `opensubsonic 0.7.0 → reqwest 0.13 "rustls"`.

What a host build proved, and what it did not:

- **Proved:** `cargo check -p music --locked` passes on host Linux, unmodified, no source edits.
  `cargo metadata --locked` passes. apt packages needed: `pkg-config libasound2-dev libpulse-dev
  cmake nasm clang libclang-dev`.
- **Not proved:** the **Android cross-compile of `music` has still never been attempted.** The
  existing `Android native` job builds `-p mobile-core` only, whose closure is nine crates and no
  TLS stack. WP-1 is therefore still the first real test of `aws-lc-sys`/`ring`/`libsqlite3-sys`
  against NDK r27c.

### `.so` sizes moved with the compiler — the armeabi-v7a delta needs to be remembered

Same source, same flags, only the compiler changed 1.85.0 → 1.98.1:

| ABI | 1.85.0 | 1.98.1 | Δ |
| --- | --- | --- | --- |
| arm64-v8a | 4,700,024 B | 5,106,544 B | +8.6% |
| armeabi-v7a | 3,839,012 B | 4,383,488 B | **+14.2%** |
| x86_64 | 4,571,520 B | 5,030,008 B | +10.0% |

`mobile-core`'s dependency closure is byte-for-byte identical before and after (94 packages, zero
version changes), so nothing entered the binary. 32-bit ARM absorbs a codegen change worst because
it has no 64-bit registers, so spill/reload and relay code costs proportionally more. That is an
explanation consistent with the evidence, **not a measurement** — proving it needs both toolchains
to diff codegen. Recorded here so a future size regression is compared against 5,106,544 /
4,383,488 / 5,030,008 rather than against the pre-vendor numbers.

### The vendor patch surface, and when it shrinks

`rust/Cargo.toml` carries seven `[patch.crates-io]` entries copied from upstream: `rodio @ 880aaba`
(because `music` asks for rodio's `symphonia-libopus` feature, which published `rodio 0.22.2` does
not have) and all six `librespot-* @ c3bbdf7`. **The six librespot patches come out in WP-8**, when
Spotify is gated out; the `rodio` patch stays as long as `music`'s audio path keeps `symphonia-libopus`.

---

## 12. WP-1 gate: `music` does **not** cross-compile for Android, and it is `rquickjs-sys`

The gate in §1h asked whether the unmodified vendored `music` crate cross-compiles for
`aarch64-linux-android`, `armv7-linux-androideabi` and `x86_64-linux-android` with NDK r27c.
**It does not.** All three ABIs fail identically, in the same crate, with the same error.

```
error: couldn't read .../rquickjs-sys-0.12.2/src/bindings/aarch64-linux-android.rs: No such file or directory
  --> rquickjs-sys-0.12.2/src/lib.rs:19:1
   |  include!(concat!("bindings/", bindings_env!("TARGET"), ".rs"));
warning: rquickjs-sys@0.12.2: rquickjs probably doesn't ship bindings for platform
         `aarch64-linux-android(n/a)`. try the `bindgen` feature instead.
error: could not compile `rquickjs-sys` (lib) due to 1 previous error
```

The same message appears for `armv7-linux-androideabi` and `x86_64-linux-android`. Verified
by three consecutive runs on the WP-1 gate workflow (runs `44754714510`, `44755335634`,
`44755962367`), after two harness bugs of my own were fixed: the first run never reached
compilation (wrong working directory), and the second discarded the error text because
GitHub keeps only one annotation per title.

### What this changes about §1c's prediction

§1c named `aws-lc-sys` (C/CMake) as "the most likely Android build failure in the whole
graph". **That was wrong.** In the failing runs `aws-lc-rs` was *compiling* when the build
aborted (`Compiling aws-lc-rs v1.19.0`, `Compiling opusic-sys v0.7.7`), and `ring` produced
artifacts. Cargo stops the whole build on the first hard error, so `aws-lc-sys`,
`libsqlite3-sys` and `opusic-sys` never got to finish — meaning **their Android
buildability is still untested**, not that they failed. The one thing this gate does
establish is that the blocker is not C or CMake at all. It is a missing pregenerated
binding file in a Rust crate.

### Why `rquickjs-sys` is in the graph, and why it cannot simply be dropped

It arrives via `ytmusic 0.1.0 → rquickjs-core 0.12.2 → rquickjs-sys 0.12.2`. `ytmusic` uses
QuickJS to execute YouTube's player JavaScript when minting a PoToken. So unlike the
`librespot`, `opensubsonic` or `oauth2` dependencies:

**`rquickjs` is a YouTube dependency, and YouTube is in scope for Release 1.** Trimming
providers does not remove it.

Three ways forward, none of them free:

1. **Enable `rquickjs-sys`'s `bindgen` feature.** The crate's own warning recommends it. It
   generates bindings for the target instead of `include!`-ing a file that does not exist.
   Cost: needs `libclang` at build time in CI, adds a bindgen step per ABI, and ties the
   build to the exact QuickJS C sources — but it is the upstream-supported path and requires
   no source edit of ours.
2. **Pre-generate the three binding files** and patch them in. Small and deterministic, but
   it is a vendor patch of a file the crate expects to own, and it must be regenerated on
   every `rquickjs` bump.
3. **Replace `rquickjs` in `ytmusic`.** Either swap to a crate with Android bindings
   (`boa`, `deno_core`, or a `wasmtime`-hosted JS) or move PoToken minting to Kotlin, which
   §1e already argues for on other grounds because `webview` is a no-op on Android.

Recommended: **option 1 first**, because it is the crate's own suggested remedy and needs no
fork of `music` or `ytmusic`. If bindgen turns out to be painful for three ABIs, option 3 is
the strategic answer given §1e's conclusion that Kotlin should own PoToken minting anyway.

### Status of the gate

- **Answered:** unmodified `music` does not build for Android with the published
  `rquickjs-sys`; the blocker is its missing pregenerated bindings for all three targets.
- **Not started:** the trim. This gate deliberately built the untrimmed crate, so nothing in
  `rust/vendor/sonora` was edited and no provider was removed.
- **Answered in §12a:** whether `aws-lc-sys`, `libsqlite3-sys` and `opusic-sys` build for
  Android — yes, all three, once the build can get past `rquickjs`.

---

## 12a. WP-1 fix: `music` cross-compiles for all three ABIs

`music` now cross-compiles for `aarch64-linux-android`, `armv7-linux-androideabi` and
`x86_64-linux-android`, with the vendored crates unmodified apart from one new dependency
copy. Evidence: workflow run `44779829727` (`wp1-bindings`), where `abi-aarch64`,
`abi-x86_64` and `abi-armv7` are all PASS and the committed lockfile passes `--locked`
(`resolve::ok (cargo metadata --locked)`, `lock-stable::committed lockfile is already
complete`).

### What the fix is, and why not the recommended option 1

Option 1 above said "enable `rquickjs-sys`'s `bindgen` feature". **That is not possible from
this repository**, and each reason is a measurement, not an inference:

| Attempt | Result |
| --- | --- |
| `--features rquickjs-sys/bindgen` on `music` | Rejected: `error: the package 'music' does not contain this feature`. Cargo only accepts `dep/feature` for a dependency of the *selected* package, and `rquickjs-sys` is transitive (run `44762272947`). |
| `[patch.crates-io] rquickjs-sys = { path = … }` | Ignored: `rquickjs-core` declares `rquickjs-sys` as a *workspace* dependency, which cargo resolves against the workspace root; the build still failed on `bindings/aarch64-linux-android.rs` (run `44768718187`). |
| Editing the extracted crate's own `default = ["bindgen"]` | No effect: `default-features = false` on the `rquickjs-core` edge overrides the dependency's default (run `44762958918`). |
| `[patch]` applied and resolving cleanly, then building | Still no effect for the same reason, and what it "generated" was the 3-line stub the crate writes when the feature is off (run `44776546926`). |

So the fix does not switch the feature on — it removes the switch. `rquickjs-sys` 0.12.2 is
vendored at `rust/vendor/rquickjs-sys/` (`[patch.crates-io]` in `rust/Cargo.toml`), where
`bindgen-rs` is a non-optional build dependency, `build.rs` generates unconditionally, and
`src/lib.rs` includes only `$OUT_DIR/bindings.rs`. `VENDORED.md` next to it records the
provenance, the three patches, the licensing (MIT throughout) and when the copy can go away.

One more thing the published crate cannot supply: `quickjs/` is a **separate git repository**
in upstream's `.gitmodules`, so the published `.crate` ships it empty although `build.rs`
copies 18 files out of it. Those 18 files are materialised in the copy.

### The trap this avoided: `cargo-ndk` does not pass `--target` to bindgen

`cargo-ndk` exports `BINDGEN_EXTRA_CLANG_ARGS_<triple>` as `--sysroot=… -I…` with **no**
`--target=`, in every released version from 3.5.4 to 4.1.2; the fix (commit `ee7329a`) is on
`master` only. Without `--target`, clang binds the host layout, so a 64-bit runner would give
armv7 64-bit `size_t` and the 8-byte `JSValue` union while the C compiled 32-bit. The
generated layouts differ exactly as the ABI requires, which is why one shared file would have
been wrong (run `44779829727`, annotation `layouts`):

| ABI | `size_t` | `JSValue` |
| --- | --- | --- |
| `aarch64-linux-android` | `::core::ffi::c_ulong` | `#[repr(C)] struct { u: JSValueUnion, tag: i64 }` |
| `x86_64-linux-android` | `::core::ffi::c_ulong` | same as aarch64 |
| `armv7-linux-androideabi` | `::core::ffi::c_uint` | `u64` (NaN boxing, no union) |

Every Android build must therefore set `BINDGEN_EXTRA_CLANG_ARGS_<triple>` itself, which is
what `.github/workflows/wp1-bindings.yml` does and what the shipped Android build has to do
too — otherwise it compiles and reads the wrong fields on 32-bit.

### §1c's open question, answered

`aws-lc-sys`, `aws-lc-rs`, `libsqlite3-sys`, `opusic-sys` and `rquickjs-sys` **all produced
build artifacts** in run `44779829727` (`built` annotations). The earlier gate could not
answer this because cargo stops at the first hard error and `rquickjs-sys` failed first; the
`aws-lc-sys` failure predicted in §1c did not happen.

### Cost

| | Before | After |
| --- | --- | --- |
| New packages in `rust/Cargo.lock` | — | 7: `bindgen 0.72.1`, `cexpr 0.6.0`, `clang-sys 1.9.1`, `itertools 0.13.0`, `libloading 0.8.9`, `prettyplease 0.2.37`, `shlex 1.3.0` (561 packages total) |
| Repository size | — | ~4 MB, of which ~2.7 MB is `quickjs.c` and the headers `build.rs` compiles |
| Build time | — | one bindgen run per target; `libclang` must be present on any machine that builds Android, and `libclang-dev` is already installed in the gate and native workflows |

### What is still not done

- `main`'s "Android native" job still builds only `mobile-core`, which does not depend on
  `music`, so it is unaffected by any of this. Wiring `music` into the shipped core is a
  separate task and the place where `BINDGEN_EXTRA_CLANG_ARGS` has to be set for real.
- The trim landed separately, in §13, because proving the build and changing the source are
  two different claims.

---

## 13. WP-1 trim: the providers Release 1 does not ship are gone

Release 1 is Deezer, YouTube Music and local files (`docs/PLAN.md` §2). `PLAN.md` §3 lists
what to remove; this section records what was actually removed, and what deliberately was
not.

### Removed

| Path | LoC | Why |
| --- | --- | --- |
| `crates/music/src/spotify/` (28 files) | 9 853 | Spotify provider, its Connect device network and its librespot-backed playback |
| `crates/music/src/apple/` (9 files) | 6 455 | Apple Music; also the only user of the Widevine CDM |
| `crates/music/src/tidal/` (7 files) | 4 988 | Tidal |
| `crates/music/src/jellyfin/` (8 files) | 4 412 | Jellyfin |
| `crates/music/src/subsonic/` (6 files) | 4 281 | Subsonic, the only user of `opensubsonic` |
| `crates/music/src/soloist/` (8 files) | 2 863 | Spotify's headless player, and the librespot client it wraps |
| `crates/music/src/binimum/` (1 file) | 164 | Lossless-hosting scrapers, unreachable without the handlers that used them |
| `crates/music/src/connect.rs` | 332 | Spotify Connect's device model, used only through the `Provider::connect` hook |
| `crates/music/src/drm.rs` | 11 | The Widevine façade; a re-export module for the removed Apple provider |
| `crates/music/src/live_tests/` (6 of 8 files) | — | Tests that drove the removed providers; `playlist_ownership.rs` is provider-neutral and stays |

**33 359 LoC of provider code**, 49% of the 68 321 lines `crates/music/src` held at
`v0.50.2` (§1g). Recounted against the pristine clone for this table, not estimated. Verified by `scripts/check-scope.py`, which fails if any of those
directories, module declarations or dependency names come back; it is wired into the
`android-music` workflow.

Three hooks came off the `Provider` trait with them, rather than left as dead defaults that
would invite a provider back: `connect()` (Spotify Connect), `soloist()` (Soloist) and
`protected()` (Widevine). The one that stayed is `Normalisation::Limited`, because it
describes a loudness behaviour of the audio chain, not a provider.

### Dependencies removed

From `crates/music/Cargo.toml`: `librespot-core`, `librespot-playback`,
`librespot-protocol`, `gethostname`, `oauth2`, `opensubsonic`, `protobuf`,
`tokio-tungstenite`. From the workspace root: the same three `librespot-*` entries,
`oauth2`, `opensubsonic` and `gethostname`, plus **all six `librespot-*` `[patch.crates-io]`
entries**, which existed only for the Spotify engine. `rust/Cargo.toml`'s `[patch]` table is
now two entries: `rquickjs-sys` (§12a) and `rodio`.

### Kept on purpose

- **`widevine`** — it is a workspace member, and removing it from the workspace is not this
  task. Nothing in `music` uses it now: the feature is off by default and its only consumer
  was `apple/`. **The next cleanup should drop the crate from the workspace and the feature
  from `music` together**; they are one decision, not two.
- **`protobuf` and `tokio-tungstenite`** — removed here because only `librespot-*` pulled
  them, but they are general crates rather than provider code; the scope check reports them
  as a note instead of a failure so a later task can bring them back without a fight.
- **`Normalisation::Limited`, `Download::direct`, `Vault::foreign`** — the parts of the audio
  and storage model the removed providers *used*. They describe the design (`docs/AUDIT.md`
  §1f), and removing them would change behaviour of code that has nothing to do with
  providers.

### Stale comments corrected

Five comments in code that survives described the removed providers as live code paths
(`engine.rs` on which providers implement `Fetch`, `lib.rs` on Soloist and on
`Capabilities::ALL`, `sink.rs` on why output watching waits, `trouble.rs` on where its
error fragments come from, `vault.rs` on Apple's Widevine path and Spotify's files). They
now say what is true, and the two that describe removed behaviour say so explicitly with a
pointer here, so a reader does not think the feature is merely unused.

### What this does not claim

- **It has not been compiled yet.** The evidence will be the `android-music` run on this
  branch. Removing a provider can only fail in `music`'s own module graph, which the gate
  builds, so that run is the check — not a local one, since this environment has no
  toolchain.
- Nothing was changed under `crates/storage`, `crates/widevine` or `mobile-core`.
- The five providers are gone from the **build**, not from the plan: Track A (Jellyfin,
  Subsonic) and Track B (Tidal, Spotify, Apple) are `docs/PLAN.md`'s expansion tracks, and
  restoring one means restoring its directory, `lib.rs` line and dependencies from the
  pristine copy at `rust/vendor/UPSTREAM.md`'s commit.
