"""
Auto-checker for devotionals: the study checks (lengths, references, original language, banned phrases, AI
rubric) applied to the devotional's sections, plus a calendar check (the file still matches its calendar row).

Same contract as studies: free checks first; the rubric runs only when they pass (or with rubric_always), and
an API outage leaves the devotional unchanged rather than flagging it.
"""
from __future__ import annotations

from collections import Counter
from dataclasses import dataclass, field

from jsonschema import Draft202012Validator

from .. import checks as study_checks
from ..books import BY_USFM
from ..checks import Outcome, Rules, reason
from ..paths import APP_BIBLE_DB
from ..store import Bible
from .calendar import Day
from .calendar import parse_anchor
from .store import (DevotionalStore, passage_label, passage_text, passage_words, related_passages, to_passage,
                    validator)

CHECKER_VERSION = "devotionals-3"  # 3: stands alone, no dates/seasons, only listed passages, Hebrews, health, pipeline wording
FIELDS = ("context", "reflect", "applyIt", "prayer")
OPTIONAL = ("prayer",)
#: Everything a person must fix before approving. Rubric scores and reviewer notes don't block: approving
#: is how a person signs those off.
BLOCKING = {"schema", "length", "reference", "original_language", "banned_phrase", "calendar", "standalone",
            "reference_not_allowed", "pipeline_wording", "authorship", "health"}

import re as _re

#: Each devotional is read alone, on any day of any year (prompt rule 9 and 10).
STANDALONE = [_re.compile(p, _re.I) for p in (
    r"\byesterday\b", r"\btomorrow\b", r"\b(this|last|next) week\b", r"\bday \d+ of \d+\b",
    r"\b(earlier|later) in (this|the) series\b", r"\bas we saw\b", r"\blast time\b", r"\bnext time\b",
    r"\bgood friday\b", r"\beaster (sunday|morning|day)\b", r"\bholy week\b", r"\bpalm sunday\b",
    r"\bthis (lent|advent|easter|christmas|season)\b", r"\bthis morning\b",
)]
DEVOTIONAL_PIPELINE_WORDS = [_re.compile(p, _re.I) for p in (
    r"\bthe theme\b", r"\bthe series\b", r"\brelated passages?\b", r"\bthe passage given\b", r"\blisted\b",
)]
HEBREWS_BY_PAUL = _re.compile(r"\bPaul(?:'s|’s)?\s+(?:letter|epistle)\s+to\s+the\s+Hebrews\b|\bPaul\s+wrote\s+(?:to\s+the\s+)?Hebrews\b", _re.I)
FAITH_INSTEAD_OF_CARE = _re.compile(
    r"\b(instead of|rather than|no need (for|to see)|don'?t need|without)\s+(?:\w+\s+){0,2}"
    r"(doctors?|medicine|medication|therapy|therapists?|counsell?ors?|treatment)\b", _re.I)


def _scan(devotional: dict, patterns, check: str, label: str, passage: str = "") -> list[dict]:
    """[passage]: words the day's own passage uses are allowed (Matthew 6:34 is about "tomorrow")."""
    out = []
    for section in ("title", *FIELDS):
        text = devotional.get(section, "")
        for pattern in patterns:
            m = pattern.search(text)
            if m and not (passage and pattern.search(passage)):
                out.append(reason(check, f"“{m.group(0)}”: {label}", section))
    return out


def check_standalone(devotional: dict, passage: str = "") -> list[dict]:
    return _scan(devotional, STANDALONE, "standalone", "devotionals stand alone and don't assume the date or season", passage)


def check_devotional_wording(devotional: dict) -> list[dict]:
    return (_scan(devotional, DEVOTIONAL_PIPELINE_WORDS, "pipeline_wording", "talks about the prompt, not to the reader")
            + _scan(devotional, [HEBREWS_BY_PAUL], "authorship", "the author of Hebrews is unknown")
            + _scan(devotional, [FAITH_INSTEAD_OF_CARE], "health", "faith never replaces medical or professional help"))


