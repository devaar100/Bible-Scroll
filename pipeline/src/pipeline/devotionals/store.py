"""Devotional files (out/devotionals/devotional-MM-DD.json) and the passage lookups drafting and checks share."""
from __future__ import annotations

import json
import os
import re
import sqlite3
import tempfile
from functools import lru_cache
from pathlib import Path

from jsonschema import Draft202012Validator

from ..books import BY_USFM
from ..paths import OUT, PIPELINE
from .calendar import Anchor

DEVOTIONALS_DIR = OUT / "devotionals"
SCHEMA_PATH = PIPELINE / "schema" / "devotional.json"
DEVOTIONAL_ID = re.compile(r"^devotional-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$")
SCHEMA_VERSION = 1


def validator() -> Draft202012Validator:
    schema = json.loads(SCHEMA_PATH.read_text())
    Draft202012Validator.check_schema(schema)
    return Draft202012Validator(schema)


class DevotionalStore:
    """One JSON file per day. Writes are atomic (temp file + rename); ids double as path guards."""

    def __init__(self, directory: Path = DEVOTIONALS_DIR):
        self.directory = directory

    def path(self, devotional_id: str) -> Path:
        if not DEVOTIONAL_ID.match(devotional_id):
            raise ValueError(f"Not a devotional id: {devotional_id!r}")
        return self.directory / f"{devotional_id}.json"

    def exists(self, devotional_id: str) -> bool:
        return self.path(devotional_id).exists()

    def load(self, devotional_id: str) -> dict:
        return json.loads(self.path(devotional_id).read_text())

    def save(self, devotional: dict) -> None:
        target = self.path(devotional["id"])
        self.directory.mkdir(parents=True, exist_ok=True)
        fd, tmp = tempfile.mkstemp(dir=self.directory, prefix=".tmp-", suffix=".json")
        with os.fdopen(fd, "w") as f:
            f.write(json.dumps(devotional, ensure_ascii=False, indent=2) + "\n")
        os.replace(tmp, target)

    def ids(self) -> list[str]:
        """Every devotional id in date order."""
        if not self.directory.exists():
            return []
        return sorted(p.stem for p in self.directory.glob("devotional-*.json") if DEVOTIONAL_ID.match(p.stem))


# ---- Passages ---------------------------------------------------------------------------------------

def to_passage(anchor: Anchor) -> dict:
    return {"book": anchor.book, "startChapter": anchor.start[0], "startVerse": anchor.start[1],
            "endChapter": anchor.end[0], "endVerse": anchor.end[1]}


def passage_label(p: dict) -> str:
    """{'book': 'JHN', 3, 1, 3, 21} -> 'John 3:1-21' (single verse: 'John 3:16')."""
    name = BY_USFM[p["book"]].name if p.get("book") in BY_USFM else p.get("book")
    if (p["startChapter"], p["startVerse"]) == (p["endChapter"], p["endVerse"]):
        return f"{name} {p['startChapter']}:{p['startVerse']}"
    if p["startChapter"] == p["endChapter"]:
        return f"{name} {p['startChapter']}:{p['startVerse']}-{p['endVerse']}"
    return f"{name} {p['startChapter']}:{p['startVerse']}-{p['endChapter']}:{p['endVerse']}"


def _range(p: dict) -> tuple:
    return BY_USFM[p["book"]].id, p["startChapter"], p["startVerse"], p["endChapter"], p["endVerse"]


def passage_text(p: dict, bible_db: Path, translation: str = "WEB") -> str:
    """The passage as numbered verses ('[1] In the beginning ...'). Raises if any verse is missing."""
    b, c1, v1, c2, v2 = _range(p)
    with sqlite3.connect(f"file:{bible_db}?mode=ro", uri=True) as db:
        rows = db.execute(
            "SELECT chapter, verse, text FROM verses WHERE translation_id = ? AND book_id = ? "
            "AND (chapter, verse) >= (?, ?) AND (chapter, verse) <= (?, ?) ORDER BY chapter, verse",
            (translation, b, c1, v1, c2, v2)).fetchall()
    if not rows:
        raise LookupError(f"No {translation} text for {passage_label(p)} in {bible_db}")
    multi = c1 != c2
    return " ".join(f"[{f'{c}:' if multi else ''}{v}] {t}" for c, v, t in rows)


@lru_cache(maxsize=512)
def _words(source_db: Path, key: tuple) -> tuple[tuple, ...]:
    b, c1, v1, c2, v2 = key
    with sqlite3.connect(f"file:{source_db}?mode=ro", uri=True) as db:
        return tuple(db.execute(
            "SELECT surface, translit, strongs, lemma, gloss FROM word WHERE book_id = ? AND chapter IS NOT NULL "
            "AND (chapter, verse) >= (?, ?) AND (chapter, verse) <= (?, ?) ORDER BY chapter, verse, word_no",
            key).fetchall())


