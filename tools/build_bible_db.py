#!/usr/bin/env python3
"""
Build app/src/main/assets/bible.db (for Room createFromAsset) from public-domain USFM sources:
WEB, KJV, BSB (Berean Standard Bible) and RV1909 (Reina-Valera 1909), all from eBible.org.

Usage (from the repo root, after `./gradlew assembleDebug` has exported the Room schema):

    python3 tools/build_bible_db.py \
        --kjv path/to/eng-kjv2006_usfm.zip \
        --web path/to/engwebp_usfm.zip \
        --bsb path/to/engbsb_usfm.zip \
        --rv1909 path/to/spaRV1909_usfm.zip

Each source can be the downloaded .zip or a directory it was unzipped into. See tools/README.md for the
download URLs, the SHA-256s of the files the committed bible.db was built from, and licence status.

The Room tables' DDL and identity hash come from the exported Room schema JSON, so the asset always matches the
@Entity classes. Extra per-translation metadata (attribution, red letters, descriptions, source) goes in a
`translation_info` table Room doesn't know about (Room validates only its own tables and the identity hash).
Every key is KJV-style (book, chapter, verse); RV1909's Spanish chapter divisions are remapped (see
RV1909_VERSIFICATION) and every verse that doesn't map one-to-one is written to the versification report.
Same sources -> byte-identical bible.db. Standard library only.
"""
from __future__ import annotations

import argparse
import hashlib
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
DEFAULT_REPORT = REPO / "tools/bible_db_report.md"

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
    #: translations.licence (Room). The app's S8 splits it at "Source:" into licence and attribution.
    licence: str
    sort_order: int
    #: translation_info (not a Room table).
    attribution: str
    description_en: str
    description_es: str
    source_url: str
    edition: str


TRANSLATIONS = {
    # WEB first: the spec makes it the modern default.
    "web": TranslationSource(
        "WEB", "World English Bible", "en",
        "Public domain. \"World English Bible\" is a trademark of eBible.org; "
        "modified text may not use that name. Source: eBible.org (engwebp).",
        0,
        attribution="World English Bible (WEB), public domain. Source: eBible.org (engwebp).",
        description_en="Modern, readable English. A public-domain update of the 1901 American Standard Version.",
        description_es="Inglés moderno y fácil de leer. Una actualización de dominio público de la American Standard Version de 1901.",
        source_url="https://eBible.org/Scriptures/engwebp_usfm.zip",
        edition="eBible.org engwebp (Protestant canon)",
    ),
    "kjv": TranslationSource(
        "KJV", "King James Version", "en",
        "Public domain outside the United Kingdom; in the UK, printing rights are held "
        "under Crown letters patent. Source: eBible.org (eng-kjv2006).",
        1,
        attribution="King James Version (KJV). Source: eBible.org (eng-kjv2006).",
        description_en="The classic 1611 translation in traditional English, loved for its poetry.",
        description_es="La traducción clásica de 1611 en inglés tradicional, apreciada por su poesía.",
        source_url="https://eBible.org/Scriptures/eng-kjv2006_usfm.zip",
        edition="eBible.org eng-kjv2006",
    ),
    "bsb": TranslationSource(
        "BSB", "Berean Standard Bible", "en",
        "Public domain: dedicated to the public domain on 30 April 2023 (berean.bible/terms.htm). "
        "Shown unmodified, so it carries the Berean name. Source: eBible.org (engbsb).",
        2,
        attribution="The Holy Bible, Berean Standard Bible, BSB. Dedicated to the public domain. Source: eBible.org (engbsb).",
        description_en="Clear, contemporary English translated straight from the Hebrew and Greek.",
        description_es="Inglés claro y actual, traducido directamente del hebreo y el griego.",
        source_url="https://eBible.org/Scriptures/engbsb_usfm.zip",
        edition="eBible.org engbsb, source files dated 2026-10-30",
    ),
    "rv1909": TranslationSource(
        "RV1909", "Reina-Valera 1909", "es",
        "Dominio público (public domain). Source: eBible.org (spaRV1909).",
        3,
        attribution="Santa Biblia, Reina-Valera 1909. Dominio público. Source: eBible.org (spaRV1909).",
        description_en="The classic Spanish Reina-Valera translation, 1909 revision, in the public domain.",
        description_es="La clásica traducción Reina-Valera en su revisión de 1909, de dominio público.",
        source_url="https://eBible.org/Scriptures/spaRV1909_usfm.zip",
        edition="eBible.org spaRV1909, source files dated 2015-08-10",
    ),
}

