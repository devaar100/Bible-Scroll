#!/usr/bin/env python3
"""
Build app/src/main/assets/bible.db (for Room createFromAsset) from public-domain USFM sources.

Usage (from the repo root, after `./gradlew assembleDebug` has exported the Room schema):

    python3 tools/build_bible_db.py \
        --kjv path/to/eng-kjv2006_usfm.zip \
        --web path/to/engwebp_usfm.zip

Each source can be the downloaded .zip or a directory it was unzipped into.
See tools/README.md for where to download them and their licence status.

The table/index DDL and Room's identity hash are read from the exported Room schema JSON, so
the asset always matches the @Entity classes exactly. Standard library only.
"""
from __future__ import annotations

import argparse
import json
import re
import sqlite3
import sys
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DEFAULT_SCHEMA_DIR = REPO / "app/schemas/aarnav100.developer.biblescroll.data.bible.BibleDatabase"
DEFAULT_OUT = REPO / "app/src/main/assets/bible.db"

# (USFM code, English name, testament) in canonical Protestant order; id = index + 1.
BOOKS = [
    ("GEN", "Genesis", "OT"), ("EXO", "Exodus", "OT"), ("LEV", "Leviticus", "OT"),
    ("NUM", "Numbers", "OT"), ("DEU", "Deuteronomy", "OT"), ("JOS", "Joshua", "OT"),
    ("JDG", "Judges", "OT"), ("RUT", "Ruth", "OT"), ("1SA", "1 Samuel", "OT"),
    ("2SA", "2 Samuel", "OT"), ("1KI", "1 Kings", "OT"), ("2KI", "2 Kings", "OT"),
    ("1CH", "1 Chronicles", "OT"), ("2CH", "2 Chronicles", "OT"), ("EZR", "Ezra", "OT"),
    ("NEH", "Nehemiah", "OT"), ("EST", "Esther", "OT"), ("JOB", "Job", "OT"),
    ("PSA", "Psalms", "OT"), ("PRO", "Proverbs", "OT"), ("ECC", "Ecclesiastes", "OT"),
    ("SNG", "Song of Solomon", "OT"), ("ISA", "Isaiah", "OT"), ("JER", "Jeremiah", "OT"),
    ("LAM", "Lamentations", "OT"), ("EZK", "Ezekiel", "OT"), ("DAN", "Daniel", "OT"),
    ("HOS", "Hosea", "OT"), ("JOL", "Joel", "OT"), ("AMO", "Amos", "OT"),
    ("OBA", "Obadiah", "OT"), ("JON", "Jonah", "OT"), ("MIC", "Micah", "OT"),
    ("NAM", "Nahum", "OT"), ("HAB", "Habakkuk", "OT"), ("ZEP", "Zephaniah", "OT"),
    ("HAG", "Haggai", "OT"), ("ZEC", "Zechariah", "OT"), ("MAL", "Malachi", "OT"),
    ("MAT", "Matthew", "NT"), ("MRK", "Mark", "NT"), ("LUK", "Luke", "NT"),
    ("JHN", "John", "NT"), ("ACT", "Acts", "NT"), ("ROM", "Romans", "NT"),
    ("1CO", "1 Corinthians", "NT"), ("2CO", "2 Corinthians", "NT"), ("GAL", "Galatians", "NT"),
    ("EPH", "Ephesians", "NT"), ("PHP", "Philippians", "NT"), ("COL", "Colossians", "NT"),
    ("1TH", "1 Thessalonians", "NT"), ("2TH", "2 Thessalonians", "NT"), ("1TI", "1 Timothy", "NT"),
    ("2TI", "2 Timothy", "NT"), ("TIT", "Titus", "NT"), ("PHM", "Philemon", "NT"),
    ("HEB", "Hebrews", "NT"), ("JAS", "James", "NT"), ("1PE", "1 Peter", "NT"),
    ("2PE", "2 Peter", "NT"), ("1JN", "1 John", "NT"), ("2JN", "2 John", "NT"),
    ("3JN", "3 John", "NT"), ("JUD", "Jude", "NT"), ("REV", "Revelation", "NT"),
]
BOOK_ID = {code: i + 1 for i, (code, _, _) in enumerate(BOOKS)}


