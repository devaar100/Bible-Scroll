"""
Auto-checker: run every check on a written study and set status to auto_checked or flagged, with reasons.

Free checks (always): schema, section lengths, Bible references, original-language mentions, banned
phrases. The AI rubric (rubric.py) runs only when those pass, unless rubric_always is set, so money is
spent only on studies that could otherwise pass.
"""
from __future__ import annotations

import json
import re
import unicodedata
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

from jsonschema import Draft202012Validator

from .paths import PIPELINE
from .prose_refs import check_reference, find_references
from .store import Bible, StudyStore, is_written, now, parse_id

CHECKS_DIR = PIPELINE / "checks"
CHECKER_VERSION = "4"  # 4: allowed set = the verses shown (context.py) + cross refs; verse-position wording;
# dotted transliterations
# 3: allowed-reference set, 20% length tolerance, pipeline wording, negated banned phrases
# 2: English words after "Greek word" aren't treated as transliterations
PROSE_FIELDS = ("meaning", "historicalContext", "lifeInBibleTimes", "theology", "didYouKnow", "applyIt")
#: Studies pass a section up to this share over its maximum; above it (or under the minimum) they are flagged.
LENGTH_TOLERANCE = 0.2


def _load_phrases(path: Path) -> list[tuple[str, re.Pattern]]:
    """One phrase per line (case-insensitive, whole words); "re:" lines are regular expressions."""
    out = []
    if not path.exists():
        return out
    for line in path.read_text().splitlines():
        phrase = line.strip()
        if not phrase or phrase.startswith("#"):
            continue
        if phrase.startswith("re:"):
            out.append((phrase, re.compile(phrase[3:].strip(), re.IGNORECASE)))
        else:
            words = r"\s+".join(re.escape(w) for w in phrase.split())
            out.append((phrase, re.compile(rf"(?<!\w){words}(?!\w)", re.IGNORECASE)))
    return out


@dataclass
class Rules:
    limits: dict[str, dict[str, int]]
    banned: list[tuple[str, re.Pattern]]
    pipeline_wording: list[tuple[str, re.Pattern]] = field(default_factory=list)

    @classmethod
    def load(cls, directory: Path = CHECKS_DIR, limits_file: str = "limits.json",
             fields: tuple[str, ...] = PROSE_FIELDS) -> "Rules":
        limits = json.loads((directory / limits_file).read_text())["words"]
        missing = set(fields) - set(limits)
        if missing:
            raise ValueError(f"{limits_file} has no limits for {sorted(missing)}")
        return cls(limits, _load_phrases(directory / "banned.txt"),
                   _load_phrases(directory / "pipeline_wording.txt"))


def reason(check: str, message: str, section: str | None = None) -> dict:
    r = {"check": check, "message": message}
    if section:
        r["section"] = section
    return r


# ---- Individual checks ------------------------------------------------------------------------------

def check_schema(study: dict, validator: Draft202012Validator) -> list[dict]:
    return [reason("schema", f"{e.json_path}: {e.message}") for e in validator.iter_errors(study)]


def check_lengths(study: dict, rules: Rules, fields: tuple[str, ...] = PROSE_FIELDS,
                  optional: tuple[str, ...] = (), tolerance: float = 0.0) -> list[dict]:
    """Flags a section under its minimum or more than [tolerance] (a share) over its maximum."""
    out = []
    for section in fields:
        if section in optional and section not in study:
            continue
        words = len(study.get(section, "").split())
        lo, hi = rules.limits[section]["min"], rules.limits[section]["max"]
        if words < lo or words > hi * (1 + tolerance):
            allowed = f"{lo}-{hi}" + (f" (+{tolerance:.0%} tolerance)" if tolerance else "")
            out.append(reason("length", f"{words} words; allowed {allowed}", section))
    return out


def length_band(words: int, hi: int) -> str | None:
    """Which over-length band a section is in, for run summaries; None if within its maximum."""
    if words <= hi:
        return None
    over = (words - hi) / hi
    return "up to 20% over" if over <= 0.2 else ("20-50% over" if over <= 0.5 else "more than 50% over")


def check_references(study: dict, bible: Bible, fields: tuple[str, ...] = PROSE_FIELDS,
                     here: tuple[int, int, int] | None = None) -> list[dict]:
    """here: the key that relative references ("v. 17") resolve against; a study's own verse by default."""
    here = here or parse_id(study["id"])
    out = []
    for section in fields:
        for ref in find_references(study.get(section, ""), here):
            problem = check_reference(ref, bible.verses, bible.chapters)
            if problem:
                out.append(reason("reference", f"“{ref.text}”: {problem}", section))
    return out


