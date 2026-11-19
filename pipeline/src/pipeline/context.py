"""
What a study request shows around its verse, shared by the request builder (generate.py) and the
reference check (checks.py), so the model may cite exactly the verses it was shown.

- Context: CONTEXT_VERSES verses either side, within the book (crossing chapter boundaries). Psalms stay
  inside their psalm: the whole psalm if it has at most WHOLE_PSALM_MAX verses, otherwise the same window.
- Book background and notes on the text come from prompts/books.json, written and reviewed by a person.
- Transliterations are cleaned for the prompt: STEPBible's syllable dots and stress capitals removed.
"""
from __future__ import annotations

import json
import re
from functools import lru_cache
from pathlib import Path

from .paths import PIPELINE
from .store import parse_id

CONTEXT_VERSES = 4
WHOLE_PSALM_MAX = 30
PSALMS = 19
BOOKS_FILE = PIPELINE / "prompts" / "books.json"

Key = tuple[int, int, int]


def context_keys(key: Key, verses) -> list[Key]:
    """The verse and its context, in order. verses: any iterable of KJV keys (needs at least this book)."""
    book, chapter, _ = key
    if book == PSALMS:
        pool = sorted(k for k in verses if k[0] == book and k[1] == chapter and k[2] > 0)
        if len(pool) <= WHOLE_PSALM_MAX:
            return pool if key in pool else sorted({*pool, key})
    else:
        pool = sorted(k for k in verses if k[0] == book and k[2] > 0)
    if key not in pool:
        raise LookupError(f"{key} isn't in the verse list")
    i = pool.index(key)
    return pool[max(0, i - CONTEXT_VERSES): i + CONTEXT_VERSES + 1]


def whole_psalm(key: Key, verses) -> bool:
    return key[0] == PSALMS and len({k for k in verses if k[0] == PSALMS and k[1] == key[1] and k[2] > 0}) \
        <= WHOLE_PSALM_MAX


# ---- Transliteration ------------------------------------------------------------------------------------

def clean_translit(text: str, greek: bool = False) -> str:
    """'be.re.Shit' -> 'bereshit', "ro.'I" -> "ro'i", 'Yah.weh' -> 'Yahweh': no dots, stress capitals lowered.
    A capital at the start of a word stays (names). A trailing hyphen (a Hebrew maqqef: 'hodu-') is dropped.
    greek: also drop apostrophes ("he'ōs" -> 'heōs'); TAGNT puts one after a rough breathing's h, whereas in
    Hebrew they stand for aleph and ayin and stay."""
    out = []
    for word in re.split(r"(\s+)", text):
        bare = word.replace(".", "").rstrip("-")
        if greek:
            bare = re.sub(r"['’ʼ]", "", bare)
        out.append(bare[:1] + bare[1:].lower() if bare else bare)
    return "".join(out)


# ---- Book background and notes on the text ------------------------------------------------------------

@lru_cache(maxsize=4)
def _books(path: Path) -> dict:
    data = json.loads(path.read_text())
    for note in data.get("textNotes", []):
        lo, hi = parse_id(note["from"]), parse_id(note["to"])
        if lo[0] != hi[0] or lo > hi:
            raise ValueError(f"{path.name}: bad textNotes range {note['from']}-{note['to']}")
    return data


def book_background(usfm: str, path: Path = BOOKS_FILE) -> str | None:
    entry = _books(path)["books"].get(usfm)
    if not entry:
        return None
    lines = [f"Author: {entry['author']}", f"Date: {entry['date']}", f"Audience: {entry['audience']}",
             f"Setting: {entry['setting']}"]
    lines += [f"Disputed: {d}" for d in entry.get("disputes", [])]
    return "\n".join(lines)


def text_note(key: Key, path: Path = BOOKS_FILE) -> str | None:
    for note in _books(path).get("textNotes", []):
        if parse_id(note["from"]) <= key <= parse_id(note["to"]):
            return note["note"]
    return None
