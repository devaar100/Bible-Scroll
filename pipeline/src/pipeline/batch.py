"""
Batch mode for study prose: Message Batches API, claude-haiku-5-5 at effort "medium", one request per verse.

Request shape: one system block that is byte-identical on every request and carries a 1-hour cache_control.
It holds study.md's System section plus the Request section's fixed output instructions (from "Return JSON
with these sections" on, filled with the word limits). The wording is unchanged, only reordered so that it can be
cached. The verse data (verse text, context, original words, cross references) follows as the user message.

Steps (python -m pipeline batch estimate|submit|status|collect|check|report|backup):
  estimate  verse count and projected cost per wave (--wave all: a table). Sends nothing unless --count-tokens
            (free token counting).
  submit    attempt 1 for the chosen verses, in batches of at most waves.yaml batch_size (500). Counts the fixed
            block's tokens first and refuses if under 512.
  status    poll every open batch.
  collect   write study files from ended batches (never over a reviewed or hand-edited study), run the free
            checks + Haiku rubric, then submit ONE retry round for verses that failed or were flagged on
            attempt 1. A verse that fails or is flagged on attempt 2 gets status "failed" (never published).
  check     run the checks again on collected verses (no generation, no retry round).
  report    rewrite out/batch_runs/<wave>/generate_summary.json + check_summary.json from state and studies
            (no API calls). submit/status/collect/check write them too.
  backup    zip out/ + state/ (same as `python -m pipeline backup`).
Everything resumes from state/batches.json and the study files: any step can be re-run after a crash.

custom_id is the verse id with "." replaced by "_" (PHP.2.17 -> PHP_2_17): the API allows only [A-Za-z0-9_-].
Every batch id is written to state/batches.json before the next API call, so a crash never loses one.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import tempfile
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

from . import generate as g
from .devotionals.generate import Prompt, fill
from .paths import APP_BIBLE_DB, OUT, PIPELINE
from .plans.ai import DraftFailed
from .store import StudyStore, is_written, now
from .studies import STUDIES_DIR
from .usage import OUTPUT_WATCH, PRICES, Usage, output_stats, request_log  # noqa: F401

MODEL = "claude-haiku-5-5"
EFFORT = "medium"
MAX_TOKENS = 16000  # adaptive thinking + six sections; a max_tokens stop counts as a failed attempt
CACHE_TTL = "1h"   # batches can take an hour or more; a 5-minute entry would expire between requests
MIN_CACHE_TOKENS = 512  # Haiku 5.5's minimum cacheable prefix; shorter blocks silently don't cache
MAX_ATTEMPTS = 2
BATCH_SIZE = 500  # default; waves.yaml batch_size overrides (never above the API's limits)
RUNS_DIR = OUT / "batch_runs"  # <wave>/generate_summary.json and check_summary.json; "all" = every wave
STATE_DIR = PIPELINE / "state"
STATE_FILE = STATE_DIR / "batches.json"
_CUSTOM_ID = re.compile(r"^[A-Za-z0-9_-]{1,64}$")


def custom_id(study_id: str) -> str:
    cid = study_id.replace(".", "_")
    if not _CUSTOM_ID.match(cid):
        raise ValueError(f"{study_id} can't be a custom_id")
    return cid


def study_id_of(cid: str) -> str:
    return cid.replace("_", ".")


# ---- Request ----------------------------------------------------------------------------------------------

@dataclass(frozen=True)
class Fixed:
    system: str          # the cached block, identical on every request
    verse_template: str  # the Request section up to the fixed tail
    prompt_version: str  # sha256(study.md)[:12], recorded in each study's meta
    system_hash: str     # sha256(system)[:12]; same hash = same cache entry
    books_hash: str = ""  # sha256(prompts/books.json)[:12]: background and text notes are prompt input too


def load_fixed(prompt_file: Path = g.PROMPT_FILE) -> Fixed:
    prompt = Prompt.load(prompt_file)
    head, tail = g.split_request(prompt)
    system = f"{prompt.system}\n\n{tail}"
    from .context import BOOKS_FILE
    return Fixed(system, head, prompt.version, hashlib.sha256(system.encode()).hexdigest()[:12],
                 hashlib.sha256(BOOKS_FILE.read_bytes()).hexdigest()[:12])


def system_blocks(fixed: Fixed) -> list[dict]:
    return [{"type": "text", "text": fixed.system, "cache_control": {"type": "ephemeral", "ttl": CACHE_TTL}}]


def params(study: dict, fixed: Fixed, bible_db: Path = APP_BIBLE_DB) -> dict:
    user = fill(fixed.verse_template, g.request_values(study, bible_db))
    return {
        "model": MODEL,
        "max_tokens": MAX_TOKENS,
        "system": system_blocks(fixed),
        "messages": [{"role": "user", "content": user}],
        "output_config": {"effort": EFFORT, "format": {"type": "json_schema", "schema": g.OUTPUT_SCHEMA}},
    }


def count_fixed_tokens(client, fixed: Fixed) -> int:
    """Exact token length of the cached block: count(system + '.') - count('.'). Free; generates nothing."""
    msgs = [{"role": "user", "content": "."}]
    with_system = client.messages.count_tokens(model=MODEL, system=system_blocks(fixed), messages=msgs)
    without = client.messages.count_tokens(model=MODEL, messages=msgs)
    return with_system.input_tokens - without.input_tokens


# ---- State ------------------------------------------------------------------------------------------------

class State:
    """state/batches.json: every batch (id, verses, usage), attempts per verse, failures, rubric usage."""

    def __init__(self, path: Path = STATE_FILE):
        self.path = path
        self.data = json.loads(path.read_text()) if path.exists() else {}
        self.data.setdefault("batches", [])
        self.data.setdefault("attempts", {})
        self.data.setdefault("failures", {})  # id -> why the last attempt produced no usable study
        self.data.setdefault("rubricUsage", Usage().as_dict(MODEL))

    @property
    def batches(self) -> list[dict]:
        return self.data["batches"]

    def attempts(self, study_id: str) -> int:
        return self.data["attempts"].get(study_id, 0)

    def open_ids(self) -> set[str]:
        return {i for b in self.batches if not b.get("collectedAt") for i in b["ids"]}

    def save(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        fd, tmp = tempfile.mkstemp(dir=self.path.parent, prefix=".tmp-", suffix=".json")
        with os.fdopen(fd, "w") as f:
            f.write(json.dumps(self.data, indent=2) + "\n")
        os.replace(tmp, self.path)


# ---- Selection --------------------------------------------------------------------------------------------

def protected(study: dict | None) -> bool:
    return bool(study) and (study.get("status") == "reviewed" or bool(study.get("meta", {}).get("editedBy")))


def plan(ids: list[str], target: StudyStore, state: State, force: bool = False,
         bible_db: Path = APP_BIBLE_DB) -> tuple[list[str], Counter]:
    """(verses to send as attempt 1, why the others are skipped)."""
    from .store import parse_id

    todo, skipped = [], Counter()
    busy = state.open_ids()
    for study_id in ids:
        existing = target.load(study_id) if target.path(study_id).exists() else None
        key = parse_id(study_id)
        if key not in g._book_rows(bible_db, "WEB", key[0]):
            skipped["no WEB text (needs a person)"] += 1  # e.g. Luke 17:36: the request quotes WEB only
        elif protected(existing):
            skipped["reviewed/edited"] += 1
        elif study_id in busy:
            skipped["in an open batch"] += 1
        elif state.attempts(study_id) >= MAX_ATTEMPTS and not force:
            skipped["failed twice (status failed)"] += 1
        elif existing and is_written(existing) and not force:
            skipped["already written (use --force)"] += 1
        else:
            todo.append(study_id)
    return todo, skipped


# ---- Estimate ---------------------------------------------------------------------------------------------

@dataclass
class Calibration:
    """Measured per-verse tokens from an earlier direct run with the same model and effort."""
    folder: Path
    verses: int
    input_per_verse: float
    output_per_verse: float
    retry_rate: float | None          # flagged share after checks, if that run was checked
    rubric_cost_per_verse: float | None


def find_calibration(out: Path = OUT) -> Calibration | None:
    best = None
    for summary_path in sorted(out.glob("*/generate_summary.json")):
        s = json.loads(summary_path.read_text())
        if s.get("model") != MODEL or s.get("effort") != EFFORT or not s.get("written"):
            continue
        if best is None or s["finishedAt"] > best[1]["finishedAt"]:
            best = (summary_path.parent, s)
    if best is None:
        return None
    folder, s = best
    usage = s["usage"]
    n = usage["requests"]
    store = StudyStore(folder)
    studies = [store.load(i) for i in store.ids()]
    written = [x for x in studies if x.get("meta", {}).get("model") == MODEL]
    statuses = Counter(x.get("status") for x in written)
    checked = statuses["auto_checked"] + statuses["flagged"]
    retry_rate = statuses["flagged"] / checked if checked else None
    rubric_cost = None
    log = out / f"check_{folder.name.removeprefix('test_')}.log"
    try:
        report = json.loads(log.read_text().rsplit("\nexit", 1)[0])
        rubric_cost = report["rubric_usage"]["costUsd"] / max(1, report["checked"])
    except (OSError, ValueError, KeyError):
        pass
    return Calibration(folder, n, usage["inputTokens"] / n, usage["outputTokens"] / n,
                       retry_rate, rubric_cost)


def project(verses: int, fixed_tokens: int, cal: Calibration) -> dict:
    """Projected batch cost in USD. Best case: one 1-hour cache write, every other request reads it.
    Worst case: no request hits the cache, so every request pays the 1-hour write (2x input) on the block."""
    if verses == 0:
        return {"verses": 0, "bestUsd": 0.0, "worstUsd": 0.0, "noCacheUsd": 0.0, "expectedRetries": 0,
                "retryUsd": 0.0, "rubricUsd": 0.0}
    i, o, _, cr = PRICES[MODEL]
    half = 0.5
    variable_in = max(0.0, cal.input_per_verse - fixed_tokens)
    per_verse_base = (variable_in * i + cal.output_per_verse * o) / 1e6 * half
    best = verses * per_verse_base + (fixed_tokens * 2 * i + max(0, verses - 1) * fixed_tokens * cr) / 1e6 * half
    worst = verses * per_verse_base + verses * fixed_tokens * 2 * i / 1e6 * half
    uncached = verses * (cal.input_per_verse * i + cal.output_per_verse * o) / 1e6 * half
    retries = round(verses * cal.retry_rate) if cal.retry_rate is not None else None
    rubric = verses * cal.rubric_cost_per_verse if cal.rubric_cost_per_verse is not None else None
    return {"verses": verses, "bestUsd": round(best, 4), "worstUsd": round(worst, 4),
            "noCacheUsd": round(uncached, 4), "expectedRetries": retries,
            "retryUsd": round(retries * best / verses, 4) if retries and verses else 0.0,
            "rubricUsd": round(rubric, 4) if rubric is not None else None}


def approx_tokens(text: str) -> float:
    """Rough token count: ~4 characters per token for English, ~1.5 for Hebrew/Greek script (pointed text
    splits into many tokens). Only for estimates; submit counts the fixed block exactly."""
    other = sum(1 for ch in text if ord(ch) > 0x2FF)
    return (len(text) - other) / 4 + other / 1.5


def estimate_row(name: str, ids: list[str], todo: list[str], skipped, fixed: Fixed, fixed_tokens: int,
                 cal: Calibration | None, source: StudyStore, batch_size: int,
                 bible_db: Path = APP_BIBLE_DB) -> dict:
    """One wave's projection. Input per verse is measured from the real requests (approx tokens); output,
    flagged share and rubric cost per verse come from the calibration run."""
    import math

    user = [approx_tokens(fill(fixed.verse_template, g.request_values(source.load(i), bible_db))) for i in todo]
    row = {"wave": name, "verses": len(ids), "toSend": len(todo), "skipped": dict(skipped),
           "batches": math.ceil(len(todo) / batch_size) if todo else 0,
           "userTokensPerVerse": round(sum(user) / len(user)) if user else 0}
    if cal is not None and todo:
        adjusted = Calibration(cal.folder, cal.verses, fixed_tokens + row["userTokensPerVerse"],
                               cal.output_per_verse, cal.retry_rate, cal.rubric_cost_per_verse)
        proj = project(len(todo), fixed_tokens, adjusted)
        # The cache is per batch: each batch of up to batch_size pays its own first write.
        i = PRICES[MODEL][0]
        extra_writes = max(0, row["batches"] - 1) * fixed_tokens * 2 * i / 1e6 * 0.5
        proj["bestUsd"] = round(proj["bestUsd"] + extra_writes, 4)
        rubric = proj["rubricUsd"] or 0.0
        retry_rubric = (proj["expectedRetries"] or 0) * (cal.rubric_cost_per_verse or 0.0)
        proj["totalBestUsd"] = round(proj["bestUsd"] + proj["retryUsd"] + rubric + retry_rubric, 4)
        proj["totalWorstUsd"] = round(proj["worstUsd"] + proj["retryUsd"] * proj["worstUsd"] / max(proj["bestUsd"], 1e-9)
                                      + rubric + retry_rubric, 4)
        row["projection"] = proj
    return row


def estimate_table(rows: list[dict]) -> str:
    head = ("wave", "verses", "to send", "skipped", "batches", "in tok/verse", "gen best $", "gen worst $",
            "retries", "retry $", "review $", "total $ (best-worst)")
    lines = [head]
    totals = Counter()
    for r in rows:
        p = r.get("projection") or {}
        lines.append((r["wave"], str(r["verses"]), str(r["toSend"]), str(sum(r["skipped"].values())),
                      str(r["batches"]), str(r["userTokensPerVerse"]), f"{p.get('bestUsd', 0):.2f}",
                      f"{p.get('worstUsd', 0):.2f}", str(p.get("expectedRetries") or 0), f"{p.get('retryUsd', 0):.2f}",
                      f"{(p.get('rubricUsd') or 0):.2f}",
                      f"{p.get('totalBestUsd', 0):.2f}-{p.get('totalWorstUsd', 0):.2f}"))
        for k in ("verses", "toSend", "batches"):
            totals[k] += r[k]
        for k in ("totalBestUsd", "totalWorstUsd"):
            totals[k] += p.get(k, 0)
    lines.append(("all", str(totals["verses"]), str(totals["toSend"]), "", str(totals["batches"]), "", "", "", "",
                  "", "", f"{totals['totalBestUsd']:.2f}-{totals['totalWorstUsd']:.2f}"))
    widths = [max(len(line[i]) for line in lines) for i in range(len(head))]
    return "\n".join("  ".join(c.ljust(w) for c, w in zip(line, widths)) for line in lines)


# ---- Submit / status --------------------------------------------------------------------------------------

def submit(client, ids: list[str], round_: int, target: StudyStore, state: State, fixed: Fixed,
           source: StudyStore, bible_db: Path = APP_BIBLE_DB, log=print, *, wave: str | None = None,
           batch_size: int = BATCH_SIZE) -> list[dict]:
    """Sends [ids] as batches of at most batch_size requests. Returns the batch records."""
    if not ids:
        log("Nothing to submit.")
        return []
    fixed_tokens = count_fixed_tokens(client, fixed)
    log(f"Fixed system block: {fixed_tokens} tokens (hash {fixed.system_hash}, TTL {CACHE_TTL})")
    if fixed_tokens < MIN_CACHE_TOKENS:
        raise SystemExit(f"The fixed block is {fixed_tokens} tokens, under the {MIN_CACHE_TOKENS}-token cache "
                         "minimum, so nothing would be cached. Not submitting.")
    records = []
    for start in range(0, len(ids), batch_size):
        chunk = ids[start:start + batch_size]
        requests = [{"custom_id": custom_id(i), "params": params(source.load(i), fixed, bible_db)} for i in chunk]
        record = {"id": None, "round": round_, "wave": wave, "ids": chunk, "model": MODEL, "effort": EFFORT,
                  "maxTokens": MAX_TOKENS, "promptVersion": fixed.prompt_version, "systemHash": fixed.system_hash,
                  "booksHash": fixed.books_hash, "fixedTokens": fixed_tokens, "outDir": str(target.directory),
                  "createdAt": now(), "processingStatus": "submitting", "collectedAt": None, "usage": None}
        state.batches.append(record)
        state.save()  # the verses are recorded as in flight before the API call
        batch = client.messages.batches.create(requests=requests)
        record["id"] = batch.id
        record["processingStatus"] = batch.processing_status
        for i in chunk:
            state.data["attempts"][i] = state.attempts(i) + 1
            state.data["failures"].pop(i, None)
        state.save()
        log(f"Submitted batch {batch.id}: {len(chunk)} verses (attempt {round_}"
            f"{', wave ' + wave if wave else ''})")
        records.append(record)
    return records


def refresh(client, state: State, log=print) -> None:
    for record in state.batches:
        if record.get("collectedAt"):
            continue
        if record["id"] is None:
            log(f"WARNING: a batch for {len(record['ids'])} verses was being submitted when the run stopped "
                f"({record['createdAt']}). Check the Console's batch list; if it exists, put its id in "
                f"{STATE_FILE.name}, otherwise delete that entry.")
            continue
        batch = client.messages.batches.retrieve(record["id"])
        record["processingStatus"] = batch.processing_status
        record["counts"] = {k: getattr(batch.request_counts, k) for k in
                            ("processing", "succeeded", "errored", "canceled", "expired")}
    state.save()


# ---- Collect ----------------------------------------------------------------------------------------------

def _mark_failed(study: dict, why: str) -> dict:
    from .checks import CHECKER_VERSION
    updated = json.loads(json.dumps(study))
    reasons = updated.get("checks", {}).get("reasons", [])
    updated["checks"] = {"checkedAt": now(), "checkerVersion": CHECKER_VERSION,
                         "reasons": [*reasons, {"check": "generation", "message": why}]}
    updated["status"] = "failed"
    return updated


def collect(client, state: State, source: StudyStore, bible, judge, log=print) -> list[str]:
    """Write ended batches' results, then check them. Returns the verses that need a retry."""
    from .checks import Rules, apply, check_study
    from .studies import validator as make_validator

    for record in state.batches:
        if record.get("collectedAt") or record["id"] is None or record.get("processingStatus") != "ended":
            continue
        target = StudyStore(Path(record["outDir"]))
        usage = Usage()
        outcomes: Counter = Counter()
        record["requests"] = []
        for result in client.messages.batches.results(record["id"]):
            sid = study_id_of(result.custom_id)
            existing = target.load(sid) if target.path(sid).exists() else None
            why = None
            sections = None
            if result.result.type == "succeeded":
                message = result.result.message
                usage.add(message.usage)
                record["requests"].append(request_log(sid, message))
                if message.stop_reason == "max_tokens":
                    why = f"cut off at max_tokens ({MAX_TOKENS})"
                elif message.stop_reason == "refusal":
                    why = "refused"
                else:
                    try:
                        sections = g.parse_sections(next((b.text for b in message.content if b.type == "text"), None))
                    except DraftFailed as error:
                        why = str(error)
            elif result.result.type == "errored":
                why = f"errored ({getattr(result.result.error, 'type', '?')})"
            else:
                why = result.result.type  # canceled / expired
            if protected(existing):
                outcomes["skipped (reviewed/edited)"] += 1
                continue
            if why:
                outcomes[why.split(" (")[0]] += 1
                state.data["failures"][sid] = why
                if state.attempts(sid) >= MAX_ATTEMPTS:
                    target.save(_mark_failed(existing or source.load(sid), f"attempt {state.attempts(sid)}: {why}"))
                continue
            study = dict(source.load(sid))
            study.update(sections)
            study["status"] = "generated"
            study["meta"] = {"model": message.model, "effort": record.get("effort", EFFORT),
                             "maxTokens": record.get("maxTokens", MAX_TOKENS), "generatedAt": now(),
                             "promptVersion": record["promptVersion"],
                             "promptHash": record.get("systemHash"), "booksHash": record.get("booksHash"),
                             "batchId": record["id"], "wave": record.get("wave"), "attempts": state.attempts(sid)}
            study["meta"] = {k: v for k, v in study["meta"].items() if v is not None}
            study.pop("checks", None)
            target.save(study)
            outcomes["written"] += 1
        record["usage"] = usage.as_dict(MODEL, batch=True)
        record["outcomes"] = dict(outcomes)
        record["collectedAt"] = now()
        state.save()
        u = record["usage"]
        log(f"Collected {record['id']}: {dict(outcomes)}; cacheWriteTokens {u['cacheWriteTokens']}, "
            f"cacheReadTokens {u['cacheReadTokens']}, ${u['costUsd']}")

    check_collected(state, bible, judge, log=log)
    return needs_retry(state)


