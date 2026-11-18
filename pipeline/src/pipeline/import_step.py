"""STEPBible TAHOT/TAGNT tagged words and TBESH/TBESG brief lexicons."""
from __future__ import annotations

import re
import sqlite3
from collections.abc import Iterator
from dataclasses import dataclass
from pathlib import Path

from .refs import StepRef, parse_step_ref

_WORD_ROW = re.compile(r"^(?:[1-3][A-Z][a-z]|[A-Z][a-z]{2})\.\d+\.\d+\S*#\d+=")


@dataclass(frozen=True)
class Word:
    ref: StepRef
    native_ref: str
    surface: str
    translit: str | None
    gloss: str | None
    strongs: str | None
    strongs_all: str | None
    lemma: str | None
    morph: str | None
    editions: str | None


_LOOKS_LIKE_WORD = re.compile(r"^[^\s#]+#\d+=")


def _rows(path: Path) -> Iterator[list[str]]:
    with open(path, encoding="utf-8-sig") as f:
        for number, line in enumerate(f, start=1):
            if _WORD_ROW.match(line):
                yield line.rstrip("\n").split("\t")
            elif _LOOKS_LIKE_WORD.match(line):
                # Never skip a word silently (a too-strict pattern once dropped every numbered book).
                raise ValueError(f"{path.name}:{number}: word row not recognised: {line[:60]!r}")


def _clean(text: str) -> str:
    """Drop STEP's affix '/' and punctuation '\\' separators from Hebrew/Greek and glosses."""
    return re.sub(r"\s+", " ", text.replace("/", "").replace("\\", "")).strip()


_ROOT_TAG = re.compile(r"\{(?P<strong>[HG]\d+[A-Za-z]?)=(?P<lemma>[^=}]*)=(?P<gloss>[^}]*)\}")


def iter_tahot(path: Path) -> Iterator[Word]:
    # Columns: Ref | Hebrew | Transliteration | Translation | dStrongs | Grammar | Meaning variants |
    #          Spelling variants | Root dStrong+Instance | Alt Strongs | Conjoin | Expanded Strong tags
    for cols in _rows(path):
        cols += [""] * (12 - len(cols))
        ref = parse_step_ref(cols[0], "TAHOT")
        root = _ROOT_TAG.search(cols[11])
        strongs_all = " ".join(re.findall(r"[HG]\d+[A-Za-z]?", cols[4])) or None
        root_in_dstrongs = re.search(r"\{([HG]\d+[A-Za-z]?)\}", cols[4])
        yield Word(
            ref=ref,
            native_ref=cols[0].split("#")[0],
            surface=_clean(cols[1]),
            translit=_clean(cols[2]) or None,
            gloss=_clean(cols[3]) or None,
            strongs=root["strong"] if root else (root_in_dstrongs.group(1) if root_in_dstrongs else None),
            strongs_all=strongs_all,
            lemma=root["lemma"].strip() if root else None,
            morph=cols[5] or None,
            editions=None,
        )


_GREEK = re.compile(r"^(?P<greek>.*?)\s*\((?P<translit>[^)]*)\)\s*$")


def iter_tagnt(path: Path) -> Iterator[Word]:
    # Columns: Ref | Greek (translit) | English | dStrongs=Grammar | Dictionary form=Gloss | editions |
    #          Meaning variants | Spelling variants | Spanish | Sub-meaning | Conjoin | sStrong+Instance | Alt Strongs
    for cols in _rows(path):
        cols += [""] * (13 - len(cols))
        ref = parse_step_ref(cols[0], "TAGNT")
        greek = _GREEK.match(cols[1])
        strong, _, morph = cols[3].partition("=")
        lemma = cols[4].split("=", 1)[0].strip()
        yield Word(
            ref=ref,
            native_ref=cols[0].split("#")[0],
            surface=(greek["greek"] if greek else cols[1]).strip(),
            translit=greek["translit"].strip() if greek else None,
            gloss=cols[2].strip() or None,
            strongs=strong.strip() or None,
            strongs_all=strong.strip() or None,
            lemma=lemma or None,
            morph=morph.strip() or None,
            editions=cols[5].strip() or None,
        )


# ---- Lexicons -------------------------------------------------------------------------------------

_LEX_ROW = re.compile(r"^[HG]\d{4}[A-Za-z]?\t")


def import_lexicon(db: sqlite3.Connection, path: Path, dataset: str) -> int:
    """TBESH/TBESG: eStrong# | dStrong | uStrong | Hebrew/Greek | Transliteration | Morph | Gloss | Meaning."""
    rows = []
    seen: set[str] = set()
    with open(path, encoding="utf-8-sig") as f:
        for line in f:
            if not _LEX_ROW.match(line):
                continue
            cols = line.rstrip("\n").split("\t") + [""] * 8
            dstrong = cols[1].split("=")[0].strip()
            if not re.fullmatch(r"[HG]\d{4}[A-Za-z]?", dstrong) or dstrong in seen:
                continue
            seen.add(dstrong)
            rows.append((dstrong, dataset, cols[0].strip(), cols[3].strip(), cols[4].strip(),
                         cols[5].strip(), cols[6].strip(), cols[7].strip()))
    db.executemany("INSERT INTO lexicon VALUES (?,?,?,?,?,?,?,?)", rows)
    return len(rows)
