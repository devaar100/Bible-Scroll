"""Every external source the pipeline downloads. Licence facts are documented in LICENCES.md."""
from __future__ import annotations

from dataclasses import dataclass
from urllib.parse import quote

STEP_RAW = "https://raw.githubusercontent.com/STEPBible/STEPBible-Data/master"


def _step(folder: str, name: str) -> str:
    return f"{STEP_RAW}/{quote(folder)}/{quote(name)}"


@dataclass(frozen=True)
class Source:
    key: str
    """Saved as data/raw/<filename>."""
    filename: str
    url: str
    dataset: str


TAHOT_PARTS = ["Gen-Deu", "Jos-Est", "Job-Sng", "Isa-Mal"]
TAGNT_PARTS = ["Mat-Jhn", "Act-Rev"]
_AMALGAMATED = "Translators Amalgamated OT+NT"

SOURCES: list[Source] = [
    *[
        Source(
            f"tahot_{p.lower()}", f"TAHOT_{p}.txt",
            _step(_AMALGAMATED, f"TAHOT {p} - Translators Amalgamated Hebrew OT - STEPBible.org CC BY.txt"),
            "TAHOT",
        )
        for p in TAHOT_PARTS
    ],
    *[
        Source(
            f"tagnt_{p.lower()}", f"TAGNT_{p}.txt",
            _step(_AMALGAMATED, f"TAGNT {p} - Translators Amalgamated Greek NT - STEPBible.org CC-BY.txt"),
            "TAGNT",
        )
        for p in TAGNT_PARTS
    ],
    Source("tbesh", "TBESH.txt",
           _step("Lexicons", "TBESH - Translators Brief lexicon of Extended Strongs for Hebrew - STEPBible.org CC BY.txt"),
           "TBESH"),
    Source("tbesg", "TBESG.txt",
           _step("Lexicons", "TBESG - Translators Brief lexicon of Extended Strongs for Greek - STEPBible.org CC BY.txt"),
           "TBESG"),
    # STEPBible's own versification table; used only to cross-check our mapping.
    Source("tvtms", "TVTMS.txt",
           _step("Versification",
                 "TVTMS - Translators Versification Traditions with Methodology for Standardisation for "
                 "Eng+Heb+Lat+Grk+Others - STEPBible.org CC BY.txt"),
           "TVTMS"),
    Source("openbible", "openbible-cross-references.zip",
           "https://a.openbible.info/data/cross-references.zip", "OpenBible"),
    # CrossWire SWORD module (zCom, ThML). Its mods.d/tsk.conf says DistributionLicense=Public Domain.
    Source("tsk", "sword-TSK.zip",
           "https://www.crosswire.org/ftpmirror/pub/sword/packages/rawzip/TSK.zip", "TSK"),
]

BY_KEY = {s.key: s for s in SOURCES}
