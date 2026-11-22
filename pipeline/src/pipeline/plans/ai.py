"""
AI-drafted plans: topical (passages + short daily intros) and chronological (reading order only).

Claude drafts; code checks. Every draft is validated (passages exist, days not empty, day lengths) and saved
as "auto_checked" or "flagged"; none ships until a person approves it in the review tool (status "reviewed").
The chronological plan is always flagged: reading-order choices differ between published chronologies.

Model: Claude Opus 5.5 with JSON-schema structured output, adaptive thinking (always on for this model) at
high effort, streaming (long outputs), and the server-side refusal fallback enabled.
"""
from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Protocol

from ..books import BOOKS, BY_USFM
from .bible import VerseIndex, minutes
from .build import (PLANS_DIR, SNAP_STEPS, content_version, difficulty, estimate_minutes, load_plans, now,
                    write_json)
from .split import split_days, to_passages
from .validate import CHECKER_VERSION, check_plan, day_words, verse_coverage

MODEL = "claude-opus-5-5"
AI_CREDITS = ("Passages and daily introductions drafted with AI help, then checked and approved by a person "
              "before publishing.")
CHRONO_ID = "bible-chronological"

BOOK_CODES = [b.usfm for b in BOOKS]


class DraftRefused(Exception):
    """The model declined (stop_reason refusal, even after the fallback)."""


class DraftFailed(Exception):
    """Unusable response (cut off, not JSON). Safe to retry."""


@dataclass(frozen=True)
class Draft:
    data: dict
    model: str


class Drafter(Protocol):
    def draft(self, system: str, prompt: str, schema: dict, max_tokens: int) -> Draft: ...


class AnthropicDrafter:
    """Credentials from the environment (ANTHROPIC_API_KEY or `ant auth login`)."""

    def __init__(self, client=None, model: str = MODEL, effort: str = "high", max_retries: int | None = None):
        import anthropic

        from ..offline import anthropic_client

        if client is None:
            client = anthropic_client()
            if max_retries is not None:
                client = client.with_options(max_retries=max_retries)
        self.client = client
        self.model = model
        self.effort = effort

    def draft(self, system: str, prompt: str, schema: dict, max_tokens: int = 64000) -> Draft:
        with self.client.beta.messages.stream(
            model=self.model,
            max_tokens=max_tokens,
            betas=["server-side-fallback-2026-07-01"],
            fallbacks="default",
            system=system,
            messages=[{"role": "user", "content": prompt}],
            output_config={"effort": self.effort, "format": {"type": "json_schema", "schema": schema}},
        ) as stream:
            message = stream.get_final_message()
        if message.stop_reason == "refusal":
            raise DraftRefused(f"declined (request {getattr(message, '_request_id', '?')})")
        if message.stop_reason == "max_tokens":
            raise DraftFailed("response was cut off at max_tokens")
        text = next((b.text for b in message.content if b.type == "text"), None)
        if text is None:
            raise DraftFailed("no text in the response")
        try:
            return Draft(json.loads(text), message.model)
        except json.JSONDecodeError as error:
            raise DraftFailed(f"not JSON: {error}") from error


# ---- Prompts and output schemas ---------------------------------------------------------------------------

SYSTEM = """You help build reading plans for a free Bible-reading app used by Christians of many traditions \
(Catholic, Orthodox, Protestant and others) and by people exploring faith. Use the 66-book Protestant canon and \
English (KJV/NRSV) chapter and verse numbering. Choose real, complete passages: a passage starts and ends at \
natural breaks, never mid-sentence. Prefer well-known, widely loved passages, and passages whose plain meaning \
speaks to the theme; don't build on a single verse taken out of context.

Writing style: short, warm and plain. No guilt or pressure, no promises Scripture doesn't make, no claims that \
everyone agrees, and no positions that Christian traditions dispute (for example on baptism, predestination, \
the end times or church authority). Intros invite the reader into the passage; they don't preach."""

PASSAGE_SCHEMA = {
    "type": "object",
    "properties": {
        "book": {"type": "string", "enum": BOOK_CODES},
        "startChapter": {"type": "integer"},
        "startVerse": {"type": "integer"},
        "endChapter": {"type": "integer"},
        "endVerse": {"type": "integer"},
    },
    "required": ["book", "startChapter", "startVerse", "endChapter", "endVerse"],
    "additionalProperties": False,
}

