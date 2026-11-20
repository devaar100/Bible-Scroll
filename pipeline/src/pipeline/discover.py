"""
The Discover list (discover.txt): one study id and its card's theme label per line ("PHP.4.13 Strength in Christ"),
in the order the app shows the cards. The label is any text up to publish.DISCOVER_THEME_MAX characters, shown on the
card as written. Blank lines and "#" comments are allowed.

`audit` reads the file without raising and reports every problem for a person to decide on (it never edits
the file):
  - malformed:  not BOOK.CHAPTER.VERSE with a known book code
  - theme:      no theme, or one longer than DISCOVER_THEME_MAX (publish refuses the list until every line has one)
  - missing:    no such KJV verse in source.db (or the app's KJV/WEB text is missing)
  - duplicate:  listed again (first position kept)
  - mapping:    the verse exists, but the Hebrew/Greek source numbers it differently (e.g. a Psalm whose Hebrew
                verse is one higher because the title is verse 1), or its words are split/merged across verses.
                The study's original words are still correct (source.db maps them to the KJV verse), but a
                reader comparing a Hebrew Bible or some modern versions will see a different verse number.

publish.load_discover (strict: raises on the first bad or duplicate line) is what publish and the batch
"discover" wave use, so a list with problems can't ship until it's fixed.
"""
from __future__ import annotations

import sqlite3
from dataclasses import dataclass, field
from pathlib import Path

from .paths import APP_BIBLE_DB, SOURCE_DB
from .publish import DISCOVER_FILE, DISCOVER_THEME_MAX
from .store import parse_id


@dataclass
class Line:
    number: int
    text: str


@dataclass
class Audit:
    entries: int = 0
    ids: list[str] = field(default_factory=list)                  # unique valid ids, file order
    malformed: list[Line] = field(default_factory=list)
    themes: list[tuple[Line, str]] = field(default_factory=list)  # (line, problem)
    missing: list[tuple[Line, str]] = field(default_factory=list)
    duplicates: list[tuple[Line, int]] = field(default_factory=list)  # (line, first line number)
    mapping: list[tuple[Line, list[dict]]] = field(default_factory=list)

    @property
    def clean(self) -> bool:
        return not (self.malformed or self.missing or self.duplicates or self.themes)


def read_lines(path: Path = DISCOVER_FILE) -> list[Line]:
    out = []
    for number, raw in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), start=1):
        text = raw.split("#", 1)[0].strip()
        if text:
            out.append(Line(number, text))
    return out


def _native_verse(native_ref: str) -> tuple[str, int, int] | None:
    """'Psa.46.11' -> ('Psa', 46, 11); TAHOT refs can carry an alternative in brackets: 'Mal.4.1(3.19)'."""
    parts = native_ref.split("(")[0].split(".")
    try:
        return parts[0], int(parts[1]), int(parts[2])
    except (IndexError, ValueError):
        return None


def audit(path: Path = DISCOVER_FILE, source_db: Path = SOURCE_DB, bible_db: Path = APP_BIBLE_DB) -> Audit:
    report = Audit()
    first_seen: dict[str, int] = {}
    with sqlite3.connect(f"file:{source_db}?mode=ro", uri=True) as src, \
            sqlite3.connect(f"file:{bible_db}?mode=ro", uri=True) as bible:
        for line in read_lines(path):
            report.entries += 1
            parts = line.text.split()
            verse_id = parts[0]
            try:
                key = parse_id(verse_id)
            except ValueError:
                report.malformed.append(line)
                continue
            if len(parts) == 1:
                report.themes.append((line, "no theme"))
            elif len(" ".join(parts[1:])) > DISCOVER_THEME_MAX:
                report.themes.append((line, f"theme longer than {DISCOVER_THEME_MAX} characters"))
            if verse_id in first_seen:
                report.duplicates.append((line, first_seen[verse_id]))
                continue
            first_seen[verse_id] = line.number
            if not src.execute("SELECT 1 FROM kjv_verse WHERE book_id=? AND chapter=? AND verse=?", key).fetchone():
                report.missing.append((line, "no such KJV verse"))
                continue
            texts = {t for (t,) in bible.execute(
                "SELECT translation_id FROM verses WHERE book_id=? AND chapter=? AND verse=?", key)}
            lacking = {"KJV", "WEB"} - texts
            if lacking:
                report.missing.append((line, f"no {'/'.join(sorted(lacking))} text in the app's bible.db"))
                continue
            report.ids.append(verse_id)
            rows = src.execute(
                "SELECT dataset, native_ref, kind, words, note FROM versification_map "
                "WHERE dataset IN ('TAHOT','TAGNT') AND kjv_book=? AND kjv_chapter=? AND kjv_verse=? "
                "ORDER BY native_ref", key).fetchall()
            unclean = []
            for dataset, native_ref, kind, words, note in rows:
                native = _native_verse(native_ref)
                same_number = native is not None and native[1:] == key[1:]
                if kind != "identical" or not same_number:
                    unclean.append({"dataset": dataset, "native": native_ref, "kind": kind, "words": words,
                                    "note": note or ""})
            if not rows:
                unclean.append({"dataset": "-", "native": "-", "kind": "no original words", "words": 0, "note": ""})
            if unclean:
                report.mapping.append((line, unclean))
    return report


def render(report: Audit, path: Path = DISCOVER_FILE) -> str:
    lines = [f"{path.name}: {report.entries} entries, {len(report.ids)} unique valid ids"]

    def section(title: str, items: list[str]) -> None:
        lines.append(f"\n{title}: {len(items)}")
        lines.extend(f"  {i}" for i in items)

    section("Malformed (not BOOK.CHAPTER.VERSE)", [f"line {l.number}: {l.text!r}" for l in report.malformed])
    section(f"Theme missing or longer than {DISCOVER_THEME_MAX} characters",
            [f"line {l.number}: {l.text} ({why})" for l, why in report.themes])
    section("Don't exist", [f"line {l.number}: {l.text} ({why})" for l, why in report.missing])
    section("Duplicates (first position kept)",
            [f"line {l.number}: {l.text} (first on line {first})" for l, first in report.duplicates])
    mapping = []
    for l, rows in report.mapping:
        desc = "; ".join(f"{r['dataset']} {r['native']} {r['kind']}" + (f" ({r['note']})" if r["note"] else "")
                         for r in rows)
        mapping.append(f"line {l.number}: {l.text} <- {desc}")
    section("Exist but don't map 1:1 (source verse numbering differs, or words split/merged)", mapping)
    return "\n".join(lines)
