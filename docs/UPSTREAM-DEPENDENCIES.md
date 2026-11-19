# Upstream dependency inventory (Phase A)

Source: `vendor/sonora` at v0.50.2, commit
`c0b501cf4c138e55670a390240fadb7577c3913a`.
Upstream declares **GPL-3.0-or-later** in its root `Cargo.toml` and includes its
license in `vendor/sonora/COPYING`; consult `vendor/sonora/THIRD-PARTY.md` for
upstream's third-party notices. This is not a completed transitive license audit.

No upstream crates are currently linked by this project. The precise dependencies
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
See the exact pinned manifests and `vendor/sonora/Cargo.lock` for versions, feature
flags and transitive dependencies. No Android dependency resolution has been run.
