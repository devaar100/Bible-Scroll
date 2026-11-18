"""Plan validator: passages exist, days aren't empty, no day runs over the stated time by more than 50%."""
from __future__ import annotations

import json
from pathlib import Path

from jsonschema import Draft202012Validator

from ..books import BY_USFM
from ..paths import PIPELINE
from .bible import VerseIndex, minutes

SCHEMA_PATH = PIPELINE / "schema" / "plan.json"
CHECKER_VERSION = "plans-1"
MAX_OVER = 1.5  # a day may run to 150% of estimatedMinutesPerDay


def schema_validator() -> Draft202012Validator:
    schema = json.loads(SCHEMA_PATH.read_text())
    Draft202012Validator.check_schema(schema)
    return Draft202012Validator(schema, format_checker=Draft202012Validator.FORMAT_CHECKER)


def passage_problem(p: dict, index: VerseIndex) -> str | None:
    book = BY_USFM.get(p.get("book", ""))
    if book is None:
        return f"unknown book {p.get('book')!r}"
    start = (book.id, p["startChapter"], p["startVerse"])
    end = (book.id, p["endChapter"], p["endVerse"])
    for key in (start, end):
        if not index.exists(key):
            return f"{book.name} {key[1]}:{key[2]} does not exist"
    if start > end:
        return f"{label(p)} runs backwards"
    return None


def day_words(day: dict, index: VerseIndex) -> int:
    total = 0
    for p in day["passages"]:
        if passage_problem(p, index) is None:
            book = BY_USFM[p["book"]].id
            total += sum(v.words for v in index.verses((book, p["startChapter"], p["startVerse"]),
                                                        (book, p["endChapter"], p["endVerse"])))
    return total


def label(p: dict) -> str:
    name = BY_USFM[p["book"]].name if p.get("book") in BY_USFM else p.get("book")
    if p["startChapter"] == p["endChapter"]:
        return f"{name} {p['startChapter']}:{p['startVerse']}-{p['endVerse']}"
    return f"{name} {p['startChapter']}:{p['startVerse']}-{p['endChapter']}:{p['endVerse']}"


def check_plan(plan: dict, index: VerseIndex, validator: Draft202012Validator | None = None) -> list[dict]:
    """Every problem as a checks reason (empty list = valid)."""
    reasons = [{"check": "schema", "message": f"{e.json_path}: {e.message}"}
               for e in (validator or schema_validator()).iter_errors(plan)]
    days = plan.get("days", [])
    numbers = [d.get("dayNumber") for d in days]
    if numbers != list(range(1, len(days) + 1)):
        reasons.append({"check": "day_numbers", "message": f"days must be numbered 1..{len(days)} in order"})
    limit = plan.get("estimatedMinutesPerDay", 0) * MAX_OVER
    for day in days:
        n = day.get("dayNumber", 0)
        if not day.get("passages"):
            reasons.append({"check": "empty_day", "message": "day has no passages", "day": n})
            continue
        for p in day["passages"]:
            problem = passage_problem(p, index)
            if problem:
                reasons.append({"check": "passage", "message": problem, "day": n})
        took = minutes(day_words(day, index))
        if limit and took > limit:
            reasons.append({"check": "day_length", "day": n, "message":
                            f"about {took:.0f} min; the plan says {plan['estimatedMinutesPerDay']} min/day "
                            f"(max {limit:.0f})"})
    return reasons


def verse_coverage(plan: dict, index: VerseIndex, expected: set | None = None) -> list[dict]:
    """
    For whole-Bible plans: every expected verse (default: all of them) read exactly once. Returns coverage
    reasons, summarised per chapter so a gap reads "Psalm 23 is missing", not 6 verse lines.
    """
    expected = set(index.order) if expected is None else expected
    seen: dict = {}
    for day in plan.get("days", []):
        for p in day.get("passages", []):
            if passage_problem(p, index):
                continue
            book = BY_USFM[p["book"]].id
            for v in index.verses((book, p["startChapter"], p["startVerse"]), (book, p["endChapter"], p["endVerse"])):
                seen[v.key] = seen.get(v.key, 0) + 1
    reasons = []
    names = {b.id: b.name for b in BY_USFM.values()}
    for label_, keys in (("missing", sorted(expected - set(seen))), ("read twice", sorted(k for k, n in seen.items() if n > 1))):
        chapters = sorted({k[:2] for k in keys})
        for b, c in chapters:
            reasons.append({"check": "coverage", "message": f"{names[b]} {c}: verses {label_}"})
    return reasons