def check_collected(state: State, bible, judge, *, wave: str | None = None, recheck: bool = False,
                    log=print) -> Counter:
    """Checks collected verses still at status generated (recheck: also auto_checked/flagged). Flagged on the
    last attempt -> failed. Rubric tokens are added to state per wave. Returns the new statuses."""
    from .checks import Rules, apply, check_study
    from .studies import validator as make_validator

    rules, validator = Rules.load(), make_validator()
    wanted = {"generated", "auto_checked", "flagged"} if recheck else {"generated"}
    statuses: Counter = Counter()
    by_wave = state.data.setdefault("rubricUsageByWave", {})
    for record in state.batches:
        if not record.get("collectedAt") or (wave and record.get("wave") != wave):
            continue
        target = StudyStore(Path(record["outDir"]))
        for sid in record["ids"]:
            if not target.path(sid).exists():
                continue
            study = target.load(sid)
            if study.get("status") not in wanted or not is_written(study) or \
                    study["meta"].get("batchId") != record["id"]:
                continue
            before = Usage(**vars(judge.usage)) if judge is not None else None
            outcome = check_study(study, bible, rules, validator, judge)
            if judge is not None:
                delta = Usage(**{k: getattr(judge.usage, k) - getattr(before, k) for k in vars(before)})
                total = Usage.from_dict(by_wave.get(record.get("wave") or "adhoc", {}))
                total.merge(delta)
                by_wave[record.get("wave") or "adhoc"] = total.as_dict(MODEL)
            if outcome.status == "unchanged":
                log(f"{sid}: rubric unavailable ({outcome.error}); left as generated, re-run collect or check")
                continue
            updated = apply(study, outcome)
            if updated["status"] == "flagged" and state.attempts(sid) >= MAX_ATTEMPTS:
                updated["status"] = "failed"
            target.save(updated)
            statuses[updated["status"]] += 1
    if judge is not None:
        total = Usage.from_dict(state.data["rubricUsage"])
        total.merge(judge.usage)
        judge.usage = Usage()
        reviews, unverified = getattr(judge, "reviews", 0), getattr(judge, "unverified_reviews", 0)
        if reviews:
            log(f"Rubric: {reviews} reviews, {unverified} unverified (a quote not found verbatim in its section)")
            totals = state.data.setdefault("rubricEvidence", {"reviews": 0, "unverified": 0})
            totals["reviews"] += reviews
            totals["unverified"] += unverified
            judge.reviews = judge.unverified_reviews = 0
        state.data["rubricUsage"] = total.as_dict(MODEL)
    state.save()
    return statuses


