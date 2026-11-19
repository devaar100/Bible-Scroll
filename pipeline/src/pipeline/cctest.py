"""
Claude Code test rounds: studies written by Claude Code subagents (study-writer, study-reviewer in .claude/agents/)
instead of the API. TEST ARTIFACTS ONLY: they live in out/cc_test/roundN/, carry meta.source
"claude-code-subagent", and publish/bundle refuse them.

  cctest merge --round N --job jobs/cc_roundN ID REPLY_FILE --model M [--attempt K]
        one writer reply (the JSON object with the six prose fields) -> out/cc_test/roundN/ID.json. Invalid JSON
        is a format failure: logged in out/cc_test/roundN/attempts.json, exit 2, nothing written.
  cctest packet --round N IDS... [--from DIR]
        the reviewer input for each study (study + WEB/BSB text, context verses, cleaned word data, cross
        references, book block, text notes), printed for the review call.
  cctest retry --round N IDS...
        review/retry-K.txt: regenerated studies under their blind labels, for one more review call.
  cctest verify --round N FINDINGS_FILE
        reviewer findings (JSON) -> every studyQuote checked verbatim against its study; unverified ones are
        discarded and counted. Writes out/cc_test/roundN/review.json.
  cctest report --round N
        the round table: attempts, code checks, reviewer ERROR/WARN/NOTE counts, length bands.

Nothing here calls an API. Never edit a generated study by hand: change prompts/checks/code and regenerate.
"""
from __future__ import annotations

import json
import re
from collections import Counter
from pathlib import Path

from . import context as ctx
from . import generate as g
from .books import BOOKS
from .paths import APP_BIBLE_DB, OUT
from .publish import CC_TEST_PART, CC_TEST_SOURCE
from .store import StudyStore, now, parse_id
from .studies import PROSE_FIELDS

CC_DIR = OUT / CC_TEST_PART
SEVERITIES = ("ERROR", "WARN", "NOTE")
CATEGORIES = ("speaker-or-attribution", "phrase-not-in-verse", "context-claim", "word-data",
              "cross-reference-accuracy", "factual-claim", "one-sided-theology", "generic-filler",
              "grammar-overclaim", "invented-image", "tone", "leak", "numbered-verse",
              "did-you-know-scope")
TITLES = {"meaning": "Meaning", "historicalContext": "Historical context", "lifeInBibleTimes": "Life in Bible times",
          "theology": "Theology", "didYouKnow": "Did you know?", "applyIt": "Apply it"}


def round_dir(n: int) -> Path:
    path = CC_DIR / f"round{n}"
    if CC_TEST_PART not in path.resolve().parts:  # never anywhere else
        raise SystemExit(f"{path} isn't under {CC_TEST_PART}/")
    return path


def _attempts_file(n: int) -> Path:
    return round_dir(n) / "attempts.json"


def load_attempts(n: int) -> dict:
    f = _attempts_file(n)
    return json.loads(f.read_text()) if f.exists() else {}


def log_attempt(n: int, study_id: str, entry: dict) -> None:
    data = load_attempts(n)
    data.setdefault(study_id, []).append({**entry, "at": now()})
    f = _attempts_file(n)
    f.parent.mkdir(parents=True, exist_ok=True)
    f.write_text(json.dumps(data, indent=2) + "\n")


# ---- Merge ----------------------------------------------------------------------------------------------

def extract_json(reply: str) -> str:
    """The reply should be exactly one JSON object; a ```json fence around it is tolerated, nothing else."""
    text = reply.strip()
    m = re.fullmatch(r"```(?:json)?\s*(\{.*\})\s*```", text, re.DOTALL)
    return m.group(1) if m else text