TOPICAL_SCHEMA = {
    "type": "object",
    "properties": {
        "title": {"type": "string"},
        "shortDescription": {"type": "string"},
        "description": {"type": "string"},
        "days": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "title": {"type": "string"},
                    "intro": {"type": "string"},
                    "passages": {"type": "array", "items": PASSAGE_SCHEMA},
                },
                "required": ["title", "intro", "passages"],
                "additionalProperties": False,
            },
        },
    },
    "required": ["title", "shortDescription", "description", "days"],
    "additionalProperties": False,
}


def topical_prompt(topic: str, days: int, minutes_per_day: int) -> str:
    return f"""Draft a {days}-day reading plan on the theme: {topic}.

For each day give:
- a title of at most 6 words;
- an intro of 2 or 3 sentences (at most 60 words) that sets up the reading without summarising it;
- 1 to 3 passages that together take about {minutes_per_day} minutes to read (roughly {minutes_per_day * 200} words).

Also give a plan title of at most 5 words, a one-line shortDescription (at most 100 characters) and a description \
of 2 or 3 sentences (at most 60 words) saying who the plan is for. Return exactly {days} days. Don't repeat a passage."""


CHRONO_SCHEMA = {
    "type": "object",
    "properties": {
        "ranges": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "book": {"type": "string", "enum": BOOK_CODES},
                    "startChapter": {"type": "integer"},
                    "endChapter": {"type": "integer"},
                },
                "required": ["book", "startChapter", "endChapter"],
                "additionalProperties": False,
            },
        },
    },
    "required": ["ranges"],
    "additionalProperties": False,
}

CHRONO_PROMPT = """List every chapter of the Bible once, in an approximate chronological reading order: the \
order in which the events happened or the books were most likely written, following a widely used mainstream \
chronology. Place each psalm group, prophet and letter near the events it belongs to.

Give the answer as consecutive chapter ranges (book, startChapter, endChapter). Every one of the 1,189 chapters \
must appear exactly once. Use whole chapters only."""


# ---- Building plans from drafts -------------------------------------------------------------------------

def slug(text: str) -> str:
    return "-".join("".join(ch if ch.isalnum() else " " for ch in text.lower()).split())


def _apply_checks(plan: dict, reasons: list[dict], always_flag: list[dict] | None = None) -> dict:
    all_reasons = [*(always_flag or []), *reasons]
    plan["checks"] = {"checkedAt": now(), "checkerVersion": CHECKER_VERSION, "reasons": all_reasons}
    plan["status"] = "flagged" if all_reasons else "auto_checked"
    return plan


def plan_from_topical(topic: str, draft: Draft, days: int, index: VerseIndex) -> dict:
    data = draft.data
    plan = {
        "id": f"{slug(topic)}-{days}-days", "schemaVersion": 1, "contentVersion": "",
        "title": data["title"].strip(), "shortDescription": data["shortDescription"].strip(),
        "description": data["description"].strip(), "category": "topics-and-seasons",
        "days": [
            {"dayNumber": i + 1, "title": d["title"].strip(), "intro": d["intro"].strip(), "passages": d["passages"]}
            for i, d in enumerate(data["days"])
        ],
        "status": "generated", "origin": "ai_drafted", "credits": AI_CREDITS,
        "meta": {"model": draft.model, "generatedAt": now()},
    }
    if len(plan["days"]) != days:
        extra = [{"check": "day_numbers", "message": f"asked for {days} days, got {len(plan['days'])}"}]
    else:
        extra = []
    _finish_ai(plan, index)
    return _apply_checks(plan, check_plan(plan, index), extra)


def _finish_ai(plan: dict, index: VerseIndex) -> None:
    valid_days = [d for d in plan["days"] if d["passages"]]
    per_day = [minutes(day_words(d, index)) for d in valid_days] or [1.0]
    plan["estimatedMinutesPerDay"] = estimate_minutes(per_day)
    plan["difficulty"] = difficulty(len(plan["days"]), plan["estimatedMinutesPerDay"])
    plan["contentVersion"] = content_version(plan)


def coverage_problems(ranges: list[dict], index: VerseIndex) -> tuple[list[tuple[int, int]], list[dict]]:
    """The chapter order the ranges describe, and every missing/duplicate/invalid chapter as a reason."""
    order: list[tuple[int, int]] = []
    reasons: list[dict] = []
    for r in ranges:
        book = BY_USFM.get(r["book"])
        if book is None or not 1 <= r["startChapter"] <= r["endChapter"] <= index.chapter_count(r["book"]):
            reasons.append({"check": "coverage", "message": f"invalid range {r}"})
            continue
        order += [(book.id, c) for c in range(r["startChapter"], r["endChapter"] + 1)]
    every = {(b, c) for b, c, _ in index.order}
    seen: set[tuple[int, int]] = set()
    for chapter in order:
        if chapter in seen:
            reasons.append({"check": "coverage", "message": f"{BOOKS[chapter[0] - 1].name} {chapter[1]} appears twice"})
        seen.add(chapter)
    for b, c in sorted(every - seen):
        reasons.append({"check": "coverage", "message": f"{BOOKS[b - 1].name} {c} is missing"})
    unique = list(dict.fromkeys(order))  # keep first occurrence of a duplicate
    return unique, reasons


