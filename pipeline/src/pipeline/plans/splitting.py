"""
The plan-splitting algorithm shared with the app (core/plans, PlanSplitter.kt). This file is the reference:
the Kotlin port must give identical output, checked by golden vectors (`python -m pipeline plans vectors`
writes core/plans/src/test/resources/splitting_vectors.json; tests on both sides compare against it).

Everything works on plain lists so both languages can mirror it exactly:
  words[i]  word count of verse i in reading order (WEB, KJV fallback; see VerseIndex)
  tiers[i]  how good a place verse i's end is to stop a day: CHAPTER > PARAGRAPH > SENTENCE > NONE
Ties always go to the earliest index (Python min / Kotlin minBy both keep the first).
"""
from __future__ import annotations

import math
from itertools import accumulate

NONE, SENTENCE, PARAGRAPH, CHAPTER = 0, 1, 2, 3
WORDS_PER_MINUTE = 200
#: Fraction of a day's length a boundary may sit from the ideal cut, per tier (times the tolerance).
TIER_REACH = {CHAPTER: 1.0, PARAGRAPH: 1.0, SENTENCE: 0.5}
#: Snapping, loosest first; the last step is pure verse-level cuts. A plan uses the first step whose days
#: all stay within MAX_OVER of the average.
SNAP_STEPS = (0.35, 0.25, 0.15, 0.08, 0.0)
MAX_OVER = 1.5


def split_cuts(words: list[int], tiers: list[int], days: int, tolerance: float) -> list[int]:
    """
    Indices of the last verse of each day except the final one (len = days - 1). Cut k aims at total*k/days
    words; it takes the nearest chapter end within reach, else the nearest paragraph end, else the nearest
    sentence end, else the nearest verse end. Every day gets at least one verse.
    """
    if days < 1:
        raise ValueError("days must be >= 1")
    if days > len(words):
        raise ValueError(f"Can't split {len(words)} verses into {days} days")
    cumulative = list(accumulate(words))
    total = cumulative[-1]
    per_day = total / days
    cuts: list[int] = []
    previous = -1
    for k in range(1, days):
        target = total * k / days
        latest = len(words) - 1 - (days - k)
        cut = None
        for tier in (CHAPTER, PARAGRAPH, SENTENCE):
            reach = tolerance * TIER_REACH[tier] * per_day
            candidates = [i for i in range(previous + 1, latest + 1) if tiers[i] >= tier]
            best = min(candidates, key=lambda i: abs(cumulative[i] - target), default=None)
            if best is not None and abs(cumulative[best] - target) <= reach:
                cut = best
                break
        if cut is None:
            cut = min(range(previous + 1, latest + 1), key=lambda i: abs(cumulative[i] - target))
        cuts.append(cut)
        previous = cut
    return cuts


def day_words(words: list[int], cuts: list[int]) -> list[int]:
    bounds = [-1, *cuts, len(words) - 1]
    return [sum(words[bounds[i] + 1: bounds[i + 1] + 1]) for i in range(len(bounds) - 1)]


def estimate_minutes(day_word_counts: list[int]) -> int:
    """Rounded-up average minutes a day, so "about M min" isn't an undersell."""
    day_minutes = [w / WORDS_PER_MINUTE for w in day_word_counts]
    return max(1, math.ceil(sum(day_minutes) / len(day_minutes)))


def within_limit(day_word_counts: list[int]) -> bool:
    """No day longer than MAX_OVER x the plan's stated minutes a day."""
    limit = estimate_minutes(day_word_counts) * MAX_OVER
    return all(w / WORDS_PER_MINUTE <= limit for w in day_word_counts)


def chapter_cuts(tiers: list[int]) -> list[int]:
    """A cut at every chapter end except the last verse."""
    return [i for i in range(len(tiers) - 1) if tiers[i] >= CHAPTER]


def plan_cuts(words: list[int], tiers: list[int], days: int) -> list[int]:
    """
    The cuts for a single-track plan: a chapter a day when the reading has exactly [days] chapters and that
    passes the length rule; otherwise the loosest snapping that passes; failing all, verse-level cuts.
    """
    chapters = chapter_cuts(tiers)
    if len(chapters) == days - 1:
        if within_limit(day_words(words, chapters)):
            return chapters
    cuts: list[int] = []
    for tolerance in SNAP_STEPS:
        cuts = split_cuts(words, tiers, days, tolerance)
        if within_limit(day_words(words, cuts)):
            return cuts
    return cuts


def days_for_minutes(total_words: int, minutes: int, verses: int) -> int:
    """Days needed at [minutes] a day (rounded up), at least 1 and at most one verse a day."""
    if minutes < 1:
        raise ValueError("minutes must be >= 1")
    return max(1, min(verses, math.ceil(total_words / (minutes * WORDS_PER_MINUTE))))
