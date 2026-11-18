"""The 66 books with each source's code. id = the app's book id (canonical Protestant order, 1-66)."""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Book:
    id: int
    usfm: str
    step: str
    osis: str
    name: str
    """TSK (CrossWire ThML) abbreviations, e.g. "Ge", "Joh"."""
    tsk: tuple[str, ...]


_ROWS = [
    # usfm, step, osis, name, tsk abbreviations
    ("GEN", "Gen", "Gen", "Genesis", ("Ge",)),
    ("EXO", "Exo", "Exod", "Exodus", ("Ex",)),
    ("LEV", "Lev", "Lev", "Leviticus", ("Le",)),
    ("NUM", "Num", "Num", "Numbers", ("Nu",)),
    ("DEU", "Deu", "Deut", "Deuteronomy", ("De",)),
    ("JOS", "Jos", "Josh", "Joshua", ("Jos",)),
    ("JDG", "Jdg", "Judg", "Judges", ("Jud", "Jdg")),
    ("RUT", "Rut", "Ruth", "Ruth", ("Ru",)),
    ("1SA", "1Sa", "1Sam", "1 Samuel", ("1Sa",)),
    ("2SA", "2Sa", "2Sam", "2 Samuel", ("2Sa",)),
    ("1KI", "1Ki", "1Kgs", "1 Kings", ("1Ki",)),
    ("2KI", "2Ki", "2Kgs", "2 Kings", ("2Ki",)),
    ("1CH", "1Ch", "1Chr", "1 Chronicles", ("1Ch",)),
    ("2CH", "2Ch", "2Chr", "2 Chronicles", ("2Ch",)),
    ("EZR", "Ezr", "Ezra", "Ezra", ("Ezr",)),
    ("NEH", "Neh", "Neh", "Nehemiah", ("Ne",)),
    ("EST", "Est", "Esth", "Esther", ("Es",)),
    ("JOB", "Job", "Job", "Job", ("Job",)),
    ("PSA", "Psa", "Ps", "Psalms", ("Ps",)),
    ("PRO", "Pro", "Prov", "Proverbs", ("Pr",)),
    ("ECC", "Ecc", "Eccl", "Ecclesiastes", ("Ec",)),
    ("SNG", "Sng", "Song", "Song of Solomon", ("So",)),
    ("ISA", "Isa", "Isa", "Isaiah", ("Isa",)),
    ("JER", "Jer", "Jer", "Jeremiah", ("Jer",)),
    ("LAM", "Lam", "Lam", "Lamentations", ("La",)),
    ("EZK", "Ezk", "Ezek", "Ezekiel", ("Eze",)),
    ("DAN", "Dan", "Dan", "Daniel", ("Da",)),
    ("HOS", "Hos", "Hos", "Hosea", ("Ho",)),
    ("JOL", "Jol", "Joel", "Joel", ("Joe",)),
    ("AMO", "Amo", "Amos", "Amos", ("Am",)),
    ("OBA", "Oba", "Obad", "Obadiah", ("Ob",)),
    ("JON", "Jon", "Jonah", "Jonah", ("Jon",)),
    ("MIC", "Mic", "Mic", "Micah", ("Mic",)),
    ("NAM", "Nam", "Nah", "Nahum", ("Na",)),
    ("HAB", "Hab", "Hab", "Habakkuk", ("Hab",)),
    ("ZEP", "Zep", "Zeph", "Zephaniah", ("Zep",)),
    ("HAG", "Hag", "Hag", "Haggai", ("Hag",)),
    ("ZEC", "Zec", "Zech", "Zechariah", ("Zec",)),
    ("MAL", "Mal", "Mal", "Malachi", ("Mal",)),
    ("MAT", "Mat", "Matt", "Matthew", ("Mt",)),
    ("MRK", "Mrk", "Mark", "Mark", ("Mr",)),
    ("LUK", "Luk", "Luke", "Luke", ("Lu",)),
    ("JHN", "Jhn", "John", "John", ("Joh",)),
    ("ACT", "Act", "Acts", "Acts", ("Ac",)),
    ("ROM", "Rom", "Rom", "Romans", ("Ro",)),
    ("1CO", "1Co", "1Cor", "1 Corinthians", ("1Co",)),
    ("2CO", "2Co", "2Cor", "2 Corinthians", ("2Co",)),
    ("GAL", "Gal", "Gal", "Galatians", ("Ga",)),
    ("EPH", "Eph", "Eph", "Ephesians", ("Eph",)),
    ("PHP", "Php", "Phil", "Philippians", ("Php",)),
    ("COL", "Col", "Col", "Colossians", ("Col",)),
    ("1TH", "1Th", "1Thess", "1 Thessalonians", ("1Th",)),
    ("2TH", "2Th", "2Thess", "2 Thessalonians", ("2Th",)),
    ("1TI", "1Ti", "1Tim", "1 Timothy", ("1Ti",)),
    ("2TI", "2Ti", "2Tim", "2 Timothy", ("2Ti",)),
    ("TIT", "Tit", "Titus", "Titus", ("Tit",)),
    ("PHM", "Phm", "Phlm", "Philemon", ("Phm",)),
    ("HEB", "Heb", "Heb", "Hebrews", ("Heb",)),
    ("JAS", "Jas", "Jas", "James", ("Jas",)),
    ("1PE", "1Pe", "1Pet", "1 Peter", ("1Pe",)),
    ("2PE", "2Pe", "2Pet", "2 Peter", ("2Pe",)),
    ("1JN", "1Jn", "1John", "1 John", ("1Jo",)),
    ("2JN", "2Jn", "2John", "2 John", ("2Jo",)),
    ("3JN", "3Jn", "3John", "3 John", ("3Jo",)),
    ("JUD", "Jud", "Jude", "Jude", ("Jude",)),
    ("REV", "Rev", "Rev", "Revelation", ("Re",)),
]

BOOKS: list[Book] = [Book(i + 1, *row) for i, row in enumerate(_ROWS)]
BY_STEP = {b.step: b for b in BOOKS}
BY_OSIS = {b.osis: b for b in BOOKS}
BY_USFM = {b.usfm: b for b in BOOKS}
BY_TSK = {abbr: b for b in BOOKS for abbr in b.tsk}
OT = [b for b in BOOKS if b.id <= 39]
NT = [b for b in BOOKS if b.id >= 40]
