#!/usr/bin/env bash
# Generate the Kotlin bindings from the compiled Rust library.
#
# Run scripts/build-rust.sh first: UniFFI reads the metadata out of the .so, which
# keeps the generated interface in lockstep with the code that is actually shipped.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root/rust"

library="${1:-$repo_root/android/core-bridge/src/main/jniLibs/arm64-v8a/libsonora_core.so}"
out_dir="$repo_root/android/core-bridge/src/generated"

if [[ ! -f "$library" ]]; then
  echo "No library at $library — run scripts/build-rust.sh first." >&2
  exit 1
fi

mkdir -p "$out_dir"
cargo run -p mobile-core --features cli --bin uniffi-bindgen -- \
  generate --library "$library" --language kotlin --config "$repo_root/rust/crates/mobile-core/uniffi.toml" --out-dir "$out_dir"

echo
echo "Bindings written to $out_dir."
echo "Build the app with: ./gradlew :app:assembleDebug -Psonora.rust=true"
