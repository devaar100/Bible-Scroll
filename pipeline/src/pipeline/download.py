"""Download every source into data/raw/ and record size, SHA-256 and date in manifest.json."""
from __future__ import annotations

import hashlib
import json
import shutil
import sys
import urllib.request
from datetime import datetime, timezone

from .offline import require_online
from .paths import MANIFEST, RAW
from .sources import SOURCES, Source

USER_AGENT = "bible-content-pipeline/1.0 (+offline build script)"


def _fetch(source: Source, force: bool) -> dict:
    target = RAW / source.filename
    if target.exists() and not force:
        print(f"  have {source.filename}")
    else:
        print(f"  get  {source.url}")
        request = urllib.request.Request(source.url, headers={"User-Agent": USER_AGENT})
        tmp = target.with_suffix(target.suffix + ".part")
        require_online(f"download {source.url}")
        with urllib.request.urlopen(request, timeout=120) as response, open(tmp, "wb") as out:
            shutil.copyfileobj(response, out)
        tmp.replace(target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    return {
        "key": source.key,
        "dataset": source.dataset,
        "url": source.url,
        "file": source.filename,
        "bytes": target.stat().st_size,
        "sha256": digest,
        "retrieved": datetime.fromtimestamp(target.stat().st_mtime, timezone.utc).isoformat(timespec="seconds"),
    }


def download(force: bool = False) -> None:
    RAW.mkdir(parents=True, exist_ok=True)
    entries = []
    for source in SOURCES:
        try:
            entries.append(_fetch(source, force))
        except OSError as error:
            sys.exit(f"Download failed for {source.key} ({source.url}): {error}")
    MANIFEST.write_text(json.dumps(entries, indent=2) + "\n")
    print(f"Wrote {MANIFEST} ({len(entries)} files)")


def manifest() -> dict[str, dict]:
    if not MANIFEST.exists():
        sys.exit("No data/raw/manifest.json: run `python -m pipeline download` first.")
    return {e["key"]: e for e in json.loads(MANIFEST.read_text())}
