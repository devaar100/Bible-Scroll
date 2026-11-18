"""The target versification: the KJV verse list in the app's bundled Bible database."""
from __future__ import annotations

import sqlite3
import sys
from dataclasses import dataclass

from .paths import APP_BIBLE_DB


@dataclass(frozen=True)
class Kjv:
    verses: frozenset[tuple[int, int, int]]
    """(book_id, chapter) pairs with a Psalm title, i.e. valid verse-0 targets."""
    titled: frozenset[tuple[int, int]]
    """book_id -> verse count per chapter, in order."""
    chapter_sizes: dict[int, list[int]]

    def has(self, book_id: int, chapter: int, verse: int) -> bool:
        if verse == 0:
            return (book_id, chapter) in self.titled
        return (book_id, chapter, verse) in self.verses


def load_kjv() -> Kjv:
    if not APP_BIBLE_DB.exists():
        sys.exit(f"Missing {APP_BIBLE_DB}: the app's Bible DB is the target versification.")
    db = sqlite3.connect(f"file:{APP_BIBLE_DB}?mode=ro", uri=True)
    try:
        verses = frozenset(db.execute("SELECT book_id, chapter, verse FROM verses WHERE translation_id = 'KJV'"))
        titled = frozenset(db.execute(
            "SELECT book_id, chapter FROM chapters WHERE translation_id = 'KJV' AND superscription IS NOT NULL"))
        sizes: dict[int, list[int]] = {}
        for book, _, count in db.execute(
            "SELECT book_id, chapter, verse_count FROM chapters WHERE translation_id = 'KJV' ORDER BY book_id, chapter"
        ):
            sizes.setdefault(book, []).append(count)
    finally:
        db.close()
    if len(verses) != 31102:
        sys.exit(f"Expected 31,102 KJV verses in {APP_BIBLE_DB}, found {len(verses)}.")
    return Kjv(verses, titled, sizes)
