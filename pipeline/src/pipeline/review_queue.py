"""Review queue order: flagged first, then Discover verses, then priority.txt, then Bible order."""
from __future__ import annotations

from pathlib import Path

from .paths import PIPELINE
from .store import parse_id

PRIORITY_FILE = PIPELINE / "priority.txt"
REVIEWABLE = ("flagged", "failed", "auto_checked")


def load_priority(path: Path = PRIORITY_FILE, discover_path: Path | None = None) -> list[tuple]:
    """
    Key prefixes in review order: the Discover verses (discover.txt) first, then priority.txt lines
    (JHN.3.16 / JHN.3 / JHN) in file order. Bad lines raise.
    """
    from .books import BY_USFM
    from .publish import DISCOVER_FILE, load_discover

    entries = [parse_id(i) for i in load_discover(discover_path or DISCOVER_FILE)]
    for number, line in enumerate(path.read_text().splitlines() if path.exists() else [], start=1):
        text = line.split("#", 1)[0].strip()
        if not text:
            continue
        parts = text.split(".")
        if parts[0] not in BY_USFM or len(parts) > 3 or not all(p.isdigit() and int(p) > 0 for p in parts[1:]):
            raise ValueError(f"{path.name}:{number}: expected BOOK, BOOK.C or BOOK.C.V, got {text!r}")
        entries.append((BY_USFM[parts[0]].id, *map(int, parts[1:])))
    return entries


def queue_order(studies: list[dict], priority: list[tuple]) -> list[dict]:
    """The studies that need a human, in review order."""

    def rank(study: dict) -> tuple:
        key = parse_id(study["id"])
        position = next((i for i, prefix in enumerate(priority) if key[:len(prefix)] == prefix), len(priority))
        return (0 if study["status"] in ("flagged", "failed") else 1, position, key)

    return sorted((s for s in studies if s.get("status") in REVIEWABLE), key=rank)