#: Words this common (articles, conjunctions, "and", "the", "to") say nothing on their own.
COMMON_WORD_COUNT = 1500


@lru_cache(maxsize=4)
def _common(source_db: Path) -> frozenset[str]:
    with sqlite3.connect(f"file:{source_db}?mode=ro", uri=True) as db:
        return frozenset(s for (s,) in db.execute(
            "SELECT strongs FROM word WHERE strongs IS NOT NULL GROUP BY strongs HAVING count(*) > ?",
            (COMMON_WORD_COUNT,)))


_EDGE = re.compile(r"^[^\w]+|[^\w\u0591-\u05C7]+$")


def _clean(text: str | None) -> str:
    """Drop punctuation, sof pasuq and paragraph markers (' ס', ' פ') around a word."""
    text = re.sub(r"[׃׀¶]|\s+[ספ]$", "", (text or "").strip())
    return _EDGE.sub("", text.strip())


def _clean_gloss(text: str | None) -> str:
    """'[their] way' / '<the> those' -> 'their way' / 'the those'."""
    return _clean(re.sub(r"[\[\]<>]", "", text or ""))


def passage_words(p: dict, source_db: Path, *, include_common: bool = False) -> list[dict]:
    """
    Hebrew/Greek words in the passage (from source.db), one per Strong's number, in passage order.
    Very common words are left out unless include_common (the checker includes them, so a mention passes).
    """
    common = frozenset() if include_common else _common(source_db)
    seen, out = set(), []
    for surface, translit, strongs, lemma, gloss in _words(source_db, _range(p)):
        if not strongs or strongs in seen or strongs in common:
            continue
        seen.add(strongs)
        out.append({"surface": _clean(surface), "transliteration": _clean(translit), "strongs": strongs,
                    "lemma": lemma or "", "gloss": _clean_gloss(gloss)})
    return out


# ---- Related passages (the only other passages a devotional may cite) ----------------------------------

STUDIES_DIR = OUT / "studies"
MAX_RELATED = 6


def passage_keys(p: dict, bible_db: Path) -> list[tuple[int, int, int]]:
    """Every verse of the passage that exists in the app's KJV, in order."""
    book = BY_USFM[p["book"]].id
    start, end = (book, p["startChapter"], p["startVerse"]), (book, p["endChapter"], p["endVerse"])
    with sqlite3.connect(bible_db) as db:
        rows = db.execute(
            "SELECT book_id, chapter, verse FROM verses WHERE translation_id = 'KJV' AND book_id = ? "
            "AND chapter BETWEEN ? AND ? ORDER BY chapter, verse", (book, start[1], end[1])).fetchall()
    return [r for r in rows if start <= r <= end]


def require_studies(studies_dir: Path | None = None) -> None:
    """Drafting needs the verse studies' data sections for related passages: fail loudly before spending."""
    directory = studies_dir or STUDIES_DIR
    if not directory.exists() or not any(directory.glob("*.json")):
        raise FileNotFoundError(f"{directory} has no studies: run `python -m pipeline studies` before drafting devotionals")


def related_passages(p: dict, bible_db: Path, studies_dir: Path | None = None) -> list[str]:
    """
    Up to MAX_RELATED cross references for the passage, from the verse studies' data sections (OpenBible by votes,
    then TSK; built from source.db, never by AI). Taken round-robin across the passage's verses so one verse doesn't
    crowd out the rest; references inside the passage itself are skipped. Format: BOOK.C.V or BOOK.C.V-BOOK.C.V.
    """
    from .calendar import parse_anchor
    studies_dir = studies_dir or STUDIES_DIR
    keys = passage_keys(p, bible_db)
    if not keys:
        return []
    usfm = {b.id: code for code, b in BY_USFM.items()}
    lists = []
    for key in keys:
        path = studies_dir / f"{usfm[key[0]]}.{key[1]}.{key[2]}.json"
        if not path.exists():
            continue  # no data for this verse (require_studies guards real drafting runs)
        lists.append([x["ref"] for x in json.loads(path.read_text())["crossReferences"]])
    first, last = keys[0], keys[-1]
    chosen: list[str] = []
    for i in range(max((len(x) for x in lists), default=0)):
        for refs in lists:
            if i >= len(refs) or refs[i] in chosen or len(chosen) == MAX_RELATED:
                continue
            a = parse_anchor(refs[i] if "-" in refs[i] else f"{refs[i]}-{refs[i]}")
            book = BY_USFM[a.book].id
            if first <= (book, *a.start) <= last:
                continue
            chosen.append(refs[i])
    return chosen
