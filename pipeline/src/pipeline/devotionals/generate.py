"""
Draft devotionals from the calendar with Claude, using prompts/devotional.md.

Claude writes the prose; everything else (date, series, cover, anchor passage) comes from the calendar, and
the passage text and original-language words come from the app's Bible and source.db, so the model is never
asked to recall Scripture from memory. Drafts are saved as "generated"; `devotionals check` sets
auto_checked/flagged. Never overwrites a reviewed or hand-edited devotional without --force.
"""
from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path

from ..books import BOOKS
from ..paths import APP_BIBLE_DB, PIPELINE, SOURCE_DB
from ..plans.ai import AnthropicDrafter, Draft, DraftFailed, DraftRefused, Drafter
from ..store import now
from .store import related_passages
from .calendar import Day, parse_anchor
from .store import SCHEMA_VERSION, DevotionalStore, passage_label, passage_text, passage_words, to_passage

PROMPT_FILE = PIPELINE / "prompts" / "devotional.md"
LIMITS_FILE = PIPELINE / "checks" / "devotional_limits.json"
MAX_ORIGINAL_WORDS = 40
#: Devotionals are drafted by Claude Haiku 5.5 at effort medium, like the study launch run (decision 26); plans keep
#: their own model (plans.ai.MODEL). max_tokens 32,000: Haiku's thinking can pass 16,000 (decision 17).
DRAFT_MODEL = "claude-haiku-5-5"
DRAFT_EFFORT = "medium"
#: SDK retries (backoff, honours retry-after) on 429/5xx/connection errors; parallel drafts hit rate limits more.
DRAFT_MAX_RETRIES = 6
MAX_TOKENS = 32000
SECTIONS = ("context", "reflect", "applyIt", "prayer")
#: The kind of step "Apply it" asks for, rotated by day of year (leap-year numbering) so consecutive days never share
#: one: 8 types, and neither the year's wrap (366 -> 1) nor a non-leap year's 02-28 -> 03-01 lands on the same one.
APPLY_IT_ACTIONS = (
    "pause and breathe",
    "write one sentence",
    "say a short prayer aloud",
    "send a message to someone",
    "reread a verse slowly",
    "ask someone a question",
    "notice something during the day",
    "do one small thing for someone",
)


def apply_it_action(day: Day) -> str:
    return APPLY_IT_ACTIONS[(day.day_of_year - 1) % len(APPLY_IT_ACTIONS)]

#: Structured output. Lengths and reference formats are checked in code (and by `devotionals check`).
OUTPUT_SCHEMA = {
    "type": "object",
    "properties": {
        "title": {"type": "string"},
        "context": {"type": "string"},
        "reflect": {"type": "string"},
        "applyIt": {"type": "string"},
        "prayer": {"type": "string"},
        "extraVerses": {"type": "array", "items": {"type": "string"}},
    },
    "required": ["title", "context", "reflect", "applyIt", "prayer", "extraVerses"],
    "additionalProperties": False,
}
_MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October",
           "November", "December"]


@dataclass(frozen=True)
class Prompt:
    system: str
    request: str
    version: str  # sha256(file)[:12]

    @classmethod
    def load(cls, path: Path = PROMPT_FILE) -> "Prompt":
        text = path.read_text()
        body = re.sub(r"<!--.*?-->", "", text, flags=re.S)
        parts = re.split(r"^# (System|Request)\s*$", body, flags=re.M)
        sections = dict(zip(parts[1::2], (p.strip() for p in parts[2::2])))
        if set(sections) != {"System", "Request"}:
            raise ValueError(f"{path.name} needs exactly a '# System' and a '# Request' section")
        return cls(sections["System"], sections["Request"], hashlib.sha256(text.encode()).hexdigest()[:12])


def limits(path: Path = LIMITS_FILE) -> dict[str, dict[str, int]]:
    return json.loads(path.read_text())["words"]


def content_version(day: Day, prompt_version: str) -> str:
    row = [day.date, day.season, day.series, day.series_day, day.series_length, day.cover_style, day.theme,
           day.anchor.text, prompt_version]
    return hashlib.sha256(json.dumps(row).encode()).hexdigest()[:12]


def date_label(mmdd: str) -> str:
    return f"{int(mmdd[3:])} {_MONTHS[int(mmdd[:2]) - 1]}"


def fill(template: str, values: dict[str, str]) -> str:
    out = template
    for key, value in values.items():
        out = out.replace("{{" + key + "}}", value)
    left = re.findall(r"\{\{(\w+)\}\}", out)
    if left:
        raise ValueError(f"prompt placeholders without values: {left}")
    return out


