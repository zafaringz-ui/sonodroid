# Upstream dependency inventory (Phase A)

Source: `rust/vendor/sonora/crates/` (vendored from v0.50.2, commit
`c0b501cf4c138e55670a390240fadb7577c3913a`; see `rust/vendor/UPSTREAM.md`).
Upstream declares **GPL-3.0-or-later** in its root `Cargo.toml` and includes its
license in the repository's `COPYING`; consult upstream's `THIRD-PARTY.md` for
upstream's third-party notices. This is not a completed transitive license audit.
`rust/Cargo.lock` is now a real resolved graph for the vendored crates, so version
questions can be answered from it directly.

No upstream crates are currently linked into the app (the vendored crates build, but
`mobile-core` does not depend on `music`). The precise dependencies
we will actually ship cannot be determined until the provider/audio integration and
feature trimming are designed and built. If the upstream `music` crate were included
unchanged, its direct unconditional dependencies listed in `crates/music/Cargo.toml`
are:

`anyhow`, `ardftsrc`, `async-trait`, `base64`, `blowfish`, `bytes`, `cbc`, `fastrand`,
`sha1`, `flate2`, `cpal`, `futures`, `gethostname`, `deunicode`, `dirs`, `http`,
`image`, `jiff`, `librespot-core`, `librespot-playback`, `librespot-protocol`, `lofty`,
`kakasi`, `log`, `md-5`, `oauth2`, `open`, `opensubsonic`, `percent-encoding`,
`protobuf`, `reqwest`, `ring`, `roxmltree`, `rodio`, `rtrb`, `rusqlite`, `rusty_aac`,
`rustfft`, `serde`, `serde_json`, `serde-saphyr`, `storage` (workspace path),
`symphonia`, `tempfile`, `tokio`, `tokio-tungstenite`, `widevine` (workspace path),
and `ytmusic` (Git revision `99f7d14`). Unix additionally lists `libc`; Linux,
macOS and Windows add their own platform-specific dependencies. Upstream's
`widevine/cdm` feature is off by default, though its crate remains a dependency.
See the exact pinned manifests under `rust/vendor/sonora/crates/` and `rust/Cargo.lock`
for versions, feature flags and transitive dependencies; the lockfile is now a real
resolved graph, not an inference. No **Android** dependency resolution has been run —
`rust/Cargo.lock` is resolved for the host, and the first Android cross-compile of
`music` has not been attempted (see `docs/AUDIT.md` §11).
