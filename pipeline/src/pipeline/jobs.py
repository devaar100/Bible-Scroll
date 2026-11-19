"""
Prepared batch jobs: build the exact generation requests offline, check them, then send them one batch at a time.

  prepare --ids FILE | --wave W [--limit N] --out jobs/NAME/
      jobs/NAME/batch-NNN.jsonl  one {"custom_id", "params"} per line, exactly what the Batches API receives,
                                 at most waves.yaml batch_size (500) per file
      jobs/NAME/manifest.json    ids, model/effort/max_tokens, prompt/system/books/limits hashes, checker version,
                                 SHA-256 of every batch file
      jobs/NAME/schedule.json    one entry per batch file: pending -> submitted (batch id, time)
  replay DIR      every code check on the studies in DIR (no AI); prints limits as enforced and the length bands,
                  writes DIR/replay.json
  submit jobs/NAME --next [--dry-run]
      sends the next pending batch file unchanged; refuses if study.md, books.json or limits.json changed since
      prepare (hash mismatch), or the file was edited. --dry-run does every check and sends nothing (works with
      PIPELINE_OFFLINE=1). After a real submit, `batch status` / `batch collect` take over as usual.

Nothing here creates an API client except submit without --dry-run.
"""
from __future__ import annotations

import hashlib
import json
from collections import Counter
from pathlib import Path

from . import batch as bt
from . import generate as g
from .checks import CHECKER_VERSION
from .paths import APP_BIBLE_DB, PIPELINE, SOURCE_DB
from .store import StudyStore, is_written, now

JOBS_DIR = PIPELINE / "jobs"
LIMITS_FILE = g.LIMITS_FILE
#: Jobs whose name starts with this are Claude Code test rounds: prepared for subagents, never submitted.
TEST_PREFIX = "cc_"


def sha(data: bytes, n: int | None = None) -> str:
    digest = hashlib.sha256(data).hexdigest()
    return digest[:n] if n else digest


def limits_hash(path: Path = LIMITS_FILE) -> str:
    return sha(path.read_bytes(), 12)


def hashes(fixed: bt.Fixed) -> dict:
    """What a prepared job is pinned to: any change means the prepared requests are no longer current."""
    return {"promptVersion": fixed.prompt_version, "systemHash": fixed.system_hash, "booksHash": fixed.books_hash,
            "limitsHash": limits_hash()}


def read_ids(path: Path) -> list[str]:
    """One id per line; blank lines and # comments ignored; duplicates are an error."""
    ids = [line.split("#", 1)[0].strip() for line in path.read_text().splitlines()]
    ids = [i for i in ids if i]
    dup = [i for i, n in Counter(ids).items() if n > 1]
    if dup:
        raise SystemExit(f"{path}: duplicate ids {dup}")
    return ids


