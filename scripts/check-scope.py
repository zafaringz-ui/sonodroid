#!/usr/bin/env python3
"""Fail if a provider that Release 1 does not ship is back in the tree.

Release 1 is Deezer, YouTube Music and local files (docs/PLAN.md §2). The Spotify, Apple
Music, Tidal, Jellyfin and Subsonic providers, the Soloist/librespot playback engine and
the Connect / Widevine hooks were removed in PR #19; the dependency crates only they used
went with them. This check exists because re-adding one is easy and quiet: a provider comes
back as a directory, a `pub mod` line, a workspace dependency and eight crates in the
lockfile, and nothing in CI would otherwise notice until the APK doubled in size.

It is deliberately a grep, not a build: it has to run in this sandbox and in a job that has
no Rust toolchain, and it is checking for names, not behaviour.

Exit code 0 when clean, 1 with a list of hits otherwise.
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MUSIC = ROOT / "rust" / "vendor" / "sonora" / "crates" / "music"

# Provider directories that must not exist.
FORBIDDEN_DIRS = ["spotify", "apple", "tidal", "jellyfin", "subsonic", "soloist", "binimum"]

# Module files that must not exist.
FORBIDDEN_FILES = ["connect.rs", "drm.rs"]

# Dependency crates that left the graph with those providers. Checked in every manifest
# under rust/, because the point is that they are gone from the workspace, not just unused.
FORBIDDEN_DEPS = [
    "librespot-core",
    "librespot-playback",
    "librespot-protocol",
    "librespot-audio",
    "librespot-metadata",
    "librespot-oauth",
    "oauth2",
    "opensubsonic",
    "gethostname",
]

# `protobuf` and `tokio-tungstenite` are only *provider* users of otherwise general crates,
# so they are reported rather than failed: the next task may well want them back for
# something else, and a false failure is worse than a note.

failures: list[str] = []


def check_dirs() -> None:
    src = MUSIC / "src"
    for name in FORBIDDEN_DIRS:
        if (src / name).exists():
            failures.append(f"{src.relative_to(ROOT)}/{name}/ exists")
    for name in FORBIDDEN_FILES:
        if (src / name).exists():
            failures.append(f"{src.relative_to(ROOT)}/{name} exists")


def check_module_declarations() -> None:
    lib = MUSIC / "src" / "lib.rs"
    text = lib.read_text()
    for name in FORBIDDEN_DIRS + [f[:-3] for f in FORBIDDEN_FILES]:
        for line in text.splitlines():
            if line.strip() in (f"pub mod {name};", f"mod {name};"):
                failures.append(f"{lib.relative_to(ROOT)}: '{line.strip()}'")


def check_manifests() -> None:
    for manifest in sorted((ROOT / "rust").rglob("Cargo.toml")):
        if "target" in manifest.parts:
            continue
        text = manifest.read_text()
        for dep in FORBIDDEN_DEPS:
            for line in text.splitlines():
                stripped = line.strip()
                if stripped.startswith(f"{dep} ") or stripped.startswith(f"{dep}=") \
                        or stripped.startswith(f"{dep} ="):
                    failures.append(
                        f"{manifest.relative_to(ROOT)}: '{stripped}' (dependency of {dep})"
                    )


def main() -> int:
    check_dirs()
    check_module_declarations()
    check_manifests()

    if failures:
        print("Trimmed providers or their dependencies are back:")
        for f in failures:
            print(f"  - {f}")
        print()
        print("Release 1 ships Deezer, YouTube Music and local files. See docs/AUDIT.md §13.")
        return 1

    print("No trimmed provider or provider-only dependency is present.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
