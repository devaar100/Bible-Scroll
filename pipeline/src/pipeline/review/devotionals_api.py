"""Review-tool endpoints for devotionals: read beside the passage and calendar row, edit, approve, flag."""
from __future__ import annotations

import json
from pathlib import Path
from typing import Callable

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from ..devotionals import calendar as cal
from ..devotionals.checks import BLOCKING, apply, check_devotional, free_checks, load_rules
from ..devotionals.publish import PUBLISHABLE
from ..devotionals.store import DevotionalStore, passage_label, passage_text, validator
from ..store import Bible, now

EDITABLE = ("title", "context", "reflect", "applyIt", "prayer")


class DevotionalAction(BaseModel):
    reviewer: str | None = None
    note: str | None = None


class DevotionalEdit(BaseModel):
    reviewer: str | None = None
    #: Section -> new text. An empty prayer removes it (prayer is optional).
    fields: dict[str, str]


def add_devotional_routes(app: FastAPI, store: DevotionalStore, calendar: Callable[[], list[cal.Day]],
                          bible: Bible, who: Callable[[str | None], str], regenerate_log: Path) -> None:
    schema = validator()
    rules = load_rules()

    def day_for(devotional_id: str) -> cal.Day | None:
        return next((d for d in calendar() if d.id == devotional_id), None)

    def load(devotional_id: str) -> dict:
        try:
            return store.load(devotional_id)
        except (ValueError, FileNotFoundError):
            raise HTTPException(404, f"No devotional {devotional_id!r}")

    def save(devotional: dict) -> dict:
        errors = [f"{e.json_path}: {e.message}" for e in schema.iter_errors(devotional)]
        if errors:
            raise HTTPException(409, {"message": "Change would make the devotional invalid", "errors": errors})
        store.save(devotional)
        return devotional

    def flag(devotional: dict, check: str, message: str, by: str) -> dict:
        updated = json.loads(json.dumps(devotional))
        checks = updated.setdefault("checks", {"checkedAt": now(), "checkerVersion": "review", "reasons": []})
        checks["reasons"].append({"check": check, "message": message, "by": by, "at": now()})
        updated["status"] = "flagged"
        return updated

    @app.get("/api/devotionals")
    def list_devotionals():
        written = {i: store.load(i) for i in store.ids()}
        rows = []
        for day in calendar():
            d = written.get(day.id)
            rows.append({"id": day.id, "date": day.date, "series": day.series, "theme": day.theme,
                         "title": d["title"] if d else None, "status": d["status"] if d else "not_written",
                         "published": bool(d and d["status"] in PUBLISHABLE)})
        order = {"flagged": 0, "generated": 1, "auto_checked": 2, "reviewed": 3, "not_written": 4}
        rows.sort(key=lambda r: (order.get(r["status"], 5), r["date"]))
        counts: dict[str, int] = {}
        for r in rows:
            counts[r["status"]] = counts.get(r["status"], 0) + 1
        return {"devotionals": rows, "counts": counts,
                "toReview": sum(1 for r in rows if r["status"] in ("flagged", "generated", "auto_checked"))}

    @app.get("/api/devotional/{devotional_id}")
    def get_devotional(devotional_id: str):
        devotional = load(devotional_id)
        day = day_for(devotional_id)
        passages = []
        for p in devotional.get("verses", []):
            try:
                text = passage_text(p, bible.bible_db) if bible.bible_db else None
            except (LookupError, KeyError):
                text = None
            passages.append({"label": passage_label(p), "text": text})
        problems = free_checks(devotional, day, bible, rules, schema)
        return {
            "devotional": devotional, "passages": passages, "problems": problems,
            "calendar": None if day is None else {"date": day.date, "series": day.series, "season": day.season,
                                                  "seriesDay": day.series_day, "seriesLength": day.series_length,
                                                  "theme": day.theme, "anchor": day.anchor.text},
            "limits": rules.limits,
            "canApprove": not any(r["check"] in BLOCKING for r in problems),
            "published": devotional["status"] in PUBLISHABLE,
        }

    @app.post("/api/devotional/{devotional_id}/approve")
    def approve(devotional_id: str, action: DevotionalAction):
        devotional = load(devotional_id)
        name = who(action.reviewer)
        blocking = [r for r in free_checks(devotional, day_for(devotional_id), bible, rules, schema)
                    if r["check"] in BLOCKING]
        if blocking:
            raise HTTPException(409, {"message": "Fix these first", "errors": [r["message"] for r in blocking]})
        updated = json.loads(json.dumps(devotional))
        updated["status"] = "reviewed"
        updated["meta"]["reviewedBy"] = name
        updated["meta"]["reviewedAt"] = now()
        return save(updated)

    @app.post("/api/devotional/{devotional_id}/edit")
    def edit(devotional_id: str, change: DevotionalEdit):
        devotional = load(devotional_id)
        name = who(change.reviewer)
        unknown = set(change.fields) - set(EDITABLE)
        if unknown:
            raise HTTPException(400, f"Only {', '.join(EDITABLE)} can be edited, not {sorted(unknown)}")
        updated = json.loads(json.dumps(devotional))
        for key, value in change.fields.items():
            if key == "prayer" and not value.strip():
                updated.pop("prayer", None)
            else:
                updated[key] = value.strip()
        base, _, edits = devotional["contentVersion"].partition(".")
        updated["contentVersion"] = f"{base}.{int(edits or 0) + 1}"
        updated["meta"]["editedBy"] = name
        updated["meta"]["editedAt"] = now()
        # Free checks only (no AI); approval stays a separate step.
        outcome = check_devotional(updated, day_for(devotional_id), bible, rules, schema, judge=None)
        return save(apply(updated, outcome))

    @app.post("/api/devotional/{devotional_id}/flag")
    def flag_devotional(devotional_id: str, action: DevotionalAction):
        devotional = load(devotional_id)
        name = who(action.reviewer)
        note = (action.note or "").strip()
        if not note:
            raise HTTPException(400, "Say why you're flagging it")
        return save(flag(devotional, "reviewer", note, name))

    @app.post("/api/devotional/{devotional_id}/regenerate")
    def regenerate(devotional_id: str, action: DevotionalAction):
        devotional = load(devotional_id)
        name = who(action.reviewer)
        note = (action.note or "").strip() or "Regenerate requested"
        with open(regenerate_log, "a") as f:
            f.write(json.dumps({"kind": "devotional", "id": devotional_id, "note": note, "by": name, "at": now(),
                                "contentVersion": devotional["contentVersion"]}) + "\n")
        return save(flag(devotional, "regenerate", note, name))
