# Status — Phase A in progress, gate not passed

## What works now (debug Deezer public mode)

- The debug build uses Deezer's public catalog endpoints for search, charts, artist top tracks, and album tracks. Signed out, playback uses public 30-second previews; optional Deezer WebView sign-in validates and encrypts the session credential with Android Keystore, then enables authenticated full-track playback with Sonora's BF_CBC_STRIPE decryption in memory. The debug badge reads `Deezer public catalog`.
- Library, playlists, downloads, and lyrics remain unavailable in this Android client. Missing optional public track fields default to empty artists, null album/artwork/preview, zero duration, and non-explicit; absent album artist is “Unknown artist”, with year/follower count unknown.
- **User-verified on device:** the user installed `sonora-debug-apk`, signed in to a Deezer Free account, and confirmed playback of a full song. **Verified in CI:** run [44846090921](https://github.com/zafaringz-ui/sonodroid/actions/runs/44846090921) passed Android assembly/tests, Rust tests/Clippy, and the Android native build at commit `154fc2d`. Session restore after restart, sign-out credential removal, seeking/background playback, and other account/track error cases are not yet device-verified.

Categories: **verified** means the indicated command was actually run with the
recorded result; **implemented but untested** means source/configuration exists but
its behavior has not been validated; **stubbed/simulated** means it is not a real
implementation. A structural check is not a build or a test.

## Phase A

- **Implemented, awaiting review — core selection is explicit:** `AppGraph` no
  longer swallows a failed native-core load with `runCatching { }.getOrNull()`.
  `CoreFactory` logs the exception class and message via `Log.e` and records a
  `FallbackReason` (`FLAG_OFF`, `CLASS_MISSING`, `FACTORY_INCOMPATIBLE`,
  `NATIVE_LOAD_FAILED`, `CORE_INIT_FAILED`). Debug builds show a persistent badge
  reading "Demo core", "Rust core (fixtures)" or "Rust core (live)", plus the reason
  when the app fell back. The decision table lives in `CoreSelectionPolicy` and is
  covered by `android/app/src/test/.../CoreSelectionPolicyTest.kt`. Note that a
  successful native load still serves fixtures until the provider clients exist:
  `api.rs` `require_online()` returns `NotImplemented` unless `use_fixtures` is set,
  so a correctly wired bridge will read "Rust core (fixtures)".

- **Implemented but untested — reported errors from CI commit `a7b3f32`:** the
  ACTION_VIEW caller now converts its `Uri` to a string, and the `with_foreign`
  UniFFI traits now use `Arc<dyn Trait>` arguments as in the upstream UniFFI 0.28.3
  async trait fixture. No compiler has verified these fixes. CI now preserves its
  generated `Cargo.lock` as an artifact; the lockfile is not yet committed because
  this environment cannot run Cargo or fetch Actions artifacts.

- **Verified — CI formatting check on commit `779c568`:**
  `gh run view 44602340109 --json jobs --jq '.jobs[] | {name,steps: [.steps[] | {name,conclusion}]}'`
  reported Rust core `Formatting` as `success`. Rust unit tests and Android assembly
  failed in that run; no details are available in this sandbox.
- **Implemented but untested — Media3/Compose follow-up:** compared player builder
  and handler signatures against AndroidX Media3 1.5.0 source and scanned scope
  modifier imports and usages. Empty Media3 playlists now use `C.INDEX_UNSET`;
  Material 3 experimental sheet and tab calls opt in. Full Compose API compatibility
  and runtime behavior have not been verified.

This sandbox has no Java, Android SDK, Rust or emulator. CI is the only available
remote build verification here; emulator and native ABI verification still require
appropriate tooling. GitHub Actions run 44599179100 failed Android assembly at
`SonoraPlayer.kt` (`setPosition`) and `Artwork.kt` (`matchParentSize` import),
according to the user-provided log. The same run's Rust formatting step failed;
the user supplied its rustfmt diff from that earlier run. The shown formatting edits
have been applied manually; `cargo fmt` and the formatting check were not run locally.
The targeted Kotlin changes and CI logging changes are **implemented but untested**.
The later CI run 44601275727 reported failures in Android assembly/tests and Rust
formatting/tests/Clippy; its detailed error logs have not been supplied.

- **Verified — upstream source:** `music`, `storage` and `widevine` are vendored in
  `rust/vendor/sonora/crates/`, byte-identical to upstream `v0.50.2`
  (`c0b501cf4c138e55670a390240fadb7577c3913a`); see `rust/vendor/UPSTREAM.md`. They are
  workspace members and `cargo check -p music --locked` passes on host Linux, but
  **they are not linked into the app**: `mobile-core` does not depend on `music`, so the
  shipped `.so` contains none of it yet. Upstream declares GPL-3.0-or-later. The
  `vendor/sonora` submodule was removed once vendoring landed (see `docs/AUDIT.md` §0
  and §11).
- **Verified — structural check only:** `python3 scripts/check-resources.py` printed
  `Resources and brace balance look fine.` This does not check Kotlin/Rust compilation.
- **Implemented but untested — Gradle wrapper:** official Gradle v8.10.2 wrapper
  files were retrieved from GitHub. `sha256sum android/gradle/wrapper/gradle-wrapper.jar`
  printed `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`.
  The distribution download checksum has not been independently verified or pinned.
- **Implemented but untested — Android project and tests:** running
  `cd android && ./gradlew :app:assembleDebug :core-bridge:testDebugUnitTest --stacktrace`
  failed: `JAVA_HOME is not set and no 'java' command could be found in your PATH.`
  No Gradle build or unit test ran.
- **Implemented but untested — Rust core and tests:** running
  `cd rust && cargo test --workspace --all-targets` failed: `cargo: command not found`.
- **Verified in CI — Android ABI builds:** the `Android native` job builds all three
  ABIs (arm64-v8a, armeabi-v7a, x86_64) with NDK r27c and cargo-ndk 3.5.4. This
  sandbox has no cargo, so the local build has still not been run here.
- **Verified in CI — UniFFI adapter:** the bindings are generated in library mode and
  the adapter compiles against them, including a check that its expected
  declarations exist. Building the `.so` requires `CARGO_PROFILE_RELEASE_STRIP=none`
  and `cargo ndk --no-strip`, or library mode finds zero components and exits 0
  without writing anything; `scripts/build-rust.sh` and `scripts/gen-bindings.sh`
  now do this and fail loudly instead.
- **Not verified anywhere — loading on a device:** no emulator or device has run the
  Rust-backed build, so the `.so` load itself (and JNA's dispatch to it) is unproven.
  `adb devices` also fails here with `adb: command not found`, so no launch has been
  attempted.
- **Stubbed/simulated — demo mode:** catalogue, playback clock, quality policy and
  demo accounts are fixtures, not live provider integration or audio output.
- **Stubbed/simulated — PoToken, provider auth and audio sink:** no production
  provider authentication, token minting or Oboe playback exists.

- **Verified — the vendored `music` crate cross-compiles for all three Android
  ABIs:** workflow run `44779829727` reports PASS for `aarch64-linux-android`,
  `armv7-linux-androideabi` and `x86_64-linux-android`, with `aws-lc-sys`,
  `libsqlite3-sys` and `opusic-sys` all producing artifacts, and the committed
  `rust/Cargo.lock` accepted under `--locked`. The blocker was `rquickjs-sys` shipping no
  Android bindings; it is fixed by a vendored copy at `rust/vendor/rquickjs-sys/` (see its
  `VENDORED.md` and `docs/AUDIT.md` §12a). This proves compilation only — nothing here has
  been shipped or run on a device with `music` linked in.

## Local commands to unblock Phase A

Use JDK 17, Android SDK 35, an Android NDK (r27c), Rust 1.85, `cargo-ndk` 3.x,
and an available emulator. In a fresh clone first run:

```sh
cd android
./gradlew :app:assembleDebug testDebugUnitTest --stacktrace
cd ../rust
cargo test --workspace --all-targets
cd ..
./scripts/build-rust.sh    # sets CARGO_PROFILE_RELEASE_STRIP=none and --no-strip
./scripts/gen-bindings.sh  # generates from target/, fails if nothing is written
cd android
./gradlew :app:assembleDebug testDebugUnitTest -Psonora.rust=true --stacktrace
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n dev.sonora.android.debug/dev.sonora.app.MainActivity
```

Run in order and stop on the first failure; paste its complete output. The commands
are instructions, not claims that they work. The Rust-enabled build is expected to
need adapter corrections after binding generation. The gate additionally requires
all tests to pass and an actual emulator launch to be verified.

## Later phases

- **Stubbed/simulated — Phase B:** real upstream providers are not connected.
- **Stubbed/simulated — Phase C:** risk spikes have not been run.
- **Stubbed/simulated — Phase D onward:** not started; ask before each phase.
