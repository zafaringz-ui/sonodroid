#!/usr/bin/env python3
"""Probe helper: patch a *cargo-extracted* rquickjs-sys build.rs in the runner's registry.

This is experiment scaffolding for WP-1, not part of the fix and not intended to stay in
the tree. It edits nothing under `rust/`; it modifies the copy cargo extracted into
`$CARGO_HOME/registry/src/...` from crates.io, so the repository and `Cargo.lock` are
untouched.

It makes two changes, both needed to learn what the real fix has to do:

1. Disable the crate's host-vs-target clamp (it reuses bundled host bindings when
   `TARGET == HOST`), so a Linux host run generates a reference set to compare against.
2. After generating, dump the bindings and print the shapes that decide ABI safety:
   `size_t`, `JS_NAN_BOXING`, `JSValue`, `FILE`, and the malloc function table.

Usage: wp1-probe-rquickjs.py <path to extracted build.rs>
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

    // PROBE ONLY (see scripts/wp1-probe-rquickjs.py): dump the generated bindings and
    // report the shapes that decide whether one layout can serve every ABI.
    {
        let dump = std::env::var("PROBE_DUMP_DIR").unwrap_or_else(|_| "/tmp/probe".into());
        let _ = fs::create_dir_all(&dump);
        let text = bindings.to_string();
        let _ = fs::write(format!("{dump}/bindings-{target}.rs"), &text);
        for pat in [
            "pub type size_t",
            "JS_NAN_BOXING",
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
        print("usage: wp1-probe-rquickjs.py <build.rs>", file=sys.stderr)
        return 2

    path = pathlib.Path(sys.argv[1])
    source = path.read_text()

    if GUARD_OLD not in source:
        print(f"guard anchor not found in {path}", file=sys.stderr)
        return 1
    source = source.replace(GUARD_OLD, GUARD_NEW, 1)

    if ANCHOR not in source:
        print(f"generate anchor not found in {path}", file=sys.stderr)
        return 1
    source = source.replace(ANCHOR, PROBE, 1)

    path.write_text(source)
    print(f"patched {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