def merge(n: int, job: Path, study_id: str, reply: str, model: str, attempt: int,
          source: StudyStore | None = None) -> dict:
    from .plans.ai import DraftFailed

    manifest = json.loads((job / "manifest.json").read_text())
    if study_id not in manifest["ids"]:
        raise SystemExit(f"{study_id} isn't in {job}")
    try:
        sections = g.parse_sections(extract_json(reply))
    except DraftFailed as error:
        log_attempt(n, study_id, {"attempt": attempt, "result": "format_failure", "why": str(error)})
        raise SystemExit(2) from error
    source = source or StudyStore(g.STUDIES_DIR)
    study = json.loads(json.dumps(source.load(study_id)))  # data sections from the built study, never edited
    study.update(sections)
    study["status"] = "generated"
    study["meta"] = {"model": model, "effort": "medium", "generatedAt": now(), "source": CC_TEST_SOURCE,
                     "round": n, "attempts": attempt, "promptVersion": manifest["promptVersion"],
                     "promptHash": manifest["systemHash"], "booksHash": manifest["booksHash"]}
    for k in ("checks", "review", "reviewedBy", "reviewedAt"):
        study.pop(k, None)
    target = StudyStore(round_dir(n))
    target.directory.mkdir(parents=True, exist_ok=True)
    target.save(study)
    log_attempt(n, study_id, {"attempt": attempt, "result": "written", "model": model})
    return study


# ---- Reviewer packet ------------------------------------------------------------------------------------

def _verse(bible_db: Path, translation: str, key) -> str:
    rows = g._verse_rows(bible_db, translation, key[0], key[1], key[2], key[2])
    return rows[0][1] if rows else "(not in this translation)"


def packet(study: dict, bible_db: Path = APP_BIBLE_DB, prefix: str = "", label: str | None = None) -> str:
    """prefix: "canary:" for canary studies, so the reviewer returns ids that can't be confused with this round's.
    label: an opaque name ("S07") shown instead of the id, for blind review (see blind())."""
    key = parse_id(study["id"])
    lang = study["originalLanguage"]
    greek = lang.get("language") == "greek"
    heading, context_text = g.context_block(key, bible_db)
    words = "\n".join(f"- {ctx.clean_translit(w['transliteration'], greek)} ({w['surface']}) = {w['gloss']} "
                      f"[lemma {w['lemma']}, {w['morphology']}]" for w in lang["words"]) or "(none listed)"
    refs = "\n".join(f"- {g.ref_label(x['ref'])}" for x in study["crossReferences"]) or "(none)"
    usfm = BOOKS[key[0] - 1].usfm
    prose = "\n\n".join(f"## {TITLES[k]}\n{study.get(k, '')}" for k in PROSE_FIELDS)
    name = label or f"{prefix}{study['id']}"
    return (f"=== STUDY {name} ({g.verse_label(key)}) ===\n\n"
            f"--- The study ---\n{prose}\n\n"
            f"--- Supplied text ---\n"
            f"World English Bible: {_verse(bible_db, 'WEB', key)}\n"
            f"Berean Standard Bible: {_verse(bible_db, 'BSB', key)}\n"
            f"King James Version: {_verse(bible_db, 'KJV', key)}\n\n"
            f"{heading}\n{context_text}\n\n"
            f"Original words in this verse (transliteration (original) = in-context gloss [lemma, morphology]):\n"
            f"{words}\n\n"
            f"Cross references (the only passages the study may cite by reference, besides this verse):\n{refs}\n\n"
            f"Book block:\n{ctx.book_background(usfm) or '(none)'}\n\n"
            f"Text notes for this verse:\n{ctx.text_note(key) or '(none)'}\n"
            f"=== END STUDY {name} ===")


# ---- Blind review batches ------------------------------------------------------------------------------

