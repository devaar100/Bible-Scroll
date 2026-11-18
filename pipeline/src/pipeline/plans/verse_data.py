"""
Data the app needs to build plans exactly like the pipeline (custom plans, H13/H14), plus the golden vectors.

assets/plans/verses.bin (all little-endian, unsigned bytes):
  b"BSPV", version (1)
  for each book 1..66: chapter count, then each chapter's KJV verse count
  for each KJV verse in canonical order: word count (WEB, KJV fallback), boundary tier (splitting.NONE..CHAPTER)

Tiers: CHAPTER = last verse of a chapter; PARAGRAPH = Masoretic paragraph mark (petuchah/setumah, from the
public-domain WLC via TAHOT) at the end of an Old Testament verse; SENTENCE = WEB text (KJV fallback) ends a
sentence. New Testament paragraphs are not used: TAGNT's paragraphing follows NA28 (see LICENCES.md).
"""
from __future__ import annotations

import json
import random
import re
import sqlite3
from pathlib import Path

from ..books import BOOKS, BY_USFM
from ..paths import APP_BIBLE_DB, REPO, SOURCE_DB
from . import splitting
from .bible import VerseIndex
from .split import boundary_tiers

VERSES_BIN = REPO / "app/src/main/assets/plans/verses.bin"
VECTORS = REPO / "core/plans/src/test/resources/splitting_vectors.json"
MAGIC = b"BSPV"
VERSION = 1
_SENTENCE_END = re.compile(r"[.?!][\"'’”)\]]*$")
_PARAGRAPH_MARK = re.compile(r"[\s׃][ספ]$")


def sentence_ends(path: Path = APP_BIBLE_DB) -> frozenset:
    with sqlite3.connect(f"file:{path}?mode=ro", uri=True) as db:
        texts = {(b, c, v): t for b, c, v, t in db.execute(
            "SELECT book_id, chapter, verse, text FROM verses WHERE translation_id = 'KJV'")}
        texts.update({(b, c, v): t for b, c, v, t in db.execute(
            "SELECT book_id, chapter, verse, text FROM verses WHERE translation_id = 'WEB'")})
    return frozenset(k for k, t in texts.items() if _SENTENCE_END.search(t.strip()))


def paragraph_ends(path: Path = SOURCE_DB) -> frozenset:
    """Old Testament verses whose last Hebrew word carries a Masoretic paragraph mark (ס or פ)."""
    with sqlite3.connect(f"file:{path}?mode=ro", uri=True) as db:
        rows = db.execute(
            "SELECT w.book_id, w.chapter, w.verse, w.surface FROM word w JOIN ("
            " SELECT book_id, chapter, verse, MAX(word_no) AS last FROM word WHERE dataset = 'TAHOT' AND chapter IS NOT NULL"
            " AND verse > 0 GROUP BY book_id, chapter, verse) m ON w.book_id = m.book_id AND w.chapter = m.chapter"
            " AND w.verse = m.verse AND w.word_no = m.last WHERE w.dataset = 'TAHOT'").fetchall()
    return frozenset((b, c, v) for b, c, v, s in rows if _PARAGRAPH_MARK.search(s.strip()))


def whole_bible(index: VerseIndex, paragraphs: frozenset, sentences: frozenset) -> tuple[list, list[int], list[int]]:
    sequence = [v for v in index.verses(index.order[0], index.order[-1])]
    return sequence, [v.words for v in sequence], boundary_tiers(sequence, index.chapter_ends, paragraphs, sentences)


def write_verses_bin(index: VerseIndex, paragraphs: frozenset, sentences: frozenset, path: Path = VERSES_BIN) -> bytes:
    sequence, words, tiers = whole_bible(index, paragraphs, sentences)
    out = bytearray(MAGIC) + bytes([VERSION])
    for book in BOOKS:
        counts = {}
        for b, c, v in index.order:
            if b == book.id:
                counts[c] = max(v, counts.get(c, 0))
        chapters = sorted(counts)
        if chapters != list(range(1, len(chapters) + 1)):
            raise ValueError(f"{book.usfm}: chapters aren't 1..n")
        out.append(len(chapters))
        for c in chapters:
            present = [v for b, cc, v in index.order if b == book.id and cc == c]
            if present != list(range(1, counts[c] + 1)):
                raise ValueError(f"{book.usfm} {c}: KJV verses aren't 1..n")
            out.append(counts[c])
    for w, t in zip(words, tiers):
        if not 0 <= w <= 255:
            raise ValueError(f"word count {w} doesn't fit a byte")
        out += bytes([w, t])
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(bytes(out))
    return bytes(out)