def _expand(book: int, start: tuple[int, int | None], end: tuple[int, int | None],
            verses: frozenset) -> set[tuple[int, int, int]]:
    """Every KJV verse key from start to end (a None verse = the whole chapter)."""
    lo = (start[0], start[1] if start[1] is not None else 0)
    hi = (end[0], end[1] if end[1] is not None else 10**6)
    return {k for k in verses if k[0] == book and lo <= (k[1], k[2]) <= hi}


def allowed_verses(study: dict, bible: Bible) -> set[tuple[int, int, int]]:
    """The verses the request showed (the verse and its context, context.context_keys) and every listed
    cross reference."""
    from .context import context_keys

    key = parse_id(study["id"])
    allowed = set(context_keys(key, (k for k in bible.verses if k[0] == key[0]))) | {key}
    for x in study.get("crossReferences", []):
        first, _, last = x["ref"].partition("-")
        fb, fc, fv = parse_id(first)
        lb, lc, lv = parse_id(last) if last else (fb, fc, fv)
        allowed |= _expand(fb, (fc, fv), (lc, lv), bible.verses)
    return allowed


def check_allowed_references(study: dict, bible: Bible, fields: tuple[str, ...] = PROSE_FIELDS) -> list[dict]:
    """
    A reference must stay inside the allowed set: the verse, the context verses the request showed, and the
    listed cross references. A whole-chapter reference passes if it is the verse's own chapter or contains a
    listed cross reference. References that don't exist are reported by check_references instead.
    """
    here = parse_id(study["id"])
    allowed = allowed_verses(study, bible)
    out = []
    for section in fields:
        for ref in find_references(study.get(section, ""), here):
            if check_reference(ref, bible.verses, bible.chapters):
                continue
            span = _expand(ref.book_id, ref.start, ref.end, bible.verses)
            whole_chapters = ref.start[1] is None
            ok = (span & allowed) if whole_chapters else span <= allowed
            if not ok:
                out.append(reason("reference_not_allowed",
                                  f"“{ref.text}” isn't a verse shown with this one or a listed cross reference",
                                  section))
    return out


# Strong's: H/G + 1-5 digits + optional letter. Hebrew/Greek: runs of letters in those scripts.
_STRONGS = re.compile(r"(?<![\w])([HG])0*(\d{1,5})[A-Za-z]?(?![\w])")
_HEBREW = re.compile(r"[א-ת][֑-ׇא-תװ-״]*")
_GREEK = re.compile(r"[Ͱ-Ͽἀ-῿]+")
_TRANSLIT_MENTION = re.compile(
    r"\b(?:Hebrew|Greek|Aramaic)\s+(?:word|term|verb|noun|adjective|phrase|root)\s+"
    r"[*_\"“‘'(]*(?P<word>[A-Za-zÀ-ɏḀ-ỿʼʾʿ'’.\-]{2,})"
)
# English words that follow "Greek word"/"Hebrew term" etc. in ordinary prose ("the Greek word for joy",
# "the Greek word behind 'joy'", "the Greek word translated ..."). They aren't transliterations, so skip them.
_NOT_TRANSLIT = frozenset("""
    for behind translated rendered here there means meant meaning that this these those which points is was
    are were be can could may might also used appears comes occurs itself again often usually only and or as
    in of to with from at by on into like than but so twice once
""".split())
_FINALS = str.maketrans("ךםןףץ", "כמנפצ")


def _bare(text: str) -> str:
    """Strip vowel points, accents and breathings; normalise final letters and case."""
    decomposed = unicodedata.normalize("NFD", text)
    letters = "".join(ch for ch in decomposed if not unicodedata.combining(ch) and ch not in "־׀׃׳״")
    return letters.translate(_FINALS).replace("ς", "σ").lower()


def _translit_key(text: str) -> str:
    """'be.re.Shit', 'bereshit', 'agapaō' -> comparable lowercase ASCII without separators."""
    return re.sub(r"[^a-z]", "", _bare(text))


def _strongs_base(code: str) -> str:
    m = re.match(r"([HG])0*(\d+)", code)
    return f"{m.group(1)}{int(m.group(2))}" if m else code


