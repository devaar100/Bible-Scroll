"""Build data/source.db from data/raw/ and write the versification report to out/."""
from __future__ import annotations

import csv
import html
import io
import re
import sqlite3
import zipfile
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from datetime import date

from .books import BOOKS, NT, OT
from .download import manifest
from .import_step import import_lexicon, iter_tagnt, iter_tahot
from .kjv import Kjv, load_kjv
from .paths import OUT, RAW, SOURCE_DB
from .refs import Span, parse_osis_ref, parse_tsk_refs
from .schema import SCHEMA
from .sources import TAGNT_PARTS, TAHOT_PARTS
from .sword import iter_entries, read_testament
from .versification import Key, MapRow, WordMapper, label, tvtms_pairs

TSK_DATA_PATH = "modules/comments/zcom/tsk/"


@dataclass
class XrefIssues:
    """Cross-reference endpoints that needed remapping or have no KJV home, per dataset."""
    remapped: Counter = field(default_factory=Counter)          # (english, kjv) -> refs
    unmapped: Counter = field(default_factory=Counter)          # key -> refs
    unparsed: list[tuple[str, str]] = field(default_factory=list)  # (annotated verse, text)


def build() -> None:
    files = manifest()
    kjv = load_kjv()
    SOURCE_DB.parent.mkdir(parents=True, exist_ok=True)
    tmp = SOURCE_DB.with_suffix(".db.part")
    tmp.unlink(missing_ok=True)
    db = sqlite3.connect(tmp)
    db.executescript(SCHEMA)
    db.executemany("INSERT INTO source_file VALUES (?,?,?,?,?,?,?)", [
        (e["key"], e["dataset"], e["url"], e["file"], e["bytes"], e["sha256"], e["retrieved"]) for e in files.values()
    ])
    db.executemany("INSERT INTO book VALUES (?,?,?,?,?)", [(b.id, b.usfm, b.step, b.osis, b.name) for b in BOOKS])
    db.executemany("INSERT INTO kjv_verse VALUES (?,?,?)", sorted(kjv.verses))
    db.executemany("INSERT INTO kjv_superscription VALUES (?,?)", sorted(kjv.titled))

    print("TAHOT words…")
    tahot, tahot_pairs = _import_tahot(db, kjv)
    print("TAGNT words…")
    tagnt = _import_tagnt(db, kjv)
    print("Lexicons…")
    lex_h = import_lexicon(db, RAW / files["tbesh"]["file"], "TBESH")
    lex_g = import_lexicon(db, RAW / files["tbesg"]["file"], "TBESG")

    english_to_kjv = {**tahot.english_to_kjv(), **tagnt.english_to_kjv()}
    print("OpenBible cross references…")
    ob_count, ob_issues = _import_openbible(db, kjv, english_to_kjv, RAW / files["openbible"]["file"])
    print("TSK cross references…")
    tsk_count, tsk_issues = _import_tsk(db, kjv, RAW / files["tsk"]["file"])

    rows = tahot.rows({b.id for b in OT}) + tagnt.rows({b.id for b in NT})
    db.executemany("INSERT INTO versification_map VALUES (?,?,?,?,?,?,?,?)", [
        (r.dataset, r.native_label or label(r.native), *(r.kjv or (None, None, None)), r.kind, r.words, r.note or None)
        for r in rows
    ])
    for dataset, issues in (("OpenBible", ob_issues), ("TSK", tsk_issues)):
        db.executemany("INSERT INTO versification_map VALUES (?,?,?,?,?,?,?,?)", [
            (dataset, label(eng), *kjv_key, "renumbered", None, f"{n} reference(s) moved")
            for (eng, kjv_key), n in sorted(issues.remapped.items())
        ] + [
            (dataset, label(key), None, None, None, "source_only", None, f"{n} reference(s) dropped")
            for key, n in sorted(issues.unmapped.items())
        ])

    print("TVTMS cross-check…")
    check = _tvtms_check(RAW / files["tvtms"]["file"], tahot_pairs, kjv)

    db.commit()
    stats = {
        "words_tahot": db.execute("SELECT count(*) FROM word WHERE dataset='TAHOT'").fetchone()[0],
        "words_tagnt": db.execute("SELECT count(*) FROM word WHERE dataset='TAGNT'").fetchone()[0],
        "lexicon": (lex_h, lex_g),
        "lexicon_misses": db.execute(
            "SELECT strongs, count(*) FROM word WHERE strongs IS NOT NULL "
            "AND strongs NOT IN (SELECT strongs FROM lexicon) GROUP BY strongs ORDER BY strongs").fetchall(),
        "xrefs": (ob_count, tsk_count),
        "unmapped_words": (tahot.unmapped_words, tagnt.unmapped_words),
    }
    db.execute("VACUUM")
    db.close()
    tmp.replace(SOURCE_DB)
    _write_report(rows, ob_issues, tsk_issues, check, stats)
    print(f"Wrote {SOURCE_DB}")


