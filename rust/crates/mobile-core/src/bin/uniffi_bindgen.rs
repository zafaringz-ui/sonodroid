//! Generates Kotlin (and Swift) bindings from the compiled library metadata.
//!
//! Usage (from the repository root):
//!   cargo run -p mobile-core --features cli --bin uniffi-bindgen -- \
//!       generate --library target/aarch64-linux-android/release/libsonora_core.so \
//!       --language kotlin --out-dir ../android/core-bridge/src/generated/kotlin
fn main() {
    uniffi::uniffi_bindgen_main();
}