# --- RV1909 versification -------------------------------------------------------------------------------------
# eBible's spaRV1909 keeps the Spanish (Hebrew-tradition) chapter divisions inside KJV-numbered slots: where a
# Spanish chapter starts earlier than the KJV one, every verse in it sits one or more numbers late, and the overflow
# at the chapter's end is MERGED into its last verse (the boundary is lost). These rules put each verse back under
# its KJV key. A merged verse goes under the first KJV verse it covers; the others are left missing and listed in
# the report as "included in". Checked verse by verse against KJV on 2026-11-18 (tools/bible_db_report.md).
# shift: (book, rv_chapter, rv_from, rv_to, kjv_chapter, kjv_from)    merge: (book, rv_chapter, rv_verse, [kjv (c, v)])
RV1909_SHIFTS = [
    ("NUM", 13, 1, 1, 12, 16), ("NUM", 13, 2, 32, 13, 1),
    ("NUM", 30, 1, 1, 29, 40), ("NUM", 30, 2, 15, 30, 1),
    ("1SA", 24, 1, 1, 23, 29), ("1SA", 24, 2, 21, 24, 1),
    ("2CH", 33, 11, 24, 33, 12),
    ("JOB", 39, 1, 3, 38, 39), ("JOB", 39, 4, 29, 39, 1), ("JOB", 40, 1, 19, 40, 6),
    ("HOS", 12, 1, 1, 11, 12), ("HOS", 12, 2, 13, 12, 1),
    ("JON", 2, 1, 1, 1, 17), ("JON", 2, 2, 9, 2, 1),
    ("2CO", 13, 13, 13, 13, 14),
]
RV1909_MERGES = [
    ("NUM", 13, 33, [(13, 32), (13, 33)]),
    ("NUM", 30, 16, [(30, 15), (30, 16)]),
    ("1SA", 24, 22, [(24, 21), (24, 22)]),
    ("2SA", 20, 25, [(20, 25), (20, 26)]),
    ("2CH", 33, 10, [(33, 10), (33, 11)]),
    ("JOB", 35, 15, [(35, 15), (35, 16)]),
    ("JOB", 39, 30, [(39, 27), (39, 28), (39, 29), (39, 30), (40, 1), (40, 2), (40, 3), (40, 4), (40, 5)]),
    ("HOS", 12, 14, [(12, 13), (12, 14)]),
    ("JON", 2, 10, [(2, 9), (2, 10)]),
    ("ACT", 19, 40, [(19, 40), (19, 41)]),
    ("2CO", 13, 12, [(13, 12), (13, 13)]),
]
# Psalm 119's Hebrew letter headings, inline at the start of verses 1, 9, ..., 169 in spaRV1909 (BSB marks them \qa).
RV1909_PS119_LETTERS = [
    "ALEPH", "BETH", "GIMEL", "DALETH", "HE", "VAU", "ZAIN", "CHETH", "TETH", "JOD", "CAPH",
    "LAMED", "MEM", "NUN", "SAMECH", "AIN", "PE", "TZADDI", "COPH", "RESH", "SIN", "TAU",
]
CAPS_WORD = re.compile(r"^[¡¿(«\"]*([A-ZÁÉÍÓÚÑÜ]+)[,.;:!?»\")]*$")


def rv1909_key_map() -> tuple[dict, dict]:
    """(book, rv_c, rv_v) -> (kjv_c, kjv_v), and (book, kjv_c, kjv_v) -> (rv key it was merged into). Validated."""
    moves, absorbed = {}, {}
    for book, rc, rf, rt, kc, kf in RV1909_SHIFTS:
        for i, rv in enumerate(range(rf, rt + 1)):
            moves[(book, rc, rv)] = (kc, kf + i)
    for book, rc, rv, targets in RV1909_MERGES:
        if (book, rc, rv) in moves:
            raise ValueError(f"RV1909 {book} {rc}:{rv} is both shifted and merged")
        moves[(book, rc, rv)] = targets[0]
        for kc, kv in targets[1:]:
            absorbed[(book, kc, kv)] = (book, targets[0][0], targets[0][1])
    targets_seen = [(k[0],) + v for k, v in moves.items()]
    if len(targets_seen) != len(set(targets_seen)):
        raise ValueError("RV1909 versification rules send two verses to one KJV key")
    return moves, absorbed


