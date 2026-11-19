"""
Community spotlight ministries (app C1/C2): community/ministries.json, published as /ministries.json and pinned by
sha256 in manifest.community. backend/scripts/seed-month.mjs reads the same file to create the month's Firestore
vote counters. Entries with "test": true are for debug builds only (the app hides them in release).
"""
from __future__ import annotations

import json
from pathlib import Path

from jsonschema import Draft202012Validator

from .paths import PIPELINE

MINISTRIES_FILE = PIPELINE / "community" / "ministries.json"
SCHEMA = PIPELINE / "schema" / "ministries.json"


def load_ministries(path: Path = MINISTRIES_FILE) -> dict | None:
    """The validated file, or None if there isn't one. Raises ValueError listing every problem."""
    if not path.exists():
        return None
    data = json.loads(path.read_text())
    validator = Draft202012Validator(json.loads(SCHEMA.read_text()))
    problems = [f"{'/'.join(map(str, e.absolute_path)) or '(root)'}: {e.message}" for e in validator.iter_errors(data)]
    if not problems:
        seen: set[str] = set()
        for m in data["ministries"]:
            if m["id"] in seen:
                problems.append(f"{m['id']}: id used twice (ids must be unique; they name Firestore documents)")
            seen.add(m["id"])
            if m.get("test") and not m["id"].startswith("test-"):
                problems.append(f"{m['id']}: test entries must have ids starting with test- (keeps them apart in Firestore)")
    if problems:
        raise ValueError(f"{path.name}:\n  " + "\n  ".join(problems))
    return data


def featured(data: dict, month: str, include_test: bool = False) -> list[dict]:
    """Ministries in the spotlight for a UTC month ("2026-12"), in file order."""
    return [m for m in data["ministries"] if month in m["months_featured"] and (include_test or not m.get("test"))]
