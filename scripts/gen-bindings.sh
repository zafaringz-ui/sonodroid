#!/usr/bin/env bash
# Generate the Kotlin bindings from the compiled Rust library.
#
# Run scripts/build-rust.sh first: UniFFI reads the metadata out of the .so, which
# keeps the generated interface in lockstep with the code that is actually shipped.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root/rust"

# Default to cargo's own output rather than the jniLibs copy: cargo-ndk may
# post-process the copy it writes, and UniFFI needs the ELF static symbol table
# that stripping removes. scripts/build-rust.sh keeps target/ unstripped.
library="${1:-$repo_root/rust/target/aarch64-linux-android/release/libsonora_core.so}"
out_dir="$repo_root/android/core-bridge/src/generated"

if [[ ! -f "$library" ]]; then
  echo "No library at $library — run scripts/build-rust.sh first." >&2
  exit 1
fi

# uniffi_bindgen's dependency tree (textwrap 0.16.4) needs rustc 1.88, while the
# library itself is pinned to 1.85.0 by rust-toolchain.toml. Bindgen is a host-side
# tool that never ships, so run it with the stable toolchain, exactly as CI does.
# Set SONORA_BINDGEN_TOOLCHAIN= (empty) to use the default toolchain instead.
bindgen_toolchain="${SONORA_BINDGEN_TOOLCHAIN-stable}"
toolchain_flag=()
if [[ -n "$bindgen_toolchain" ]]; then
  toolchain_flag=("+$bindgen_toolchain")
fi

mkdir -p "$out_dir"
# `|| true` is deliberately absent: a failure must abort the script.
cargo "${toolchain_flag[@]}" run -p mobile-core --features cli --bin uniffi-bindgen -- \
  generate --library "$library" --language kotlin --config "$repo_root/rust/crates/mobile-core/uniffi.toml" --out-dir "$out_dir"

# Library mode exits 0 even when it finds no components and writes nothing, so
# check for the generated files instead of trusting the exit code.
generated=$(find "$out_dir/dev/sonora/core/ffi" -name '*.kt' -print 2>/dev/null | head -n 1)
if [[ -z "$generated" ]]; then
  echo "uniffi-bindgen produced no Kotlin under $out_dir/dev/sonora/core/ffi." >&2
  echo "The library probably has no uniffi metadata — rebuild with scripts/build-rust.sh." >&2
  exit 1
fi

echo
echo "Bindings written to $out_dir."
echo "Build the app with: ./gradlew :app:assembleDebug -Psonora.rust=true"