def check_original_language(study: dict, bible: Bible, fields: tuple[str, ...] = PROSE_FIELDS,
                            words: list[dict] | None = None, where: str = "this verse") -> list[dict]:
    """words: the passage's words (strongs, surface, lemma, transliteration); a study's own by default."""
    words = study["originalLanguage"]["words"] if words is None else words
    strongs = {_strongs_base(w["strongs"]) for w in words}
    forms = set()
    for w in words:
        forms.add(_bare(w["surface"]))
        forms.update(_bare(part) for part in re.split(r"[,;]\s*", w["lemma"]))
    forms.discard("")
    translits = {_translit_key(w["transliteration"]) for w in words}
    translits.update(_translit_key(bible.lexicon_translit.get(w["strongs"], "")) for w in words)
    translits.discard("")

    out = []
    for section in fields:
        prose = study.get(section, "")
        for m in _STRONGS.finditer(prose):
            if f"{m.group(1)}{int(m.group(2))}" not in strongs:
                out.append(reason("original_language", f"Strong's {m.group(0)} isn't in {where}", section))
        for m in [*_HEBREW.finditer(prose), *_GREEK.finditer(prose)]:
            mention = _bare(m.group(0))
            if len(mention) >= 2 and not any(mention == f or mention in f for f in forms):
                out.append(reason("original_language", f"“{m.group(0)}” isn't a word in {where}", section))
        for m in _TRANSLIT_MENTION.finditer(prose):
            if m["word"].lower() in _NOT_TRANSLIT:
                continue
            key = _translit_key(m["word"])
            ok = any(key == t or (min(len(key), len(t)) >= 4 and (t.startswith(key) or key.startswith(t)))
                     for t in translits)
            if key and not ok:
                out.append(reason("original_language", f"“{m['word']}” doesn't match a word in {where}", section))
    return out


_NEGATORS = re.compile(r"\b(?:not|no|never|nor|without)\b|n't\b|n’t\b", re.IGNORECASE)
NEGATION_WINDOW = 4  # words before a banned phrase, within the same clause


def negated(text: str, start: int) -> bool:
    """True if a negating word appears in the 4 words before [start], without crossing . , ; : ! ?"""
    clause = re.split(r"[.,;:!?]", text[:start])[-1]
    return bool(_NEGATORS.search(" ".join(clause.split()[-NEGATION_WINDOW:])))


def check_banned(study: dict, rules: Rules, fields: tuple[str, ...] = PROSE_FIELDS) -> list[dict]:
    """Banned phrases, except negated uses ("not for guaranteed success", "no right answer you must reach")."""
    out = []
    for section in fields:
        text = study.get(section, "")
        for phrase, pattern in rules.banned:
            m = next((m for m in pattern.finditer(text) if not negated(text, m.start())), None)
            if m:
                out.append(reason("banned_phrase", f"“{m.group(0)}” (rule: {phrase})", section))
    return out


def check_pipeline_wording(study: dict, rules: Rules, fields: tuple[str, ...] = PROSE_FIELDS) -> list[dict]:
    """Words about the generation input ("cross references", "supplied"...). Never excused by negation."""
    out = []
    for section in fields:
        for phrase, pattern in rules.pipeline_wording:
            m = pattern.search(study.get(section, ""))
            if m:
                out.append(reason("pipeline_wording", f"“{m.group(0)}” (rule: {phrase})", section))
    return out


# Nearby verses by number or position: "verse 5", "vv. 3-4", "the next verse", "two verses later".
_VERSE_POSITION = [
    re.compile(r"\b(?:verses?|vv?\.)\s*\d+", re.IGNORECASE),
    re.compile(r"\b(?:next|previous|following|preceding|prior|earlier|later|last|first|final|opening|closing)"
               r"\s+(?:(?:few|two|three|four|several)\s+)?verses?\b", re.IGNORECASE),
    re.compile(r"\bverses?\s+(?:before|after|earlier|later|above|below|back|ahead)\b", re.IGNORECASE),
]


def check_verse_position(study: dict, fields: tuple[str, ...] = PROSE_FIELDS) -> list[dict]:
    """Neighbouring verses must be described by what they say, never by number or position."""
    out = []
    for section in fields:
        text = study.get(section, "")
        for pattern in _VERSE_POSITION:
            m = pattern.search(text)
            if m:
                out.append(reason("verse_position", f"“{m.group(0)}”: describe the verse by what it says",
                                  section))
                break
    return out


_LETTERS = "A-Za-zÀ-ɏḀ-ỿ'’ʼʾʿ"
_DOTTED = re.compile(rf"(?<![{_LETTERS}.])[{_LETTERS}]+(?:\.[{_LETTERS}]+)+")