def blind_batches(entries: list[tuple[str, str]], size: int, seed: int) -> list[list[tuple[str, str]]]:
    """entries: (source, id) pairs, e.g. ("round", "SNG.8.6"), ("canary", "SNG.8.6"). Shuffled with [seed] and dealt
    into batches of at most [size] so that no batch holds the same verse twice (a canary never sits next to this
    round's study of its verse). Deterministic for a given seed."""
    import random

    order = list(entries)
    random.Random(seed).shuffle(order)
    count = -(-len(order) // size)
    batches: list[list[tuple[str, str]]] = [[] for _ in range(count)]
    for entry in order:
        fits = [b for b in batches if len(b) < size and all(e[1] != entry[1] for e in b)]
        if not fits:
            batches.append([entry])
            continue
        min(fits, key=len).append(entry)
    return [b for b in batches if b]


def blind(n: int, canary_dir: Path | None, size: int = 5, seed: int = 0, bible_db: Path = APP_BIBLE_DB) -> dict:
    """Write out/cc_test/roundN/review/batch-K.txt with every study under an opaque label (S01, S02, ...; label order
    is shuffled too) and review/labels.json mapping label -> {source, id}. Canary studies come from [canary_dir]
    (read only) and are not marked in the packets."""
    import random

    rd = round_dir(n)
    store = StudyStore(rd)
    entries = [("round", i) for i in store.ids() if store.load(i).get("meta", {}).get("source") == CC_TEST_SOURCE]
    canary = StudyStore(canary_dir) if canary_dir else None
    if canary:
        entries += [("canary", i) for i in canary.ids()]
    batches = blind_batches(entries, size, seed)
    flat = [e for b in batches for e in b]
    names = [f"S{k:02d}" for k in range(1, len(flat) + 1)]
    random.Random(seed + 1).shuffle(names)
    label_of = dict(zip(flat, names))
    out = rd / "review"
    out.mkdir(exist_ok=True)
    labels = {}
    for k, batch in enumerate(batches, 1):
        parts = []
        for src, sid in batch:
            study = (canary if src == "canary" else store).load(sid)
            labels[label_of[(src, sid)]] = {"source": src, "id": sid, "batch": k}
            parts.append(packet(study, bible_db, label=label_of[(src, sid)]))
        (out / f"batch-{k}.txt").write_text("\n\n".join(parts) + "\n")
    (out / "labels.json").write_text(json.dumps({"seed": seed, "size": size, "canaryDir": str(canary_dir or ""),
                                                 "labels": dict(sorted(labels.items()))}, indent=2) + "\n")
    return labels


def retry_packet(n: int, ids: list[str], bible_db: Path = APP_BIBLE_DB) -> Path:
    """Write review/retry-K.txt: the regenerated round studies [ids] under the labels they had in the blind
    batches. Pass its reply to verify after the batch replies: a later review of a label replaces the earlier."""
    rd = round_dir(n)
    meta = json.loads((rd / "review" / "labels.json").read_text())
    label_of = {v["id"]: k for k, v in meta["labels"].items() if v["source"] == "round"}
    missing = [i for i in ids if i not in label_of]
    if missing:
        raise SystemExit(f"Not in this round's blind batches: {', '.join(missing)}")
    store = StudyStore(rd)
    k = 1 + len(list((rd / "review").glob("retry-*.txt")))
    out = rd / "review" / f"retry-{k}.txt"
    out.write_text("\n\n".join(packet(store.load(i), bible_db, label=label_of[i]) for i in ids) + "\n")
    return out


# ---- Reviewer findings ----------------------------------------------------------------------------------

def _norm(text: str) -> str:
    text = text.translate(str.maketrans({"“": '"', "”": '"', "‘": "'", "’": "'", "—": "-", "–": "-"}))
    return re.sub(r"\s+", " ", text).strip()


def section_key(section: str) -> str | None:
    s = section.strip().lower().rstrip("?").replace("did you know", "didyouknow")
    for k, title in TITLES.items():
        if s in (k.lower(), title.lower().rstrip("?").replace("did you know", "didyouknow")):
            return k
    return None


def quote_in_study(study: dict, section: str | None, quote: str) -> bool:
    """studyQuote must appear verbatim (quotes/dashes/whitespace normalised) in the named section, or anywhere in
    the study when the section can't be matched."""
    q = _norm(quote)
    if not q:
        return False
    key = section_key(section or "")
    texts = [study.get(key, "")] if key else [study.get(k, "") for k in PROSE_FIELDS]
    return any(q in _norm(t) for t in texts)


def verify(n: int, findings: dict, studies: dict[str, dict]) -> dict:
    """findings: {"studies": [{"id", "didYouKnowAboutThisVerse": bool, "didYouKnowNote", "findings": [...]}]}.
    Returns the verified review with discarded findings counted (never silently dropped)."""
    kept, discarded = {}, []
    dyk = {}
    for entry in findings["studies"]:
        sid = entry["id"]
        study = studies.get(sid)
        if study is None:
            raise SystemExit(f"Reviewer returned an unknown study id {sid}")
        if sid in dyk:  # a later review of the same study (a retry) replaces the earlier one
            kept.pop(sid, None)
            discarded = [d for d in discarded if d["id"] != sid]
        dyk[sid] = {"about": entry.get("didYouKnowAboutThisVerse"), "note": entry.get("didYouKnowNote", "")}
        for f in entry.get("findings", []):
            bad = [k for k in ("section", "severity", "category", "studyQuote", "supportQuote", "explanation")
                   if k not in f]
            if bad or f["severity"] not in SEVERITIES or f["category"] not in CATEGORIES:
                discarded.append({"id": sid, "why": f"malformed ({bad or f.get('severity') or f.get('category')})",
                                  "finding": f})
            elif not quote_in_study(study, f["section"], f["studyQuote"]):
                discarded.append({"id": sid, "why": "studyQuote not verbatim in the study", "finding": f})
            else:
                kept.setdefault(sid, []).append(f)
    return {"round": n, "verifiedAt": now(), "findings": kept, "discarded": discarded, "didYouKnow": dyk}


# ---- Round report ---------------------------------------------------------------------------------------

def report(n: int, canaries: tuple[str, ...] = ()) -> str:
    rd = round_dir(n)
    replay = json.loads((rd / "replay.json").read_text()) if (rd / "replay.json").exists() else {"results": {}}
    review = json.loads((rd / "review.json").read_text()) if (rd / "review.json").exists() else {}
    attempts = load_attempts(n)
    from .checks import Rules, band
    rules = Rules.load()
    rows = ["| verse | attempts | format fails | code checks | ERROR | WARN | NOTE | DYK on own verse | over range, allowed | over limit / under |",
            "|---|---|---|---|---|---|---|---|---|---|"]
    store = StudyStore(rd)
    labels = review.get("labels", {})
    key_of = {(v["source"], v["id"]): k for k, v in labels.items()}  # blind review: findings are keyed by label
    for sid in store.ids():
        st = store.load(sid)
        if st.get("meta", {}).get("source") != CC_TEST_SOURCE:
            continue
        rk = key_of.get(("round", sid), sid)
        att = attempts.get(sid, [])
        fmt = sum(1 for a in att if a["result"] == "format_failure")
        written = sum(1 for a in att if a["result"] == "written")
        res = replay["results"].get(sid)
        code = "-" if res is None else ("pass" if res["passed"] else
                                         "REJECT: " + ", ".join(sorted({x["check"] for x in res["reasons"]})))
        sev = Counter(f["severity"] for f in review.get("findings", {}).get(rk, []))
        bands = [band(len(st.get(f, "").split()), rules.limits[f], rules.grace) for f in PROSE_FIELDS]
        over = [f for f, b in zip(PROSE_FIELDS, bands) if b == "over range, allowed"]
        bad = [f for f, b in zip(PROSE_FIELDS, bands) if b in ("over limit", "under range")]
        d = review.get("didYouKnow", {}).get(rk, {}).get("about")
        rows.append(f"| {sid} | {written} | {fmt} | {code} | {sev['ERROR']} | {sev['WARN']} | {sev['NOTE']} | "
                    f"{'-' if d is None else ('yes' if d else 'NO')} | {', '.join(over) or '-'} | {', '.join(bad) or '-'} |")
    canary_rows = []
    for (src, cid), k in sorted(key_of.items(), key=lambda x: x[0][1]):
        if src != "canary":
            continue
        fs = review.get("findings", {}).get(k, [])
        sev = Counter(f["severity"] for f in fs)
        d = review.get("didYouKnow", {}).get(k, {}).get("about")
        canary_rows.append(f"| {cid} ({k}) | {'FLAGGED' if sev['ERROR'] else 'not flagged'} | {sev['ERROR']} | "
                           f"{sev['WARN']} | {sev['NOTE']} | {'-' if d is None else ('yes' if d else 'NO')} | "
                           f"{'; '.join(sorted({f['category'] for f in fs})) or '-'} |")
    if canary_rows:
        rows += ["", "| canary (label) | ERROR flag | ERROR | WARN | NOTE | DYK on own verse | categories |",
                 "|---|---|---|---|---|---|---|", *canary_rows]
    return "\n".join(rows)