def prepare(ids: list[str], out: Path, *, source: StudyStore | None = None, bible_db: Path = APP_BIBLE_DB,
            batch_size: int = bt.BATCH_SIZE, label: str | None = None, studies_out: Path | None = None,
            log=print) -> dict:
    source = source or StudyStore(g.STUDIES_DIR)
    if out.exists() and any(out.iterdir()):
        raise SystemExit(f"{out} already exists and isn't empty; prepare into a new folder")
    if batch_size < 1 or batch_size > bt.BATCH_SIZE:
        raise SystemExit(f"batch size must be 1-{bt.BATCH_SIZE}")
    fixed = bt.load_fixed()
    lines, skipped, included = [], {}, []
    known = set(source.ids())
    for study_id in ids:
        if study_id not in known:
            skipped[study_id] = "no built study (run `studies`?)"
            continue
        try:
            params = bt.params(source.load(study_id), fixed, bible_db)
        except LookupError as error:
            skipped[study_id] = str(error)
            continue
        lines.append(json.dumps({"custom_id": bt.custom_id(study_id), "params": params}, ensure_ascii=False,
                                sort_keys=True))
        included.append(study_id)
    if not lines:
        raise SystemExit(f"Nothing to prepare (skipped: {skipped})")
    out.mkdir(parents=True, exist_ok=True)
    files, schedule = {}, []
    for n, start in enumerate(range(0, len(lines), batch_size), 1):
        name = f"batch-{n:03d}.jsonl"
        data = ("\n".join(lines[start:start + batch_size]) + "\n").encode()
        (out / name).write_bytes(data)
        files[name] = sha(data)
        schedule.append({"file": name, "count": len(lines[start:start + batch_size]),
                         "ids": included[start:start + batch_size], "status": "pending", "batchId": None,
                         "submittedAt": None})
    name = label or out.name
    test_only = name.startswith(TEST_PREFIX)
    manifest = {
        "name": name, "createdAt": now(), "testOnly": test_only,
        "model": bt.MODEL, "effort": bt.EFFORT, "maxTokens": bt.MAX_TOKENS, "cacheTtl": bt.CACHE_TTL,
        "batchSize": batch_size, **hashes(fixed),
        "checkerVersion": CHECKER_VERSION,
        "studiesOut": str(studies_out or g.STUDIES_DIR), "count": len(included), "ids": included,
        "skipped": skipped, "files": files,
        "fixedBlockChars": len(fixed.system),
    }
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    (out / "schedule.json").write_text(json.dumps({"batches": schedule}, indent=2) + "\n")
    log(f"Prepared {len(included)} requests in {len(files)} file(s) under {out}"
        f"{' (test only: never submitted)' if test_only else ''}; prompt {fixed.prompt_version}, system "
        f"{fixed.system_hash}, books {fixed.books_hash}, limits {manifest['limitsHash']}")
    if skipped:
        log(f"Skipped {len(skipped)}: {skipped}")
    return manifest


def load_requests(job: Path, file: str) -> list[dict]:
    return [json.loads(line) for line in (job / file).read_text().splitlines() if line.strip()]


def request_text(request: dict) -> tuple[str, str]:
    """(system block text, user message) of one prepared request."""
    p = request["params"]
    return "".join(b["text"] for b in p["system"]), p["messages"][0]["content"]


def verify(job: Path) -> tuple[dict, dict, list[str]]:
    """(manifest, schedule, problems): hash drift since prepare and edited batch files."""
    manifest = json.loads((job / "manifest.json").read_text())
    schedule = json.loads((job / "schedule.json").read_text())
    problems = []
    current = hashes(bt.load_fixed())
    names = {"promptVersion": "prompts/study.md", "systemHash": "the cached system block",
             "booksHash": "prompts/books.json", "limitsHash": "checks/limits.json"}
    for key, value in current.items():
        if manifest.get(key) != value:
            problems.append(f"{names[key]} changed since prepare ({key} {manifest.get(key)} -> {value}); "
                            "prepare a new job")
    for name, digest in manifest["files"].items():
        path = job / name
        if not path.exists():
            problems.append(f"{name} is missing")
        elif sha(path.read_bytes()) != digest:
            problems.append(f"{name} was edited after prepare (SHA-256 differs)")
    return manifest, schedule, problems


