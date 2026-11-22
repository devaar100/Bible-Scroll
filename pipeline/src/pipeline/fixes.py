"""
`pipeline fixes apply FILE`: apply hand-fixed prose (format studyFixes/1) to out/studies.

FILE holds {"studies": {ID: {section: {"before", "after"}}}}. A section is replaced only when the study's current
text equals "before" exactly; anything else is skipped and listed, so a fix never lands on text it wasn't written
for. A study with at least one applied section is marked edited the same way the review tool's Edit does
(meta.editedBy/editedAt, contentVersion "<hash>.N") plus meta.editNote naming the fixes file. The model fields stay.
meta.editedBy makes the study protected (batch.protected), so batch submit/collect and the retry round never
overwrite it. Then the code checks run again (no AI) and decide auto_checked or flagged.

Devotionals (format devotionalFixes/1, {"devotionals": {devotional-MM-DD: {section: {"before", "after"}}}}) work the
same way on out/devotionals, like the review tool's devotional Edit: sections title/context/reflect/applyIt/prayer,
meta.editedBy/editedAt/editNote, contentVersion .N, then the devotional code checks (no AI). editedBy keeps
`devotionals draft --stale` from redrafting it. An AI reviewer reason is not kept: run `devotionals check --recheck
ID` to have the edited text reviewed again.
"""
from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path

from .checks import PROSE_FIELDS, Rules, apply, check_study
from .store import Bible, StudyStore, now

FORMAT = "studyFixes/1"
DEVOTIONAL_FORMAT = "devotionalFixes/1"
DEVOTIONAL_SECTIONS = ("title", "context", "reflect", "applyIt", "prayer")  # review tool's EDITABLE


class FixesError(ValueError):
    pass


@dataclass
class StudyResult:
    id: str
    applied: list[str] = field(default_factory=list)
    skipped: list[tuple[str, str]] = field(default_factory=list)  # (section, why)
    status: str | None = None
    reasons: list[dict] = field(default_factory=list)


def load(path: Path) -> dict:
    data = json.loads(path.read_text())
    if data.get("format") != FORMAT:
        raise FixesError(f"{path}: format is {data.get('format')!r}, expected {FORMAT!r}")
    for sid, sections in data.get("studies", {}).items():
        for section, fix in sections.items():
            if section not in PROSE_FIELDS:
                raise FixesError(f"{sid}: {section!r} is not a prose section")
            if not isinstance(fix.get("before"), str) or not isinstance(fix.get("after"), str):
                raise FixesError(f"{sid}.{section}: needs string 'before' and 'after'")
    return data


def apply_fixes(path: Path, store: StudyStore, bible: Bible, *, by: str, note: str | None = None,
                dry_run: bool = False) -> list[StudyResult]:
    from .studies import validator as make_validator

    data = load(path)
    rules, validator = Rules.load(), make_validator()
    note = note or f"Hand-edited from {path.name}"
    results = []
    for sid, sections in data["studies"].items():
        result = StudyResult(sid)
        results.append(result)
        if not store.path(sid).exists():
            result.skipped = [(s, "no study file") for s in sections]
            continue
        study = store.load(sid)
        if study.get("status") == "reviewed":
            result.skipped = [(s, "study is reviewed") for s in sections]
            continue
        updated = json.loads(json.dumps(study))
        for section, fix in sections.items():
            if study.get(section) == fix["after"]:
                result.skipped.append((section, "already applied"))
            elif study.get(section) != fix["before"]:
                result.skipped.append((section, "current text differs from 'before'"))
            else:
                updated[section] = fix["after"]
                result.applied.append(section)
        if not result.applied:
            continue
        base, _, edits = study["contentVersion"].partition(".")
        updated["contentVersion"] = f"{base}.{int(edits or 0) + 1}"
        updated["meta"]["editedBy"] = by
        updated["meta"]["editedAt"] = now()
        entry = f"{note}: {', '.join(result.applied)}"
        earlier = study.get("meta", {}).get("editNote")
        updated["meta"]["editNote"] = f"{earlier}; {entry}" if earlier else entry  # keep earlier edits' notes
        updated = apply(updated, check_study(updated, bible, rules, validator, judge=None))
        errors = [f"{e.json_path}: {e.message}" for e in validator.iter_errors(updated)]
        if errors:
            raise FixesError(f"{sid}: the fixed study would be invalid: {errors}")
        result.status, result.reasons = updated["status"], updated["checks"]["reasons"]
        if not dry_run:
            store.save(updated)
    return results


