# rquickjs-sys 0.12.2, patched for Android

`rquickjs` reaches `music` through the YouTube provider:

```
music -> ytmusic 0.1.0 -> rquickjs 0.12.2 -> rquickjs-core 0.12.2 -> rquickjs-sys 0.12.2
```

`ytmusic` uses QuickJS to run YouTube's player JavaScript when minting a PoToken, so this
is not a dependency the provider trim can remove — YouTube is in Release 1 scope. See
`docs/AUDIT.md` §12.

## Why this copy exists

`rquickjs-sys` 0.12.2 ships pre-generated bindings for 16 targets and **none of them is
Android**. `src/lib.rs` does:

```rust
include!(concat!(env!("OUT_DIR"), "/bindings.rs"));
#[cfg(not(feature = "bindgen"))]
include!(concat!("bindings/", bindings_env!("TARGET"), ".rs"));
```

so any Android build dies at compile time with

```
error: couldn't read .../src/bindings/aarch64-linux-android.rs: No such file or directory
```

The crate's own remedy is its `bindgen` feature: generate the bindings at build time
instead of including a file that does not exist. That cannot be switched on from this
repository, for two reasons, both measured rather than assumed:

1. `--features rquickjs-sys/bindgen` is rejected. Cargo only accepts `dep/feature` for a
   dependency of the *selected* package, and `rquickjs-sys` is transitive:
   `error: the package 'music' does not contain this feature: rquickjs-sys/bindgen`
   (run `44762272947`).
2. `[patch.crates-io] rquickjs-sys = { path = ... }` does not reach the crate either:
   `rquickjs-core` declares `rquickjs-sys` as a *workspace* dependency
   (`rquickjs-sys = { workspace = true }`, `rquickjs-sys = { version = "0.12.2",
   default-features = false }`), which cargo resolves against the workspace root, and
   `[patch]` does not apply to workspace dependencies (run `44768718187`; the build still
   failed with the same missing `bindings/aarch64-linux-android.rs`).

Patching the *published manifest* by hand is therefore the only way to turn the feature on,
and a patched published manifest is what a vendored copy is. Hence here, rather than a
`[patch.crates-io]` entry.

## What was changed, exactly

Everything else is byte-for-byte the published crate.

| File | Change |
| --- | --- |
| `Cargo.toml` | `default = ["bindgen"]` added under `[features]` (the crate has no `default` key of its own). |
| `quickjs/` | The upstream crate declares this directory as a git submodule (`.gitmodules`: `path = sys/quickjs`, `url = https://github.com/quickjs-ng/quickjs.git`, commit `fd0a0210b7be00957751871e7e01b8291268fc29`). It is a **separate repository**, so `cargo package` leaves it empty and the published `.crate` contains no C sources. `build.rs` copies 18 files out of it, so this copy carries them: `cargo fetch` cannot materialise them. Only the 18 files `build.rs` names, plus upstream's `LICENSE`, are present — not the other ~4 MB of the quickjs-ng tree. |
| `src/bindings/*.rs` | Kept, unchanged. For host builds `build.rs` reuses the bundled file for the host triple, so a host build never needs libclang. |

`quickjs` is quickjs-ng 0.15.1 (`Set version to 0.15.1`), MIT licensed, `LICENSE`
included here.

## Licensing

`rquickjs-sys` and `rquickjs` are MIT (this `LICENSE`, copyright 2020 Mees Delzenne);
quickjs-ng is MIT (`quickjs/LICENSE`). Both are compatible with this repository's
GPL-3.0-or-later and their notices travel with the sources, which is what MIT requires of
a redistribution. This copy is part of the corresponding source we publish (see
`docs/AUDIT.md` §1a).

## When this can go away

- `rquickjs` starts shipping Android bindings, or `rquickjs-core` gains a public
  `bindgen` feature that can be enabled from a parent crate, or
- PoToken minting moves to Kotlin (`docs/AUDIT.md` §1e/§12 option 3), which drops
  `rquickjs` from the graph entirely, or
- `cargo-ndk` is replaced by something that passes `--target` to bindgen **and** the
  feature can be enabled — see the next section.

Rebuild checklist on a `rquickjs` bump: re-copy `sys/` from the new tag, re-copy the 18
quickjs files at the submodule commit the new tag records, re-apply the one-line
`default = ["bindgen"]`, and update the versions in this file.

## Android caveat: bindgen needs a target

`cargo-ndk` exports `BINDGEN_EXTRA_CLANG_ARGS_<triple>` as `--sysroot=... -I...` with
**no** `--target=`, in every released version from 3.5.4 through 4.1.2 (the fix, commit
`ee7329a`, is on `master` only). Without `--target`, clang defaults to the *host* layout,
so bindgen silently emits 64-bit `size_t` and the 64-bit `JSValue` union for a 32-bit ABI
while the C code compiles 32-bit. Any Android build with this copy must set
`BINDGEN_EXTRA_CLANG_ARGS_<triple>` itself, exactly as
`.github/workflows/wp1-bindings.yml` does.
