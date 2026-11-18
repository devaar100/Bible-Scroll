import struct
import zlib

import pytest

from pipeline.sword import Testament as SwordTestament, iter_entries


def module(texts: list[str]) -> SwordTestament:
    """One compressed block holding every entry, like a tiny zCom testament."""
    raw = b""
    bzv = b""
    for t in texts:
        data = t.encode()
        bzv += struct.pack("<IIH", 0, len(raw), len(data))
        raw += data
    block = zlib.compress(raw)
    bzs = struct.pack("<III", 0, len(block), len(raw))
    return SwordTestament(bzs, bzv, block)


def test_kjv_layout_skips_headings():
    # module, testament, book, chapter 1, v1, v2, chapter 2, v1
    t = module(["", "", "book", "ch1", "a", "b", "ch2", "c"])
    got = [(e.chapter, e.verse, e.text) for e in iter_entries(t, [(1, [2, 1])])]
    assert got == [(1, 0, "ch1"), (1, 1, "a"), (1, 2, "b"), (2, 0, "ch2"), (2, 1, "c")]


def test_wrong_versification_is_rejected():
    t = module(["", "", "book", "ch1", "a", "b", "extra"])
    with pytest.raises(ValueError):
        list(iter_entries(t, [(1, [2])]))
