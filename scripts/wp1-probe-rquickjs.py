#!/usr/bin/env python3
"""Patch cargo-extracted crates in the runner so rquickjs binds Android correctly.

Two jobs, both on throwaway copies under `$CARGO_HOME/registry/src/`, never on `rust/`:

`enable-bindgen <extracted rquickjs-core dir>`
    Turn on `rquickjs-sys`'s `bindgen` feature. It cannot be switched on from our side:
    the dependency chain is music -> ytmusic -> rquickjs -> rquickjs-core -> rquickjs-sys,
    `rquickjs-core` declares `rquickjs-sys` with `default-features = false`, and cargo
    rejects `--features rquickjs-sys/bindgen` on `music` outright
    ("the package 'music' does not contain this feature", run 44762272947). So the
    feature is added to the dependency edge in `rquickjs-core`'s own manifest, which is
    the edge that owns the decision.

`instrument-sys <extracted rquickjs-sys dir>`
    In `build.rs`: disable the host-vs-target clamp (so an ABI build on a 64-bit runner
    really generates that ABI's bindings) and dump the generated bindings, so the Android
    layouts can be captured and compared.

Both print what they changed, because the output is read back from annotations.
"""
from __future__ import annotations

import pathlib
import re
import sys

# ---------------------------------------------------------------- rquickjs-core

DEP_LINE = re.compile(r"^rquickjs-sys = (.*)$", re.MULTILINE)


def enable_bindgen(crate: pathlib.Path) -> int:
    manifest = crate / "Cargo.toml"
    text = manifest.read_text()

    match = DEP_LINE.search(text)
    if match is None:
        print(f"no 'rquickjs-sys =' dependency line in {manifest}", file=sys.stderr)
        return 1

    value = match.group(1)
    print(f"core dep before: rquickjs-sys = {value}")

    if "features" in value:
        print("core dep already sets features; leaving it alone", file=sys.stderr)
        return 1

    if value.startswith("{"):
        new_value = value.replace("{", '{ features = ["bindgen"],', 1)
    else:
        # A bare version string, e.g. rquickjs-sys = "0.12.2".
        version = value.strip().strip('"')
        new_value = f'{{ version = "{version}", features = ["bindgen"] }}'

    text = text[: match.start(1)] + new_value + text[match.end(1) :]
    manifest.write_text(text)
    print(f"core dep after:  rquickjs-sys = {new_value}")
    return 0


# ---------------------------------------------------------------- rquickjs-sys

GUARD_OLD = 'if target == host && env::var("CARGO_FEATURE_UPDATE_BINDINGS").is_err() {'
GUARD_NEW = (
    'if target == host'
    ' && env::var("CARGO_FEATURE_UPDATE_BINDINGS").is_err()'
    ' && env::var("PROBE_HOST_BINDGEN").is_err() {'
)

ANCHOR = '    let bindings = builder.generate().expect("Unable to generate bindings");'

DUMP = ANCHOR + '''

    // Instrumentation, not part of the fix: dump the generated bindings and report the
    // shapes that decide whether one layout can serve every ABI.
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


def instrument_sys(crate: pathlib.Path) -> int:
    build_rs = crate / "build.rs"
    source = build_rs.read_text()

    if GUARD_OLD not in source:
        print(f"guard anchor not found in {build_rs}", file=sys.stderr)
        return 1
    source = source.replace(GUARD_OLD, GUARD_NEW, 1)

    if ANCHOR not in source:
        print(f"generate anchor not found in {build_rs}", file=sys.stderr)
        return 1
    source = source.replace(ANCHOR, DUMP, 1)

    build_rs.write_text(source)
    print(f"instrumented {build_rs}")
    return 0


def main() -> int:
    if len(sys.argv) != 3 or sys.argv[1] not in {"enable-bindgen", "instrument-sys"}:
        print(
            "usage: wp1-probe-rquickjs.py {enable-bindgen <rquickjs-core dir>"
            " | instrument-sys <rquickjs-sys dir>}",
            file=sys.stderr,
        )
        return 2

    crate = pathlib.Path(sys.argv[2])
    if sys.argv[1] == "enable-bindgen":
        return enable_bindgen(crate)
    return instrument_sys(crate)


if __name__ == "__main__":
    raise SystemExit(main())
