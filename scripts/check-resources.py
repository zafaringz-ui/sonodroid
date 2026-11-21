#!/usr/bin/env python3
"""Cheap structural checks that do not need Gradle, a JDK or a Rust toolchain.

Verifies that every XML resource parses, every TOML file loads, and that Kotlin/Java
sources have balanced braces and parentheses. This catches the most common
hand-editing mistakes in a sandbox without compilers; it is not a compiler.
"""
from __future__ import annotations

import json
import sys
import tomllib
import xml.dom.minidom
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
failures: list[str] = []


def check_xml() -> None:
    for path in sorted(ROOT.rglob("*.xml")):
        if "build" in path.parts or ".gradle" in path.parts:
            continue
        try:
            xml.dom.minidom.parse(str(path))
        except Exception as exc:  # noqa: BLE001 - report and continue
            failures.append(f"XML {path.relative_to(ROOT)}: {exc}")


def check_toml() -> None:
    for path in sorted(ROOT.rglob("*.toml")):
        if "target" in path.parts:
            continue
        try:
            tomllib.loads(path.read_text())
        except Exception as exc:  # noqa: BLE001
            failures.append(f"TOML {path.relative_to(ROOT)}: {exc}")


def check_json() -> None:
    for path in sorted(ROOT.rglob("*.json")):
        if any(part in {"build", "node_modules", "target"} for part in path.parts):
            continue
        try:
            json.loads(path.read_text())
        except Exception as exc:  # noqa: BLE001
            failures.append(f"JSON {path.relative_to(ROOT)}: {exc}")


def strip_strings_and_comments(source: str) -> str:
    out: list[str] = []
    i = 0
    length = len(source)
    while i < length:
        char = source[i]
        if source.startswith('"""', i):
            end = source.find('"""', i + 3)
            i = length if end == -1 else end + 3
            continue
        if char == '"':
            i += 1
            while i < length:
                if source[i] == "\\":
                    i += 2
                    continue
                if source[i] == '"':
                    i += 1
                    break
                i += 1
            continue
        if char == "'":
            i += 1
            while i < length:
                if source[i] == "\\":
                    i += 2
                    continue
                if source[i] == "'":
                    i += 1
                    break
                i += 1
            continue
        if source.startswith("//", i):
            end = source.find("\n", i)
            i = length if end == -1 else end
            continue
        if source.startswith("/*", i):
            end = source.find("*/", i + 2)
            i = length if end == -1 else end + 2
            continue
        out.append(char)
        i += 1
    return "".join(out)


def check_braces() -> None:
    for suffix in ("*.kt", "*.kts", "*.java"):
        for path in sorted(ROOT.rglob(suffix)):
            if any(part in {"build", ".gradle"} for part in path.parts):
                continue
            text = strip_strings_and_comments(path.read_text())
            for opener, closer in (("{", "}"), ("(", ")"), ("[", "]")):
                if text.count(opener) != text.count(closer):
                    failures.append(
                        f"BRACES {path.relative_to(ROOT)}: "
                        f"{text.count(opener)} {opener} vs {text.count(closer)} {closer}"
                    )


def main() -> int:
    check_xml()
    check_toml()
    check_json()
    check_braces()
    if failures:
        print("\n".join(failures))
        print(f"\n{len(failures)} problem(s) found.")
        return 1
    print("Resources and brace balance look fine.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
