#!/usr/bin/env bash
# Cross-compile the Rust core for the three Android ABIs and drop the shared
# objects into the Gradle module that packages them.
#
# Requirements: rustup (toolchain from rust/rust-toolchain.toml), the Android NDK
# (r27c recommended) exported as ANDROID_NDK_HOME, and cargo-ndk 3.x.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
rust_dir="$repo_root/rust"
jni_dir="$repo_root/android/core-bridge/src/jniLibs"

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  echo "ANDROID_NDK_HOME is not set; point it at the NDK (e.g. \$ANDROID_SDK_ROOT/ndk/27.2.12479018)" >&2
  exit 1
fi

# Pinned: 3.5.5-3.5.7 are yanked on crates.io, so 3.5.4 is the newest installable
# release. CI installs the same version.
cargo_ndk_version="3.5.4"
if ! command -v cargo-ndk >/dev/null 2>&1; then
  echo "cargo-ndk missing, installing ${cargo_ndk_version}…"
  cargo install cargo-ndk --version "$cargo_ndk_version" --locked
fi

cd "$rust_dir"

# cargo-ndk 3.x requires the Rust targets to be installed up front.
rustup target add \
  aarch64-linux-android \
  armv7-linux-androideabi \
  x86_64-linux-android

# UniFFI's library-mode metadata lives in the ELF static symbol table, and two
# layers would delete it: `strip = "symbols"` in [profile.release] (rustc strips
# the link) and cargo-ndk's own post-copy strip. Both must be disabled or
# gen-bindings.sh finds zero components in the output. CI does the same.
export CARGO_PROFILE_RELEASE_STRIP=none

cargo ndk \
  --target arm64-v8a \
  --target armeabi-v7a \
  --target x86_64 \
  --no-strip \
  --output-dir "$jni_dir" \
  build --release --locked -p mobile-core

echo
echo "Shared objects:"
find "$jni_dir" -name 'libsonora_core.so' -print

# Fail here rather than at bindgen time if the metadata did not survive.
for abi in arm64-v8a armeabi-v7a x86_64; do
  so="$jni_dir/$abi/libsonora_core.so"
  if [[ ! -f "$so" ]]; then
    echo "Missing $so" >&2
    exit 1
  fi
  if ! command -v readelf >/dev/null 2>&1; then
    continue
  fi
  if ! readelf -sW "$so" | grep -qi 'UNIFFI_META_SONORA_CORE'; then
    echo "$so has no uniffi metadata symbols; bindgen library mode would produce nothing" >&2
    exit 1
  fi
done

echo
echo "Metadata symbols present in all three libraries; now run scripts/gen-bindings.sh."
