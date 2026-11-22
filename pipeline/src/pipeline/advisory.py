"""
Reviewer report: the studies with advisory reasons (checks.advisory), grouped by criterion.

Decision 25: the AI reviewer and the non-blocking code checks never block a study or trigger a retry. Their
reasons are kept in checks.advisory and listed here for a person to read. Report only; nothing here changes a study.
"""
from __future__ import annotations

import json
import os
import re
import tempfile
from collections import Counter
from pathlib import Path

from .store import now

_RUBRIC_LABEL = re.compile(r"^(factual risk|one-sided theology|tone) \d/3")


def criterion(reason: dict) -> str:
    """The reviewer's dimension for a rubric reason ("rubric: tone"), else the check name."""
    if reason.get("check") == "rubric":
        m = _RUBRIC_LABEL.match(reason.get("message", ""))
        return f"rubric: {m.group(1)}" if m else "rubric: other"
    return reason.get("check", "?")


def build(studies: list[dict], title: str) -> dict:
    """{title, generatedAt, studies, flagged, byCriterion: {criterion: studies}, entries: [...]} over written
    studies; an entry is a study with at least one advisory reason."""
    entries, by_criterion = [], Counter()
    for st in sorted(studies, key=lambda s: s.get("id", "")):
        advisory = (st.get("checks") or {}).get("advisory") or []
        if not advisory:
            continue
        by_criterion.update({criterion(r) for r in advisory})
        entries.append({"id": st["id"], "status": st.get("status"),
                        "reasons": [{"criterion": criterion(r), "section": r.get("section"), "message": r["message"]}
                                    for r in advisory]})
    return {"title": title, "generatedAt": now(), "studies": len(studies), "flagged": len(entries),
            "byCriterion": dict(sorted(by_criterion.items(), key=lambda kv: (-kv[1], kv[0]))), "entries": entries}


def markdown(report: dict) -> str:
    lines = [f"# Reviewer report: {report['title']}", "",
             f"Advisory only (decision 25): these never block a study or trigger a retry. "
             f"{report['flagged']} of {report['studies']} studies have at least one. Generated {report['generatedAt']}.",
             ""]
    if report["byCriterion"]:
        lines += ["| criterion | studies |", "|---|---|"]
        lines += [f"| {c} | {n} |" for c, n in report["byCriterion"].items()]
        lines.append("")
    for e in report["entries"]:
        lines.append(f"## {e['id']} ({e['status']})")
        for r in e["reasons"]:
            where = f" [{r['section']}]" if r.get("section") else ""
            lines.append(f"- **{r['criterion']}**{where}: {r['message']}")
        lines.append("")
    return "\n".join(lines)


def _atomic(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=path.parent, prefix=".tmp-", suffix=path.suffix)
    with os.fdopen(fd, "w") as f:
        f.write(text)
    os.replace(tmp, path)


def write(report: dict, folder: Path) -> Path:
    """folder/reviewer_report.json + reviewer_report.md. Returns the .md path."""
    _atomic(folder / "reviewer_report.json", json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    md = folder / "reviewer_report.md"
    _atomic(md, markdown(report) + "\n")
    return md