def render(results: list[StudyResult], dry_run: bool = False) -> str:
    lines = []
    applied = sum(len(r.applied) for r in results)
    skipped = [(r.id, s, why) for r in results for s, why in r.skipped]
    for r in results:
        if r.applied:
            why = "; ".join(f"{x['check']}" + (f" ({x['section']})" if x.get("section") else "") for x in r.reasons)
            lines.append(f"{r.id}: applied {', '.join(r.applied)} -> {r.status}" + (f": {why}" if why else ""))
    lines.append(f"{applied} section(s) {'would be ' if dry_run else ''}applied in "
                 f"{sum(1 for r in results if r.applied)} file(s); {len(skipped)} skipped")
    lines += [f"  skipped {sid} {section}: {why}" for sid, section, why in skipped]
    return "\n".join(lines)


def load_devotional_fixes(path: Path) -> dict:
    data = json.loads(path.read_text())
    if data.get("format") != DEVOTIONAL_FORMAT:
        raise FixesError(f"{path}: format is {data.get('format')!r}, expected {DEVOTIONAL_FORMAT!r}")
    for did, sections in data.get("devotionals", {}).items():
        for section, fix in sections.items():
            if section not in DEVOTIONAL_SECTIONS:
                raise FixesError(f"{did}: {section!r} is not an editable devotional section")
            if not isinstance(fix.get("before"), str) or not isinstance(fix.get("after"), str):
                raise FixesError(f"{did}.{section}: needs string 'before' and 'after'")
    return data


def apply_devotional_fixes(path: Path, store, bible: Bible, days: list, *, by: str, note: str | None = None,
                           dry_run: bool = False) -> list[StudyResult]:
    """Hand fixes for devotionals (see the module docstring). [days]: the calendar (devotionals.calendar.load())."""
    from .devotionals.checks import apply as dapply, check_devotional, load_rules
    from .devotionals.store import validator as make_validator

    data = load_devotional_fixes(path)
    rules, validator = load_rules(), make_validator()
    by_id = {d.id: d for d in days}
    note = note or f"Hand-edited from {path.name}"
    results = []
    for did, sections in data["devotionals"].items():
        result = StudyResult(did)
        results.append(result)
        if not store.exists(did):
            result.skipped = [(s, "no devotional file") for s in sections]
            continue
        devotional = store.load(did)
        if devotional.get("status") == "reviewed":
            result.skipped = [(s, "devotional is reviewed") for s in sections]
            continue
        updated = json.loads(json.dumps(devotional))
        for section, fix in sections.items():
            current = devotional.get(section, "")
            if current == fix["after"]:
                result.skipped.append((section, "already applied"))
            elif current != fix["before"]:
                result.skipped.append((section, "current text differs from 'before'"))
            else:
                if section == "prayer" and not fix["after"].strip():
                    updated.pop("prayer", None)
                else:
                    updated[section] = fix["after"].strip()
                result.applied.append(section)
        if not result.applied:
            continue
        base, _, edits = devotional["contentVersion"].partition(".")
        updated["contentVersion"] = f"{base}.{int(edits or 0) + 1}"
        updated["meta"]["editedBy"] = by
        updated["meta"]["editedAt"] = now()
        entry = f"{note}: {', '.join(result.applied)}"
        earlier = devotional.get("meta", {}).get("editNote")
        updated["meta"]["editNote"] = f"{earlier}; {entry}" if earlier else entry
        updated = dapply(updated, check_devotional(updated, by_id.get(did), bible, rules, validator, judge=None))
        errors = [f"{e.json_path}: {e.message}" for e in validator.iter_errors(updated)]
        if errors:
            raise FixesError(f"{did}: the fixed devotional would be invalid: {errors}")
        result.status, result.reasons = updated["status"], updated["checks"]["reasons"]
        if not dry_run:
            store.save(updated)
    return results