def check_allowed_passages(devotional: dict, bible: Bible, day: Day) -> list[dict]:
    """extraVerses must come from the related passages listed in the request, and prose may cite only the anchor's
    chapter(s) and those passages (the study rule "cite only supplied references")."""
    related = related_passages(to_passage(day.anchor), bible.bible_db or APP_BIBLE_DB)
    out = []
    for extra in devotional.get("verses", [])[1:]:
        label = (f"{extra['book']}.{extra['startChapter']}.{extra['startVerse']}" +
                 ("" if (extra["startChapter"], extra["startVerse"]) == (extra["endChapter"], extra["endVerse"])
                  else f"-{extra['book']}.{extra['endChapter']}.{extra['endVerse']}"))
        if label not in related:
            out.append(reason("reference_not_allowed", f"{passage_label(extra)} isn't one of the related passages", "verses"))
    allowed = set()
    anchor = to_passage(day.anchor)
    book = BY_USFM[anchor["book"]].id
    allowed |= {k for k in bible.verses if k[0] == book and anchor["startChapter"] <= k[1] <= anchor["endChapter"]}
    for ref in related:
        a = parse_anchor(ref if "-" in ref else f"{ref}-{ref}")
        allowed |= set(study_checks._expand(BY_USFM[a.book].id, a.start, a.end, bible.verses))
    here = (book, anchor["startChapter"], anchor["startVerse"])
    for section in FIELDS:
        for ref in study_checks.find_references(devotional.get(section, ""), here):
            if study_checks.check_reference(ref, bible.verses, bible.chapters):
                continue  # doesn't exist: check_references reports it
            span = study_checks._expand(ref.book_id, ref.start, ref.end, bible.verses)
            ok = (span & allowed) if ref.start[1] is None else span <= allowed
            if not ok:
                out.append(reason("reference_not_allowed", f"“{ref.text}” isn't this passage's chapter or a related passage", section))
    return out


def load_rules() -> Rules:
    return Rules.load(limits_file="devotional_limits.json", fields=FIELDS)


def check_calendar(devotional: dict, day: Day | None) -> list[dict]:
    if day is None:
        return [reason("calendar", f"{devotional.get('date')} isn't in the calendar")]
    expected = {
        "date": day.date, "dayOfYear": day.day_of_year, "theme": day.theme, "coverStyle": day.cover_style,
        "series": {"title": day.series, "season": day.season, "day": day.series_day, "length": day.series_length},
    }
    out = [reason("calendar", f"{k} is {devotional.get(k)!r}; the calendar says {v!r}")
           for k, v in expected.items() if devotional.get(k) != v]
    verses = devotional.get("verses") or [{}]
    if verses[0] != to_passage(day.anchor):
        out.append(reason("calendar", f"first passage isn't the calendar's anchor {day.anchor.text}", "verses"))
    return out


def check_verses(devotional: dict, bible: Bible) -> list[dict]:
    out = []
    for p in devotional.get("verses", []):
        book = BY_USFM.get(p.get("book"))
        if book is None:
            out.append(reason("reference", f"unknown book {p.get('book')!r}", "verses"))
            continue
        start, end = (book.id, p["startChapter"], p["startVerse"]), (book.id, p["endChapter"], p["endVerse"])
        missing = [k for k in (start, end) if k not in bible.verses]
        if missing:
            out.append(reason("reference", f"{book.name} {missing[0][1]}:{missing[0][2]} doesn't exist", "verses"))
        elif start > end:
            out.append(reason("reference", f"{passage_label(p)} runs backwards", "verses"))
    return out


