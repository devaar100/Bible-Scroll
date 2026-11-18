"""Split a reading sequence into N days of about equal length, preferring to cut at chapter ends."""
from __future__ import annotations

from ..books import BOOKS
from . import splitting
from .bible import Verse

# A chapter end is used when it's within this fraction of a day's length of the ideal cut.
SNAP_TOLERANCE = 0.35


def split_days(sequence: list[Verse], days: int, chapter_ends: set, tolerance: float = SNAP_TOLERANCE,
               paragraph_ends: frozenset = frozenset(), sentence_ends: frozenset = frozenset()) -> list[list[Verse]]:
    """
    Contiguous, non-empty, covering every verse once. The cut rules live in splitting.split_cuts (shared with
    the app). Chapter ends are [chapter_ends] plus any change of chapter in the sequence, so this works on any
    order of verses (also the chronological plan's reordered sequence).
    """
    words = [v.words for v in sequence]
    tiers = boundary_tiers(sequence, chapter_ends, paragraph_ends, sentence_ends)
    cuts = splitting.split_cuts(words, tiers, days, tolerance)
    bounds = [-1, *cuts, len(sequence) - 1]
    return [sequence[bounds[i] + 1: bounds[i + 1] + 1] for i in range(days)]


def boundary_tiers(sequence: list[Verse], chapter_ends: set, paragraph_ends: frozenset = frozenset(),
                   sentence_ends: frozenset = frozenset()) -> list[int]:
    tiers = []
    for i, v in enumerate(sequence):
        if v.key in chapter_ends or (i + 1 < len(sequence) and sequence[i + 1].key[:2] != v.key[:2]):
            tiers.append(splitting.CHAPTER)
        elif v.key in paragraph_ends:
            tiers.append(splitting.PARAGRAPH)
        elif v.key in sentence_ends:
            tiers.append(splitting.SENTENCE)
        else:
            tiers.append(splitting.NONE)
    return tiers


def to_passages(day: list[Verse], position: dict) -> list[dict]:
    """
    Runs of canonically adjacent verses within one book become one passage each. [position] is the
    canonical index of each verse key (VerseIndex.position), so a jump (e.g. Psalm 3 then Psalm 51 in a
    chronological plan) starts a new passage instead of silently covering everything between.
    """
    passages: list[dict] = []
    run: list[Verse] = []

    def flush():
        if run:
            (b, c1, v1), (_, c2, v2) = run[0].key, run[-1].key
            passages.append({"book": BOOKS[b - 1].usfm, "startChapter": c1, "startVerse": v1,
                             "endChapter": c2, "endVerse": v2})
            run.clear()

    for verse in day:
        if run and (verse.key[0] != run[-1].key[0] or position[verse.key] != position[run[-1].key] + 1):
            flush()
        run.append(verse)
    flush()
    return passages