def needs_retry(state: State) -> list[str]:
    """Verses on attempt 1 (collected) that produced nothing usable or were flagged."""
    busy = state.open_ids()
    out = []
    for record in state.batches:
        if not record.get("collectedAt"):
            continue
        target = StudyStore(Path(record["outDir"]))
        for sid in record["ids"]:
            if sid in busy or sid in out or state.attempts(sid) >= MAX_ATTEMPTS:
                continue
            study = target.load(sid) if target.path(sid).exists() else None
            if protected(study):
                continue
            if sid in state.data["failures"] or (study and study.get("status") == "flagged"):
                out.append(sid)
    return out


# ---- Summaries ----------------------------------------------------------------------------------------

def _records(state: State, wave: str | None) -> list[dict]:
    return [r for r in state.batches if wave is None or (r.get("wave") or "adhoc") == wave]


def _latest_studies(records: list[dict]) -> dict[str, dict]:
    """(id -> study) for every verse in [records], read once from the folder of its latest batch."""
    where: dict[str, str] = {}
    for record in records:
        for sid in record["ids"]:
            where[sid] = record["outDir"]
    out = {}
    for sid, folder in where.items():
        store = StudyStore(Path(folder))
        out[sid] = store.load(sid) if store.path(sid).exists() else {}
    return out


