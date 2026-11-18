"""
The theme calendar (devotionals/calendar.csv): one editable row per day of a leap year, keyed by MM-DD.

Columns: date (MM-DD), season, series, series_day, series_length, cover_style, theme, anchor
(BOOK.C.V-BOOK.C.V in one book). 02-29 is a standalone bonus devotional (series "Leap day", 1 of 1).
Easter moves (Mar 22 - Apr 25), so the calendar uses fixed-date seasons whose themes fit any year:
Lent-friendly series from mid-February, "The cross" then "Living hope" across the Easter window.

load() fails loudly: every problem in the file is listed, nothing is skipped.
"""
from __future__ import annotations

import csv
import re
from dataclasses import dataclass
from datetime import date, timedelta
from pathlib import Path

from ..books import BY_USFM
from ..paths import PIPELINE

CALENDAR = PIPELINE / "devotionals" / "calendar.csv"
COLUMNS = ["date", "season", "series", "series_day", "series_length", "cover_style", "theme", "anchor"]
#: Typographic/gradient cover designs drawn by the app (no images). Keep in sync with schema/devotional.json.
COVER_STYLES = ("dawn", "meadow", "ocean", "ember", "dusk", "stone", "linen", "night")
LEAP_DAY = "02-29"
_ANCHOR = re.compile(r"^([1-3A-Z][A-Z0-9]{2})\.(\d+)\.(\d+)-([1-3A-Z][A-Z0-9]{2})\.(\d+)\.(\d+)$")


@dataclass(frozen=True)
class Anchor:
    book: str
    start: tuple[int, int]
    end: tuple[int, int]

    @property
    def text(self) -> str:
        return f"{self.book}.{self.start[0]}.{self.start[1]}-{self.book}.{self.end[0]}.{self.end[1]}"

    def keys(self, book_id: int) -> tuple[tuple[int, int, int], tuple[int, int, int]]:
        return (book_id, *self.start), (book_id, *self.end)


@dataclass(frozen=True)
class Day:
    date: str            # MM-DD
    season: str
    series: str
    series_day: int
    series_length: int
    cover_style: str
    theme: str
    anchor: Anchor

    @property
    def id(self) -> str:
        return f"devotional-{self.date}"

    @property
    def day_of_year(self) -> int:
        return day_of_year(self.date)


def all_dates() -> list[str]:
    """Every MM-DD of a leap year, in order (366)."""
    d, out = date(2024, 1, 1), []
    while d.year == 2024:
        out.append(d.strftime("%m-%d"))
        d += timedelta(days=1)
    return out


def day_of_year(mmdd: str) -> int:
    """Leap-year numbering: Feb 29 = 60, Dec 31 = 366. The app looks devotionals up by MM-DD."""
    return date(2024, int(mmdd[:2]), int(mmdd[3:])).timetuple().tm_yday


def parse_anchor(text: str) -> Anchor:
    m = _ANCHOR.match(text.strip())
    if not m or m.group(1) != m.group(4) or m.group(1) not in BY_USFM:
        raise ValueError(f"anchor {text!r} isn't BOOK.C.V-BOOK.C.V within one book")
    start, end = (int(m.group(2)), int(m.group(3))), (int(m.group(5)), int(m.group(6)))
    if end < start:
        raise ValueError(f"anchor {text!r} ends before it starts")
    return Anchor(m.group(1), start, end)


class CalendarError(ValueError):
    def __init__(self, problems: list[str]):
        super().__init__(f"{len(problems)} problem(s) in the calendar:\n  " + "\n  ".join(problems))
        self.problems = problems


def load(path: Path = CALENDAR, verses: frozenset | set | None = None) -> list[Day]:
    """
    Reads and validates the calendar. verses: the KJV verse keys (book_id, chapter, verse); when given,
    every anchor's first and last verse must exist. Raises CalendarError listing every problem.
    """
    problems: list[str] = []
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        if reader.fieldnames != COLUMNS:
            raise CalendarError([f"columns must be {','.join(COLUMNS)}; got {reader.fieldnames}"])
        rows = list(reader)

    days: list[Day] = []
    for line, row in enumerate(rows, start=2):
        where = f"line {line} ({row.get('date')})"
        try:
            if any((row[c] or "").strip() == "" for c in COLUMNS):
                raise ValueError("empty cell")
            anchor = parse_anchor(row["anchor"])
            if row["cover_style"] not in COVER_STYLES:
                raise ValueError(f"cover_style {row['cover_style']!r} isn't one of {', '.join(COVER_STYLES)}")
            day = Day(row["date"].strip(), row["season"].strip(), row["series"].strip(), int(row["series_day"]),
                      int(row["series_length"]), row["cover_style"], row["theme"].strip(), anchor)
        except (ValueError, KeyError) as error:
            problems.append(f"{where}: {error}")
            continue
        if verses is not None:
            book_id = BY_USFM[anchor.book].id
            for key in anchor.keys(book_id):
                if key not in verses:
                    problems.append(f"{where}: {anchor.text}: {anchor.book} {key[1]}:{key[2]} doesn't exist (KJV)")
        days.append(day)

    expected = all_dates()
    found = [d.date for d in days]
    if found != expected and not problems:
        missing = sorted(set(expected) - set(found))
        extra = sorted(set(found) - set(expected))
        dupes = sorted({d for d in found if found.count(d) > 1})
        problems.append(f"dates must be every day of a leap year in order, once each "
                        f"(missing {missing[:5]}, unknown {extra[:5]}, repeated {dupes[:5]})")

    # Series numbering: consecutive rows (the leap bonus may sit inside a series), 1..length, one cover.
    regular = [d for d in days if d.date != LEAP_DAY]
    i = 0
    while i < len(regular):
        first = regular[i]
        run = [first]
        while i + len(run) < len(regular) and regular[i + len(run)].series == first.series \
                and regular[i + len(run)].series_day != 1:
            run.append(regular[i + len(run)])
        numbers = [d.series_day for d in run]
        if numbers != list(range(1, len(run) + 1)) or any(d.series_length != len(run) for d in run):
            problems.append(f"series {first.series!r} from {first.date}: days {numbers} don't count 1..n "
                            f"or series_length isn't {len(run)}")
        if len({d.cover_style for d in run}) > 1:
            problems.append(f"series {first.series!r} from {first.date}: one cover_style per series")
        i += len(run)

    leap = next((d for d in days if d.date == LEAP_DAY), None)
    if leap and (leap.series_length != 1):
        problems.append("02-29 must be a standalone bonus day (series_length 1)")

    seen: dict[str, str] = {}
    for d in days:
        if d.anchor.text in seen:
            problems.append(f"{d.date}: anchor {d.anchor.text} already used on {seen[d.anchor.text]}")
        seen.setdefault(d.anchor.text, d.date)
    if problems:
        raise CalendarError(problems)
    return days


def write(days: list[dict], path: Path = CALENDAR) -> None:
    """Writes rows (dicts with COLUMNS keys). Used by the drafting script; edit the CSV by hand after."""
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS, lineterminator="\n")
        writer.writeheader()
        writer.writerows(days)


def balance(days: list[Day]) -> dict:
    """OT/NT split and series sizes, for the report after editing."""
    ot = sum(1 for d in days if BY_USFM[d.anchor.book].id <= 39)
    return {"days": len(days), "oldTestament": ot, "newTestament": len(days) - ot,
            "series": len({(d.series, d.date) for d in days if d.series_day == 1})}