def check_dotted_translit(study: dict, fields: tuple[str, ...] = PROSE_FIELDS) -> list[dict]:
    """STEPBible syllable dots leaking into prose ("be.re.Shit", "ro.'I"). Skips e.g./i.e.-style abbreviations."""
    out = []
    for section in fields:
        for m in _DOTTED.finditer(study.get(section, "")):
            if all(len(part) <= 1 for part in m.group(0).split(".")):
                continue
            out.append(reason("dotted_translit", f"“{m.group(0)}”: write the word without syllable dots", section))
    return out


def free_checks(study: dict, bible: Bible, rules: Rules, validator: Draft202012Validator) -> list[dict]:
    reasons = check_schema(study, validator)
    if any(r["check"] == "schema" for r in reasons) and "originalLanguage" not in study:
        return reasons  # too broken to check further
    return [*reasons, *check_lengths(study, rules, tolerance=LENGTH_TOLERANCE), *check_references(study, bible),
            *check_allowed_references(study, bible), *check_original_language(study, bible),
            *check_banned(study, rules), *check_pipeline_wording(study, rules), *check_verse_position(study),
            *check_dotted_translit(study)]


# ---- Orchestration ----------------------------------------------------------------------------------

@dataclass
class Outcome:
    status: str               # auto_checked | flagged | unchanged (rubric unavailable)
    reasons: list[dict]
    rubric: dict | None = None
    error: str | None = None


def check_study(study: dict, bible: Bible, rules: Rules, validator: Draft202012Validator,
                judge=None, rubric_always: bool = False) -> Outcome:
    """
    Runs the checks on one study. judge: a rubric.Judge, or None to skip the AI pass.
    Never mutates [study]; apply() writes the outcome.
    """
    reasons = free_checks(study, bible, rules, validator)
    rubric = None
    if judge is not None and (rubric_always or not reasons):
        from .rubric import RubricUnavailable
        try:
            result = judge.judge(study, bible.text(parse_id(study["id"])) or "")
        except RubricUnavailable as error:
            # API down, rate limited, etc.: not the study's fault. Leave it for the next run.
            return Outcome("unchanged", reasons, None, str(error))
        rubric = result.rubric
        reasons += result.reasons
    return Outcome("flagged" if reasons else "auto_checked", reasons, rubric)


def apply(study: dict, outcome: Outcome, checker_version: str = CHECKER_VERSION) -> dict:
    """The study (or devotional) with the outcome written in (status + checks block). Reviewer reasons are kept."""
    updated = json.loads(json.dumps(study))
    from .reports import open_reports
    # Reviewer reasons stay; so do open `pipeline report` reasons, so a recheck can't clear a reported study.
    kept = [r for r in study.get("checks", {}).get("reasons", []) if r["check"] in ("reviewer", "regenerate")]
    kept += open_reports(study)
    checks = {"checkedAt": now(), "checkerVersion": checker_version, "reasons": [*kept, *outcome.reasons]}
    if outcome.rubric:
        checks["rubric"] = outcome.rubric
    updated["checks"] = checks
    updated["status"] = "flagged" if checks["reasons"] else "auto_checked"
    return updated


@dataclass
class RunSummary:
    checked: int = 0
    statuses: Counter = field(default_factory=Counter)
    reasons: Counter = field(default_factory=Counter)
    skipped_not_written: int = 0
    skipped_already_done: int = 0
    rubric_unavailable: list[str] = field(default_factory=list)


def run(store: StudyStore, bible: Bible, judge=None, *, recheck: bool = False, rubric_always: bool = False,
        ids: list[str] | None = None, limit: int | None = None, rules: Rules | None = None) -> RunSummary:
    """Check written studies with status 'generated' (or, with recheck, also auto_checked/flagged)."""
    from .studies import validator as make_validator

    rules = rules or Rules.load()
    validator = make_validator()
    wanted = {"generated", "auto_checked", "flagged"} if recheck else {"generated"}
    summary = RunSummary()
    for study_id in ids or store.ids():
        if limit is not None and summary.checked >= limit:
            break
        study = store.load(study_id)
        if not is_written(study):
            summary.skipped_not_written += 1
            continue
        if study.get("status") not in wanted:
            summary.skipped_already_done += 1
            continue
        outcome = check_study(study, bible, rules, validator, judge, rubric_always)
        if outcome.status == "unchanged":
            summary.rubric_unavailable.append(f"{study_id}: {outcome.error}")
            continue
        updated = apply(study, outcome)
        store.save(updated)
        summary.checked += 1
        summary.statuses[updated["status"]] += 1
        summary.reasons.update(r["check"] for r in updated["checks"]["reasons"])
    return summary
