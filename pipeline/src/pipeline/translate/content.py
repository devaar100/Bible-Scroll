"""
Plans and devotionals as translation units, and where translations live.

A translation is stored in the document itself: doc["i18n"][lang] = {status, sourceVersion, <texts>, meta, checks}
(schema/plan.json $defs.planLocale, schema/devotional.json $defs.devotionalLocale). sourceVersion is the English
contentVersion it was made from; when the English changes the locale becomes "stale" and stops shipping until it's
retranslated. Only auto_checked or reviewed, non-stale locales are published, without status, meta or checks.

Plan category titles (index.json) are one more unit, stored in out/plans/categories.i18n.json.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

from .engine import CHECKER_VERSION, Outcome, Unit, now

PUBLISHABLE = ("auto_checked", "reviewed")

# ---- Status helpers (plans and devotionals alike) ----

def locale_publishable(doc: dict, lang: str) -> bool:
    loc = doc.get("i18n", {}).get(lang)
    return bool(loc) and loc["status"] in PUBLISHABLE and loc["sourceVersion"] == doc["contentVersion"]


def mark_stale(i18n: dict | None, content_version: str) -> dict | None:
    """After the English is rebuilt: locales made from older English become stale (kept, not shipped)."""
    if not i18n:
        return i18n
    return {lang: (loc if loc["sourceVersion"] == content_version else {**loc, "status": "stale"})
            for lang, loc in i18n.items()}


def needs_translation(doc: dict, lang: str, force: bool = False, retry_flagged: bool = False) -> bool:
    """
    Missing, stale or never checked: always. Flagged: only with retry_flagged (a person may be looking at it).
    auto_checked and reviewed: only with force (never overwrite a reviewed translation without --force).
    """
    loc = doc.get("i18n", {}).get(lang)
    if loc is None or loc["sourceVersion"] != doc["contentVersion"] or loc["status"] in ("stale", "translated"):
        return True
    if force:
        return True
    return retry_flagged and loc["status"] == "flagged"


def _record(outcome: Outcome) -> tuple[dict, dict]:
    meta = {"model": outcome.model or "", "translatedAt": now(), "checkedAt": now(), "retried": outcome.retried}
    if outcome.drift_model:
        meta["driftModel"] = outcome.drift_model
    if outcome.prompt_hash:
        meta["promptHash"] = outcome.prompt_hash
    if outcome.drift_prompt_hash:
        meta["driftPromptHash"] = outcome.drift_prompt_hash
    checks = {"checkerVersion": CHECKER_VERSION, "reasons": outcome.reasons, "minor": outcome.minor}
    if outcome.drift is not None:
        checks["drift"] = outcome.drift
    return meta, checks


def approve(doc: dict, lang: str, reviewer: str) -> None:
    loc = doc["i18n"][lang]
    if loc["sourceVersion"] != doc["contentVersion"]:
        raise ValueError(f"{doc['id']} {lang} is stale: retranslate before approving")
    loc["status"] = "reviewed"
    loc["meta"] = {**loc["meta"], "reviewedBy": reviewer, "reviewedAt": now()}


# ---- Plans ----

PLAN_FIELDS = {  # key -> (where it shows, character limit: ~25% above the English schema limit)
    "title": ("plan title on cards and the plan page", 75),
    "shortDescription": ("one-line hook on the plan card", 150),
    "description": ("'About this plan' on the plan page", 1000),
    "credits": ("small print on the plan page", 375),
}


def plan_unit(plan: dict) -> Unit:
    fields, where, limits = {}, {}, {}
    for key, (place, limit) in PLAN_FIELDS.items():
        if plan.get(key):
            fields[key], where[key], limits[key] = plan[key], place, limit
    for day in plan["days"]:
        n = day["dayNumber"]
        if day.get("title"):
            k = f"day.{n}.title"
            fields[k], where[k], limits[k] = day["title"], f"heading of day {n}", 100
        if day.get("intro"):
            k = f"day.{n}.intro"
            fields[k], where[k], limits[k] = day["intro"], f"short introduction before day {n}'s reading", 750
    return Unit(plan["id"], fields, where, limits)


def apply_plan(plan: dict, lang: str, outcome: Outcome) -> bool:
    """Store the outcome. Returns False (and stores nothing) if there is no usable text, e.g. a refusal."""
    t = outcome.texts
    if not all(t.get(k) for k in ("title", "shortDescription", "description")):
        return False
    meta, checks = _record(outcome)
    loc = {"status": outcome.status, "sourceVersion": plan["contentVersion"],
           **{k: t[k] for k in PLAN_FIELDS if t.get(k)}}
    days = []
    for day in plan["days"]:
        n = day["dayNumber"]
        entry = {"dayNumber": n, **{f: t[f"day.{n}.{f}"] for f in ("title", "intro") if t.get(f"day.{n}.{f}")}}
        if len(entry) > 1:
            days.append(entry)
    if days:
        loc["days"] = days
    loc["meta"], loc["checks"] = meta, checks
    plan.setdefault("i18n", {})[lang] = loc
    return True


def plan_reuse(plans: dict[str, dict], lang: str) -> dict[str, str]:
    """English -> approved translation for whole fields seen in other plans (e.g. the shared credits line)."""
    memory = {}
    for plan in plans.values():
        if not locale_publishable(plan, lang):
            continue
        loc = plan["i18n"][lang]
        for key in ("credits",):
            if plan.get(key) and loc.get(key):
                memory.setdefault(plan[key], loc[key])
    return memory


def public_plan_i18n(plan: dict) -> dict:
    """The i18n block as shipped: publishable locales only, texts only."""
    out = {}
    for lang, loc in plan.get("i18n", {}).items():
        if locale_publishable(plan, lang):
            out[lang] = {k: v for k, v in loc.items() if k not in ("status", "sourceVersion", "meta", "checks")}
    return out


# ---- Plan categories (index.json titles) ----

def categories_version(categories: list[dict]) -> str:
    return hashlib.sha256(json.dumps([[c["id"], c["title"]] for c in categories]).encode()).hexdigest()[:12]


def categories_unit(categories: list[dict]) -> Unit:
    fields = {c["id"]: c["title"] for c in categories}
    return Unit("plan-categories", fields, {k: "section heading in the reading plans browser" for k in fields},
                {k: 60 for k in fields})


def load_categories_i18n(path: Path) -> dict:
    return json.loads(path.read_text()) if path.exists() else {}


def category_titles(categories: list[dict], store: dict) -> dict[str, dict[str, str]]:
    """lang -> {category id: title} for publishable, up-to-date category translations."""
    version = categories_version(categories)
    return {lang: loc["titles"] for lang, loc in store.items()
            if loc["status"] in PUBLISHABLE and loc["sourceVersion"] == version}


# ---- Devotionals ----

DEVOTIONAL_FIELDS = {
    "title": ("devotional title on cards and the cover", 75),
    "theme": ("one-line theme under the title", 200),
    "seriesTitle": ("name of the devotional series", 100),
    "context": ("'Context' section: what is happening in the passage", 1200),
    "reflect": ("'Reflect' section: the heart of the devotional", 1800),
    "applyIt": ("'Apply it' section: one step for today", 900),
    "prayer": ("closing prayer", 700),
}


def devotional_unit(devotional: dict, scripture: str) -> Unit:
    source = {**devotional, "seriesTitle": devotional["series"]["title"]}
    fields = {k: source[k] for k in DEVOTIONAL_FIELDS if source.get(k)}
    return Unit(devotional["id"], fields, {k: DEVOTIONAL_FIELDS[k][0] for k in fields},
                {k: DEVOTIONAL_FIELDS[k][1] for k in fields}, scripture=scripture)


def apply_devotional(devotional: dict, lang: str, outcome: Outcome) -> bool:
    t = outcome.texts
    required = [k for k in DEVOTIONAL_FIELDS if k != "prayer"]
    if not all(t.get(k) for k in required):
        return False
    meta, checks = _record(outcome)
    devotional.setdefault("i18n", {})[lang] = {
        "status": outcome.status, "sourceVersion": devotional["contentVersion"],
        **{k: t[k] for k in DEVOTIONAL_FIELDS if t.get(k)}, "meta": meta, "checks": checks,
    }
    return True


def public_devotional_i18n(devotional: dict) -> dict:
    out = {}
    for lang, loc in devotional.get("i18n", {}).items():
        if locale_publishable(devotional, lang):
            out[lang] = {k: loc[k] for k in DEVOTIONAL_FIELDS if loc.get(k)}
    return out