def submit_next(job: Path, *, dry_run: bool = False, client=None, state: bt.State | None = None, log=print) -> dict | None:
    manifest, schedule, problems = verify(job)
    if problems:
        raise SystemExit("Refusing to submit:\n  " + "\n  ".join(problems))
    if manifest.get("testOnly"):
        raise SystemExit(f"{manifest['name']} is a Claude Code test job ({TEST_PREFIX}*): it is never submitted")
    from .publish import CC_TEST_PART
    if CC_TEST_PART in Path(manifest["studiesOut"]).parts:
        raise SystemExit(f"studiesOut {manifest['studiesOut']} is under {CC_TEST_PART}/: never submitted")
    pending = [b for b in schedule["batches"] if b["status"] == "pending"]
    if not pending:
        log("Every batch file in this job has been submitted.")
        return None
    entry = pending[0]
    requests = load_requests(job, entry["file"])
    ids = [bt.study_id_of(r["custom_id"]) for r in requests]
    if ids != entry["ids"]:
        raise SystemExit(f"{entry['file']}: its custom_ids don't match schedule.json")
    bad = [r["custom_id"] for r in requests
           if r["params"].get("model") != bt.MODEL or r["params"]["output_config"].get("effort") != bt.EFFORT]
    if bad:
        raise SystemExit(f"{entry['file']}: requests not on {bt.MODEL}/{bt.EFFORT}: {bad[:5]}")
    state = state or bt.State()
    busy = sorted(set(ids) & state.open_ids())
    if busy:
        raise SystemExit(f"{len(busy)} verse(s) are already in an open batch: {busy[:5]}")
    info = {"file": entry["file"], "count": len(requests), "first": ids[0], "last": ids[-1],
            "bytes": (job / entry["file"]).stat().st_size, "promptVersion": manifest["promptVersion"],
            "systemHash": manifest["systemHash"], "remainingAfter": len(pending) - 1}
    if dry_run:
        log("Dry run: would submit " + json.dumps(info))
        return info
    if client is None:
        from .offline import anthropic_client
        client = anthropic_client()
    fixed = bt.load_fixed()
    fixed_tokens = bt.check_fixed_tokens(client, fixed, log)
    target = StudyStore(Path(manifest["studiesOut"]))
    target.directory.mkdir(parents=True, exist_ok=True)
    record = bt.send(client, requests, 1, target, state, fixed, fixed_tokens, log, wave=manifest["name"],
                     extra={"job": str(job), "jobFile": entry["file"]})
    entry.update(status="submitted", batchId=record["id"], submittedAt=now())
    (job / "schedule.json").write_text(json.dumps(schedule, indent=2) + "\n")
    return {**info, "batchId": record["id"]}


# ---- Replay: every code check, no AI ---------------------------------------------------------------------

def replay(directory: Path, *, bible=None, rules=None, log=print) -> dict:
    from .checks import Rules, band_counts, describe_limits, free_checks
    from .studies import validator as make_validator
    from .store import Bible

    rules = rules or Rules.load()
    bible = bible or Bible(SOURCE_DB, APP_BIBLE_DB)
    validator = make_validator()
    store = StudyStore(directory)
    results, studies = {}, []
    for study_id in store.ids():
        study = store.load(study_id)
        if not is_written(study):
            continue
        studies.append(study)
        reasons = free_checks(study, bible, rules, validator)
        results[study_id] = {"passed": not reasons, "reasons": reasons,
                             "words": {f: len(study.get(f, "").split()) for f in rules.limits}}
    bands = band_counts(studies, rules)
    report = {"directory": str(directory), "checkedAt": now(), "studies": len(results),
              "passed": sum(r["passed"] for r in results.values()),
              "rejected": sorted(i for i, r in results.items() if not r["passed"]),
              "reasons": dict(Counter(x["check"] for r in results.values() for x in r["reasons"]).most_common()),
              "graceShare": rules.grace, "limits": rules.limits, "bands": bands, "results": results}
    log("Limits as enforced (checks/limits.json):")
    log(describe_limits(rules))
    log("Length bands (sections): " + ", ".join(f"{k} {v}" for k, v in bands["total"].items()))
    for f, c in bands["bySection"].items():
        log(f"  {f:<18} " + ", ".join(f"{k} {v}" for k, v in c.items() if v))
    for study_id, r in results.items():
        log(f"{study_id}: " + ("pass" if r["passed"] else
                              "REJECT " + "; ".join(f"[{x['check']}{'/' + x['section'] if x.get('section') else ''}] "
                                                   f"{x['message']}" for x in r["reasons"])))
    log(f"{report['passed']}/{report['studies']} passed")
    (directory / "replay.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    return report


def wave_ids(name: str, source: StudyStore) -> list[str]:
    from . import waves as wv
    plan = wv.load()
    return wv.wave_ids(plan.get(name), source.ids())