# ---- Words ------------------------------------------------------------------------------------------

_WORD_SQL = "INSERT INTO word (dataset, book_id, chapter, verse, native_ref, word_no, text_type, surface, translit, " \
            "strongs, strongs_all, lemma, morph, gloss, editions) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"


def _import_tahot(db: sqlite3.Connection, kjv: Kjv) -> tuple[WordMapper, set[tuple[Key, Key]]]:
    mapper = WordMapper("TAHOT", kjv)
    pairs: set[tuple[Key, Key]] = set()  # (English/KJV verse, Hebrew verse) for the TVTMS check
    for part in TAHOT_PARTS:
        batch = []
        for w in iter_tahot(RAW / f"TAHOT_{part}.txt"):
            r = w.ref
            english = (r.book_id, r.chapter, r.verse)
            hebrew = (r.book_id, *r.hebrew) if r.hebrew else english
            target = mapper.add(native=hebrew, english=english, target=english)
            if target:
                pairs.add((target, hebrew))
            batch.append(("TAHOT", r.book_id, *(target[1:] if target else (None, None)), w.native_ref, r.word_no,
                          r.text_type, w.surface, w.translit, w.strongs, w.strongs_all, w.lemma, w.morph, w.gloss, None))
        db.executemany(_WORD_SQL, batch)
    return mapper, pairs


def _import_tagnt(db: sqlite3.Connection, kjv: Kjv) -> WordMapper:
    mapper = WordMapper("TAGNT", kjv)
    for part in TAGNT_PARTS:
        batch = []
        for w in iter_tagnt(RAW / f"TAGNT_{part}.txt"):
            r = w.ref
            nrsv = (r.book_id, r.chapter, r.verse)
            wanted = (r.book_id, *r.kjv) if r.kjv else nrsv
            target = mapper.add(native=nrsv, english=nrsv, target=wanted)
            batch.append(("TAGNT", r.book_id, *(target[1:] if target else (None, None)), w.native_ref, r.word_no,
                          r.text_type, w.surface, w.translit, w.strongs, w.strongs_all, w.lemma, w.morph, w.gloss,
                          w.editions))
        db.executemany(_WORD_SQL, batch)
    return mapper


# ---- Cross references -------------------------------------------------------------------------------

def _resolve(key: Key, kjv: Kjv, english_to_kjv: dict[Key, Key], issues: XrefIssues) -> Key | None:
    if kjv.has(*key):
        return key
    moved = english_to_kjv.get(key)
    if moved and kjv.has(*moved):
        issues.remapped[(key, moved)] += 1
        return moved
    issues.unmapped[key] += 1
    return None