@dataclass(frozen=True)
class TranslationSource:
    id: str
    name: str
    language: str
    licence: str
    sort_order: int


TRANSLATIONS = {
    # WEB first: the spec makes it the modern default.
    "web": TranslationSource(
        "WEB", "World English Bible", "en",
        "Public domain. \"World English Bible\" is a trademark of eBible.org; "
        "modified text may not use that name. Source: eBible.org (engwebp).",
        0,
    ),
    "kjv": TranslationSource(
        "KJV", "King James Version", "en",
        "Public domain outside the United Kingdom; in the UK, printing rights are held "
        "under Crown letters patent. Source: eBible.org (eng-kjv2006).",
        1,
    ),
}

# Whole lines that are not Scripture text (titles, headings, intros, speaker labels).
DROP_LINE_MARKERS = {
    "id", "ide", "h", "toc1", "toc2", "toc3", "mt", "mt1", "mt2", "mt3", "ms", "ms1", "ms2",
    "s", "s1", "s2", "s3", "r", "sp", "cl", "rem", "ip", "is", "is1", "ili", "ili1", "imt",
    "imt1", "io1", "iot", "sr", "mr", "usfm", "sts",
}
# Paragraph/poetry markers: the rest of the line continues the current verse.
PARA_MARKERS = {
    "p", "m", "b", "nb", "pi", "pi1", "pi2", "mi", "li", "li1", "li2", "q", "q1", "q2", "q3",
    "qr", "qc", "qm1", "qm2", "pc", "pmo", "pm", "pmr", "lh", "lf",
}

WJ_OPEN, WJ_CLOSE = "\ue000", "\ue001"
LINE_MARKER = re.compile(r"^\\([a-z]+[0-9]*)(?:\s+|$)(.*)$")
VERSE_SPLIT = re.compile(r"\\v\s+(\d+)\s*")
NOTES = re.compile(r"\\(f|fe|x)\s.*?\\\1\*", re.S)
WORD = re.compile(r"\\(\+?w)\s+([^|\\]*?)(?:\|[^\\]*?)?\\\1\*")
WJ_START = re.compile(r"\\\+?wj\s")
WJ_END = re.compile(r"\\\+?wj\*")
OTHER_CLOSE = re.compile(r"\\\+?[a-z]+[0-9]*\*")
OTHER_OPEN = re.compile(r"\\\+?[a-z]+[0-9]*\s?")


@dataclass
class ParsedVerse:
    chapter: int
    verse: int
    raw: list[str] = field(default_factory=list)


@dataclass
class ParsedBook:
    code: str
    verses: list[ParsedVerse] = field(default_factory=list)
    superscriptions: dict[int, str] = field(default_factory=dict)


def strip_inline(raw: str) -> str:
    s = NOTES.sub("", raw)
    s = WORD.sub(r"\2", s)
    s = WJ_START.sub(WJ_OPEN, s)
    s = WJ_END.sub(WJ_CLOSE, s)
    s = OTHER_CLOSE.sub("", s)
    s = OTHER_OPEN.sub("", s)
    s = s.replace("¶", "")
    s = re.sub(r"\s+", " ", s).strip()
    if "\\" in s:
        raise ValueError(f"unhandled USFM markup left in: {s!r}")
    return s


def utf16_len(s: str) -> int:
    return len(s.encode("utf-16-le")) // 2


