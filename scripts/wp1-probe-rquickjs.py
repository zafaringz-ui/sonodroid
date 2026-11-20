#!/usr/bin/env python3
"""Probe helper for WP-1: make the *cargo-extracted* rquickjs-sys build with bindgen.

Experiment scaffolding, not the fix, and not intended to stay in the tree. Nothing under
`rust/` is edited: this modifies the copy cargo extracted into
`$CARGO_HOME/registry/src/...` from crates.io, which is why no repository file and no
committed `Cargo.lock` entry is touched by it.

Why this shape and not `--features rquickjs-sys/bindgen`: cargo only accepts
`dep/feature` for a dependency of the *selected* package, and `rquickjs-sys` is a
transitive dependency (music -> ytmusic -> rquickjs -> rquickjs-core -> rquickjs-sys).
Measured, not assumed: run 44762272947 failed with

    error: the package 'music' does not contain this feature: rquickjs-sys/bindgen

So enabling the feature has to happen in the crate's own manifest, which is exactly what
the real fix does (via a `[patch.crates-io]` copy of the crate).

Two changes to the extracted copy:

1. `Cargo.toml`: `default = ["bindgen"]`, so the feature is on without a CLI flag.
2. `build.rs`: drop the host-vs-target clamp so an ABI build on a 64-bit runner really
   generates that ABI's bindings, and dump the result so the layouts can be compared.

Usage: wp1-probe-rquickjs.py <extracted crate directory>
"""
from __future__ import annotations

import pathlib
import sys

GUARD_OLD = 'if target == host && env::var("CARGO_FEATURE_UPDATE_BINDINGS").is_err() {'
GUARD_NEW = (
    'if target == host'
    ' && env::var("CARGO_FEATURE_UPDATE_BINDINGS").is_err()'
    ' && env::var("PROBE_HOST_BINDGEN").is_err() {'
)

ANCHOR = '    let bindings = builder.generate().expect("Unable to generate bindings");'

PROBE = ANCHOR + '''

    // PROBE ONLY (scripts/wp1-probe-rquickjs.py): dump the generated bindings and report
    // the shapes that decide whether one layout can serve every ABI.
    {
        let dump = std::env::var("PROBE_DUMP_DIR").unwrap_or_else(|_| "/tmp/probe".into());
        let _ = fs::create_dir_all(&dump);
        let text = bindings.to_string();
        let _ = fs::write(format!("{dump}/bindings-{target}.rs"), &text);
        for pat in [
            "pub type size_t",
            "define JS_NAN_BOXING 1",
            "pub union JSValueUnion",
            "pub type JSValue = ",
            "pub type FILE",
            "pub struct JSMallocFunctions",
        ] {
            let n = text.lines().filter(|l| l.contains(pat)).count();
            println!("PROBE_TARGET={target} PAT={pat:?} LINES={n}");
        }
        for l in text.lines() {
            if l.starts_with("pub type size_t")
                || l.contains("define JS_NAN_BOXING")
                || l.starts_with("pub type JSValue = ")
                || l.starts_with("pub type FILE")
            {
                println!("PROBE_TARGET={target} SHAPE={l}");
            }
        }
    }'''


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: wp1-probe-rquickjs.py <extracted crate directory>", file=sys.stderr)
        return 2

    crate = pathlib.Path(sys.argv[1])
    manifest = crate / "Cargo.toml"
    build_rs = crate / "build.rs"

    # 1. Turn the feature on by default. The crate has no `default` key, so add one.
    text = manifest.read_text()
    if 'default = ["bindgen"]' in text:
        print("manifest already defaults to bindgen")
    else:
        anchor = "[features]\nbindgen = [\"bindgen-rs\"]\n"
        if anchor not in text:
            print(f"[features] anchor not found in {manifest}", file=sys.stderr)
            return 1
        text = text.replace(anchor, anchor + 'default = ["bindgen"]\n', 1)
        manifest.write_text(text)
        print(f"patched {manifest}: default = [\"bindgen\"]")

    # 2. Clamp off + dump on.
    source = build_rs.read_text()
    if GUARD_OLD not in source:
        print(f"guard anchor not found in {build_rs}", file=sys.stderr)
        return 1
    source = source.replace(GUARD_OLD, GUARD_NEW, 1)

    if ANCHOR not in source:
        print(f"generate anchor not found in {build_rs}", file=sys.stderr)
        return 1
    source = source.replace(ANCHOR, PROBE, 1)
    build_rs.write_text(source)
    print(f"patched {build_rs}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