def _import_openbible(db, kjv: Kjv, english_to_kjv: dict[Key, Key], path) -> tuple[int, XrefIssues]:
    issues = XrefIssues()
    rows = []
    with zipfile.ZipFile(path) as z:
        name = next(n for n in z.namelist() if n.endswith(".txt"))
        text = io.TextIOWrapper(z.open(name), encoding="utf-8")
        next(text)  # header: From Verse  To Verse  Votes  #www.openbible.info CC-BY <date>
        for line in text:
            src, dst, votes = line.rstrip("\n").split("\t")[:3]
            try:
                a, b = parse_osis_ref(src), parse_osis_ref(dst)
            except ValueError:
                issues.unparsed.append((src, dst))
                continue
            frm = _resolve((a.book_id, a.chapter, a.verse), kjv, english_to_kjv, issues)
            start = _resolve((b.book_id, b.chapter, b.verse), kjv, english_to_kjv, issues)
            end = _resolve((b.end_book, b.chapter_end, b.verse_end), kjv, english_to_kjv, issues)
            if not (frm and start and end):
                continue
            rows.append(("OpenBible", *frm, *start, *end, int(votes), None, None, dst))
    db.executemany(
        "INSERT INTO xref (dataset, from_book, from_chapter, from_verse, to_book, to_chapter, to_verse, "
        "to_book_end, to_chapter_end, to_verse_end, votes, keyword, note, native) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", rows)
    return len(rows), issues


_REF_TAG = re.compile(r"(<scripRef([^>]*)>(.*?)</scripRef>)", re.S)


def _import_tsk(db, kjv: Kjv, path) -> tuple[int, XrefIssues]:
    issues = XrefIssues()
    rows = []
    with zipfile.ZipFile(path) as z:
        for testament, books in (("ot", OT), ("nt", NT)):
            module = read_testament(z, TSK_DATA_PATH, testament)
            layout = [(b.id, kjv.chapter_sizes[b.id]) for b in books]
            for entry in iter_entries(module, layout):
                if entry.verse == 0 or not entry.text:
                    continue
                keyword = None
                parts = _REF_TAG.split(entry.text)
                # split() yields: text, whole tag, attrs, body, text, whole tag, attrs, body, ...
                i = 0
                while i < len(parts):
                    chunk = parts[i]
                    if i % 4 == 0:
                        lines = [html.unescape(re.sub(r"<[^>]+>", "", s)).strip()
                                 for s in re.split(r"<br\s*/?>", chunk)]
                        lines = [s for s in lines if s]
                        if lines:
                            keyword = lines[-1].rstrip(".").strip() or keyword
                        i += 1
                        continue
                    attrs, body = parts[i + 1], parts[i + 2]
                    i += 3
                    if "passage=" in attrs:
                        continue  # chapter outline link, not a cross reference
                    parsed = parse_tsk_refs(html.unescape(body), entry.book_id, entry.chapter)
                    here = label((entry.book_id, entry.chapter, entry.verse))
                    issues.unparsed.extend((here, u) for u in parsed.unparsed)
                    for ref in parsed.refs:
                        s: Span = ref.span
                        ok_start = kjv.has(s.book_id, s.chapter, s.verse)
                        ok_end = kjv.has(s.book_id, s.chapter_end, s.verse_end)
                        if not (ok_start and ok_end):
                            bad = (s.book_id, s.chapter, s.verse) if not ok_start else (s.book_id, s.chapter_end, s.verse_end)
                            issues.unmapped[bad] += 1
                            continue
                        rows.append(("TSK", entry.book_id, entry.chapter, entry.verse, s.book_id, s.chapter, s.verse,
                                     s.book_id, s.chapter_end, s.verse_end, None, keyword, ref.note, ref.native))
    db.executemany(
        "INSERT INTO xref (dataset, from_book, from_chapter, from_verse, to_book, to_chapter, to_verse, "
        "to_book_end, to_chapter_end, to_verse_end, votes, keyword, note, native) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", rows)
    return len(rows), issues


# ---- TVTMS cross-check ------------------------------------------------------------------------------

@dataclass
class TvtmsCheck:
    compared: int
    agree: int
    disagree: list[tuple[Key, Key, list[Key]]]  # (KJV verse, TVTMS Hebrew, our Hebrew verses)
    ours_not_in_tvtms: list[tuple[Key, Key]]     # our renumberings TVTMS doesn't list


