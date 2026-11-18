"""Reading and writing study files, plus the lookups the checker and review tool share."""
from __future__ import annotations

import json
import os
import re
import sqlite3
import tempfile
from dataclasses import dataclass
from datetime import datetime, timezone
from functools import cached_property
from pathlib import Path

from .books import BOOKS, BY_USFM

STUDY_ID = re.compile(r"^([1-3A-Z][A-Z0-9]{2})\.([1-9][0-9]*)\.([1-9][0-9]*)$")
Key = tuple[int, int, int]


def now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def parse_id(study_id: str) -> Key:
    """'JHN.3.16' -> (43, 3, 16). Raises ValueError for anything else (also guards file paths)."""
    m = STUDY_ID.match(study_id)
    if not m or m.group(1) not in BY_USFM:
        raise ValueError(f"Not a study id: {study_id!r}")
    return BY_USFM[m.group(1)].id, int(m.group(2)), int(m.group(3))


def make_id(key: Key) -> str:
    return f"{BOOKS[key[0] - 1].usfm}.{key[1]}.{key[2]}"


def is_written(study: dict) -> bool:
    """Prose exists (an AI model has filled it in)."""
    return study.get("meta", {}).get("model") is not None


class StudyStore:
    """One JSON file per study in a directory. Writes are atomic (temp file + rename)."""

    def __init__(self, directory: Path):
        self.directory = directory

    def path(self, study_id: str) -> Path:
        parse_id(study_id)
        return self.directory / f"{study_id}.json"

    def load(self, study_id: str) -> dict:
        return json.loads(self.path(study_id).read_text())

    def save(self, study: dict) -> None:
        target = self.path(study["id"])
        fd, tmp = tempfile.mkstemp(dir=self.directory, prefix=".tmp-", suffix=".json")
        with os.fdopen(fd, "w") as f:
            f.write(json.dumps(study, ensure_ascii=False, indent=2) + "\n")
        os.replace(tmp, target)

    def ids(self) -> list[str]:
        """Every study id, in Bible order."""
        found = []
        for p in self.directory.glob("*.json"):
            try:
                found.append((parse_id(p.stem), p.stem))
            except ValueError:
                continue
        return [study_id for _, study_id in sorted(found)]

    def all(self) -> list[dict]:
        return [self.load(i) for i in self.ids()]


@dataclass
class Bible:
    """Verse existence (source.db) and verse text (the app's bible.db)."""
    source_db: Path
    bible_db: Path | None = None

    @cached_property
    def verses(self) -> frozenset[Key]:
        with sqlite3.connect(f"file:{self.source_db}?mode=ro", uri=True) as db:
            return frozenset(db.execute("SELECT book_id, chapter, verse FROM kjv_verse"))

    @cached_property
    def chapters(self) -> frozenset[tuple[int, int]]:
        return frozenset((b, c) for b, c, _ in self.verses)

    @cached_property
    def lexicon_translit(self) -> dict[str, str]:
        with sqlite3.connect(f"file:{self.source_db}?mode=ro", uri=True) as db:
            return dict(db.execute("SELECT strongs, translit FROM lexicon WHERE translit <> ''"))

    def text(self, key: Key, translation: str = "KJV") -> str | None:
        if self.bible_db is None or not self.bible_db.exists():
            return None
        with sqlite3.connect(f"file:{self.bible_db}?mode=ro", uri=True) as db:
            row = db.execute(
                "SELECT text FROM verses WHERE translation_id = ? AND book_id = ? AND chapter = ? AND verse = ?",
                (translation, *key),
            ).fetchone()
        return row[0] if row else None

    def candidate_xrefs(self, key: Key) -> list[dict]:
        """Every cross reference in source.db for a verse (the review tool shows these next to the chosen 8)."""
        with sqlite3.connect(f"file:{self.source_db}?mode=ro", uri=True) as db:
            rows = db.execute(
                "SELECT dataset, to_book, to_chapter, to_verse, to_book_end, to_chapter_end, to_verse_end, votes, "
                "keyword FROM xref WHERE from_book = ? AND from_chapter = ? AND from_verse = ? "
                "ORDER BY dataset, -coalesce(votes, 0), id",
                key,
            ).fetchall()
        return [
            {"dataset": ds, "start": make_id((tb, tc, tv)) if tv else f"{BOOKS[tb - 1].usfm}.{tc}.title",
             "end": make_id((eb, ec, ev)) if ev else None, "votes": votes, "keyword": keyword}
            for ds, tb, tc, tv, eb, ec, ev, votes, keyword in rows
        ]
