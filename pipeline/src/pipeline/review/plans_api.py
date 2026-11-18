"""Review-tool endpoints for reading plans (AI-drafted ones need a person's approval before they ship)."""
from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Callable

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from ..books import BY_USFM
from ..plans.ai import revalidate
from ..plans.bible import VerseIndex, minutes
from ..plans.build import build_index, load_plans, publishable, write_json
from ..plans.validate import check_plan, day_words, label, schema_validator
from ..store import now

PLAN_ID = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")
#: Problems that block approval. "verification" (chronological) and reviewer notes don't: approving is how
#: a person signs those off.
BLOCKING = {"schema", "passage", "empty_day", "day_length", "day_numbers", "coverage"}
_REF = re.compile(r"^([1-3A-Z][A-Z0-9]{2})\.(\d+)\.(\d+)(?:-([1-3A-Z][A-Z0-9]{2})\.(\d+)\.(\d+))?$")


class PlanAction(BaseModel):
    reviewer: str | None = None
    note: str | None = None


class PlanDayEdit(BaseModel):
    dayNumber: int
    title: str | None = None
    intro: str | None = None
    #: One passage per line: JHN.3.1-JHN.3.21 (or a single verse JHN.3.16).
    passages: str | None = None


class PlanEdit(BaseModel):
    reviewer: str | None = None
    title: str | None = None
    shortDescription: str | None = None
    description: str | None = None
    days: list[PlanDayEdit] = []


def parse_passages(text: str) -> list[dict]:
    passages = []
    for number, line in enumerate(filter(None, (l.strip() for l in text.splitlines())), start=1):
        m = _REF.match(line)
        if not m or (m.group(4) and m.group(4) != m.group(1)):
            raise HTTPException(400, f"Passage line {number} isn't BOOK.C.V or BOOK.C.V-BOOK.C.V in one book: {line!r}")
        b, c1, v1 = m.group(1), int(m.group(2)), int(m.group(3))
        c2, v2 = (int(m.group(5)), int(m.group(6))) if m.group(4) else (c1, v1)
        passages.append({"book": b, "startChapter": c1, "startVerse": v1, "endChapter": c2, "endVerse": v2})
    return passages


def format_passage(p: dict) -> str:
    return f"{p['book']}.{p['startChapter']}.{p['startVerse']}-{p['book']}.{p['endChapter']}.{p['endVerse']}"


