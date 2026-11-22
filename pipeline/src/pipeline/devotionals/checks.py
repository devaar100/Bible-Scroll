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

CHECKER_VERSION = "devotionals-6"  # 6: "listed" is pipeline wording only when it points at the input
# 5: standalone blocks series/calendar references only; "you must" only as a
# sentence-opening command; "word count" passes before a quoted word
# 4: advisory repeated_openings report (per month, never blocking)
# 3: stands alone, no dates/seasons, only listed passages, Hebrews, health, pipeline wording
FIELDS = ("context", "reflect", "applyIt", "prayer")
OPTIONAL = ("prayer",)
#: Everything a person must fix before approving. Rubric scores and reviewer notes don't block: approving
#: is how a person signs those off.
BLOCKING = {"schema", "length", "reference", "original_language", "banned_phrase", "calendar", "standalone",
            "reference_not_allowed", "pipeline_wording", "authorship", "health"}

import re as _re

#: Each devotional is read alone, on any day of any year (prompt rule 9 and 10). Blocks references to the series or
#: the calendar ("tomorrow's reading", "yesterday's", "last time we", "this week's", "come back tomorrow", "this
#: season", dates); everyday "next time you feel…", "next week", "tomorrow" inside a sentence pass (devotionals-5).
STANDALONE = [_re.compile(p, _re.I) for p in (
    r"\b(?:tomorrow|today|next|last)['’]s (?:devotional|reading|passage|entry|study|lesson)s?\b",
    r"\byesterday['’]s\b", r"\b(?:last|next) time we\b", r"\bthis week['’]s\b",
    r"\bcome back (?:tomorrow|next|later|again|here|to (?:this|these|the|today['’]s) "
    r"(?:devotional|reading|page|passage)s?)\b",
    r"\bday \d+ of \d+\b", r"\b(earlier|later) in (this|the) series\b", r"\bas we saw\b",
    r"\bgood friday\b", r"\beaster (sunday|morning|day)\b", r"\bholy week\b", r"\bpalm sunday\b",
    r"\bthis (lent|advent|easter|christmas|season)\b", r"\bthis morning\b",
)]
#: "listed" only where it points at the writer's input ("the passages listed", "listed above", "listed under"); a
#: list in the text itself ("The offers listed are costly ones", Micah 6:6-7) passes (devotionals-6).
DEVOTIONAL_PIPELINE_WORDS = [_re.compile(p, _re.I) for p in (
    r"\bthe theme\b", r"\bthe series\b", r"\brelated passages?\b", r"\bthe passage given\b",
    r"\blisted (?:above|below|here|under)\b", r"\bas listed\b",
    r"\b(?:passages?|verses?|words?|references?|readings?|texts?) (?:you were |that were |that are |are )?listed\b",
    r"\blisted (?:passages?|verses?|words?|references?|readings?|texts?)\b",
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
    if judge is None or not (rubric_always or not reasons):
        return reviewed(reasons, None)
    from ..rubric import RubricUnavailable
    try:
        result = judge.judge(devotional, *review_input(devotional, bible))
    except RubricUnavailable as error:
        return Outcome("unchanged", reasons, None, str(error))
    return reviewed(reasons, result)


def review_input(devotional: dict, bible: Bible) -> tuple:
    """(passage text, rubric subject) for judge.judge. Reads the Bible database: call on the main thread."""
    text = passage_text(devotional["verses"][0], bible.bible_db) if bible.bible_db else ""
    return text, rubric_subject(devotional)


def reviewed(reasons: list[dict], result) -> Outcome:
    """The outcome once the reviewer answered (result: a RubricResult, or None when it didn't run). For devotionals
    every reason, the reviewer's included, flags."""
    if result is None:
        return Outcome("flagged" if reasons else "auto_checked", reasons, None)
    reasons = reasons + result.reasons
    return Outcome("flagged" if reasons else "auto_checked", reasons, result.rubric)


def apply(devotional: dict, outcome: Outcome) -> dict:
    return study_checks.apply(devotional, outcome, CHECKER_VERSION)


@dataclass
class RunSummary:
    checked: int = 0
    statuses: Counter = field(default_factory=Counter)
    reasons: Counter = field(default_factory=Counter)
    skipped: int = 0
    rubric_unavailable: list[str] = field(default_factory=list)


#: Reviewer requests in flight at once (`devotionals check --workers N`).
CHECK_WORKERS = 8


def run(store: DevotionalStore, days: list[Day], bible: Bible, judge=None, *, recheck: bool = False,
        rubric_always: bool = False, ids: list[str] | None = None, limit: int | None = None,
        workers: int = CHECK_WORKERS) -> RunSummary:
    """Check devotionals with status 'generated' (with recheck, also auto_checked/flagged). Reviewed: never.

    Code checks run here; devotionals that pass go to the reviewer on up to [workers] threads (judge.judge is the
    only thing a worker does). Each devotional is saved here as its review comes in; stopping keeps what was
    saved, and a re-run checks the rest."""
    from concurrent.futures import FIRST_COMPLETED, ThreadPoolExecutor, wait

    from ..rubric import RubricUnavailable

    rules, schema = load_rules(), validator()
    by_id = {d.id: d for d in days}
    wanted = {"generated", "auto_checked", "flagged"} if recheck else {"generated"}
    summary = RunSummary()
    workers = max(1, workers)
    pending: dict = {}
    started = 0

    def finish(devotional: dict, outcome: Outcome) -> None:
        updated = apply(devotional, outcome)
        store.save(updated)
        summary.checked += 1
        summary.statuses[updated["status"]] += 1
        summary.reasons.update(r["check"] for r in updated["checks"]["reasons"])

    def collect(future) -> None:
        devotional, reasons = pending.pop(future)
        try:
            result = future.result()
        except RubricUnavailable as error:
            summary.rubric_unavailable.append(f"{devotional['id']}: {error}")
            return
        finish(devotional, reviewed(reasons, result))

    pool = ThreadPoolExecutor(max_workers=workers) if judge is not None else None
    try:
        for devotional_id in ids or store.ids():
            if limit is not None and started >= limit:
                break
            devotional = store.load(devotional_id)
            if devotional.get("status") not in wanted:
                summary.skipped += 1
                continue
            started += 1
            reasons = free_checks(devotional, by_id.get(devotional_id), bible, rules, schema)
            if judge is None or not (rubric_always or not reasons):
                finish(devotional, reviewed(reasons, None))
                continue
            pending[pool.submit(judge.judge, devotional, *review_input(devotional, bible))] = (devotional, reasons)
            while len(pending) >= 2 * workers:
                done, _ = wait(pending, return_when=FIRST_COMPLETED)
                for future in done:
                    collect(future)
        while pending:
            done, _ = wait(pending, return_when=FIRST_COMPLETED)
            for future in done:
                collect(future)
    finally:
        if pool is not None:
            pool.shutdown(wait=not pending, cancel_futures=True)
    return summary


# ---- Advisory: repeated openings across a month (never blocking) ------------------------------------------

#: An opening (first three words of a section) may appear this many times in one month before it is reported.
OPENING_MAX_PER_MONTH = 2
OPENING_SECTIONS = ("title", "context", "reflect", "applyIt", "prayer")


def opening(text: str, words: int = 3) -> str | None:
    """First [words] words, lower-cased, punctuation dropped ("Today, before the day" -> "today before the")."""
    found = _re.findall(r"[\w’']+", (text or "").lower())
    return " ".join(found[:words]) if len(found) >= words else None


def repeated_openings(devotionals: list[dict], max_uses: int = OPENING_MAX_PER_MONTH) -> list[dict]:
    """Advisory only: every (month, section, opening) used more than [max_uses] times. Never changes a status."""
    from collections import defaultdict

    seen: dict = defaultdict(list)
    for d in devotionals:
        month = d.get("date", "")[:2]
        for section in OPENING_SECTIONS:
            o = opening(d.get(section, ""))
            if o:
                seen[(month, section, o)].append(d.get("date"))
    return [{"month": m, "section": s, "opening": o, "count": len(dates), "dates": sorted(dates)}
            for (m, s, o), dates in sorted(seen.items()) if len(dates) > max_uses]
