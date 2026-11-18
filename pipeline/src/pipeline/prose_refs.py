"""
Find Bible references in English prose and check they exist in the KJV verse list.

Handles: "John 3:16", "Jn 3:16-18", "1 Cor. 13:4–7", "Psalm 23" (chapter), "Romans 5:8; 8:32" (list
continuing the same book), "Gen 1:1-2:3" (cross-chapter), "v. 17" / "verses 15–17" (relative to the
study's own chapter), and "In 1:12" / "See 2:5" (a sentence-opening word + chapter:verse = the study's own
book). A capitalised word that isn't a book name, directly followed by chapter:verse
(e.g. "Hezekiah 3:5"), is reported as an unrecognised reference.
"""
from __future__ import annotations

import re
from dataclasses import dataclass

from .books import BOOKS

# English names and common abbreviations per USFM code (matched case-sensitively after a word boundary,
# with an optional trailing full stop). Numbered books also accept "1st"/"First"/"I" forms.
_ALIASES: dict[str, list[str]] = {
    "GEN": ["Genesis", "Gen", "Ge", "Gn"], "EXO": ["Exodus", "Exod", "Exo", "Ex"],
    "LEV": ["Leviticus", "Lev", "Le", "Lv"], "NUM": ["Numbers", "Num", "Nu", "Nm"],
    "DEU": ["Deuteronomy", "Deut", "Deu", "Dt"], "JOS": ["Joshua", "Josh", "Jos"],
    "JDG": ["Judges", "Judg", "Jdg", "Jdgs"], "RUT": ["Ruth", "Rut", "Ru"],
    "1SA": ["Samuel", "Sam", "Sa"], "2SA": ["Samuel", "Sam", "Sa"],
    "1KI": ["Kings", "Kgs", "Ki"], "2KI": ["Kings", "Kgs", "Ki"],
    "1CH": ["Chronicles", "Chron", "Chr", "Ch"], "2CH": ["Chronicles", "Chron", "Chr", "Ch"],
    "EZR": ["Ezra", "Ezr"], "NEH": ["Nehemiah", "Neh", "Ne"], "EST": ["Esther", "Esth", "Est"],
    "JOB": ["Job", "Jb"], "PSA": ["Psalms", "Psalm", "Pss", "Psa", "Ps"],
    "PRO": ["Proverbs", "Prov", "Pro", "Prv", "Pr"], "ECC": ["Ecclesiastes", "Eccl", "Eccles", "Ecc", "Qoh"],
    "SNG": ["Song of Songs", "Song of Solomon", "Song", "Songs", "SoS", "Cant"],
    "ISA": ["Isaiah", "Isa", "Is"], "JER": ["Jeremiah", "Jer", "Je"], "LAM": ["Lamentations", "Lam", "La"],
    "EZK": ["Ezekiel", "Ezek", "Eze", "Ezk"], "DAN": ["Daniel", "Dan", "Da", "Dn"],
    "HOS": ["Hosea", "Hos", "Ho"], "JOL": ["Joel", "Jl"], "AMO": ["Amos", "Am"],
    "OBA": ["Obadiah", "Obad", "Ob"], "JON": ["Jonah", "Jon"], "MIC": ["Micah", "Mic", "Mc"],
    "NAM": ["Nahum", "Nah", "Na"], "HAB": ["Habakkuk", "Hab"], "ZEP": ["Zephaniah", "Zeph", "Zep"],
    "HAG": ["Haggai", "Hag"], "ZEC": ["Zechariah", "Zech", "Zec"], "MAL": ["Malachi", "Mal"],
    "MAT": ["Matthew", "Matt", "Mat", "Mt"], "MRK": ["Mark", "Mrk", "Mk", "Mr"],
    "LUK": ["Luke", "Luk", "Lk"], "JHN": ["John", "Jhn", "Jn"], "ACT": ["Acts", "Act", "Ac"],
    "ROM": ["Romans", "Rom", "Ro", "Rm"],
    "1CO": ["Corinthians", "Cor", "Co"], "2CO": ["Corinthians", "Cor", "Co"],
    "GAL": ["Galatians", "Gal", "Ga"], "EPH": ["Ephesians", "Eph"], "PHP": ["Philippians", "Phil", "Php"],
    "COL": ["Colossians", "Col"],
    "1TH": ["Thessalonians", "Thess", "Thes", "Th"], "2TH": ["Thessalonians", "Thess", "Thes", "Th"],
    "1TI": ["Timothy", "Tim", "Ti"], "2TI": ["Timothy", "Tim", "Ti"], "TIT": ["Titus", "Tit"],
    "PHM": ["Philemon", "Philem", "Phm", "Phlm"], "HEB": ["Hebrews", "Heb"], "JAS": ["James", "Jas", "Jm"],
    "1PE": ["Peter", "Pet", "Pe", "Pt"], "2PE": ["Peter", "Pet", "Pe", "Pt"],
    "1JN": ["John", "Jn", "Jo", "Jhn"], "2JN": ["John", "Jn", "Jo", "Jhn"], "3JN": ["John", "Jn", "Jo", "Jhn"],
    "JUD": ["Jude", "Jud"], "REV": ["Revelation", "Rev", "Re", "Rv", "Apocalypse"],
}
_ORDINALS = {"1": ["1", "1st", "First", "I"], "2": ["2", "2nd", "Second", "II"], "3": ["3", "3rd", "Third", "III"]}