def free_checks(devotional: dict, day: Day | None, bible: Bible, rules: Rules,
                schema: Draft202012Validator) -> list[dict]:
    reasons = study_checks.check_schema(devotional, schema)
    reasons += check_calendar(devotional, day)
    if day is None or any(r["section"] == "verses" for r in reasons if "section" in r):
        return reasons  # can't anchor the prose checks
    anchor = to_passage(day.anchor)
    here = (BY_USFM[anchor["book"]].id, anchor["startChapter"], anchor["startVerse"])
    words = []
    for p in devotional["verses"]:
        if p.get("book") in BY_USFM:
            words += passage_words(p, bible.source_db, include_common=True)
    reasons += check_verses(devotional, bible)
    reasons += study_checks.check_lengths(devotional, rules, FIELDS, OPTIONAL)
    reasons += study_checks.check_references(devotional, bible, FIELDS, here)
    reasons += study_checks.check_original_language(devotional, bible, ("title", *FIELDS), words, "these passages")
    reasons += study_checks.check_banned(devotional, rules, ("title", *FIELDS))
    reasons += study_checks.check_pipeline_wording(devotional, rules, ("title", *FIELDS))
    reasons += check_standalone(devotional, passage_text(anchor, bible.bible_db or APP_BIBLE_DB))
    reasons += check_devotional_wording(devotional)
    reasons += check_allowed_passages(devotional, bible, day)
    return reasons


def rubric_subject(devotional: dict):
    from ..rubric import DEVOTIONAL_SYSTEM_PROMPT, Subject
    label = passage_label(devotional["verses"][0])
    return Subject(f"Theme: {devotional['theme']}. Passage: {label} (WEB)",
                   {"title": "Title", "context": "Context", "reflect": "Reflect", "applyIt": "Apply it",
                    "prayer": "Prayer"}, DEVOTIONAL_SYSTEM_PROMPT)


def check_devotional(devotional: dict, day: Day | None, bible: Bible, rules: Rules,
                     schema: Draft202012Validator, judge=None, rubric_always: bool = False) -> Outcome:
    """Never mutates [devotional]; checks.apply(..., CHECKER_VERSION) writes the outcome."""
    reasons = free_checks(devotional, day, bible, rules, schema)
    rubric = None
    if judge is not None and (rubric_always or not reasons):
        from ..rubric import RubricUnavailable
        try:
            text = passage_text(devotional["verses"][0], bible.bible_db) if bible.bible_db else ""
            result = judge.judge(devotional, text, rubric_subject(devotional))
        except RubricUnavailable as error:
            return Outcome("unchanged", reasons, None, str(error))
        rubric = result.rubric
        reasons += result.reasons
    return Outcome("flagged" if reasons else "auto_checked", reasons, rubric)


def apply(devotional: dict, outcome: Outcome) -> dict:
    return study_checks.apply(devotional, outcome, CHECKER_VERSION)


@dataclass
class RunSummary:
    checked: int = 0
    statuses: Counter = field(default_factory=Counter)
    reasons: Counter = field(default_factory=Counter)
    skipped: int = 0
    rubric_unavailable: list[str] = field(default_factory=list)


def run(store: DevotionalStore, days: list[Day], bible: Bible, judge=None, *, recheck: bool = False,
        rubric_always: bool = False, ids: list[str] | None = None, limit: int | None = None) -> RunSummary:
    """Check devotionals with status 'generated' (with recheck, also auto_checked/flagged). Reviewed: never."""
    rules, schema = load_rules(), validator()
    by_id = {d.id: d for d in days}
    wanted = {"generated", "auto_checked", "flagged"} if recheck else {"generated"}
    summary = RunSummary()
    for devotional_id in ids or store.ids():
        if limit is not None and summary.checked >= limit:
            break
        devotional = store.load(devotional_id)
        if devotional.get("status") not in wanted:
            summary.skipped += 1
            continue
        outcome = check_devotional(devotional, by_id.get(devotional_id), bible, rules, schema, judge, rubric_always)
        if outcome.status == "unchanged":
            summary.rubric_unavailable.append(f"{devotional_id}: {outcome.error}")
            continue
        updated = apply(devotional, outcome)
        store.save(updated)
        summary.checked += 1
        summary.statuses[updated["status"]] += 1
        summary.reasons.update(r["check"] for r in updated["checks"]["reasons"])
    return summary
