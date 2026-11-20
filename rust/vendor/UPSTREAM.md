# Vendored upstream source

This directory holds a pristine copy of three crates from Sonora upstream, so that
`rust/` can build them as workspace members without depending on a network fetch or
on the `vendor/sonora` submodule being initialised.

| Field | Value |
| --- | --- |
| Repository | https://github.com/sonorahq/sonora |
| Tag | `v0.50.2` |
| Commit | `c0b501cf4c138e55670a390240fadb7577c3913a` |
| Copied paths | `crates/music` → `rust/vendor/sonora/crates/music`<br>`crates/storage` → `rust/vendor/sonora/crates/storage`<br>`crates/widevine` → `rust/vendor/sonora/crates/widevine` |

Local modifications are the commits after the pristine-copy commit.

## Why these three crates

`music` is the whole non-UI core (providers, engine, stream, decoder glue, vault, EQ,
lyrics, scrobbling). It is the crate Release 1 builds on. `music` has exactly two
in-repo dependencies that are not themselves vendored crates:

- `storage` (`crates/storage`) — cache and database handles; 2 source files.
- `widevine` (`crates/widevine`) — CDM parsing shim; its C++ host is behind the
  non-default `cdm` feature, so it needs no C++ compiler here.

Both are self-contained: neither depends on another upstream crate, and neither
references `gpui`.

## How this copy was made

```sh
git clone --branch v0.50.2 --depth 1 https://github.com/sonorahq/sonora.git
cp -a crates/music crates/storage crates/widevine <repo>/rust/vendor/sonora/crates/
```

No file was edited. `diff -r` against the clone reports no differences for all three
crates, including every `LICENSE` under `crates/widevine/shim/`.

## Licensing

Upstream is GPL-3.0-or-later. The repository root `LICENSE` is GPL-3.0. This copy is
corresponding source for the binaries we distribute; see `docs/AUDIT.md` §1a for the
obligations that come with it. Nothing here is relicensed, and the copied trees keep
their own license files and source headers.

## Relationship to the `vendor/sonora` submodule

`vendor/sonora` remains, untouched, as a provenance pointer at the same commit. This
directory is the copy that is actually compiled. Whether the submodule is retained is
a separate decision.
