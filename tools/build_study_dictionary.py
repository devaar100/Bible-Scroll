#!/usr/bin/env python3
"""
Build the zlib preset dictionary the app uses to store studies compressed (core/content StudyCompression).

    python3 tools/build_study_dictionary.py          # needs pipeline/out/site (pipeline `publish --local` or a publish)

Each verse's study is stored as its own small zlib stream. A small stream can't learn the JSON keys and the house
style from itself, so a preset dictionary of sample studies does that: 33 MB of Gospels and Psalms JSON is stored
as about 9.4 MB, against 14 MB with plain zlib (2026-11-24).

The sample comes from the Discover books only (never the bundled Gospels and Psalms, so the saving isn't measured
on text the dictionary already holds), Old and New Testament alternating, picked deterministically by id.

Changing the dictionary changes every stored row: bump ContentDatabase's version in the same change (the content
DB is a cache and is rebuilt from the bundle and the CDN). The app rejects rows made with another dictionary.
"""
from __future__ import annotations

import gzip
import json
import sys
import zlib
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SITE = REPO / "pipeline/out/site"
OUT = REPO / "core/content/src/main/resources/aarnav100/developer/biblescroll/core/content/db/study_dictionary.bin"
BUNDLED = {"MAT", "MRK", "LUK", "JHN", "PSA"}
SIZE = 32 * 1024  # zlib's window: bytes further back than this are never used
STEP = 7  # every 7th study (by id) per testament, so the sample spreads over many books


def studies() -> tuple[list[bytes], list[bytes]]:
    manifest = json.loads((SITE / "manifest.json").read_text())
    old, new = [], []
    for book in manifest["books"]:
        if book["book"] in BUNDLED:
            continue
        path = SITE / book["url"].split(".web.app/", 1)[1]
        for study in json.loads(gzip.decompress(path.read_bytes()))["verses"]:
            # The app stores each study as kotlinx.serialization writes it: compact, UTF-8, keys in model order.
            # Key order differs from the app's, which costs little: the dictionary is matched by substrings.
            text = json.dumps(study, ensure_ascii=False, separators=(",", ":")).encode()
            greek = study.get("originalLanguage", {}).get("language") == "greek"
            (new if greek else old).append((study["id"], text))
    pick = lambda rows: [text for _, text in sorted(rows)[::STEP]]
    return pick(old), pick(new)


def main() -> int:
    old, new = studies()
    sample = bytearray()
    for text in (text for pair in zip(old, new) for text in pair):
        if len(sample) + len(text) <= SIZE:  # skip one that doesn't fit; a shorter one later may
            sample += text
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(bytes(sample))
    print(f"Wrote {OUT.relative_to(REPO)}: {len(sample):,} bytes, adler32 {zlib.adler32(bytes(sample)):08x}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
