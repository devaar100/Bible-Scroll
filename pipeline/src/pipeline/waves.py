"""
waves.yaml: the launch run's named verse sets, in run order, and the batch size.

Only the shape in waves.yaml is accepted (top-level `batch_size`, then `waves:` with `- name:` items holding
`source:` or `books: [A, B]`). Anything else raises, so a typo can't silently drop a wave. The requirements
have no YAML library, and this file doesn't need one.
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

from .books import BY_USFM
from .paths import PIPELINE

WAVES_FILE = PIPELINE / "waves.yaml"
MAX_BATCH = 500


@dataclass(frozen=True)
class Wave:
    name: str
    source: str | None = None
    books: tuple[str, ...] = ()


@dataclass(frozen=True)
class Waves:
    batch_size: int
    waves: tuple[Wave, ...]

    def get(self, name: str) -> Wave:
        for w in self.waves:
            if w.name == name:
                return w
        raise KeyError(f"No wave {name!r} in waves.yaml (have: {', '.join(self.names)})")

    @property
    def names(self) -> list[str]:
        return [w.name for w in self.waves]


_ITEM = re.compile(r"^  - name:\s*([a-z0-9_-]+)\s*$")
_FIELD = re.compile(r"^    (source|books):\s*(.+?)\s*$")


def load(path: Path = WAVES_FILE) -> Waves:
    batch_size, items, in_waves = None, [], False
    for n, raw in enumerate(path.read_text().splitlines(), 1):
        line = raw.split(" #")[0].rstrip() if not raw.lstrip().startswith("#") else ""
        if not line:
            continue
        if m := re.match(r"^batch_size:\s*(\d+)$", line):
            batch_size = int(m[1])
        elif line == "waves:":
            in_waves = True
        elif in_waves and (m := _ITEM.match(line)):
            items.append({"name": m[1]})
        elif in_waves and items and (m := _FIELD.match(line)):
            key, value = m[1], m[2]
            if key in items[-1]:
                raise ValueError(f"{path.name}:{n}: {key} given twice")
            items[-1][key] = value
        else:
            raise ValueError(f"{path.name}:{n}: unexpected line {raw!r}")
    if batch_size is None or not 1 <= batch_size <= MAX_BATCH:
        raise ValueError(f"{path.name}: batch_size must be 1-{MAX_BATCH}")
    waves = []
    for item in items:
        if ("source" in item) == ("books" in item):
            raise ValueError(f"{path.name}: wave {item['name']} needs exactly one of source or books")
        books = ()
        if "books" in item:
            m = re.fullmatch(r"\[([A-Z0-9, ]+)\]", item["books"])
            if not m:
                raise ValueError(f"{path.name}: wave {item['name']}: books must look like [MRK, MAT]")
            books = tuple(b.strip() for b in m[1].split(",") if b.strip())
            unknown = [b for b in books if b not in BY_USFM]
            if unknown:
                raise ValueError(f"{path.name}: wave {item['name']}: unknown books {unknown}")
        waves.append(Wave(item["name"], item.get("source"), books))
    names = [w.name for w in waves]
    if len(set(names)) != len(names) or not waves:
        raise ValueError(f"{path.name}: wave names must be unique and there must be at least one")
    return Waves(batch_size, tuple(waves))


def wave_ids(wave: Wave, all_ids: list[str]) -> list[str]:
    """The wave's study ids in order. all_ids: every built study id, in Bible order."""
    if wave.source is not None:
        from .publish import DISCOVER_FILE, load_discover
        if wave.source != DISCOVER_FILE.name:
            raise ValueError(f"wave {wave.name}: only {DISCOVER_FILE.name} is supported as a source "
                             "(it has the single strict loader publish uses)")
        return load_discover()
    prefixes = tuple(f"{b}." for b in wave.books)
    return [i for i in all_ids if i.startswith(prefixes)]
