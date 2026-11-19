# Upstream integration: first inspection

Inspected [Sonora v0.50.2](https://github.com/sonorahq/sonora/tree/v0.50.2),
commit `c0b501cf4c138e55670a390240fadb7577c3913a`. This is a source inspection,
not a build, dependency audit, security review, or proof of Android compatibility.
The upstream source is pinned as the `vendor/sonora` submodule; its dependencies have not yet been integrated into the build.

## What is reusable and what needs adaptation

- `crates/music/src/deezer/` implements a Deezer client, authentication, stream
  resolution and striped decryption. `DeezerClient::connect` validates an ARL and
  implements the upstream `MusicApi` trait. Model conversion and asynchronous
  error handling are needed at the mobile-core boundary. The provider's own sign-in
  flow also stores credentials, so it cannot simply be used unchanged on Android.
- `crates/music/src/youtube/` wraps the separately pinned
  [ytmusic-rs](https://github.com/sonorahq/ytmusic-rs) dependency (revision
  `99f7d14` in upstream's workspace manifest). It includes an API client, playback
  factory, session cookie rotation and a PoToken minter adapter. Its actual Android
  behavior and that dependency's transitive licenses still need review.
- `crates/music/src/engine.rs` implements fetching, decoding, buffering and playback
  coordination. Its sink and output paths depend on desktop-oriented Rodio/CPAL;
  it is not an Oboe engine. Separating the audio sink and proving gapless behavior
  on Android are substantial work, not a dependency switch.
- `crates/music/src/potoken.rs` is a browser-independent token handoff and cache.
  A real Android WebView attestation implementation must be designed and validated
  against the upstream protocol. The current prototype's JavaScript is a placeholder.
- The upstream `state` crate depends on GPUI, UI, WebView and desktop services;
  it should not be used unchanged for Android state management. The `music` crate
  is also monolithic: its manifest unconditionally includes other providers,
  CPAL/Rodio, storage and Widevine support, so importing it wholesale would bring
  substantial unrelated dependencies and may fail to compile for Android. No
  Android compatibility has been verified.

## Critical differences from the prototype

- Upstream declares Rust edition 2024 and pins toolchain `1.98.1` in its
  `rust-toolchain.toml`; this repository pins `1.85.0`. Do not assume the existing
  toolchain is sufficient. Check the actual compiler and dependency requirements
  before changing either pin.
- Upstream stores provider credentials as serialized files in private OS directories
  (`music/src/credentials.rs`, `deezer/auth.rs`, `youtube/mod.rs`); YouTube also
  persists rotating cookies. Owner-only permissions are not Android Keystore
  encryption. Integrating these paths unchanged would violate the mobile credential
  design. Inject secure storage, including cookie rotation and sign-out cleanup,
  before enabling real accounts.
- The prototype's quality ladder and plan mapping are fixtures, not evidence of
  actual service capabilities. Use upstream offers and real account responses,
  and verify them rather than presenting simulated tiers as verified facts.
- Upstream's root manifest declares GPL-3.0-or-later, and its `COPYING` contains
  the license. Review `THIRD-PARTY.md`, the upstream lockfile, and all transitive
  source and license obligations for the exact components ultimately shipped.

## Suggested next step

First establish reproducible Android and Rust builds for the existing scaffold and
fix its known compile errors; then make a narrowly scoped integration spike for the
Deezer catalogue client, with an injected credential store and no real credentials
or playback enabled until reviewed. Record the exact upstream revision and changes,
add model mapping and mocked tests, and check the transitive dependency and Android
build impact. Keep YouTube integration and Oboe output as separate subsequent steps.

No build or test was run for this inspection; see `VERIFICATION.md` for the current
prototype's outstanding verification and safety limitations.