def extract_jesus_ranges(marked: str) -> tuple[str, str | None]:
    """Removes WJ sentinels; returns (plain text, 'start-end;...' in UTF-16 units) ."""
    out: list[str] = []
    ranges: list[list[int]] = []
    pos = 0  # UTF-16 offset into output
    start: int | None = None
    for ch in marked:
        if ch == WJ_OPEN:
            if start is None:
                start = pos
        elif ch == WJ_CLOSE:
            if start is not None:
                ranges.append([start, pos])
                start = None
        else:
            out.append(ch)
            pos += utf16_len(ch)
    if start is not None:  # unclosed at verse end: close it here
        ranges.append([start, pos])
    text = "".join(out)
    # Normalise whitespace again (sentinels may have sat between spaces) while remapping offsets.
    text, ranges = _collapse_spaces(text, ranges)
    cleaned = []
    for a, b in ranges:
        while a < b and text[a].isspace():  # BMP-only text after notes are removed
            a += 1
        while b > a and text[b - 1].isspace():
            b -= 1
        if b > a:
            if cleaned and a <= cleaned[-1][1] + 1:
                cleaned[-1][1] = b
            else:
                cleaned.append([a, b])
    encoded = ";".join(f"{a}-{b}" for a, b in cleaned) or None
    return text, encoded


def _collapse_spaces(text: str, ranges: list[list[int]]) -> tuple[str, list[list[int]]]:
    if any(ord(c) > 0xFFFF for c in text):
        raise ValueError("non-BMP character in verse text; offset remapping assumes BMP")
    keep = []
    mapping = []  # old index -> new index
    prev_space = True  # also trims leading space
    for c in text:
        mapping.append(len(keep))
        if c.isspace():
            if prev_space:
                continue
            keep.append(" ")
            prev_space = True
        else:
            keep.append(c)
            prev_space = False
    mapping.append(len(keep))
    new = "".join(keep)
    if new.endswith(" "):
        new = new[:-1]
    n = len(new)
    return new, [[min(mapping[a], n), min(mapping[b], n)] for a, b in ranges]


def parse_usfm(content: str) -> ParsedBook | None:
    m = re.match(r"\ufeff?\\id\s+([A-Z0-9]{3})", content)
    if not m or m.group(1) not in BOOK_ID:
        return None  # front matter, glossary, deuterocanon, etc.
    book = ParsedBook(m.group(1))
    chapter = 0
    current: ParsedVerse | None = None

    for line in content.splitlines():
        line = line.strip()
        if not line:
            continue
        lm = LINE_MARKER.match(line)
        if lm:
            marker, rest = lm.group(1), lm.group(2)
            if marker == "c":
                chapter = int(rest.split()[0])
                current = None
                continue
            if marker == "d":
                # Psalm title: before verse 1 it is a superscription; later ones are headings.
                if current is None or current.chapter != chapter:
                    book.superscriptions[chapter] = strip_inline(rest).replace(WJ_OPEN, "").replace(WJ_CLOSE, "")
                continue
            if marker in DROP_LINE_MARKERS:
                continue
            if marker in PARA_MARKERS:
                line = rest
            elif marker != "v":
                raise ValueError(f"{book.code} {chapter}: unknown line marker \\{marker}")
        if chapter == 0:
            continue  # book introduction

        parts = VERSE_SPLIT.split(line)
        # parts = [text-before-first-\v, num, text, num, text, ...]
        if parts[0].strip() and current is not None:
            current.raw.append(parts[0])
        for i in range(1, len(parts), 2):
            current = ParsedVerse(chapter, int(parts[i]))
            book.verses.append(current)
            current.raw.append(parts[i + 1])
    return book


def read_sources(path: Path) -> list[str]:
    if path.is_dir():
        return [p.read_text(encoding="utf-8-sig") for p in sorted(path.glob("*.usfm"))]
    with zipfile.ZipFile(path) as z:
        return [
            z.read(n).decode("utf-8-sig")
            for n in sorted(z.namelist())
            if n.lower().endswith((".usfm", ".sfm")) and not n.startswith("__MACOSX")
        ]


def load_schema(schema_dir: Path) -> dict:
    files = sorted(schema_dir.glob("*.json"), key=lambda p: int(p.stem))
    if not files:
        sys.exit(f"No Room schema in {schema_dir}. Run ./gradlew assembleDebug first.")
    return json.loads(files[-1].read_text())["database"]


def create_schema(conn: sqlite3.Connection, db: dict) -> None:
    for entity in db["entities"]:
        name = entity["tableName"]
        conn.execute(entity["createSql"].replace("${TABLE_NAME}", name))
        for index in entity.get("indices", []):
            conn.execute(index["createSql"].replace("${TABLE_NAME}", name))
    for q in db["setupQueries"]:  # room_master_table + identity hash
        conn.execute(q)
    conn.execute(f"PRAGMA user_version = {int(db['version'])}")


