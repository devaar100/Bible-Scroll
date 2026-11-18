"""
Build one study skeleton per KJV verse (out/studies/<USFM>.<c>.<v>.json) straight from data/source.db.

Filled from data, no AI: originalLanguage, crossReferences, citations. The prose sections stay empty
for the AI step. Every file is validated against schema/study.json before the build succeeds.
"""
from __future__ import annotations

import hashlib
import json
import shutil
import sqlite3
import sys
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from jsonschema import Draft202012Validator

from .paths import OUT, PIPELINE, SOURCE_DB

SCHEMA_PATH = PIPELINE / "schema" / "study.json"
STUDIES_DIR = OUT / "studies"
SUMMARY_PATH = OUT / "studies_summary.json"
MAX_CROSS_REFERENCES = 8
SCHEMA_VERSION = 3  # 3: status "failed"; meta.promptVersion/batchId/attempts
PROSE_FIELDS = ("meaning", "historicalContext", "lifeInBibleTimes", "theology", "didYouKnow", "applyIt")

# Part of contentVersion: bump the text whenever a selection rule below changes.
BUILDER_RULES = (
    "words: TAHOT minus X (LXX) and empty Qere slots; TAGNT critical (N) else TR (K). "
    "lemma fallback: lexicon headword. xrefs: OpenBible votes>0 desc, then TSK in source order; "
    "no self, no verse-0 targets, one per start verse; max 8."
)

STEP_URL = "https://stepbible.github.io/STEPBible-Data/"
CITATIONS = {
    "TAHOT": {
        "source": "STEP Bible, TAHOT (Translators Amalgamated Hebrew OT), Tyndale House Cambridge",
        "licence": "CC BY 4.0",
        "url": STEP_URL,
    },
    "TAGNT": {
        "source": "STEP Bible, TAGNT (Translators Amalgamated Greek NT), Tyndale House Cambridge",
        "licence": "CC BY 4.0",
        "url": STEP_URL,
    },
    "OpenBible": {
        "source": "OpenBible.info cross references",
        "licence": "CC BY 4.0",
        "url": "https://www.openbible.info/labs/cross-references/",
    },
    "TSK": {
        "source": "Treasury of Scripture Knowledge (c. 1880), CrossWire SWORD module 1.4",
        "licence": "Public Domain",
        "url": "https://www.crosswire.org/sword/modules/ModInfo.jsp?modName=TSK",
    },
}

Key = tuple[int, int, int]


@dataclass(frozen=True)
class WordRow:
    dataset: str
    text_type: str
    surface: str
    translit: str | None
    strongs: str | None
    lemma: str | None
    morph: str | None
    gloss: str | None


@dataclass(frozen=True)
class XrefRow:
    dataset: str
    to: Key
    to_end: Key
    votes: int | None
    keyword: str | None


DATA_FIELDS = ("originalLanguage", "crossReferences", "citations")


def merge(existing: dict | None, fresh: dict, stats: "Stats") -> dict:
    """
    Rebuilding must never lose work: keep an existing study's prose, status, meta and checks, and take
    only the data sections (and schemaVersion) from the fresh build. If the data changed under a study that
    was already checked or reviewed, send it back to "generated" so it's checked again; its review fields
    stay in meta as history.
    """
    if existing is None:
        return fresh
    merged = dict(fresh)
    for name in PROSE_FIELDS:
        merged[name] = existing.get(name, "")
    merged["meta"] = existing.get("meta", fresh["meta"])
    merged["status"] = existing.get("status", "generated")
    if "checks" in existing:
        merged["checks"] = existing["checks"]
    if any(existing.get(name) != fresh[name] for name in DATA_FIELDS):
        merged["contentVersion"] = fresh["contentVersion"]
        if merged["status"] != "generated":
            merged["status"] = "generated"
            stats.reset_for_recheck += 1
    else:
        merged["contentVersion"] = existing.get("contentVersion", fresh["contentVersion"])
    if merged["status"] != "generated" or merged["meta"].get("model"):
        stats.kept_existing_work += 1
    return merged


@dataclass
class Stats:
    empty_slots_skipped: int = 0
    lxx_words_skipped: int = 0
    lemma_from_lexicon: int = 0
    tr_fallback_verses: int = 0
    aramaic_verses: int = 0
    verses_without_xrefs: int = 0
    kept_existing_work: int = 0
    reset_for_recheck: int = 0


