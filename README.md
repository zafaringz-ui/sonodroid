# Sonora for Android

An **unfinished, unverified prototype** of a Kotlin/Compose Android client and a Rust
mobile core, based on the [merged plan](docs/PLAN.md). This is not a working music
app or a release candidate. It has not been compiled, synced or run.

The default Android configuration uses an in-memory demo client and fictional data.
The Rust crate is a new domain prototype, **not the upstream Sonora core**. There is
no real Deezer or YouTube Music integration, authentication, audio decoder or Oboe
output. The UniFFI adapter is experimental and has not been checked against generated
bindings; the Rust-enabled build should not be assumed to compile. The PoToken code
is a placeholder and cannot be relied on to mint tokens. Quality values are mock
policy data, not verified service capabilities. No performance, accessibility,
security or compatibility acceptance criteria have been validated.

Real sign-in is disabled in the prototype: do not enter account credentials. No
YouTube downloads or Deezer offline implementation is provided.

## Layout

- `android/`: 12 Gradle modules: app shell, Compose features and design system,
  core bridge, secure storage, WebView and Media3 playback-service scaffolding.
- `rust/crates/mobile-core/`: Rust domain model, queue, playback-state clock,
  quality policy, fixtures and UniFFI declarations.
- `scripts/`: preliminary native build/binding scripts and structural checks.
- `docs/PLAN.md`, `docs/plans/`: the supplied plans; `docs/VERIFICATION.md` records
  outstanding work and checks.

## Getting started (requires verification)

Install JDK 17 and Android SDK 35. Initialize the pinned upstream source with
`git submodule update --init --recursive`. From `android/`, try
`./gradlew :app:assembleDebug` and fix compile errors before use. The Gradle 8.10.2
wrapper has been added but cannot be verified without a JDK and network access.
Rust development additionally needs Rust 1.85, Android NDK r27c and cargo-ndk;
`scripts/build-rust.sh` and `scripts/gen-bindings.sh` are untested starting points.
Do not rely on the experimental `-Psonora.rust=true` path until the adapter and
bindings are reconciled. CI configuration is also unverified.

`python3 scripts/check-resources.py` checks resource syntax and delimiter counts;
it is not a compiler or a functional test.

## License

GPL-3.0-or-later; see [LICENSE](LICENSE). The plan identifies upstream Sonora as
GPL-3.0; upstream source is pinned as a submodule; its dependencies have not yet been integrated or fully audited. Provider terms and
licensing need review before distribution.
