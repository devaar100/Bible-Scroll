"""Parsing for the reference formats used by STEPBible, OpenBible.info and the TSK module."""
from __future__ import annotations

import re
from dataclasses import dataclass

from .books import BY_OSIS, BY_STEP, BY_TSK


@dataclass(frozen=True)
class StepRef:
    book_id: int
    chapter: int            # English (NRSV) numbering
    verse: int              # 0 = Psalm title
    word_no: int
    text_type: str
    hebrew: tuple[int, int] | None = None   # TAHOT (...): Hebrew numbering when different
    kjv: tuple[int, int] | None = None      # TAGNT [...]: KJV numbering when different
    na: tuple[int, int] | None = None       # TAGNT (...): Nestle-Aland numbering when different
    other: tuple[int, int] | None = None    # TAGNT {...}: other traditions

    @property
    def verse_ref(self) -> str:
        return f"{self.chapter}.{self.verse}"


_STEP = re.compile(
    r"^(?P<book>(?:[1-3][A-Z][a-z]|[A-Z][a-z]{2}))\.(?P<c>\d+)\.(?P<v>\d+)"
    r"(?:\((?P<pc>\d+)\.(?P<pv>\d+)\))?(?:\[(?P<kc>\d+)\.(?P<kv>\d+)\])?(?:\{(?P<oc>\d+)\.(?P<ov>\d+)\})?"
    r"#(?P<w>\d+)=(?P<type>\S+)$"
)


def parse_step_ref(field: str, dataset: str) -> StepRef:
    """'Mal.4.1(3.19)#01=L' (TAHOT) or 'Act.19.41[19.40]#03=NKO' (TAGNT)."""
    m = _STEP.match(field.strip())
    if not m:
        raise ValueError(f"Unrecognised {dataset} reference: {field!r}")
    book = BY_STEP.get(m["book"])
    if book is None:
        raise ValueError(f"Unknown book code in {field!r}")

    def pair(c: str, v: str) -> tuple[int, int] | None:
        return (int(m[c]), int(m[v])) if m[c] else None

    paren = pair("pc", "pv")
    return StepRef(
        book_id=book.id,
        chapter=int(m["c"]),
        verse=int(m["v"]),
        word_no=int(m["w"]),
        text_type=m["type"],
        hebrew=paren if dataset == "TAHOT" else None,
        na=paren if dataset == "TAGNT" else None,
        kjv=pair("kc", "kv"),
        other=pair("oc", "ov"),
    )


@dataclass(frozen=True)
class Span:
    """A verse or verse range (inclusive). Ranges may run into the next book (OpenBible only)."""
    book_id: int
    chapter: int
    verse: int
    chapter_end: int
    verse_end: int
    book_end: int | None = None

    @property
    def end_book(self) -> int:
        return self.book_end or self.book_id


def parse_osis_ref(text: str) -> Span:
    """OpenBible: 'Gen.1.1', 'Gen.1.1-Gen.1.3', or across books: 'Lev.27.34-Num.1.1'."""
    start, _, end = text.partition("-")
    sb, sc, sv = _osis(start)
    if end:
        eb, ec, ev = _osis(end)
        if (eb, ec, ev) < (sb, sc, sv):
            raise ValueError(f"Range runs backwards: {text!r}")
    else:
        eb, ec, ev = sb, sc, sv
    return Span(sb, sc, sv, ec, ev, eb if eb != sb else None)


def _osis(part: str) -> tuple[int, int, int]:
    code, chapter, verse = part.split(".")
    book = BY_OSIS.get(code)
    if book is None:
        raise ValueError(f"Unknown OSIS book {code!r}")
    return book.id, int(chapter), int(verse)


# ---- TSK (CrossWire ThML) ----------------------------------------------------------------------

@dataclass(frozen=True)
class TskRef:
    span: Span
    native: str
    note: str | None = None   # "marg", "Heb", "Gr" when a marker follows this reference


@dataclass
class TskParse:
    refs: list[TskRef]
    unparsed: list[str]


_BOOK_PREFIX = re.compile(r"^((?:[1-3])?[A-Z][a-z]*)\s+(.*)$")
# "*title(s)" = the Psalm title; any other "*word:" (marg, margin(s), Heb, Gr, Sept, compared with, etc)
# annotates the preceding reference.
_REF_START = re.compile(r"((?:[1-3])?[A-Z][a-z]*\s+\d|\d)")