def build(sources: dict[str, Path], schema_dir: Path, out: Path) -> None:
    db = load_schema(schema_dir)
    out.parent.mkdir(parents=True, exist_ok=True)
    tmp = out.with_suffix(".tmp")
    tmp.unlink(missing_ok=True)
    conn = sqlite3.connect(tmp)
    create_schema(conn, db)

    max_chapters: dict[int, int] = {}
    for key, path in sources.items():
        tr = TRANSLATIONS[key]
        conn.execute(
            "INSERT INTO translations (id, name, language_code, licence, sort_order) VALUES (?,?,?,?,?)",
            (tr.id, tr.name, tr.language, tr.licence, tr.sort_order),
        )
        books = {b.code: b for b in (parse_usfm(c) for c in read_sources(path)) if b}
        missing = [c for c, _, _ in BOOKS if c not in books]
        if missing:
            sys.exit(f"{tr.id}: missing books {missing} in {path}")

        ordinal = 0
        verse_rows, chapter_rows = [], []
        jesus_count = 0
        for code, _, _ in BOOKS:
            book = books[code]
            bid = BOOK_ID[code]
            seen: set[tuple[int, int]] = set()
            per_chapter: dict[int, int] = {}
            for v in book.verses:
                key_cv = (v.chapter, v.verse)
                if key_cv in seen:
                    sys.exit(f"{tr.id} {code} {v.chapter}:{v.verse} duplicated")
                seen.add(key_cv)
                text, jesus = extract_jesus_ranges(strip_inline(" ".join(v.raw)))
                if not text:
                    # e.g. WEB Acts 8:37: number kept, text only in a variant footnote.
                    print(f"skipped {tr.id} {code} {v.chapter}:{v.verse} (no text)", file=sys.stderr)
                    continue
                ordinal += 1
                jesus_count += jesus is not None
                verse_rows.append((tr.id, bid, v.chapter, v.verse, ordinal, text, jesus))
                per_chapter[v.chapter] = per_chapter.get(v.chapter, 0) + 1
            chapters = sorted(per_chapter)
            if chapters != list(range(1, len(chapters) + 1)):
                sys.exit(f"{tr.id} {code}: non-contiguous chapters {chapters}")
            max_chapters[bid] = max(max_chapters.get(bid, 0), len(chapters))
            for ch in chapters:
                chapter_rows.append((tr.id, bid, ch, per_chapter[ch], book.superscriptions.get(ch)))

        conn.executemany(
            "INSERT INTO verses (translation_id, book_id, chapter, verse, ordinal, text, jesus_ranges) "
            "VALUES (?,?,?,?,?,?,?)", verse_rows)
        conn.executemany(
            "INSERT INTO chapters (translation_id, book_id, chapter, verse_count, superscription) "
            "VALUES (?,?,?,?,?)", chapter_rows)
        print(f"{tr.id}: {ordinal} verses, {len(chapter_rows)} chapters, "
              f"{jesus_count} verses with words of Jesus")

    conn.executemany(
        "INSERT INTO books (id, code, name, testament, chapter_count) VALUES (?,?,?,?,?)",
        [(BOOK_ID[c], c, n, t, max_chapters[BOOK_ID[c]]) for c, n, t in BOOKS],
    )
    conn.commit()
    conn.execute("VACUUM")
    conn.close()
    tmp.replace(out)
    print(f"Wrote {out} ({out.stat().st_size / 1_048_576:.1f} MiB, schema v{db['version']})")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--kjv", type=Path, required=True, help="eng-kjv2006_usfm.zip or its unzipped dir")
    ap.add_argument("--web", type=Path, required=True, help="engwebp_usfm.zip or its unzipped dir")
    ap.add_argument("--schema-dir", type=Path, default=DEFAULT_SCHEMA_DIR)
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = ap.parse_args()
    build({"web": args.web, "kjv": args.kjv}, args.schema_dir, args.out)


if __name__ == "__main__":
    main()