def _status(study: dict) -> str:
    if study.get("status") == "failed":
        return "failed"
    return study.get("status", "not_written") if is_written(study) else "not_written"


OPENINGS_SHOWN = 5


def openings(studies: list[dict], top: int = OPENINGS_SHOWN) -> dict:
    """Per section, the commonest first four words (lowercased, punctuation stripped). Report only."""
    from .checks import PROSE_FIELDS
    out = {}
    for f in PROSE_FIELDS:
        counter: Counter = Counter()
        for st in studies:
            text = st.get(f, "")
            if text:
                counter[" ".join(re.sub(r"[^\w\s'’-]", "", text).lower().split()[:4])] += 1
        out[f] = [{"words": w, "count": n} for w, n in counter.most_common(top)]
    return out


_BAD_HC_OPENING = re.compile(r"^\s*(?:this verse|the verse|verse)\b", re.IGNORECASE)


def generate_summary(state: State, wave: str | None = None) -> dict:
    records = _records(state, wave)
    studies = _latest_studies(records)
    usage = Usage()
    outcomes: Counter = Counter()
    for r in records:
        if r.get("usage"):
            usage.merge(Usage.from_dict(r["usage"]))
        outcomes.update(r.get("outcomes", {}))
    requests = [q for r in records for q in r.get("requests", [])]
    cost = usage.cost(MODEL, batch=True) or 0.0
    return {
        "mode": "batch", "wave": wave or "all", "model": MODEL, "effort": EFFORT, "maxTokens": MAX_TOKENS,
        "cacheTtl": CACHE_TTL, "updatedAt": now(),
        "verses": len(studies),
        "statuses": dict(Counter(_status(st) for st in studies.values())),
        "outcomes": dict(outcomes),
        "retries": {"verses": sum(1 for sid in studies if state.attempts(sid) >= 2),
                    "batches": sum(1 for r in records if r["round"] >= 2)},
        "batches": [{"id": r["id"], "round": r["round"], "wave": r.get("wave"), "verses": len(r["ids"]),
                     "status": r["processingStatus"], "collectedAt": r.get("collectedAt"),
                     "promptVersion": r.get("promptVersion"), "promptHash": r.get("systemHash"),
                     "cacheReadTokens": (r.get("usage") or {}).get("cacheReadTokens"),
                     "cacheWriteTokens": (r.get("usage") or {}).get("cacheWriteTokens"),
                     "costUsd": (r.get("usage") or {}).get("costUsd")} for r in records],
        "outputTokens": output_stats(requests),  # incl. thinking-token max and median, max_tokens hits
        "usage": usage.as_dict(MODEL, batch=True),
        "cacheReadTokens": usage.cache_read_input_tokens,
        "costUsd": round(cost, 4),
        "promptVersions": dict(Counter(r.get("promptVersion") for r in records)),
        "promptHashes": dict(Counter(r.get("systemHash") for r in records)),
    }


