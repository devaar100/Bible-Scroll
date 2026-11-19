"""
`pipeline report` / `pipeline reports`: pull a study after someone reports a problem with it (e.g. an email from the
app's Report a problem sheet).

A report flags the study (checks.reasons entry with check "report"), queues it in out/regenerate_queue.jsonl (the
same file the review tool's Regenerate button writes) and keeps it out of publish while the report is open.

A report is open until the prose changes or a person looks at it again: meta.generatedAt, editedAt or reviewedAt
later than the report. Publish checks that directly, not just the status, so `check --recheck` or an approval of
the old text can't put a reported study back on the CDN by accident.
"""
from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

from .paths import OUT
from .store import StudyStore, is_written, now, parse_id

REPORT_CHECK = "report"
QUEUE = OUT / "regenerate_queue.jsonl"
#: Meta timestamps that mean the reported text was regenerated, edited or looked at again.
_RESOLVING = ("generatedAt", "editedAt", "reviewedAt")


def _time(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def is_open(reason: dict, study: dict) -> bool:
    """A report reason still applies: nothing regenerated, edited or reviewed the study since it was made."""
    if reason.get("check") != REPORT_CHECK or not reason.get("at"):
        return False
    reported = _time(reason["at"])
    meta = study.get("meta", {})
    return not any(meta.get(field) and _time(meta[field]) > reported for field in _RESOLVING)


def open_reports(study: dict) -> list[dict]:
    return [r for r in study.get("checks", {}).get("reasons", []) if is_open(r, study)]


def all_reports(study: dict) -> list[dict]:
    return [r for r in study.get("checks", {}).get("reasons", []) if r.get("check") == REPORT_CHECK]


@dataclass
class ReportResult:
    id: str
    added: bool  # False: an open report with the same reason was already there


class ReportError(ValueError):
    pass


def report(store: StudyStore, ids: list[str], reason: str, by: str | None = None,
           queue: Path = QUEUE, at: str | None = None) -> list[ReportResult]:
    """
    Flag every study in [ids] with [reason]. All ids are checked first; if any is bad, nothing is written.
    Re-reporting a study with the same open reason changes nothing (and isn't queued twice).
    """
    from .studies import validator

    reason = " ".join(reason.split())
    if not reason:
        raise ReportError("Give a reason: --reason \"what's wrong\"")
    problems, studies = [], []
    for study_id in dict.fromkeys(ids):  # keep order, drop repeats
        try:
            parse_id(study_id)
        except ValueError:
            problems.append(f"{study_id}: not a study id (expected e.g. JHN.3.16)")
            continue
        if not store.path(study_id).exists():
            problems.append(f"{study_id}: no study file (run `pipeline studies` first)")
            continue
        study = store.load(study_id)
        if not is_written(study):
            problems.append(f"{study_id}: has no prose yet, so there's nothing to report")
            continue
        studies.append(study)
    if problems:
        raise ReportError("Nothing was changed:\n  " + "\n  ".join(problems))

    when = at or now()
    check = validator()
    results = []
    for study in studies:
        if any(r["message"] == reason for r in open_reports(study)):
            results.append(ReportResult(study["id"], added=False))
            continue
        updated = json.loads(json.dumps(study))
        checks = updated.setdefault("checks", {"checkedAt": when, "checkerVersion": "report", "reasons": []})
        entry = {"check": REPORT_CHECK, "message": reason, "at": when}
        if by:
            entry["by"] = by
        checks["reasons"].append(entry)
        updated["status"] = "flagged"
        errors = [e.message for e in check.iter_errors(updated)]
        if errors:  # never write a study the schema rejects
            raise ReportError(f"{study['id']}: the flagged study fails schema/study.json: {errors[:3]}")
        store.save(updated)
        line = {"kind": "study", "source": "report", "id": study["id"], "note": reason, "at": when,
                "contentVersion": study["contentVersion"], "promptVersion": study["meta"].get("promptVersion"),
                "previousStatus": study.get("status")}
        if by:
            line["by"] = by
        queue.parent.mkdir(parents=True, exist_ok=True)
        with open(queue, "a") as f:
            f.write(json.dumps(line, ensure_ascii=False) + "\n")
        results.append(ReportResult(study["id"], added=True))
    return results


@dataclass
class ReportRow:
    id: str
    status: str
    reason: str
    at: str
    by: str | None
    open: bool


def list_reports(store: StudyStore, include_resolved: bool = False) -> list[ReportRow]:
    """Every study flagged by `pipeline report`, in Bible order (open reports only unless include_resolved)."""
    rows = []
    for study in store.all():
        for r in all_reports(study):
            still = is_open(r, study)
            if still or include_resolved:
                rows.append(ReportRow(study["id"], study.get("status", "generated"), r["message"], r["at"], r.get("by"), still))
    return rows


def render(rows: list[ReportRow]) -> str:
    if not rows:
        return "No reported studies."
    lines = [f"{len({r.id for r in rows})} reported stud{'y' if len({r.id for r in rows}) == 1 else 'ies'}:"]
    for r in rows:
        state = "open" if r.open else "resolved (prose regenerated, edited or reviewed since)"
        who = f" by {r.by}" if r.by else ""
        lines.append(f"  {r.id}  [{r.status}, {state}]  {r.at[:10]}{who}: {r.reason}")
    return "\n".join(lines)
