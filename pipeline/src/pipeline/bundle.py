"""
Copy the Gospels and Psalms book files from the local release into the app's assets, for offline use.

Run before release builds (no Gradle involved):
    PYTHONPATH=src .venv/bin/python -m pipeline bundle
Writes app/src/main/assets/studies/{BOOK}.json.gzip plus manifest.json (the bundled subset of the release
manifest, so the app knows which contentVersion it already has and can skip downloading those books).
"""
from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path

from .paths import REPO
from .publish import SITE, _site_path, canonical_json, refuse_test_path

BUNDLED_BOOKS = ("MAT", "MRK", "LUK", "JHN", "PSA")
#: Not ".gz": Android's asset packager (AAPT) gunzips *.gz assets and drops the suffix, so the app would find
#: neither the file name nor the bytes the manifest's SHA-256 describes. ".gzip" is packaged byte-for-byte.
BUNDLED_SUFFIX = ".json.gzip"
ASSETS = REPO / "app/src/main/assets/studies"


@dataclass
class BundleResult:
    copied: list[str]
    missing: list[str]   # bundled books with no publishable verses yet
    removed: list[str]
    bytes: int


def bundle(site: Path = SITE, assets: Path = ASSETS, books: tuple[str, ...] = BUNDLED_BOOKS) -> BundleResult:
    refuse_test_path(site)
    refuse_test_path(assets)
    manifest_path = site / "manifest.json"
    if not manifest_path.exists():
        raise FileNotFoundError(f"No {manifest_path}: run `python -m pipeline publish --local` first to build the release")
    manifest = json.loads(manifest_path.read_text())
    entries = {b["book"]: b for b in manifest["books"]}

    assets.mkdir(parents=True, exist_ok=True)
    copied, missing, total = [], [], 0
    subset = []
    for book in books:
        entry = entries.get(book)
        if entry is None:
            missing.append(book)
            continue
        data = (site / _site_path(entry["url"])).read_bytes()
        if hashlib.sha256(data).hexdigest() != entry["sha256"]:
            raise ValueError(f"{book}: file in {site} doesn't match its manifest SHA-256; rebuild the release")
        target = assets / f"{book}{BUNDLED_SUFFIX}"
        if not target.exists() or target.read_bytes() != data:
            target.write_bytes(data)
        copied.append(book)
        total += len(data)
        subset.append({**entry, "url": f"{book}{BUNDLED_SUFFIX}"})

    removed = []
    for stale in [*assets.glob("*.json.gz"), *assets.glob(f"*{BUNDLED_SUFFIX}")]:
        if stale.name.removesuffix(".json.gz").removesuffix(BUNDLED_SUFFIX) not in copied or stale.name.endswith(".json.gz"):
            stale.unlink()
            removed.append(stale.name)
    bundled = {"formatVersion": manifest["formatVersion"], "schemaVersion": manifest["schemaVersion"],
               "release": manifest["release"], "books": subset}
    (assets / "manifest.json").write_bytes(canonical_json(bundled))
    return BundleResult(copied, missing, removed, total)
