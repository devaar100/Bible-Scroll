"""
Batch mode for study prose: Message Batches API, claude-haiku-5-5 at effort "medium", one request per verse.

Request shape: one system block that is byte-identical on every request and carries a 1-hour cache_control.
It holds study.md's System section plus the Request section's fixed output instructions (from "Return JSON
with these sections" on, filled with the word limits). The wording is unchanged, only reordered so that it can be
cached. The verse data (verse text, context, original words, cross references) follows as the user message.

Steps (python -m pipeline batch estimate|submit|status|collect):
  estimate  verse count and projected cost. Sends nothing unless --count-tokens (free token counting).
  submit    attempt 1 for the chosen verses. Counts the fixed block's tokens first and refuses if under 512.
  status    poll every open batch.
  collect   write study files from ended batches (never over a reviewed or hand-edited study), run the free
            checks + Haiku rubric, then submit ONE retry batch for verses that failed or were flagged on
            attempt 1. A verse that fails or is flagged on attempt 2 gets status "failed" (never published).

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


def load_fixed(prompt_file: Path = g.PROMPT_FILE) -> Fixed:
    prompt = Prompt.load(prompt_file)
    head, tail = g.split_request(prompt)
    system = f"{prompt.system}\n\n{tail}"
    return Fixed(system, head, prompt.version, hashlib.sha256(system.encode()).hexdigest()[:12])


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


def plan(ids: list[str], target: StudyStore, state: State, force: bool = False) -> tuple[list[str], Counter]:
    """(verses to send as attempt 1, why the others are skipped)."""
    todo, skipped = [], Counter()
    busy = state.open_ids()
    for study_id in ids:
        existing = target.load(study_id) if target.path(study_id).exists() else None
        if protected(existing):
            skipped["reviewed/edited"] += 1
        elif study_id in busy:
            skipped["in an open batch"] += 1
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


# ---- Submit / status --------------------------------------------------------------------------------------

def submit(client, ids: list[str], round_: int, target: StudyStore, state: State, fixed: Fixed,
           source: StudyStore, bible_db: Path = APP_BIBLE_DB, log=print) -> dict | None:
    if not ids:
        log("Nothing to submit.")
        return None
    fixed_tokens = count_fixed_tokens(client, fixed)
    log(f"Fixed system block: {fixed_tokens} tokens (hash {fixed.system_hash}, TTL {CACHE_TTL})")
    if fixed_tokens < MIN_CACHE_TOKENS:
        raise SystemExit(f"The fixed block is {fixed_tokens} tokens, under the {MIN_CACHE_TOKENS}-token cache "
                         "minimum, so nothing would be cached. Not submitting.")
    requests = [{"custom_id": custom_id(i), "params": params(source.load(i), fixed, bible_db)} for i in ids]
    record = {"id": None, "round": round_, "ids": ids, "model": MODEL, "effort": EFFORT, "maxTokens": MAX_TOKENS,
              "promptVersion": fixed.prompt_version, "systemHash": fixed.system_hash, "fixedTokens": fixed_tokens,
              "outDir": str(target.directory), "createdAt": now(), "processingStatus": "submitting",
              "collectedAt": None, "usage": None}
    state.batches.append(record)
    state.save()  # the verses are recorded as in flight before the API call
    batch = client.messages.batches.create(requests=requests)
    record["id"] = batch.id
    record["processingStatus"] = batch.processing_status
    for i in ids:
        state.data["attempts"][i] = state.attempts(i) + 1
        state.data["failures"].pop(i, None)
    state.save()
    log(f"Submitted batch {batch.id}: {len(ids)} verses (attempt {round_})")
    return record


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
                             "promptVersion": record["promptVersion"], "batchId": record["id"],
                             "attempts": state.attempts(sid)}
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

    # Check every collected verse that is written but not yet checked (also ones a rubric outage left behind).
    rules, validator = Rules.load(), make_validator()
    for record in state.batches:
        if not record.get("collectedAt"):
            continue
        target = StudyStore(Path(record["outDir"]))
        for sid in record["ids"]:
            if not target.path(sid).exists():
                continue
            study = target.load(sid)
            if study.get("status") != "generated" or not is_written(study) or \
                    study["meta"].get("batchId") != record["id"]:
                continue
            outcome = check_study(study, bible, rules, validator, judge)
            if outcome.status == "unchanged":
                log(f"{sid}: rubric unavailable ({outcome.error}); left as generated, re-run collect")
                continue
            updated = apply(study, outcome)
            if updated["status"] == "flagged" and state.attempts(sid) >= MAX_ATTEMPTS:
                updated["status"] = "failed"
            target.save(updated)
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
    return needs_retry(state)


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


# ---- Summary ----------------------------------------------------------------------------------------------

def summary(state: State) -> dict:
    from .checks import CHECKS_DIR, PROSE_FIELDS, length_band

    limits = json.loads((CHECKS_DIR / "limits.json").read_text())["words"]
    statuses: Counter = Counter()
    hashes: Counter = Counter()
    bands: Counter = Counter()
    reviews = unverified = 0
    seen = set()
    batch_usage = Usage()
    for record in state.batches:
        if record.get("usage"):
            batch_usage.merge(Usage.from_dict(record["usage"]))
        target = StudyStore(Path(record["outDir"]))
        for sid in record["ids"]:
            if (sid, record["outDir"]) in seen:
                continue
            seen.add((sid, record["outDir"]))
            study = target.load(sid) if target.path(sid).exists() else {}
            statuses["not_written" if not is_written(study) and study.get("status") != "failed"
                     else study.get("status", "not_written")] += 1
            hashes[study.get("meta", {}).get("promptVersion") or "(none)"] += 1
            if is_written(study):
                for f in PROSE_FIELDS:
                    band = length_band(len(study.get(f, "").split()), limits[f]["max"])
                    if band:
                        bands[band] += 1
            rubric = study.get("checks", {}).get("rubric")
            if rubric:
                reviews += 1
                unverified += any(rubric.get(k, {}).get("verified") is False
                                  for k in ("factualRisk", "oneSidedTheology", "tone"))
    rubric = Usage.from_dict(state.data["rubricUsage"])
    batch_cost = batch_usage.cost(MODEL, batch=True) or 0.0
    rubric_cost = rubric.cost(MODEL) or 0.0
    return {
        "verses": len(seen),
        "statuses": dict(statuses),
        "batches": [{"id": b["id"], "round": b["round"], "verses": len(b["ids"]), "status": b["processingStatus"],
                     "maxTokens": b.get("maxTokens"),
                     "cacheWriteTokens": (b.get("usage") or {}).get("cacheWriteTokens"),
                     "cacheReadTokens": (b.get("usage") or {}).get("cacheReadTokens"),
                     "outputTokens": output_stats(b.get("requests", []))} for b in state.batches],
        "outputTokens": output_stats([r for b in state.batches for r in b.get("requests", [])]),
        "sectionsOverMax": {k: bands[k] for k in ("up to 20% over", "20-50% over", "more than 50% over")},
        "reviews": reviews,
        "unverifiedReviews": unverified,
        "costSoFarUsd": round(batch_cost + rubric_cost, 4),
        "generationUsd": round(batch_cost, 4),
        "rubricUsd": round(rubric_cost, 4),
        "cacheReadTokens": batch_usage.cache_read_input_tokens,
        "cacheWriteTokens": batch_usage.cache_creation_input_tokens,
        "promptVersions": dict(hashes),
    }


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
