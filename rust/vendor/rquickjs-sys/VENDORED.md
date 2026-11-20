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
repository. Four attempts, each measured:

1. `--features rquickjs-sys/bindgen` is rejected. Cargo only accepts `dep/feature` for a
   dependency of the *selected* package, and `rquickjs-sys` is transitive:
   `error: the package 'music' does not contain this feature: rquickjs-sys/bindgen`
   (run `44762272947`).
2. `[patch.crates-io]` does not reach it either: `rquickjs-core` declares `rquickjs-sys` as
   a *workspace* dependency (`rquickjs-sys = { workspace = true }`, `rquickjs-sys = { version
   = "0.12.2", default-features = false }`), which cargo resolves against the workspace
   root, and `[patch]` does not apply to workspace dependencies (run `44768718187`; the
   build still failed on the missing `bindings/aarch64-linux-android.rs`).
3. **Even a `[patch]` that did apply cannot switch the feature on.** `default-features =
   false` on the `rquickjs-core` edge overrides any `default` set in this manifest. Measured
   with the patch applied and resolving fine (run `44776546926`): the build still failed on
   the same missing file, and the `bindings.rs` bindgen would have written was the 3-line
   stub the crate writes when the feature is off.
4. Editing the *published* `rquickjs-sys` manifest (its own `default`) does nothing, for the
   same reason as 3: the dependent's `default-features = false` wins (run `44762958918`).

So the feature switch is removed rather than enabled:

| File | Change |
| --- | --- |
| `Cargo.toml` | `bindgen-rs` is a non-optional build dependency; `bindgen`/`default` remain as **no-op** features so a manifest asking for them still resolves. |
| `build.rs` | Generation is unconditional: the `#[cfg]` split between the stub and the bindgen implementation is gone, and so is the host shortcut that copied a committed file instead of generating one. |
| `src/lib.rs` | Only `include!(concat!(env!("OUT_DIR"), "/bindings.rs"))`. The `#[cfg(not(feature = "bindgen"))]` include of the committed file is gone, which is the `couldn't read .../bindings/<triple>.rs` failure. |
| `build.rs` (added) | Dumps the generated bindings to `$RQUICKJS_SYS_DUMP_BINDINGS` when that is set, and prints the layout lines, so CI can capture and compare them. |
| `quickjs/` | The 18 files `build.rs` copies, materialised, because the published crate cannot supply them — see below. |

Everything else is byte-for-byte the published crate. `src/bindings/*.rs` is still there,
unchanged, but nothing includes it any more — it is kept so the patch is smaller and so a
future upstream fix is easy to diff against.

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
quickjs files at the submodule commit the new tag records, re-apply the three patches in
the table above, and update the versions in this file.

## Android caveat: bindgen needs a target

`cargo-ndk` exports `BINDGEN_EXTRA_CLANG_ARGS_<triple>` as `--sysroot=... -I...` with
**no** `--target=`, in every released version from 3.5.4 through 4.1.2 (the fix, commit
`ee7329a`, is on `master` only). Without `--target`, clang defaults to the *host* layout,
so bindgen silently emits 64-bit `size_t` and the 64-bit `JSValue` union for a 32-bit ABI
while the C code compiles 32-bit. Any Android build with this copy must set
`BINDGEN_EXTRA_CLANG_ARGS_<triple>` itself, exactly as
`.github/workflows/wp1-bindings.yml` does.
