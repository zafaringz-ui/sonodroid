#!/usr/bin/env bash
# Cross-compile the Rust core for the three Android ABIs and drop the shared
# objects into the Gradle module that packages them.
#
# Requirements: rustup (toolchain from rust/rust-toolchain.toml), the Android NDK
# (r27c recommended) exported as ANDROID_NDK_HOME, and cargo-ndk 3.x.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
rust_dir="$repo_root/rust"
jni_dir="$repo_root/android/core-bridge/src/main/jniLibs"

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  echo "ANDROID_NDK_HOME is not set; point it at the NDK (e.g. \$ANDROID_SDK_ROOT/ndk/27.2.12479018)" >&2
  exit 1
fi

if ! command -v cargo-ndk >/dev/null 2>&1; then
  echo "cargo-ndk missing, installing…"
  cargo install cargo-ndk --version '^3' --locked
fi

cd "$rust_dir"

# cargo-ndk 3.x requires the Rust targets to be installed up front.
rustup target add \
  aarch64-linux-android \
  armv7-linux-androideabi \
  x86_64-linux-android

cargo ndk \
  --target arm64-v8a \
  --target armeabi-v7a \
  --target x86_64 \
  --output-dir "$jni_dir" \
  build --release -p mobile-core

echo
echo "Shared objects:"
find "$jni_dir" -name 'libsonora_core.so' -print
