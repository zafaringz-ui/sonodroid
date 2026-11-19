# Status — Phase A in progress, gate not passed

Categories: **verified** means the indicated command was actually run with the
recorded result; **implemented but untested** means source/configuration exists but
its behavior has not been validated; **stubbed/simulated** means it is not a real
implementation. A structural check is not a build or a test.

## Phase A

- **Verified — upstream pin:** `git -C vendor/sonora describe --tags --exact-match`
  printed `v0.50.2`; `git submodule status` printed commit
  `c0b501cf4c138e55670a390240fadb7577c3913a` at `vendor/sonora`. Upstream declares
  GPL-3.0-or-later; see `docs/UPSTREAM-DEPENDENCIES.md` for the direct dependency
  inventory and audit limitations. It is not linked to the app.
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
- **Implemented but untested — Android ABI builds:** running
  `cd rust && cargo ndk --target arm64-v8a --target armeabi-v7a --target x86_64 --output-dir ../android/core-bridge/src/main/jniLibs build --release -p mobile-core`
  failed: `cargo: command not found`. No ABI was built.
- **Implemented but untested — UniFFI adapter:** `./scripts/gen-bindings.sh` failed
  because `android/core-bridge/src/main/jniLibs/arm64-v8a/libsonora_core.so` does not
  exist. Bindings have not been generated and the adapter has not been reconciled.
- **Implemented but untested — emulator launch:** `adb devices` failed with
  `adb: command not found`. No emulator launch has been attempted.
- **Stubbed/simulated — demo mode:** catalogue, playback clock, quality policy and
  demo accounts are fixtures, not live provider integration or audio output.
- **Stubbed/simulated — PoToken, provider auth and audio sink:** no production
  provider authentication, token minting or Oboe playback exists.

## Local commands to unblock Phase A

Use JDK 17, Android SDK 35, an Android NDK (r27c), Rust 1.85, `cargo-ndk` 3.x,
and an available emulator. In a fresh clone first run:

```sh
git submodule update --init --recursive
cd android
./gradlew :app:assembleDebug testDebugUnitTest --stacktrace
cd ../rust
cargo test --workspace --all-targets
cargo ndk --target arm64-v8a --target armeabi-v7a --target x86_64 --output-dir ../android/core-bridge/src/main/jniLibs build --release -p mobile-core
cd ..
./scripts/gen-bindings.sh
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