def check_summary(state: State, wave: str | None = None) -> dict:
    from .checks import CHECKER_VERSION, CHECKS_DIR, PROSE_FIELDS, length_band

    limits = json.loads((CHECKS_DIR / "limits.json").read_text())["words"]
    records = _records(state, wave)
    studies = _latest_studies(records)
    written = [st for st in studies.values() if is_written(st)]
    bands: Counter = Counter()
    by_section: dict[str, Counter] = {f: Counter() for f in PROSE_FIELDS}
    reasons: Counter = Counter()
    reviews = unverified_reviews = unverified_judgments = 0
    for st in written:
        for f in PROSE_FIELDS:
            band = length_band(len(st.get(f, "").split()), limits[f]["max"])
            if band:
                bands[band] += 1
                by_section[f][band] += 1
        reasons.update(r["check"] for r in st.get("checks", {}).get("reasons", []))
        rubric = st.get("checks", {}).get("rubric")
        if rubric:
            reviews += 1
            bad = sum(1 for k in ("factualRisk", "oneSidedTheology", "tone")
                      if rubric.get(k, {}).get("verified") is False)
            unverified_judgments += bad
            unverified_reviews += bad > 0
    if wave is None:
        rubric_usage = Usage.from_dict(state.data["rubricUsage"])
    else:
        rubric_usage = Usage.from_dict(state.data.get("rubricUsageByWave", {}).get(wave, {}))
    rubric_cost = rubric_usage.cost(MODEL) or 0.0
    return {
        "wave": wave or "all", "checkerVersion": CHECKER_VERSION, "reviewer": {"model": MODEL, "effort": EFFORT},
        "updatedAt": now(),
        "verses": len(studies), "written": len(written),
        "statuses": dict(Counter(_status(st) for st in studies.values())),
        "retries": sum(1 for sid in studies if state.attempts(sid) >= 2),
        "reasons": dict(reasons.most_common()),
        "overLength": {"total": {k: bands[k] for k in ("up to 20% over", "20-50% over", "more than 50% over")},
                       "bySection": {f: dict(c) for f, c in by_section.items() if c}},
        "rubric": {"reviews": reviews, "unverifiedReviews": unverified_reviews,
                   "unverifiedJudgments": unverified_judgments, "usage": rubric_usage.as_dict(MODEL),
                   "costUsd": round(rubric_cost, 4)},
        "historicalContextBadOpenings": sum(1 for st in written
                                            if _BAD_HC_OPENING.match(st.get("historicalContext", ""))),
        "openings": openings(written),
        "promptVersions": dict(Counter(st.get("meta", {}).get("promptVersion") for st in written)),
    }


