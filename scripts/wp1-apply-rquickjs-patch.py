#!/usr/bin/env python3
"""Add the vendored rquickjs-sys patch to rust/Cargo.toml, idempotently.

Kept as a script rather than an inline heredoc so the same edit can be applied by hand
when the fix lands, and so CI does not have to quote Python inside a YAML block scalar.

The entry has to go *inside* the existing `[patch.crates-io]` table: appending a second
header is a hard error ("duplicate key", run 44776146749), because a TOML table cannot be
reopened under the same name.

Usage: wp1-apply-rquickjs-patch.py [path to Cargo.toml]
"""
from __future__ import annotations

import pathlib
import sys

HEADER = "\n[patch.crates-io]\n"
ENTRY = (
    "\n"
    "# Local patch: rquickjs-sys 0.12.2 ships no Android bindings, so its `bindgen`\n"
    "# feature is on by default in this copy. See vendor/rquickjs-sys/VENDORED.md.\n"
    'rquickjs-sys = { path = "vendor/rquickjs-sys" }'
)
MARKER = "vendor/rquickjs-sys"


def main() -> int:
    manifest = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "Cargo.toml")
    text = manifest.read_text()

    if MARKER in text:
        print("::notice title=patch::already present")
        return 0

    # Anchor on the header *line*: the string also appears inside an earlier comment
    # ("Upstream declares rodio here plus a [patch.crates-io] redirect..."), and replacing
    # that occurrence corrupts the rodio comment instead of adding a patch entry.
    if HEADER not in text:
        print(f"::error title=patch::no {HEADER} table to extend")
        return 1

    manifest.write_text(text.replace(HEADER, HEADER + ENTRY + "\n", 1))
    print("::notice title=patch::added inside the existing [patch.crates-io]")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
