"""
Redraft one series of the calendar with Claude (new themes and anchors, same dates and length).

The calendar itself was drafted with Claude and then edited by hand; this is for replacing a series you don't
like. It rewrites only that series' rows, validates the whole file before saving, and refuses when a
devotional in the series is already reviewed (unless force).
"""
from __future__ import annotations

from pathlib import Path

from ..books import BOOKS
from ..plans.ai import DraftFailed, Drafter
from . import calendar as cal
from .store import DevotionalStore

SYSTEM = """You plan a 366-day calendar of short daily devotionals for a free Bible-reading app used by \
Christians of many traditions. Each day has a theme (a short phrase) and one anchor passage of 3 to 25 verses. \
Choose passages you are certain exist, with exact verse numbers in the KJV numbering. Keep themes warm and \
plain, avoid contested doctrine as a theme, and avoid passages already used elsewhere in the calendar."""

SCHEMA = {
    "type": "object",
    "properties": {"days": {"type": "array", "items": {
        "type": "object",
        "properties": {"theme": {"type": "string"}, "anchor": {"type": "string"}},
        "required": ["theme", "anchor"], "additionalProperties": False}}},
    "required": ["days"],
    "additionalProperties": False,
}


def redraft_series(first_date: str, drafter: Drafter, *, title: str | None = None, guidance: str = "",
                   path: Path = cal.CALENDAR, verses=None, store: DevotionalStore | None = None,
                   force: bool = False) -> list[cal.Day]:
    days = cal.load(path, verses)
    start = next((i for i, d in enumerate(days) if d.date == first_date and d.series_day == 1), None)
    if start is None:
        raise ValueError(f"No series starts on {first_date}")
    series = days[start]
    rows = [d for d in days if d.series == series.series and d.date != cal.LEAP_DAY
            and days.index(d) >= start][:series.series_length]
    if store and not force:
        reviewed = [d.date for d in rows if store.exists(d.id) and store.load(d.id).get("status") == "reviewed"]
        if reviewed:
            raise ValueError(f"Reviewed devotionals in this series ({', '.join(reviewed)}); use --force")
    used = sorted(d.anchor.text for d in days if d not in rows)
    prompt = (f"Replace the series \"{series.series}\" ({series.season}), {len(rows)} days, "
              f"{rows[0].date} to {rows[-1].date}.\n"
              + (f"New series title: {title}\n" if title else "")
              + (f"Guidance: {guidance}\n" if guidance else "")
              + f"Current days:\n" + "\n".join(f"- {d.theme} ({d.anchor.text})" for d in rows)
              + f"\n\nReturn exactly {len(rows)} days in order. Anchor format: BOOK.C.V-BOOK.C.V within one book, "
              f"book codes: {', '.join(b.usfm for b in BOOKS)}.\nAnchors already used elsewhere: {', '.join(used)}")
    draft = drafter.draft(SYSTEM, prompt, SCHEMA, 16000)
    new = draft.data["days"]
    if len(new) != len(rows):
        raise DraftFailed(f"asked for {len(rows)} days, got {len(new)}")
    replacement = {d.date: n for d, n in zip(rows, new)}
    out = []
    for d in days:
        n = replacement.get(d.date)
        out.append({"date": d.date, "season": d.season, "series": (title or d.series) if n else d.series,
                    "series_day": d.series_day, "series_length": d.series_length, "cover_style": d.cover_style,
                    "theme": n["theme"].strip() if n else d.theme, "anchor": n["anchor"].strip() if n else d.anchor.text})
    tmp = path.with_suffix(".draft.csv")
    cal.write(out, tmp)
    try:
        result = cal.load(tmp, verses)  # raises CalendarError listing every problem; calendar.csv untouched
    except cal.CalendarError:
        tmp.unlink()
        raise
    tmp.replace(path)
    return [d for d in result if d.date in replacement]
