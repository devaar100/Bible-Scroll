"""
Minimal reader for CrossWire SWORD zCom/zText modules (compressed, BlockType=BOOK), enough for TSK.

Format (per testament: ot.* / nt.*):
  .bzs  block index, 12 bytes each: offset, compressed size, uncompressed size (uint32 LE)
  .bzv  verse index, 10 bytes each: block number, start, size (uint32, uint32, uint16 LE)
  .bzz  zlib-compressed blocks
Verse index order (KJV versification): [0] module heading, [1] testament heading, then per book a
book heading followed, per chapter, by a chapter heading and the chapter's verses.
"""
from __future__ import annotations

import struct
import zipfile
import zlib
from collections.abc import Iterator
from dataclasses import dataclass


@dataclass(frozen=True)
class Entry:
    book_id: int
    chapter: int
    """0 = chapter heading (TSK puts the chapter outline there in some modules)."""
    verse: int
    text: str


class Testament:
    def __init__(self, bzs: bytes, bzv: bytes, bzz: bytes):
        self._blocks = [struct.unpack_from("<III", bzs, i) for i in range(0, len(bzs), 12)]
        self._bzv = bzv
        self._bzz = bzz
        self._cache: dict[int, bytes] = {}

    def __len__(self) -> int:
        return len(self._bzv) // 10

    def text(self, index: int) -> str:
        block, start, size = struct.unpack_from("<IIH", self._bzv, index * 10)
        if size == 0:
            return ""
        if block not in self._cache:
            offset, compressed, _ = self._blocks[block]
            self._cache = {block: zlib.decompress(self._bzz[offset:offset + compressed])}
        return self._cache[block][start:start + size].decode("utf-8", "replace")


def read_testament(archive: zipfile.ZipFile, data_path: str, name: str) -> Testament:
    def read(ext: str) -> bytes:
        return archive.read(f"{data_path}{name}.{ext}")
    return Testament(read("bzs"), read("bzv"), read("bzz"))


def iter_entries(
    testament: Testament,
    books: list[tuple[int, list[int]]],
) -> Iterator[Entry]:
    """[books] = (book_id, verse count per chapter) in canonical order for this testament."""
    index = 2  # module heading, testament heading
    for book_id, verse_counts in books:
        index += 1  # book heading
        for chapter, count in enumerate(verse_counts, start=1):
            yield Entry(book_id, chapter, 0, testament.text(index))
            index += 1
            for verse in range(1, count + 1):
                yield Entry(book_id, chapter, verse, testament.text(index))
                index += 1
    if index != len(testament):
        raise ValueError(f"Verse index has {len(testament)} entries but KJV layout needs {index}: not KJV versification?")