def write_summaries(state: State, runs_dir: Path = RUNS_DIR) -> dict[str, Path]:
    """generate_summary.json + check_summary.json for every wave in state and for "all". Atomic writes."""
    written = {}
    waves = sorted({r.get("wave") or "adhoc" for r in state.batches})
    for wave in [*waves, None]:
        folder = runs_dir / (wave or "all")
        folder.mkdir(parents=True, exist_ok=True)
        for name, data in (("generate_summary.json", generate_summary(state, wave)),
                           ("check_summary.json", check_summary(state, wave))):
            fd, tmp = tempfile.mkstemp(dir=folder, prefix=".tmp-", suffix=".json")
            with os.fdopen(fd, "w") as f:
                f.write(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
            os.replace(tmp, folder / name)
        written[wave or "all"] = folder
    return written


def summary(state: State) -> dict:
    """Short console view (the files hold the detail)."""
    g_, c = generate_summary(state), check_summary(state)
    return {"verses": g_["verses"], "statuses": g_["statuses"], "retries": g_["retries"],
            "outputTokens": g_["outputTokens"], "cacheReadTokens": g_["cacheReadTokens"],
            "generationUsd": g_["costUsd"], "rubricUsd": c["rubric"]["costUsd"],
            "costSoFarUsd": round(g_["costUsd"] + c["rubric"]["costUsd"], 4),
            "sectionsOverMax": c["overLength"]["total"], "unverifiedReviews": c["rubric"]["unverifiedReviews"],
            "promptHashes": g_["promptHashes"]}


# ---- Backup -----------------------------------------------------------------------------------------------

def backup(dest_dir: Path, sources: tuple[Path, ...] = (OUT, STATE_DIR)) -> Path:
    """Zip out/ and state/ into dest_dir/biblescroll-pipeline-<UTC time>.zip (written atomically)."""
    import zipfile
    from datetime import datetime, timezone

    dest_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    target = dest_dir / f"biblescroll-pipeline-{stamp}.zip"
    fd, tmp = tempfile.mkstemp(dir=dest_dir, prefix=".tmp-", suffix=".zip")
    os.close(fd)
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as z:
        for root in sources:
            if not root.exists():
                continue
            for path in sorted(root.rglob("*")):
                if path.is_file() and not path.name.startswith(".tmp-"):
                    z.write(path, path.relative_to(root.parent))
    os.replace(tmp, target)
    return target


DEFAULT_SOURCE = STUDIES_DIR