def _passages(sequence: list, cuts: list[int]) -> list[list[list]]:
    """Each day as [[book, c1, v1, c2, v2], ...]: runs within one book (the sequence is canonical, contiguous)."""
    bounds = [-1, *cuts, len(sequence) - 1]
    days = []
    for i in range(len(bounds) - 1):
        day, run = [], []
        for verse in sequence[bounds[i] + 1: bounds[i + 1] + 1]:
            if run and verse.key[0] != run[-1].key[0]:
                day.append([BOOKS[run[0].key[0] - 1].usfm, *run[0].key[1:], *run[-1].key[1:]])
                run = []
            run.append(verse)
        day.append([BOOKS[run[0].key[0] - 1].usfm, *run[0].key[1:], *run[-1].key[1:]])
        days.append(day)
    return days


#: Real plans the app might build: (books, "days" | "minutes", value).
PLAN_CASES = [
    (["JHN"], "days", 21), (["JHN"], "minutes", 10), (["JHN"], "minutes", 5), (["ROM"], "days", 16),
    (["PRO"], "days", 31), (["PSA"], "days", 30), (["PSA"], "minutes", 15), (["GEN", "EXO"], "days", 45),
    (["MAT", "MRK", "LUK", "JHN"], "days", 40), (["JUD"], "days", 3), (["RUT"], "days", 1), (["ISA"], "minutes", 20),
    (["EST"], "minutes", 5), ([b.usfm for b in BOOKS if b.id >= 40], "days", 90), (["OBA", "JON", "MIC"], "days", 7),
    (["1SA"], "days", 31), (["LEV"], "minutes", 10),
]


def selection(index: VerseIndex, codes: list[str], paragraphs: frozenset, sentences: frozenset):
    wanted = sorted({BY_USFM[c].id for c in codes})
    sequence = [v for v in index.verses(index.order[0], index.order[-1]) if v.key[0] in wanted]
    return sequence, [v.words for v in sequence], boundary_tiers(sequence, index.chapter_ends, paragraphs, sentences)


def build_vectors(index: VerseIndex, paragraphs: frozenset, sentences: frozenset, data: bytes) -> dict:
    import hashlib

    plans = []
    for codes, mode, value in PLAN_CASES:
        sequence, words, tiers = selection(index, codes, paragraphs, sentences)
        days = value if mode == "days" else splitting.days_for_minutes(sum(words), value, len(words))
        cuts = splitting.plan_cuts(words, tiers, days)
        plans.append({
            "books": codes, mode: value, "expectedDays": days, "cuts": cuts,
            "minutesPerDay": splitting.estimate_minutes(splitting.day_words(words, cuts)),
            "passages": _passages(sequence, cuts),
        })
    # Synthetic cases: every tier, ties, tight and loose tolerances, independent of Bible data.
    rng = random.Random(20261118)
    raw = []
    for n in range(40):
        size = rng.randint(1, 60)
        words = [rng.choice([1, 3, 3, 7, 12, 20, 20, 35]) for _ in range(size)]
        tiers = [rng.choice([0, 0, 0, 1, 1, 2, 3]) for _ in range(size)]
        days = rng.randint(1, size)
        tolerance = rng.choice(splitting.SNAP_STEPS + (0.5, 1.0))
        raw.append({"words": words, "tiers": tiers, "days": days, "tolerance": tolerance,
                    "splitCuts": splitting.split_cuts(words, tiers, days, tolerance),
                    "planCuts": splitting.plan_cuts(words, tiers, days),
                    "estimateMinutes": splitting.estimate_minutes(splitting.day_words(words, splitting.plan_cuts(words, tiers, days)))})
    return {"formatVersion": 1, "versesBinSha256": hashlib.sha256(data).hexdigest(), "plans": plans, "raw": raw}


def write_all(index: VerseIndex | None = None) -> tuple[Path, Path]:
    index = index or VerseIndex.load()
    paragraphs, sentences = paragraph_ends(), sentence_ends()
    data = write_verses_bin(index, paragraphs, sentences)
    VECTORS.parent.mkdir(parents=True, exist_ok=True)
    VECTORS.write_text(json.dumps(build_vectors(index, paragraphs, sentences, data), indent=1) + "\n")
    return VERSES_BIN, VECTORS