def parse_tsk_refs(body: str, book_id: int, chapter: int) -> TskParse:
    """
    Parse one <scripRef> body, e.g. "10,12,18; Ec 2:13; 11:7; Ps 60:1; *title".

    Grammar (from a survey of all 85,508 TSK reference blocks):
      segments split by ';'; a segment may start with a book abbreviation (else: the previous book);
      items split by ','; an item is c:v | c:v-v | c:v-c:v | v | v-v | v-c:v. A bare verse uses the
      current chapter (initially the annotated verse's chapter). "*title" means the title (verse 0) of
      the last referenced chapter; "*marg:", "*Heb:", "*Gr:" annotate the preceding reference.
    """
    refs: list[TskRef] = []
    unparsed: list[str] = []
    book, current_chapter = book_id, chapter
    last_chapter: int | None = None

    for segment in body.split(";"):
        for raw_item in segment.split(","):
            item = raw_item.strip()
            if not item:
                continue
            prefix = _BOOK_PREFIX.match(item)
            if prefix and prefix.group(1) in BY_TSK:
                book = BY_TSK[prefix.group(1)].id
                item = prefix.group(2).strip()
                current_chapter = None  # a new book needs an explicit chapter
            if not any(ch.isdigit() for ch in item):
                # No digits: a note, not a reference ("*marg:", "*Sept:", "marg:", names like "Pharez").
                if item.startswith("*") or item.endswith(":"):
                    kind = item.lstrip("*").rstrip(":").strip()
                    if kind in ("title", "titles"):
                        if last_chapter is None:
                            unparsed.append(raw_item.strip())
                        else:
                            refs.append(TskRef(Span(book, last_chapter, 0, last_chapter, 0), raw_item.strip()))
                    elif refs:
                        previous = refs[-1]
                        note = {"margins": "marg", "margin": "marg"}.get(kind, kind)
                        refs[-1] = TskRef(previous.span, previous.native, note)
                continue
            if item.startswith("*"):
                # "*title Joh 10:22" / "*marg: 5:3": a marker followed by a reference.
                body = item[1:].strip()
                tail = _REF_START.search(body)
                kind = body[: tail.start()].strip().rstrip(":").strip()
                rest = body[tail.start():].strip()
                if kind in ("title", "titles"):
                    if last_chapter is None:
                        unparsed.append(raw_item.strip())
                    else:
                        refs.append(TskRef(Span(book, last_chapter, 0, last_chapter, 0), raw_item.strip()))
                elif refs:
                    previous = refs[-1]
                    note = {"margins": "marg", "margin": "marg"}.get(kind, kind)
                    refs[-1] = TskRef(previous.span, previous.native, note)
                if not rest:
                    continue
                item = rest  # e.g. "*title Joh 10:22"
                prefix = _BOOK_PREFIX.match(item)
                if prefix and prefix.group(1) in BY_TSK:
                    book = BY_TSK[prefix.group(1)].id
                    item = prefix.group(2).strip()
            span = _tsk_item(item, book, current_chapter)
            if span is None:
                unparsed.append(raw_item.strip())
                continue
            refs.append(TskRef(span, raw_item.strip()))
            current_chapter = span.chapter_end
            last_chapter = span.chapter_end
    return TskParse(refs, unparsed)


_ITEM = re.compile(r"^(?:(\d+):)?(\d+)(?:-(?:(\d+):)?(\d+))?$")


def _tsk_item(item: str, book_id: int, chapter: int | None) -> Span | None:
    m = _ITEM.match(item.replace(" ", ""))
    if not m:
        return None
    c1 = int(m.group(1)) if m.group(1) else chapter
    if c1 is None:
        return None
    v1 = int(m.group(2))
    if m.group(4) is None:
        return Span(book_id, c1, v1, c1, v1)
    c2 = int(m.group(3)) if m.group(3) else c1
    v2 = int(m.group(4))
    if (c2, v2) < (c1, v1):
        return None  # e.g. "Heb 9:21-15": a typo in the source; reported as unparsed rather than guessed
    return Span(book_id, c1, v1, c2, v2)
