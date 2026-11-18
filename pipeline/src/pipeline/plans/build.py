"""Assemble plans, validate, write out/plans/ (+ index.json), and bundle publishable plans into the app."""
from __future__ import annotations

import hashlib
import json
import math
import shutil
from datetime import datetime, timezone
from pathlib import Path

from ..paths import OUT, REPO
from .bible import VerseIndex, minutes
from .catalogue import CATALOGUE, CATEGORIES, START_HERE, StructuralPlan
from .split import split_days, to_passages
from .validate import CHECKER_VERSION, check_plan, day_words, schema_validator

PLANS_DIR = OUT / "plans"
APP_ASSETS = REPO / "app/src/main/assets/plans"
STRUCTURAL_CREDITS = ("Reading plan by Bible Scroll. Daily times are estimated from the World English Bible "
                      "at about 200 words a minute.")
CONTENT_FIELDS = ("title", "shortDescription", "description", "category", "days", "estimatedMinutesPerDay")


def now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def content_version(plan: dict) -> str:
    content = {k: plan[k] for k in CONTENT_FIELDS}
    return hashlib.sha256(json.dumps(content, sort_keys=True).encode()).hexdigest()[:12]


def difficulty(days: int, per_day: int) -> str:
    if per_day >= 15 or days >= 180:
        return "hard"
    if per_day <= 8 and days <= 31:
        return "easy"
    return "medium"


def estimate_minutes(day_minutes: list[float]) -> int:
    """Rounded-up average, so "about M min" isn't an undersell."""
    return max(1, math.ceil(sum(day_minutes) / len(day_minutes)))


def finish(plan: dict, index: VerseIndex, existing: dict | None = None) -> dict:
    """Fill estimatedMinutesPerDay/difficulty if missing, set contentVersion, keep meta from an unchanged earlier build."""
    if "estimatedMinutesPerDay" not in plan:
        plan["estimatedMinutesPerDay"] = estimate_minutes([minutes(day_words(d, index)) for d in plan["days"]])
    plan.setdefault("difficulty", difficulty(len(plan["days"]), plan["estimatedMinutesPerDay"]))
    plan["contentVersion"] = content_version(plan)
    if existing and existing.get("contentVersion") == plan["contentVersion"]:
        plan["meta"] = existing["meta"]
    return plan


#: Chapter-end snapping, loosest first. Looser keeps more days on chapter boundaries; if that makes any day too
#: long for the 1.5x rule, the next (tighter) setting is tried, ending with pure verse-level cuts.
SNAP_STEPS = (0.35, 0.25, 0.15, 0.08, 0.0)


def make_structural(definition: StructuralPlan, index: VerseIndex, existing: dict | None = None) -> dict:
    last: list[dict] = []
    # "Romans in 16 days" reads as a chapter a day: use exactly that when it passes the length rule.
    strategies: list[float | None] = [None] if _chapter_a_day(definition, index) else []
    for tolerance in [*strategies, *SNAP_STEPS]:
        plan = _structural(definition, index, existing, tolerance)
        last = check_plan(plan, index)
        if not last:
            plan["checks"] = {"checkedAt": now(), "checkerVersion": CHECKER_VERSION, "reasons": []}
            if existing and existing.get("contentVersion") == plan["contentVersion"] and "checks" in existing:
                plan["checks"] = existing["checks"]
            return plan
    # Even verse-level cuts failed: our own generator is wrong, so stop rather than ship it.
    raise ValueError(f"{definition.id} failed validation: {last[:5]}")


def _chapter_a_day_track(codes: tuple[str, ...], days: int, index: VerseIndex) -> bool:
    return len(codes) == 1 and index.chapter_count(codes[0]) == days


def _chapter_a_day(definition: StructuralPlan, index: VerseIndex) -> bool:
    return any(_chapter_a_day_track(codes, definition.days, index) for codes in definition.tracks)