# ---- Words ------------------------------------------------------------------------------------------

def select_words(rows: list[WordRow], lexicon_lemma: dict[str, str], stats: Stats) -> dict:
    """The verse's originalLanguage block. Raises if a verse ends up with no usable words."""
    dataset = rows[0].dataset
    if dataset == "TAHOT":
        kept = []
        for r in rows:
            if r.text_type.startswith("X"):
                stats.lxx_words_skipped += 1  # reconstructed from the Septuagint; not in the Hebrew text KJV follows
            elif not r.surface:
                stats.empty_slots_skipped += 1  # Qere with no written word ("read but not written" slot)
            else:
                kept.append(r)
        aramaic = sum(1 for r in kept if (r.morph or "").startswith("A"))
        language = "aramaic" if aramaic * 2 > len(kept) else "hebrew"
        if language == "aramaic":
            stats.aramaic_verses += 1
        basis = "leningrad"
    else:
        # Type letters: N = Nestle-Aland (critical) text, K = KJV's Greek (Scrivener TR), O = other editions.
        kept = [r for r in rows if "n" in r.text_type.lower()]
        basis = "critical"
        if not kept:
            kept = [r for r in rows if "k" in r.text_type.lower()]
            basis = "textus_receptus"
            stats.tr_fallback_verses += 1
        language = "greek"
    if not kept:
        raise ValueError(f"No usable {dataset} words")

    words = []
    for r in kept:
        lemma = r.lemma
        if not lemma:
            lemma = lexicon_lemma.get(r.strongs or "")
            stats.lemma_from_lexicon += 1
        if not (lemma and r.strongs and r.morph and r.translit):
            raise ValueError(f"Incomplete word {r}")
        words.append({
            "surface": r.surface,
            "lemma": lemma,
            "transliteration": r.translit,
            "strongs": r.strongs,
            "gloss": r.gloss or "",
            "morphology": r.morph,
        })
    return {"language": language, "textBasis": basis, "words": words}


# ---- Cross references -------------------------------------------------------------------------------

def rank_cross_references(here: Key, candidates: list[XrefRow], usfm: dict[int, str]) -> list[dict]:
    """
    Top cross references for one verse: OpenBible by votes (only > 0: lower means readers voted it down),
    then TSK in the order the Treasury lists them. Skips references to the verse itself, Psalm titles
    (verse 0, not a verse in the app), and a second reference starting at the same verse.
    """
    openbible = sorted((c for c in candidates if c.dataset == "OpenBible" and (c.votes or 0) > 0),
                       key=lambda c: (-(c.votes or 0), c.to, c.to_end))
    tsk = [c for c in candidates if c.dataset == "TSK"]
    chosen: list[dict] = []
    starts: set[Key] = set()
    for c in [*openbible, *tsk]:
        if len(chosen) == MAX_CROSS_REFERENCES:
            break
        if c.to[2] == 0 or c.to_end[2] == 0 or c.to in starts or _contains(c, here):
            continue
        starts.add(c.to)
        entry = {"ref": format_ref(c.to, c.to_end, usfm), "source": c.dataset}
        if c.dataset == "TSK" and c.keyword:
            entry["note"] = c.keyword
        chosen.append(entry)
    return chosen


def _contains(c: XrefRow, key: Key) -> bool:
    return c.to <= key <= c.to_end


def format_ref(start: Key, end: Key, usfm: dict[int, str]) -> str:
    first = f"{usfm[start[0]]}.{start[1]}.{start[2]}"
    return first if start == end else f"{first}-{usfm[end[0]]}.{end[1]}.{end[2]}"


# ---- Assembly ---------------------------------------------------------------------------------------

def make_study(key: Key, usfm: dict[int, str], original: dict, xrefs: list[dict], word_dataset: str,
               content_version: str, generated_at: str) -> dict:
    used = [word_dataset, *dict.fromkeys(x["source"] for x in xrefs)]
    return {
        "id": f"{usfm[key[0]]}.{key[1]}.{key[2]}",
        "schemaVersion": SCHEMA_VERSION,
        "contentVersion": content_version,
        "originalLanguage": original,
        "crossReferences": xrefs,
        **{field: "" for field in PROSE_FIELDS},
        "citations": [CITATIONS[d] for d in used],
        "status": "generated",
        "meta": {"model": None, "generatedAt": generated_at},
    }


