"""Verse word counts and existence, from the app's bundled Bible (KJV versification)."""
from __future__ import annotations

import sqlite3
import sys
from dataclasses import dataclass
from functools import cached_property
from pathlib import Path

from ..books import BOOKS, BY_USFM
from ..paths import APP_BIBLE_DB

WORDS_PER_MINUTE = 200
Key = tuple[int, int, int]  # book_id, chapter, verse


@dataclass(frozen=True)
class Verse:
    key: Key
    words: int


class VerseIndex:
    """
    Every KJV verse in canonical order with a word count. Counts come from the World English Bible (the
    app's modern default, closest to what most people read); verses WEB doesn't have fall back to KJV.
    """

    def __init__(self, counts: dict[Key, int]):
        self.counts = counts
        self.order: list[Key] = sorted(counts)
        self.position = {k: i for i, k in enumerate(self.order)}

    @classmethod
    def load(cls, path: Path = APP_BIBLE_DB) -> "VerseIndex":
        if not path.exists():
            sys.exit(f"Missing {path}: plans need the app's bundled Bible for verse lists and word counts.")
        with sqlite3.connect(f"file:{path}?mode=ro", uri=True) as db:
            kjv = {(b, c, v): len(t.split()) for b, c, v, t in
                   db.execute("SELECT book_id, chapter, verse, text FROM verses WHERE translation_id = 'KJV'")}
            web = {(b, c, v): len(t.split()) for b, c, v, t in
                   db.execute("SELECT book_id, chapter, verse, text FROM verses WHERE translation_id = 'WEB'")}
        return cls({k: (web.get(k) or n) for k, n in kjv.items()})

    def exists(self, key: Key) -> bool:
        return key in self.counts

    @cached_property
    def chapter_ends(self) -> set[Key]:
        """The last verse of every chapter."""
        last: dict[tuple[int, int], int] = {}
        for b, c, v in self.order:
            last[(b, c)] = max(v, last.get((b, c), 0))
        return {(b, c, v) for (b, c), v in last.items()}

    def verses(self, start: Key, end: Key) -> list[Verse]:
        """Inclusive range in canonical order (must exist)."""
        i, j = self.position[start], self.position[end]
        return [Verse(k, self.counts[k]) for k in self.order[i:j + 1]]

    def book(self, code: str) -> list[Verse]:
        book_id = BY_USFM[code].id
        return [Verse(k, self.counts[k]) for k in self.order if k[0] == book_id]

    def books(self, codes: list[str]) -> list[Verse]:
        out: list[Verse] = []
        for code in codes:
            out += self.book(code)
        return out

    def chapters(self, code: str, first: int, last: int) -> list[Verse]:
        book_id = BY_USFM[code].id
        return [Verse(k, self.counts[k]) for k in self.order if k[0] == book_id and first <= k[1] <= last]

    def chapter_count(self, code: str) -> int:
        book_id = BY_USFM[code].id
        return max(c for b, c, _ in self.order if b == book_id)


def minutes(words: int) -> float:
    return words / WORDS_PER_MINUTE


OT_CODES = [b.usfm for b in BOOKS if b.id <= 39]
NT_CODES = [b.usfm for b in BOOKS if b.id >= 40]