def split_title(verse_one: str) -> tuple[str | None, str]:
    """RV1909 Psalm 'title + verse 1': the title is everything before the first ALL-CAPS word (each chapter's first
    word is set in capitals; a one-letter capital counts only when the next word is capitals too, as in "A TI")."""
    tokens = verse_one.split(" ")
    for i, t in enumerate(tokens):
        m = CAPS_WORD.match(t)
        if m and (len(m.group(1)) >= 2 or (i + 1 < len(tokens) and CAPS_WORD.match(tokens[i + 1]))):
            return (" ".join(tokens[:i]) or None, " ".join(tokens[i:]))
    return None, verse_one

# Whole lines that are not Scripture text (titles, headings, intros, speaker labels).
DROP_LINE_MARKERS = {
    "id", "ide", "h", "toc1", "toc2", "toc3", "mt", "mt1", "mt2", "mt3", "ms", "ms1", "ms2",
    "s", "s1", "s2", "s3", "r", "sp", "cl", "rem", "ip", "is", "is1", "ili", "ili1", "imt",
    "imt1", "io1", "iot", "sr", "mr", "usfm", "sts",
    "qa",  # acrostic headings (Psalm 119 "Aleph", Lamentations) in BSB
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
                    title = strip_inline(rest).replace(WJ_OPEN, "").replace(WJ_CLOSE, "")
                    if title:  # BSB has a stray empty \d before Zechariah 12:1
                        book.superscriptions[chapter] = title
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


def sha256(path: Path) -> str:
    if path.is_dir():
        digest = hashlib.sha256()
        for p in sorted(path.rglob("*")):
            if p.is_file():
                digest.update(p.relative_to(path).as_posix().encode() + b"\0" + p.read_bytes())
        return digest.hexdigest()
    return hashlib.sha256(path.read_bytes()).hexdigest()


@dataclass
class Report:
    lines: list[str] = field(default_factory=list)

    def section(self, title: str) -> None:
        self.lines += ["", f"## {title}", ""]

    def add(self, line: str = "") -> None:
        self.lines.append(line)


def ref(code: str, c: int, v: int) -> str:
    return f"{code} {c}:{v}"


def build(sources: dict[str, Path], schema_dir: Path, out: Path, report_path: Path) -> None:
    db = load_schema(schema_dir)
    out.parent.mkdir(parents=True, exist_ok=True)
    tmp = out.with_suffix(".tmp")
    tmp.unlink(missing_ok=True)
    conn = sqlite3.connect(tmp)
    create_schema(conn, db)
    conn.execute(
        "CREATE TABLE translation_info (id TEXT NOT NULL PRIMARY KEY, attribution TEXT NOT NULL, "
        "has_red_letters INTEGER NOT NULL, description_en TEXT NOT NULL, description_es TEXT NOT NULL, "
        "source_url TEXT NOT NULL, source_sha256 TEXT NOT NULL, edition TEXT NOT NULL)"
    )

    report = Report(["# bible.db build report", "",
                     "Generated by tools/build_bible_db.py. Keys are KJV-style (book, chapter, verse). Do not edit."])
    report.section("Sources")
    report.add("| Translation | Source | SHA-256 |")
    report.add("|---|---|---|")

    parsed: dict[str, dict[str, ParsedBook]] = {}
    for key, path in sources.items():
        books = {b.code: b for b in (parse_usfm(c) for c in read_sources(path)) if b}
        missing = [c for c, _, _ in BOOKS if c not in books]
        if missing:
            sys.exit(f"{TRANSLATIONS[key].id}: missing books {missing} in {path}")
        parsed[key] = books
        report.add(f"| {TRANSLATIONS[key].id} | {TRANSLATIONS[key].source_url} | `{sha256(path)}` |")

    # KJV's verse keys and psalm titles are the reference for every other translation.
    def texts(books: dict[str, ParsedBook]) -> dict[tuple[str, int, int], tuple[str, str | None]]:
        result = {}
        for code, _, _ in BOOKS:
            for v in books[code].verses:
                k = (code, v.chapter, v.verse)
                if k in result:
                    sys.exit(f"{code} {v.chapter}:{v.verse} duplicated")
                result[k] = extract_jesus_ranges(strip_inline(" ".join(v.raw)))
        return result

    kjv_texts = texts(parsed["kjv"])
    kjv_keys = {k for k, (t, _) in kjv_texts.items() if t}
    kjv_titled = set(parsed["kjv"]["PSA"].superscriptions)

    max_chapters: dict[int, int] = {}
    for key, path in sources.items():
        tr = TRANSLATIONS[key]
        books = parsed[key]
        verses = texts(books)
        superscriptions = {(code, c): t for code in books for c, t in books[code].superscriptions.items()}
        notes: list[str] = []
        absorbed: dict = {}

        if key == "rv1909":
            moves, absorbed = rv1909_key_map()
            remapped = {}
            for (code, c, v), value in verses.items():
                if not value[0]:
                    continue  # the empty KJV-numbered slots left by the Spanish chapter divisions
                kc, kv = moves.get((code, c, v), (c, v))
                if (code, kc, kv) in remapped:
                    sys.exit(f"RV1909: two verses land on {ref(code, kc, kv)}")
                remapped[(code, kc, kv)] = value
            for book, rc, rf, rt, kc, kf in RV1909_SHIFTS:
                span = f"{rc}:{rf}" if rf == rt else f"{rc}:{rf}–{rt}"
                target = f"{kc}:{kf}" if rf == rt else f"{kc}:{kf}–{kf + rt - rf}"
                notes.append(f"moved: spaRV1909 {book} {span} -> {book} {target}")
            for book, rc, rv, targets in RV1909_MERGES:
                covered = ", ".join(f"{c}:{v}" for c, v in targets)
                notes.append(f"merged in the source: spaRV1909 {book} {rc}:{rv} holds KJV {book} {covered}; "
                             f"kept whole under {book} {targets[0][0]}:{targets[0][1]} (the verse boundaries aren't in the source)")
            for (code, kc, kv), into in sorted(absorbed.items(), key=lambda m: (BOOK_ID[m[0][0]], m[0][1], m[0][2])):
                notes.append(f"merged: {ref(code, kc, kv)} has no verse of its own; its text is included in {ref(*into)}")
            verses = remapped
            # Psalm titles: in verse 1 in spaRV1909; split into the superscription where KJV has one.
            for c in range(1, 151):
                text, jesus = verses[("PSA", c, 1)]
                title, rest = split_title(text)
                if c in kjv_titled:
                    if not title:
                        sys.exit(f"RV1909 Psalm {c}: KJV has a title but none was found in verse 1")
                    verses[("PSA", c, 1)] = (rest, None)
                    superscriptions[("PSA", c)] = title
                elif title:
                    notes.append(f"kept: Psalm {c}:1 starts \"{title}\" before the capitalised first word; "
                                 "KJV has no title there (\"Praise ye the LORD\" is verse text), so it stays in verse 1")
            # Psalm 119's Hebrew letter headings.
            for i, letter in enumerate(RV1909_PS119_LETTERS):
                k = ("PSA", 119, 1 + 8 * i)
                text, jesus = verses[k]
                first, _, rest = text.partition(" ")
                if first.rstrip(".") != letter:
                    sys.exit(f"RV1909 {ref(*k)}: expected the heading {letter}, found {first!r}")
                verses[k] = (rest, None)
            notes.append("removed: the 22 Hebrew letter headings at the start of Psalm 119:1, 9, ..., 169 "
                         "(" + ", ".join(RV1909_PS119_LETTERS) + ")")

        conn.execute(
            "INSERT INTO translations (id, name, language_code, licence, sort_order) VALUES (?,?,?,?,?)",
            (tr.id, tr.name, tr.language, tr.licence, tr.sort_order),
        )

        ordinal = 0
        verse_rows, chapter_rows = [], []
        jesus_count = 0
        skipped = []
        for code, _, _ in BOOKS:
            bid = BOOK_ID[code]
            per_chapter: dict[int, int] = {}
            for (c, v) in sorted((k[1], k[2]) for k in verses if k[0] == code):
                text, jesus = verses[(code, c, v)]
                if not text:
                    skipped.append(ref(code, c, v))
                    continue
                ordinal += 1
                jesus_count += jesus is not None
                verse_rows.append((tr.id, bid, c, v, ordinal, text, jesus))
                per_chapter[c] = per_chapter.get(c, 0) + 1
            chapters = sorted(per_chapter)
            if chapters != list(range(1, len(chapters) + 1)):
                sys.exit(f"{tr.id} {code}: non-contiguous chapters {chapters}")
            max_chapters[bid] = max(max_chapters.get(bid, 0), len(chapters))
            for ch in chapters:
                chapter_rows.append((tr.id, bid, ch, per_chapter[ch], superscriptions.get((code, ch))))

        conn.executemany(
            "INSERT INTO verses (translation_id, book_id, chapter, verse, ordinal, text, jesus_ranges) "
            "VALUES (?,?,?,?,?,?,?)", verse_rows)
        conn.executemany(
            "INSERT INTO chapters (translation_id, book_id, chapter, verse_count, superscription) "
            "VALUES (?,?,?,?,?)", chapter_rows)
        has_red = jesus_count > 0
        conn.execute(
            "INSERT INTO translation_info VALUES (?,?,?,?,?,?,?,?)",
            (tr.id, tr.attribution, int(has_red), tr.description_en, tr.description_es, tr.source_url, sha256(path), tr.edition),
        )

        have = {(BOOKS[r[1] - 1][0], r[2], r[3]) for r in verse_rows}
        missing = sorted(kjv_keys - have, key=lambda k: (BOOK_ID[k[0]], k[1], k[2]))
        extra = sorted(have - kjv_keys, key=lambda k: (BOOK_ID[k[0]], k[1], k[2]))
        titles = sum(1 for r in chapter_rows if r[1] == 19 and r[4])
        report.section(f"{tr.id}: {tr.name}")
        report.add(f"- Verses: {ordinal} (KJV {len(kjv_keys)}); chapters: {len(chapter_rows)}; psalm titles: {titles}")
        report.add(f"- Words of Jesus marked (`\\wj`): {'yes' if has_red else 'no'}, in {jesus_count} verses -> has_red_letters = {int(has_red)}")
        report.add(f"- Missing vs KJV ({len(missing)}): " + (", ".join(
            ref(*k) + (f" (in {ref(*absorbed[k])})" if k in absorbed else "") for k in missing) or "none"))
        report.add(f"- Extra vs KJV ({len(extra)}): " + (", ".join(ref(*k) for k in extra) or "none"))
        if skipped:
            report.add(f"- Numbered in the source but with no text, skipped ({len(skipped)}): {', '.join(skipped)}")
        for n in notes:
            report.add(f"- {n}")
        print(f"{tr.id}: {ordinal} verses, {len(chapter_rows)} chapters, {jesus_count} verses with words of Jesus, "
              f"{len(missing)} missing / {len(extra)} extra vs KJV")

    conn.executemany(
        "INSERT INTO books (id, code, name, testament, chapter_count) VALUES (?,?,?,?,?)",
        [(BOOK_ID[c], c, n, t, max_chapters[BOOK_ID[c]]) for c, n, t in BOOKS],
    )
    conn.commit()
    conn.execute("VACUUM")
    conn.close()
    tmp.replace(out)
    size = out.stat().st_size
    report.section("Output")
    report.add(f"- bible.db: {size:,} bytes ({size / 1_048_576:.1f} MiB), Room schema v{db['version']}, "
               f"SHA-256 `{hashlib.sha256(out.read_bytes()).hexdigest()}`")
    report_path.write_text("\n".join(report.lines) + "\n")
    print(f"Wrote {out} ({size / 1_048_576:.1f} MiB, schema v{db['version']}) and {report_path}")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--kjv", type=Path, required=True, help="eng-kjv2006_usfm.zip or its unzipped dir")
    ap.add_argument("--web", type=Path, required=True, help="engwebp_usfm.zip or its unzipped dir")
    ap.add_argument("--bsb", type=Path, required=True, help="engbsb_usfm.zip or its unzipped dir")
    ap.add_argument("--rv1909", type=Path, required=True, help="spaRV1909_usfm.zip or its unzipped dir")
    ap.add_argument("--schema-dir", type=Path, default=DEFAULT_SCHEMA_DIR)
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    ap.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    args = ap.parse_args()
    # Insert order = sort order; KJV is parsed first internally as the versification reference.
    build({"web": args.web, "kjv": args.kjv, "bsb": args.bsb, "rv1909": args.rv1909}, args.schema_dir, args.out, args.report)


if __name__ == "__main__":
    main()