def content_version(db: sqlite3.Connection) -> str:
    """Hash of the source files, the builder rules and the schema: changes only when output would."""
    h = hashlib.sha256()
    for key, digest in db.execute("SELECT key, sha256 FROM source_file ORDER BY key"):
        h.update(f"{key}={digest}\n".encode())
    h.update(BUILDER_RULES.encode())
    h.update(SCHEMA_PATH.read_bytes())
    return h.hexdigest()[:12]


def validator() -> Draft202012Validator:
    schema = json.loads(SCHEMA_PATH.read_text())
    Draft202012Validator.check_schema(schema)
    return Draft202012Validator(schema, format_checker=Draft202012Validator.FORMAT_CHECKER)


def build_studies(db_path: Path = SOURCE_DB, out_dir: Path = STUDIES_DIR) -> Stats:
    if not db_path.exists():
        sys.exit(f"Missing {db_path}: run `python -m pipeline build` first.")
    db = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    usfm = dict(db.execute("SELECT id, usfm FROM book"))
    lexicon_lemma = dict(db.execute("SELECT strongs, lemma FROM lexicon WHERE lemma <> ''"))
    verses: list[Key] = list(db.execute("SELECT book_id, chapter, verse FROM kjv_verse ORDER BY 1, 2, 3"))
    version = content_version(db)
    generated_at = datetime.now(timezone.utc).isoformat(timespec="seconds")

    words: dict[Key, list[WordRow]] = defaultdict(list)
    for book, chapter, verse, *rest in db.execute(
        "SELECT book_id, chapter, verse, dataset, text_type, surface, translit, strongs, lemma, morph, gloss "
        "FROM word WHERE verse > 0 ORDER BY id"
    ):
        words[(book, chapter, verse)].append(WordRow(*rest))

    xrefs: dict[Key, list[XrefRow]] = defaultdict(list)
    for fb, fc, fv, ds, tb, tc, tv, eb, ec, ev, votes, keyword in db.execute(
        "SELECT from_book, from_chapter, from_verse, dataset, to_book, to_chapter, to_verse, "
        "to_book_end, to_chapter_end, to_verse_end, votes, keyword FROM xref ORDER BY id"
    ):
        xrefs[(fb, fc, fv)].append(XrefRow(ds, (tb, tc, tv), (eb, ec, ev), votes, keyword))
    db.close()

    check = validator()
    stats = Stats()
    tmp = out_dir.with_name(out_dir.name + ".part")
    shutil.rmtree(tmp, ignore_errors=True)
    tmp.mkdir(parents=True)
    errors: list[str] = []
    for key in verses:
        rows = words.get(key)
        if not rows:
            raise ValueError(f"No source words for {key}; rerun `build` and check the versification report")
        original = select_words(rows, lexicon_lemma, stats)
        chosen = rank_cross_references(key, xrefs.get(key, []), usfm)
        if not chosen:
            stats.verses_without_xrefs += 1
        fresh = make_study(key, usfm, original, chosen, rows[0].dataset, version, generated_at)
        existing_path = out_dir / f"{fresh['id']}.json"
        existing = json.loads(existing_path.read_text()) if existing_path.exists() else None
        study = merge(existing, fresh, stats)
        for error in check.iter_errors(study):
            errors.append(f"{study['id']}: {error.json_path}: {error.message}")
        (tmp / f"{study['id']}.json").write_text(json.dumps(study, ensure_ascii=False, indent=2) + "\n")
    if errors:
        raise ValueError(f"{len(errors)} schema errors, e.g.:\n" + "\n".join(errors[:20]))

    # Swap directories so a crash never leaves a half-written set: old -> .old, new -> out_dir, drop .old.
    old = out_dir.with_name(out_dir.name + ".old")
    shutil.rmtree(old, ignore_errors=True)
    if out_dir.exists():
        out_dir.replace(old)
    tmp.replace(out_dir)
    shutil.rmtree(old, ignore_errors=True)
    summary = {"studies": len(verses), "contentVersion": version, "generatedAt": generated_at, **vars(stats)}
    SUMMARY_PATH.write_text(json.dumps(summary, indent=2) + "\n")
    print(f"Wrote {len(verses):,} studies to {out_dir} (all valid against {SCHEMA_PATH.name})")
    print(json.dumps(summary, indent=2))
    return stats