def add_plan_routes(app: FastAPI, plans_dir: Path, index: Callable[[], VerseIndex], who: Callable[[str | None], str],
                    regenerate_log: Path) -> None:
    validator = schema_validator()

    def path(plan_id: str) -> Path:
        if not PLAN_ID.match(plan_id):
            raise HTTPException(404, f"No plan {plan_id!r}")
        return plans_dir / f"{plan_id}.json"

    def load(plan_id: str) -> dict:
        p = path(plan_id)
        if not p.exists():
            raise HTTPException(404, f"No plan {plan_id!r}")
        return json.loads(p.read_text())

    def save(plan: dict) -> dict:
        errors = [f"{e.json_path}: {e.message}" for e in validator.iter_errors(plan)]
        if errors:
            raise HTTPException(409, {"message": "Change would make the plan invalid", "errors": errors})
        write_json(path(plan["id"]), plan)
        write_json(plans_dir / "index.json", build_index(load_plans(plans_dir)))
        return plan

    def ai_only(plan: dict) -> None:
        if plan["origin"] != "ai_drafted":
            raise HTTPException(409, "Structural plans are computed from word counts; change the catalogue instead")

    def needs_review(plan: dict) -> bool:
        return plan["origin"] == "ai_drafted" and plan["status"] != "reviewed"

    @app.get("/api/plans")
    def list_plans():
        plans = load_plans(plans_dir).values()
        rows = [{"id": p["id"], "title": p["title"], "status": p["status"], "origin": p["origin"],
                 "category": p["category"], "days": len(p["days"]), "needsReview": needs_review(p),
                 "published": publishable(p)} for p in plans]
        # Review order: flagged, then other drafts awaiting review, then everything else; by title within each.
        rows.sort(key=lambda r: (0 if r["status"] == "flagged" else 1 if r["needsReview"] else 2, r["title"]))
        return {"plans": rows, "toReview": sum(r["needsReview"] for r in rows)}

    @app.get("/api/plan/{plan_id}")
    def get_plan(plan_id: str):
        plan = load(plan_id)
        idx = index()
        days = []
        for d in plan["days"]:
            words = day_words(d, idx)
            days.append({
                **d,
                "minutes": round(minutes(words), 1),
                "labels": [label(p) if p.get("book") in BY_USFM else str(p) for p in d["passages"]],
                "passagesText": "\n".join(format_passage(p) for p in d["passages"]),
            })
        current = check_plan(plan, idx)
        return {"plan": plan, "days": days, "problems": current,
                "canApprove": plan["origin"] == "ai_drafted" and not any(r["check"] in BLOCKING for r in current),
                "published": publishable(plan)}

    @app.post("/api/plan/{plan_id}/approve")
    def approve(plan_id: str, action: PlanAction):
        plan = load(plan_id)
        ai_only(plan)
        name = who(action.reviewer)
        blocking = [r for r in check_plan(plan, index()) if r["check"] in BLOCKING]
        if blocking:
            raise HTTPException(409, {"message": "Fix these first", "errors": [r["message"] for r in blocking]})
        plan["status"] = "reviewed"
        plan["meta"]["reviewedBy"] = name
        plan["meta"]["reviewedAt"] = now()
        return save(plan)

    @app.post("/api/plan/{plan_id}/edit")
    def edit(plan_id: str, change: PlanEdit):
        plan = load(plan_id)
        ai_only(plan)
        name = who(change.reviewer)
        for field in ("title", "shortDescription", "description"):
            value = getattr(change, field)
            if value is not None:
                plan[field] = value.strip()
        by_number = {d["dayNumber"]: d for d in plan["days"]}
        for day in change.days:
            target = by_number.get(day.dayNumber)
            if target is None:
                raise HTTPException(400, f"No day {day.dayNumber}")
            for field in ("title", "intro"):
                value = getattr(day, field)
                if value is not None:
                    if value.strip():
                        target[field] = value.strip()
                    else:
                        target.pop(field, None)
            if day.passages is not None:
                target["passages"] = parse_passages(day.passages)
        plan["meta"]["editedBy"] = name
        plan["meta"]["editedAt"] = now()
        plan["status"] = "generated"
        return save(revalidate(plan, index()))  # re-checked: auto_checked or flagged; approval stays separate

    def flag(plan: dict, check: str, message: str, by: str) -> dict:
        checks = plan.setdefault("checks", {"checkedAt": now(), "checkerVersion": "review", "reasons": []})
        checks["reasons"].append({"check": check, "message": message, "by": by, "at": now()})
        plan["status"] = "flagged"
        return plan

    @app.post("/api/plan/{plan_id}/flag")
    def flag_plan(plan_id: str, action: PlanAction):
        plan = load(plan_id)
        ai_only(plan)
        name = who(action.reviewer)
        note = (action.note or "").strip()
        if not note:
            raise HTTPException(400, "Say why you're flagging it")
        return save(flag(plan, "reviewer", note, name))

    @app.post("/api/plan/{plan_id}/regenerate")
    def regenerate(plan_id: str, action: PlanAction):
        plan = load(plan_id)
        ai_only(plan)
        name = who(action.reviewer)
        note = (action.note or "").strip() or "Regenerate requested"
        with open(regenerate_log, "a") as f:
            f.write(json.dumps({"kind": "plan", "id": plan_id, "note": note, "by": name, "at": now(),
                                "contentVersion": plan["contentVersion"]}) + "\n")
        return save(flag(plan, "regenerate", note, name))
