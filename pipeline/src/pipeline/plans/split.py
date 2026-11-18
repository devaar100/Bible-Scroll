"""Split a reading sequence into N days of about equal length, preferring to cut at chapter ends."""
from __future__ import annotations

from itertools import accumulate

from ..books import BOOKS
from .bible import Verse

# A chapter end is used when it's within this fraction of a day's length of the ideal cut.
SNAP_TOLERANCE = 0.35


def split_days(sequence: list[Verse], days: int, chapter_ends: set, tolerance: float = SNAP_TOLERANCE) -> list[list[Verse]]:
    """
    Contiguous, non-empty, covering every verse once. Cut k aims at total*k/days words; the nearest chapter
    end is taken if it's within SNAP_TOLERANCE of a day's length, otherwise the nearest verse end. Works on
    any order of verses (also the chronological plan's reordered sequence).
    """
    if days < 1:
        raise ValueError("days must be >= 1")
    if days > len(sequence):
        raise ValueError(f"Can't split {len(sequence)} verses into {days} days")
    cumulative = list(accumulate(v.words for v in sequence))
    total = cumulative[-1]
    per_day = total / days
    ends_at_chapter = [i for i, v in enumerate(sequence)
                       if v.key in chapter_ends or (i + 1 < len(sequence) and sequence[i + 1].key[:2] != v.key[:2])]

    cuts: list[int] = []  # index of the last verse of each day except the final one
    previous = -1
    for k in range(1, days):
        target = total * k / days
        remaining_days = days - k
        latest = len(sequence) - 1 - remaining_days  # leave at least one verse per remaining day
        candidates = [i for i in ends_at_chapter if previous < i <= latest]
        best_chapter = min(candidates, key=lambda i: abs(cumulative[i] - target), default=None)
        if best_chapter is not None and abs(cumulative[best_chapter] - target) <= tolerance * per_day:
            cut = best_chapter
        else:
            cut = min(range(previous + 1, latest + 1), key=lambda i: abs(cumulative[i] - target))
        cuts.append(cut)
        previous = cut

    bounds = [-1, *cuts, len(sequence) - 1]
    return [sequence[bounds[i] + 1: bounds[i + 1] + 1] for i in range(days)]


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
