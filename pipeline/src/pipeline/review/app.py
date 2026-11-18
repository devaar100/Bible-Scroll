"""
Local review tool: one study at a time beside the verse and its source data.

Run: PYTHONPATH=src .venv/bin/python -m pipeline review [--reviewer "Name"]  ->  http://127.0.0.1:8765
Binds to localhost only; there is no login. Every write is validated against the schema and is atomic.
"""
from __future__ import annotations

import json
from pathlib import Path

from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from pydantic import BaseModel

from ..checks import PROSE_FIELDS, Rules, apply, check_study
from ..review_queue import PRIORITY_FILE, load_priority, queue_order
from ..store import Bible, StudyStore, is_written, now, parse_id
from ..plans.bible import VerseIndex
from ..plans.build import PLANS_DIR
from ..studies import validator as make_validator
from .devotionals_api import add_devotional_routes
from .plans_api import add_plan_routes

STATIC = Path(__file__).parent / "static"
MAX_RANGE_VERSES = 6


class Action(BaseModel):
    reviewer: str | None = None
    note: str | None = None


class Edit(BaseModel):
    reviewer: str | None = None
    fields: dict[str, str]


def create_app(store: StudyStore, bible: Bible, *, reviewer: str | None = None,
               priority_path: Path = PRIORITY_FILE, regenerate_log: Path | None = None,
               rules: Rules | None = None, plans_dir: Path | None = None,
               verse_index: VerseIndex | None = None, devotionals_dir: Path | None = None,
               calendar_path: Path | None = None) -> FastAPI:
    app = FastAPI(title="Study review", docs_url=None, redoc_url=None)
    validator = make_validator()
    rules = rules or Rules.load()
    regenerate_log = regenerate_log or store.directory.parent / "regenerate_queue.jsonl"
    # id -> status, for the queue. Built once; kept current by our own writes; POST /api/reload re-reads.
    statuses: dict[str, str] = {}

    def reload() -> None:
        statuses.clear()
        for study_id in store.ids():
            study = store.load(study_id)
            if is_written(study):
                statuses[study_id] = study.get("status", "generated")

    reload()

    def load(study_id: str) -> dict:
        try:
            parse_id(study_id)
            return store.load(study_id)
        except (ValueError, FileNotFoundError):
            raise HTTPException(404, f"No study {study_id!r}")

    def who(action_reviewer: str | None) -> str:
        name = (action_reviewer or reviewer or "").strip()
        if not name:
            raise HTTPException(400, "Reviewer name required")
        return name

    def save(study: dict) -> dict:
        errors = [f"{e.json_path}: {e.message}" for e in validator.iter_errors(study)]
        if errors:
            raise HTTPException(409, {"message": "Change would make the study invalid", "errors": errors})
        store.save(study)
        statuses[study["id"]] = study["status"]
        return study

    def writable(study: dict) -> None:
        if not is_written(study):
            raise HTTPException(409, "This study has no prose yet; there is nothing to review")

    def flag(study: dict, check: str, message: str, by: str) -> dict:
        updated = json.loads(json.dumps(study))
        checks = updated.setdefault("checks", {"checkedAt": now(), "checkerVersion": "review", "reasons": []})
        checks["reasons"].append({"check": check, "message": message, "by": by, "at": now()})
        updated["status"] = "flagged"
        return updated

    def queue() -> list[str]:
        stubs = [{"id": i, "status": s} for i, s in statuses.items()]
        return [s["id"] for s in queue_order(stubs, load_priority(priority_path))]

    def passage(ref: str) -> str | None:
        start, _, end = ref.partition("-")
        a = parse_id(start)
        b = parse_id(end) if end else a
        if a[:2] != b[:2] or b[2] - a[2] >= MAX_RANGE_VERSES:
            first = bible.text(a)
            return f"{first} …" if first else None
        texts = [bible.text((a[0], a[1], v)) for v in range(a[2], b[2] + 1)]
        return " ".join(t for t in texts if t) or None

    @app.get("/")
    def index():
        return FileResponse(STATIC / "index.html")

    @app.get("/api/config")
    def config():
        return {"reviewer": reviewer}

    @app.get("/api/queue")
    def get_queue(limit: int = 300):
        order = queue()
        counts: dict[str, int] = {}
        for s in statuses.values():
            counts[s] = counts.get(s, 0) + 1
        return {"total": len(order), "counts": counts,
                "items": [{"id": i, "status": statuses[i]} for i in order[:limit]]}

    @app.post("/api/reload")
    def post_reload():
        reload()
        return {"written": len(statuses)}

    @app.get("/api/study/{study_id}")
    def get_study(study_id: str):
        study = load(study_id)
        key = parse_id(study_id)
        order = queue()
        position = order.index(study_id) if study_id in order else None
        following = order[position + 1] if position is not None and position + 1 < len(order) else (
            order[0] if order and order[0] != study_id else None)
        return {
            "study": study,
            "verse": {"KJV": bible.text(key, "KJV"), "WEB": bible.text(key, "WEB")},
            "crossReferenceText": {x["ref"]: passage(x["ref"]) for x in study.get("crossReferences", [])},
            "candidates": bible.candidate_xrefs(key),
            "schemaErrors": [f"{e.json_path}: {e.message}" for e in validator.iter_errors(study)],
            "queue": {"position": position, "total": len(order), "next": following},
        }

    @app.post("/api/study/{study_id}/approve")
    def approve(study_id: str, action: Action):
        study = load(study_id)
        writable(study)
        name = who(action.reviewer)
        updated = json.loads(json.dumps(study))
        updated["status"] = "reviewed"
        updated["meta"]["reviewedBy"] = name
        updated["meta"]["reviewedAt"] = now()
        return save(updated)

    @app.post("/api/study/{study_id}/edit")
    def edit(study_id: str, change: Edit):
        study = load(study_id)
        writable(study)
        name = who(change.reviewer)
        unknown = set(change.fields) - set(PROSE_FIELDS)
        if unknown:
            raise HTTPException(400, f"Only prose sections can be edited, not {sorted(unknown)}")
        updated = json.loads(json.dumps(study))
        updated.update(change.fields)
        base, _, edits = study["contentVersion"].partition(".")
        updated["contentVersion"] = f"{base}.{int(edits or 0) + 1}"
        updated["meta"]["editedBy"] = name
        updated["meta"]["editedAt"] = now()
        # Re-run the free checks on the edited text (no AI). The result decides auto_checked vs flagged;
        # approval stays a separate, explicit step.
        outcome = check_study(updated, bible, rules, validator, judge=None)
        return save(apply(updated, outcome))

    @app.post("/api/study/{study_id}/flag")
    def flag_study(study_id: str, action: Action):
        study = load(study_id)
        writable(study)
        name = who(action.reviewer)
        note = (action.note or "").strip()
        if not note:
            raise HTTPException(400, "Say why you're flagging it")
        return save(flag(study, "reviewer", note, name))

    @app.post("/api/study/{study_id}/regenerate")
    def regenerate(study_id: str, action: Action):
        """No generator exists yet: record the request for it, and flag the study so it isn't approved meanwhile."""
        study = load(study_id)
        writable(study)
        name = who(action.reviewer)
        note = (action.note or "").strip() or "Regenerate requested"
        with open(regenerate_log, "a") as f:
            f.write(json.dumps({"id": study_id, "note": note, "by": name, "at": now(),
                                "contentVersion": study["contentVersion"]}) + "\n")
        return save(flag(study, "regenerate", note, name))

    # Reading plans (H8-H11): the same buttons, for AI-drafted plans.
    loaded: list[VerseIndex] = [verse_index] if verse_index else []

    def index() -> VerseIndex:
        if not loaded:
            loaded.append(VerseIndex.load())
        return loaded[0]

    add_plan_routes(app, plans_dir or PLANS_DIR, index, who, regenerate_log)

    # Devotionals (H15-H17). The calendar is re-read on each request so CSV edits show up without a restart.
    from ..devotionals import calendar as cal
    from ..devotionals.store import DEVOTIONALS_DIR, DevotionalStore

    def calendar() -> list:
        try:
            return cal.load(calendar_path or cal.CALENDAR)
        except cal.CalendarError as error:
            raise HTTPException(500, {"message": "calendar.csv has problems", "errors": error.problems})

    add_devotional_routes(app, DevotionalStore(devotionals_dir or DEVOTIONALS_DIR), calendar, bible, who,
                          regenerate_log)
    return app


def serve(store: StudyStore, bible: Bible, reviewer: str | None, port: int = 8765) -> None:
    import uvicorn

    app = create_app(store, bible, reviewer=reviewer)
    print(f"Review tool: http://127.0.0.1:{port}  (Ctrl+C to stop)")
    uvicorn.run(app, host="127.0.0.1", port=port, log_level="warning")