def _tvtms_check(path, ours: set[tuple[Key, Key]], kjv: Kjv) -> TvtmsCheck:
    theirs = {(e, h) for e, h in tvtms_pairs(path) if e[0] <= 39 and kjv.has(*e)}
    ours_by_eng: dict[Key, set[Key]] = defaultdict(set)
    for e, h in ours:
        ours_by_eng[e].add(h)
    agree, disagree = 0, []
    for e, h in sorted(theirs):
        if h in ours_by_eng.get(e, ()):
            agree += 1
        elif e in ours_by_eng:
            disagree.append((e, h, sorted(ours_by_eng[e])))
    their_set = theirs
    renumbered = sorted((e, h) for e, h in ours if e != h and (e, h) not in their_set)
    return TvtmsCheck(len(theirs), agree, disagree, renumbered)


# ---- Report -----------------------------------------------------------------------------------------

KIND_ORDER = ["title", "renumbered", "split", "merged", "source_only", "kjv_only"]
KIND_HELP = {
    "title": "Hebrew verse(s) that are the Psalm title in KJV (verse 0 = the app's chapter superscription)",
    "renumbered": "whole verse maps one-to-one to a KJV verse with a different number",
    "split": "one source verse's words land in more than one KJV verse",
    "merged": "one KJV verse receives words from more than one source verse",
    "source_only": "source verse with no KJV verse (words kept with NULL chapter/verse)",
    "kjv_only": "KJV verse with no source words",
}


def _runs(rows: list[MapRow]) -> list[str]:
    """Compress consecutive rows (same book/chapters, both sides +1) into ranges."""
    out: list[str] = []
    run: list[MapRow] = []

    def flush():
        if not run:
            return
        a, b = run[0], run[-1]
        native = label(a.native) if a is b else f"{label(a.native)}–{b.native[2]}"
        if a.kjv is None:
            target = "no KJV verse"
        else:
            target = label(a.kjv) if a is b else f"{label(a.kjv)}–{b.kjv[2]}"
        words = sum(r.words or 0 for r in run)
        extra = f" ({words} words)" if a.kind in ("split", "merged", "source_only") else ""
        note = f" — {a.note}" if a.note and a is b else ""
        out.append(f"- {native} → {target}{extra}{note}")
        run.clear()

    for r in rows:
        if run:
            p = run[-1]
            consecutive = (
                r.native[:2] == p.native[:2] and r.native[2] == p.native[2] + 1
                and r.kjv is not None and p.kjv is not None and r.kjv[:2] == p.kjv[:2] and r.kjv[2] == p.kjv[2] + 1
                and r.kind == p.kind and not r.note and not p.note
            )
            if not consecutive:
                flush()
        run.append(r)
    flush()
    return out


