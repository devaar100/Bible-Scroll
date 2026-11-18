"""Progress report: how many studies are at each stage, per book."""
from __future__ import annotations

from collections import Counter, defaultdict

from .books import BOOKS
from .store import StudyStore, is_written, parse_id

STAGES = ("not_written", "generated", "auto_checked", "flagged", "failed", "reviewed")


def stage(study: dict) -> str:
    if study.get("status") == "generated" and not is_written(study):
        return "not_written"
    return study.get("status", "generated")


def count(store: StudyStore) -> dict[int, Counter]:
    by_book: dict[int, Counter] = defaultdict(Counter)
    for study_id in store.ids():
        by_book[parse_id(study_id)[0]][stage(store.load(study_id))] += 1
    return by_book


def render(by_book: dict[int, Counter]) -> str:
    header = ["Book", *STAGES, "total", "% reviewed"]
    rows = []
    totals: Counter = Counter()
    for book in BOOKS:
        c = by_book.get(book.id)
        if not c:
            continue
        totals.update(c)
        total = sum(c.values())
        rows.append([book.name, *(str(c[s]) for s in STAGES), str(total), f"{100 * c['reviewed'] / total:.1f}"])
    grand = sum(totals.values())
    rows.append(["**Total**", *(str(totals[s]) for s in STAGES), str(grand),
                 f"{100 * totals['reviewed'] / grand:.1f}" if grand else "0.0"])
    lines = ["| " + " | ".join(header) + " |", "|" + "---|" * len(header)]
    lines += ["| " + " | ".join(r) + " |" for r in rows]
    return "\n".join(lines)