BOOK_BY_ALIAS: dict[str, int] = {}
for _book in BOOKS:
    for _alias in _ALIASES[_book.usfm]:
        if _book.usfm[0] in "123":
            for _ordinal in _ORDINALS[_book.usfm[0]]:
                BOOK_BY_ALIAS[f"{_ordinal} {_alias}"] = _book.id
                if _ordinal.isdigit():
                    BOOK_BY_ALIAS[f"{_ordinal}{_alias}"] = _book.id
        else:
            BOOK_BY_ALIAS[_alias] = _book.id
BOOK_BY_ALIAS["Revelations"] = 66  # common slip; resolve rather than flag
_NAMES = "|".join(re.escape(a) for a in sorted(BOOK_BY_ALIAS, key=len, reverse=True))

_DASH = r"\s*[-–—]\s*"
# Book + chapter[:verse[-[chapter:]verse]], then optional ", v" / "; c:v" continuations.
_REF = re.compile(
    rf"(?<![\w])(?P<book>{_NAMES})\.?\s+(?P<body>\d+(?::\d+)?(?:{_DASH}\d+(?::\d+)?)?"
    rf"(?:\s*[,;]\s*(?!(?:{_NAMES})\b)\d+(?::\d+)?(?:{_DASH}\d+(?::\d+)?)?)*)"
)  # a continuation number that starts the next book ("Romans 5:8, 1 John 4:9") isn't part of this reference
_UNKNOWN = re.compile(r"(?<![\w.])(?P<name>(?:[1-3]\s?)?[A-Z][a-z]+)\.?\s+(?P<cv>\d+:\d+)")
# Sentence-opening words that precede a same-book "chapter:verse" ("In 1:12", "Compare 2:5").
_SAME_BOOK_LEADS = frozenset("""
    In See Compare Cf At From By And Also As Like Unlike Then Later Earlier Before After With Since Until
    Through Throughout Back Here There Elsewhere Chapter Verse Verses
""".split())
_RELATIVE = re.compile(rf"\b(?:verses?|vv?\.)\s*(?P<a>\d+)(?:{_DASH}(?P<b>\d+))?")
_ITEM = re.compile(rf"^(\d+)(?::(\d+))?(?:{_DASH}(\d+)(?::(\d+))?)?$")


@dataclass(frozen=True)
class Found:
    text: str
    book_id: int | None
    start: tuple[int, int | None] | None  # (chapter, verse or None for a whole chapter)
    end: tuple[int, int | None] | None
    problem: str | None = None


def find_references(prose: str, here: tuple[int, int, int]) -> list[Found]:
    """Every reference in [prose]. here = the study's own verse, for "v. 17"-style references."""
    found: list[Found] = []
    covered: list[tuple[int, int]] = []
    for m in _REF.finditer(prose):
        book = BOOK_BY_ALIAS[m["book"]]
        chapter: int | None = None
        for index, raw in enumerate(re.split(r"\s*[,;]\s*", m["body"])):
            item = _ITEM.match(raw.strip())
            if not item:
                found.append(Found(f"{m['book']} {raw}", book, None, None, "unparseable reference"))
                continue
            a, b, c, d = (int(g) if g else None for g in item.groups())
            if b is not None:
                # chapter:verse, optionally to verse or to chapter:verse
                start = (a, b)
                end = (c, d) if d is not None else ((a, c) if c is not None else start)
            elif index == 0 or chapter is None:
                # first item without a colon: whole chapter ("Psalm 23") or chapters ("Psalms 23-24")
                start, end = (a, None), (c if c is not None else a, None)
            else:
                # later bare numbers are verses in the current chapter ("Rom 5:8, 10-12")
                start, end = (chapter, a), (chapter, c if c is not None else a)
            chapter = end[0] if end[1] is not None else None
            found.append(Found(f"{m['book']} {raw}", book, start, end))
        covered.append(m.span())

    for m in _UNKNOWN.finditer(prose):
        if any(s <= m.start() < e for s, e in covered):
            continue
        if m["name"] in _SAME_BOOK_LEADS:
            c, v = (int(x) for x in m["cv"].split(":"))
            found.append(Found(m.group(0), here[0], (c, v), (c, v)))
        elif m["name"] not in BOOK_BY_ALIAS:
            found.append(Found(m.group(0), None, None, None, f"unrecognised book name {m['name']!r}"))

    for m in _RELATIVE.finditer(prose):
        a = int(m["a"])
        b = int(m["b"]) if m["b"] else a
        found.append(Found(m.group(0), here[0], (here[1], a), (here[1], b)))
    return found


def check_reference(ref: Found, verses: frozenset, chapters: frozenset) -> str | None:
    """None if the reference exists in KJV, else what's wrong."""
    if ref.problem:
        return ref.problem
    book = ref.book_id
    for point in (ref.start, ref.end):
        chapter, verse = point
        if verse is None:
            if (book, chapter) not in chapters:
                return f"{BOOKS[book - 1].name} has no chapter {chapter}"
        elif (book, chapter, verse) not in verses:
            return f"{BOOKS[book - 1].name} {chapter}:{verse} does not exist"
    if (ref.start[0], ref.start[1] or 0) > (ref.end[0], ref.end[1] or 0):
        return "range runs backwards"
    return None