def plan_from_chronological(draft: Draft, index: VerseIndex, days: int = 365) -> dict:
    order, coverage = coverage_problems(draft.data["ranges"], index)
    sequence = [v for (b, c) in order for v in index.chapters(BOOKS[b - 1].usfm, c, c)]
    verification = [{"check": "verification", "message":
                     "AI-drafted reading order. Check it against a published chronology before approving; "
                     "sources differ on where books like Job, the Psalms and the prophets belong."}]
    plan: dict = {}
    for tolerance in SNAP_STEPS:
        split = split_days(sequence, days, index.chapter_ends, tolerance)
        plan = {
            "id": CHRONO_ID, "schemaVersion": 1, "contentVersion": "",
            "title": "The Bible in chronological order",
            "shortDescription": "The whole Bible in a year, in the order events happened.",
            "description": "Read the whole Bible over a year in an approximate historical order: the psalms beside "
                           "the stories they belong to, the prophets beside the kings they spoke to, and Paul's "
                           "letters within the story of Acts. Chronologies differ, so treat the order as one "
                           "careful suggestion.",
            "category": "year-round-habits",
            "days": [{"dayNumber": i + 1, "passages": to_passages(day, index.position)} for i, day in enumerate(split)],
            "status": "generated", "origin": "ai_drafted", "credits": AI_CREDITS,
            "meta": {"model": draft.model, "generatedAt": now()},
        }
        _finish_ai(plan, index)
        if not [r for r in check_plan(plan, index) if r["check"] == "day_length"]:
            break
    return _apply_checks(plan, check_plan(plan, index), verification + coverage)


# ---- Running ----------------------------------------------------------------------------------------

def save_draft(plan: dict, force: bool = False) -> str:
    """Write a draft unless that would replace a plan a person already reviewed (then require force)."""
    existing = load_plans().get(plan["id"])
    if existing and existing.get("status") == "reviewed" and not force:
        return f"skipped {plan['id']}: already reviewed (use --force to replace)"
    if existing and existing.get("origin") == "structural":
        return f"skipped {plan['id']}: a structural plan has that id"
    write_json(PLANS_DIR / f"{plan['id']}.json", plan)
    return f"{plan['id']}: {plan['status']}" + (
        f" ({len(plan['checks']['reasons'])} reason(s))" if plan["checks"]["reasons"] else "")


def draft_topical(topics: list[str], drafter: Drafter, index: VerseIndex, days: int = 7,
                  minutes_per_day: int = 5, force: bool = False) -> list[str]:
    results = []
    for topic in topics:
        try:
            draft = drafter.draft(SYSTEM, topical_prompt(topic, days, minutes_per_day), TOPICAL_SCHEMA, 16000)
        except (DraftRefused, DraftFailed) as error:
            results.append(f"{topic}: not drafted ({error})")
            continue
        results.append(save_draft(plan_from_topical(topic, draft, days, index), force))
    return results


def draft_chronological(drafter: Drafter, index: VerseIndex, force: bool = False) -> str:
    try:
        draft = drafter.draft(SYSTEM, CHRONO_PROMPT, CHRONO_SCHEMA, 64000)
    except (DraftRefused, DraftFailed) as error:
        return f"chronological: not drafted ({error})"
    return save_draft(plan_from_chronological(draft, index), force)


def revalidate(plan: dict, index: VerseIndex) -> dict:
    """
    Re-check an AI plan after edits. Keeps reviewer/regenerate notes and the chronological plan's verification
    flag; recomputes everything else, including whole-Bible coverage for the chronological plan.
    """
    kept = [r for r in plan.get("checks", {}).get("reasons", []) if r["check"] in ("reviewer", "regenerate", "verification")]
    _finish_ai(plan, index)
    reasons = check_plan(plan, index)
    if plan["id"] == CHRONO_ID:
        reasons += verse_coverage(plan, index)
    return _apply_checks(plan, reasons, kept)