def build_request(day: Day, prompt: Prompt, bible_db: Path = APP_BIBLE_DB, source_db: Path = SOURCE_DB,
                  word_limits: dict | None = None) -> str:
    p = to_passage(day.anchor)
    words = passage_words(p, source_db)[:MAX_ORIGINAL_WORDS]
    word_limits = word_limits or limits()
    values = {
        "date_label": date_label(day.date), "series": day.series, "season": day.season,
        "series_day": str(day.series_day), "series_length": str(day.series_length), "theme": day.theme,
        "anchor_label": passage_label(p), "passage_text": passage_text(p, bible_db),
        "original_words": "\n".join(f"- {w['transliteration']} ({w['surface']}) = {w['gloss']}" for w in words)
                          or "(none listed)",
        "book_codes": ", ".join(b.usfm for b in BOOKS),
        "related_passages": "\n".join(f"- {r}" for r in related_passages(p, bible_db)) or "(none)",
        "applyIt_action": apply_it_action(day),
        **{f"{k}_words": f"{v['min']}-{v['max']}" for k, v in word_limits.items()},
    }
    return fill(prompt.request, values)


def from_draft(day: Day, draft: Draft, prompt: Prompt) -> dict:
    """The working devotional. Raises DraftFailed for output the checks can't even read (bad extraVerses)."""
    data = draft.data
    extras = []
    for text in data.get("extraVerses", [])[:2]:
        try:
            anchor = parse_anchor(text if "-" in text else f"{text}-{text}")
        except ValueError as error:
            raise DraftFailed(f"extraVerses: {error}") from error
        if anchor.text != day.anchor.text:
            extras.append(to_passage(anchor))
    devotional = {
        "id": day.id, "schemaVersion": SCHEMA_VERSION, "contentVersion": content_version(day, prompt.version),
        "date": day.date, "dayOfYear": day.day_of_year, "title": data["title"].strip(), "theme": day.theme,
        "series": {"title": day.series, "season": day.season, "day": day.series_day, "length": day.series_length},
        "coverStyle": day.cover_style, "verses": [to_passage(day.anchor), *extras],
        **{k: data[k].strip() for k in ("context", "reflect", "applyIt")},
        "status": "generated",
        "meta": {"model": draft.model, "generatedAt": now(), "promptVersion": prompt.version},
    }
    if data.get("prayer", "").strip():
        devotional["prayer"] = data["prayer"].strip()
    return devotional


def is_stale(devotional: dict, day: Day, prompt: Prompt) -> bool:
    return devotional["contentVersion"].partition(".")[0] != content_version(day, prompt.version)


#: Drafts in flight at once (`devotionals draft --workers N`).
DRAFT_WORKERS = 8


def draft_days(days: list[Day], drafter: Drafter, store: DevotionalStore, *, force: bool = False,
               stale: bool = False, prompt: Prompt | None = None, bible_db: Path = APP_BIBLE_DB,
               source_db: Path = SOURCE_DB, workers: int = DRAFT_WORKERS):
    """
    Drafts each day, yielding one line per day. Existing files are skipped unless:
      stale=True: redraft ones whose calendar row or prompt changed, but never reviewed or edited ones;
      force=True: redraft everything asked for, including reviewed and edited ones.
    A refusal or unusable response is reported and the day skipped; the run carries on.

    Up to [workers] drafts run at once. Only drafter.draft runs on a worker thread: building each request (database
    reads), turning the answer into a devotional and saving it all happen here, on the caller's thread, as each
    answer comes in. Lines come in completion order. Stopping (Ctrl-C) drops the queued days; saved days stay saved,
    and a re-run skips them.
    """
    from concurrent.futures import FIRST_COMPLETED, ThreadPoolExecutor, wait

    prompt = prompt or Prompt.load()
    word_limits = limits()
    workers = max(1, workers)
    pending: dict = {}

    def finish(future) -> str:
        day = pending.pop(future)
        try:
            devotional = from_draft(day, future.result(), prompt)
        except DraftRefused as error:
            return f"{day.date}: refused ({error}); needs a person to write it"
        except (DraftFailed, KeyError, AttributeError) as error:
            return f"{day.date}: failed ({error}); safe to retry"
        store.save(devotional)
        return f"{day.date}: drafted \"{devotional['title']}\""

    pool = ThreadPoolExecutor(max_workers=workers)
    try:
        for day in days:
            if store.exists(day.id) and not force:
                current = store.load(day.id)
                protected = current.get("status") == "reviewed" or current.get("meta", {}).get("editedBy")
                if not stale or protected or not is_stale(current, day, prompt):
                    why = "reviewed/edited; use --force" if protected else "exists"
                    yield f"{day.date}: skipped ({why})"
                    continue
            request = build_request(day, prompt, bible_db, source_db, word_limits)
            pending[pool.submit(drafter.draft, prompt.system, request, OUTPUT_SCHEMA, MAX_TOKENS)] = day
            while len(pending) >= 2 * workers:  # bounded: don't queue the whole year at once
                done, _ = wait(pending, return_when=FIRST_COMPLETED)
                for future in done:
                    yield finish(future)
        while pending:
            done, _ = wait(pending, return_when=FIRST_COMPLETED)
            for future in done:
                yield finish(future)
    finally:
        pool.shutdown(wait=not pending, cancel_futures=True)


def default_drafter() -> AnthropicDrafter:
    """The drafter for every devotional call (draft and redraft-series)."""
    return AnthropicDrafter(model=DRAFT_MODEL, effort=DRAFT_EFFORT, max_retries=DRAFT_MAX_RETRIES)