def _write_report(rows: list[MapRow], ob: XrefIssues, tsk: XrefIssues, check: TvtmsCheck, stats: dict) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    issues = [r for r in rows if r.kind != "identical"]
    with open(OUT / "versification_issues.csv", "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["dataset", "kind", "native_ref", "kjv_ref", "words", "note"])
        for r in issues:
            w.writerow([r.dataset, r.kind, label(r.native), label(r.kjv), r.words, r.note])
        for dataset, x in (("OpenBible", ob), ("TSK", tsk)):
            for (eng, k), n in sorted(x.remapped.items()):
                w.writerow([dataset, "renumbered", label(eng), label(k), n, "cross reference endpoint moved"])
            for key, n in sorted(x.unmapped.items()):
                w.writerow([dataset, "source_only", label(key), "-", n, "cross reference dropped"])
            for here, text in x.unparsed:
                w.writerow([dataset, "unparsed", here, "-", "", text])

    counts = Counter((r.dataset, r.kind) for r in rows)
    lines = [
        "# Versification report",
        "",
        f"Generated {date.today().isoformat()} by `python -m pipeline build`. Target: the app's KJV verse list "
        "(31,102 verses; Psalm titles are chapter superscriptions, written here as verse `title`).",
        "Every row below is also in `versification_issues.csv`, one row per verse.",
        "",
        "## Summary",
        "",
        "| Dataset | " + " | ".join(["identical", *KIND_ORDER]) + " |",
        "|---|" + "---|" * (len(KIND_ORDER) + 1),
    ]
    for ds in ("TAHOT", "TAGNT"):
        lines.append(f"| {ds} | " + " | ".join(str(counts[(ds, k)]) for k in ["identical", *KIND_ORDER]) + " |")
    lines += [
        "",
        "Counts are (source verse → KJV verse) pairs. Kinds:",
        *[f"- **{k}**: {v}" for k, v in KIND_HELP.items()],
        "",
        f"Words: TAHOT {stats['words_tahot']:,}, TAGNT {stats['words_tagnt']:,}; "
        f"without a KJV verse: TAHOT {stats['unmapped_words'][0]}, TAGNT {stats['unmapped_words'][1]}. "
        f"Lexicon entries: TBESH {stats['lexicon'][0]:,}, TBESG {stats['lexicon'][1]:,}; "
        f"distinct word Strong's numbers missing from the lexicons: {len(stats['lexicon_misses'])} (listed at the end). "
        f"Cross references kept: OpenBible {stats['xrefs'][0]:,}, TSK {stats['xrefs'][1]:,}.",
        "",
    ]
    for ds in ("TAHOT", "TAGNT"):
        native = "Hebrew" if ds == "TAHOT" else "NRSV"
        for kind in KIND_ORDER:
            subset = [r for r in rows if r.dataset == ds and r.kind == kind]
            if not subset:
                continue
            lines += [f"## {ds}: {kind} ({len(subset)})", "", f"{native} verse → KJV verse.", "", *_runs(subset), ""]

    for ds, x in (("OpenBible", ob), ("TSK", tsk)):
        lines += [f"## {ds} cross references", ""]
        if x.remapped:
            lines += [f"Endpoints moved to a KJV verse ({sum(x.remapped.values())} references, "
                      f"{len(x.remapped)} verses):", ""]
            lines += [f"- {label(e)} → {label(k)} ({n})" for (e, k), n in sorted(x.remapped.items())] + [""]
        if x.unmapped:
            lines += [f"References dropped because the verse isn't in KJV ({sum(x.unmapped.values())} references, "
                      f"{len(x.unmapped)} verses):", ""]
            lines += [f"- {label(k)} ({n})" for k, n in sorted(x.unmapped.items())] + [""]
        if x.unparsed:
            lines += [f"Unparsed reference text ({len(x.unparsed)}), left out:", ""]
            lines += [f"- at {here}: `{text}`" for here, text in x.unparsed] + [""]
        if not (x.remapped or x.unmapped or x.unparsed):
            lines += ["Every reference mapped as-is.", ""]

    lines += [
        "## Cross-check against STEPBible TVTMS (OT, English KJV ↔ Hebrew)",
        "",
        f"Compared {check.compared:,} verse pairs from TVTMS rows whose English-KJV and Hebrew cells expand to equal-"
        f"length single-chapter ranges. Agree: {check.agree:,}. Disagree: {len(check.disagree)}.",
        "",
    ]
    if check.disagree:
        lines += ["KJV verse: TVTMS Hebrew vs TAHOT Hebrew", ""]
        lines += [f"- {label(e)}: TVTMS {label(h)} vs TAHOT {', '.join(label(o) for o in ours)}"
                  for e, h, ours in check.disagree] + [""]
    lines += [
        f"TAHOT renumberings (English ≠ Hebrew) that this check couldn't compare: {len(check.ours_not_in_tvtms)}. "
        "TVTMS covers them in cross-chapter, unequal-length or merged/split rows, which the check skips; they are "
        "listed here (KJV ← Hebrew) for manual review:",
        "",
        *[f"- {label(e)} ← {label(h)}" for e, h in check.ours_not_in_tvtms],
        "",
        "## Strong's numbers used by words but missing from TBESH/TBESG",
        "",
        "Words keep the number; the app should fall back to the word's own lemma and gloss for these.",
        "",
        *[f"- {code} ({n} words)" for code, n in stats["lexicon_misses"]],
        "",
    ]
    (OUT / "versification_report.md").write_text("\n".join(lines))
    print(f"Wrote {OUT / 'versification_report.md'} and versification_issues.csv ({len(issues)} rows)")