def _structural(definition: StructuralPlan, index: VerseIndex, existing: dict | None, tolerance: float | None) -> dict:
    """
    tolerance None = a chapter a day for every track that is one book with exactly as many chapters as days
    (e.g. Proverbs in a 31-day plan); other tracks are balanced with the loosest snapping.
    """
    tracks = []
    for codes in definition.tracks:
        if tolerance is None and _chapter_a_day_track(codes, definition.days, index):
            tracks.append([index.chapters(codes[0], c, c) for c in range(1, definition.days + 1)])
        else:
            tracks.append(split_days(index.books(list(codes)), definition.days, index.chapter_ends,
                                     SNAP_STEPS[0] if tolerance is None else tolerance))
    days = []
    for n in range(definition.days):
        passages = []
        for track in tracks:
            passages += to_passages(track[n], index.position)
        days.append({"dayNumber": n + 1, "passages": passages})
    plan = {
        "id": definition.id, "schemaVersion": 1, "contentVersion": "",
        "title": definition.title, "shortDescription": definition.short, "description": definition.description,
        "category": definition.category, "days": days, "status": "auto_checked", "origin": "structural",
        "credits": STRUCTURAL_CREDITS, "meta": {"generatedAt": now()},
    }
    return finish(plan, index, existing)


def publishable(plan: dict) -> bool:
    """Structural plans ship once they validate; AI-drafted plans only once a person has reviewed them."""
    if plan["origin"] == "structural":
        return plan["status"] in ("auto_checked", "reviewed")
    return plan["status"] == "reviewed"


def load_plans(directory: Path = PLANS_DIR) -> dict[str, dict]:
    return {p.stem: json.loads(p.read_text()) for p in sorted(directory.glob("*.json")) if p.name != "index.json"}


def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    tmp.replace(path)


def build_index(plans: dict[str, dict]) -> dict:
    """index.json: publishable plans grouped by category, plus the "Start here" featured list."""
    shown = {pid: p for pid, p in sorted(plans.items()) if publishable(p)}
    summary = {pid: {"id": pid, "title": p["title"], "shortDescription": p["shortDescription"],
                     "category": p["category"], "days": len(p["days"]), "estimatedMinutesPerDay": p["estimatedMinutesPerDay"],
                     "difficulty": p["difficulty"], "contentVersion": p["contentVersion"]} for pid, p in shown.items()}
    return {
        "formatVersion": 1,
        "startHere": [pid for pid in START_HERE if pid in shown],
        "categories": [
            {**c, "plans": [pid for pid, p in shown.items() if p["category"] == c["id"]]} for c in CATEGORIES
        ],
        "plans": list(summary.values()),
    }


def build_structural(directory: Path = PLANS_DIR, index: VerseIndex | None = None) -> dict:
    """(Re)build every structural plan and index.json. AI-drafted plans in the folder are left untouched."""
    index = index or VerseIndex.load()
    existing = load_plans(directory)
    ids = [d.id for d in CATALOGUE]
    if len(ids) != len(set(ids)):
        raise ValueError("duplicate plan ids in the catalogue")
    for definition in CATALOGUE:
        current = existing.get(definition.id)
        if current and current.get("origin") != "structural":
            raise ValueError(f"{definition.id} exists as a {current.get('origin')} plan; refusing to overwrite it")
        write_json(directory / f"{definition.id}.json", make_structural(definition, index, current))
    plans = load_plans(directory)
    write_json(directory / "index.json", build_index(plans))
    return {"structural": len(CATALOGUE), "total": len(plans),
            "publishable": sum(1 for p in plans.values() if publishable(p))}


def bundle(directory: Path = PLANS_DIR, assets: Path = APP_ASSETS) -> list[str]:
    """Copy publishable plans + index.json into the app's assets (replacing what was there)."""
    plans = load_plans(directory)
    validator = schema_validator()
    chosen = [p for p in plans.values() if publishable(p)]
    for plan in chosen:
        errors = list(validator.iter_errors(plan))
        if errors:
            raise ValueError(f"{plan['id']} isn't schema-valid: {errors[0].message}")
    if assets.exists():
        shutil.rmtree(assets)
    for plan in chosen:
        write_json(assets / f"{plan['id']}.json", public_plan(plan))
    write_json(assets / "index.json", build_index(plans))
    return sorted(p["id"] for p in chosen)


def public_plan(plan: dict) -> dict:
    """What ships: no review notes or reviewer names."""
    out = {k: v for k, v in plan.items() if k not in ("checks", "meta")}
    out["reviewed"] = plan["status"] == "reviewed"
    return out
