"""
Map source verses onto the app's KJV verse keys and classify everything that doesn't map one-to-one.

Rules
- TAHOT: every word carries its English (NRSV) reference, with the Hebrew one in (...) when different.
  The English reference is the KJV key; verse 0 is a Psalm title and maps to the KJV superscription.
  The native verse (for the map) is the Hebrew reference.
- TAGNT: the main reference is NRSV; [...] gives the KJV reference when different. That is the KJV key.
  The native verse is the NRSV reference.
- OpenBible (ESV/NRSV-style English numbering): kept when the verse exists in KJV, otherwise moved to
  the KJV verse that holds most of that NRSV verse's words (from TAHOT/TAGNT); otherwise unmapped.
- TSK: already KJV versification (checked by the SWORD index layout); only existence is checked.
"""
from __future__ import annotations

import re
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path

from .books import BOOKS, BY_STEP
from .kjv import Kjv

Key = tuple[int, int, int]  # book_id, chapter, verse


@dataclass(frozen=True)
class MapRow:
    dataset: str
    native: Key
    native_label: str
    kjv: Key | None
    kind: str
    words: int | None
    note: str = ""


@dataclass
class WordMapper:
    """Collects (native verse -> KJV verse) word counts while words are imported."""
    dataset: str
    kjv: Kjv
    counts: dict[Key, Counter] = field(default_factory=lambda: defaultdict(Counter))
    english: dict[Key, Counter] = field(default_factory=lambda: defaultdict(Counter))
    unmapped_words: int = 0

    def add(self, native: Key, english: Key, target: Key) -> Key | None:
        """Returns the KJV key for one word, or None when the source verse has no KJV home."""
        ok = self.kjv.has(*target)
        self.counts[native][target if ok else None] += 1
        if ok:
            self.english[english][target] += 1
        else:
            self.unmapped_words += 1
        return target if ok else None

    def rows(self, testament_books: set[int]) -> list[MapRow]:
        sources_per_kjv: dict[Key, set[Key]] = defaultdict(set)
        for native, targets in self.counts.items():
            for target in targets:
                if target is not None:
                    sources_per_kjv[target].add(native)

        rows: list[MapRow] = []
        for native in sorted(self.counts):
            targets = self.counts[native]
            mapped = [t for t in targets if t is not None]
            for target, words in sorted(targets.items(), key=lambda kv: (kv[0] is None, kv[0] or (0, 0, 0))):
                if target is None:
                    kind = "source_only"
                elif target[2] == 0:
                    kind = "title"
                elif len(mapped) > 1:
                    kind = "split"
                elif len(sources_per_kjv[target]) > 1:
                    kind = "merged"
                elif target != native:
                    kind = "renumbered"
                else:
                    kind = "identical"
                note = ""
                if kind == "split":
                    note = "native verse spread over " + ", ".join(label(t) for t in sorted(mapped))
                elif kind in ("merged", "title") and len(sources_per_kjv[target]) > 1:
                    note = "KJV verse gathers " + ", ".join(label(n) for n in sorted(sources_per_kjv[target]))
                rows.append(MapRow(self.dataset, native, label(native), target, kind, words, note))

        covered = set(sources_per_kjv)
        for key in sorted(self.kjv.verses):
            if key[0] in testament_books and key not in covered:
                rows.append(MapRow(self.dataset, key, "", key, "kjv_only", 0, "no source words land on this KJV verse"))
        return rows

    def english_to_kjv(self) -> dict[Key, Key]:
        """NRSV/English verse -> the KJV verse holding most of its words (for remapping OpenBible)."""
        return {eng: targets.most_common(1)[0][0] for eng, targets in self.english.items()}


def label(key: Key | None) -> str:
    if key is None:
        return "-"
    book, chapter, verse = key
    return f"{BOOKS[book - 1].step}.{chapter}.{'title' if verse == 0 else verse}"


# ---- TVTMS cross-check ----------------------------------------------------------------------------

_CELL = re.compile(r"^(?P<book>(?:[1-3][A-Z][a-z]|[A-Z][a-z]{2}))\.(?P<c>\d+):(?P<v>\d+|Title)(?:-(?:(?P<c2>\d+):)?(?P<v2>\d+))?$")


def _expand(cell: str, kjv: Kjv | None = None) -> list[Key] | None:
    m = _CELL.match(cell.strip())
    if not m or m["book"] not in BY_STEP:
        return None
    book = BY_STEP[m["book"]].id
    c1 = int(m["c"])
    v1 = 0 if m["v"] == "Title" else int(m["v"])
    if not m["v2"]:
        return [(book, c1, v1)]
    c2 = int(m["c2"]) if m["c2"] else c1
    v2 = int(m["v2"])
    if c2 == c1:
        return [(book, c1, v) for v in range(v1, v2 + 1)]
    return None  # cross-chapter ranges need verse counts per tradition; not compared


def tvtms_pairs(path: Path) -> set[tuple[Key, Key]]:
    """(English KJV verse, Hebrew verse) pairs from TVTMS rows whose two cells expand to equal lengths."""
    pairs: set[tuple[Key, Key]] = set()
    eng_col = heb_col = None
    with open(path, encoding="utf-8-sig") as f:
        for line in f:
            cols = [c.strip() for c in line.rstrip("\n").split("\t")]
            if cols[0].startswith("$") or cols[0] == "BIBLES":
                # Column names are on the "$Book.c:v--c:v" header line or, in some records, a BIBLES row.
                eng_col = next((i for i, c in enumerate(cols) if c.startswith("English")), None)
                heb_col = next((i for i, c in enumerate(cols) if c == "Hebrew" or c.startswith("Hebrew")), None)
                continue
            if eng_col is None or heb_col is None or max(eng_col, heb_col) >= len(cols):
                continue
            if not re.match(r"^[A-Z][A-Za-z]+$", cols[0]) or cols[0] in ("BIBLES", "TEST"):
                continue
            eng, heb = _expand(cols[eng_col]), _expand(cols[heb_col])
            if eng and heb and len(eng) == len(heb):
                pairs.update(zip(eng, heb))
    return pairs
