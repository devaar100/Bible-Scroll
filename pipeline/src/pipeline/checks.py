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
CHECKER_VERSION = "1"
PROSE_FIELDS = ("meaning", "historicalContext", "lifeInBibleTimes", "theology", "didYouKnow", "applyIt")


@dataclass
class Rules:
    limits: dict[str, dict[str, int]]
    banned: list[tuple[str, re.Pattern]]

    @classmethod
    def load(cls, directory: Path = CHECKS_DIR) -> "Rules":
        limits = json.loads((directory / "limits.json").read_text())["words"]
        missing = set(PROSE_FIELDS) - set(limits)
        if missing:
            raise ValueError(f"limits.json has no limits for {sorted(missing)}")
        banned = []
        for line in (directory / "banned.txt").read_text().splitlines():
            phrase = line.strip()
            if not phrase or phrase.startswith("#"):
                continue
            if phrase.startswith("re:"):
                banned.append((phrase, re.compile(phrase[3:].strip(), re.IGNORECASE)))
            else:
                words = r"\s+".join(re.escape(w) for w in phrase.split())
                banned.append((phrase, re.compile(rf"(?<!\w){words}(?!\w)", re.IGNORECASE)))
        return cls(limits, banned)


def reason(check: str, message: str, section: str | None = None) -> dict:
    r = {"check": check, "message": message}
    if section:
        r["section"] = section
    return r


# ---- Individual checks ------------------------------------------------------------------------------

def check_schema(study: dict, validator: Draft202012Validator) -> list[dict]:
    return [reason("schema", f"{e.json_path}: {e.message}") for e in validator.iter_errors(study)]


def check_lengths(study: dict, rules: Rules) -> list[dict]:
    out = []
    for section in PROSE_FIELDS:
        words = len(study.get(section, "").split())
        lo, hi = rules.limits[section]["min"], rules.limits[section]["max"]
        if not lo <= words <= hi:
            out.append(reason("length", f"{words} words; allowed {lo}-{hi}", section))
    return out


def check_references(study: dict, bible: Bible) -> list[dict]:
    here = parse_id(study["id"])
    out = []
    for section in PROSE_FIELDS:
        for ref in find_references(study.get(section, ""), here):
            problem = check_reference(ref, bible.verses, bible.chapters)
            if problem:
                out.append(reason("reference", f"“{ref.text}”: {problem}", section))
    return out


# Strong's: H/G + 1-5 digits + optional letter. Hebrew/Greek: runs of letters in those scripts.
_STRONGS = re.compile(r"(?<![\w])([HG])0*(\d{1,5})[A-Za-z]?(?![\w])")
_HEBREW = re.compile(r"[א-ת][֑-ׇא-תװ-״]*")
_GREEK = re.compile(r"[Ͱ-Ͽἀ-῿]+")
_TRANSLIT_MENTION = re.compile(
    r"\b(?:Hebrew|Greek|Aramaic)\s+(?:word|term|verb|noun|adjective|phrase|root)\s+"
    r"[*_\"“‘'(]*(?P<word>[A-Za-zÀ-ɏḀ-ỿʼʾʿ'’.\-]{2,})"
)
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


def check_original_language(study: dict, bible: Bible) -> list[dict]:
    words = study["originalLanguage"]["words"]
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
    for section in PROSE_FIELDS:
        prose = study.get(section, "")
        for m in _STRONGS.finditer(prose):
            if f"{m.group(1)}{int(m.group(2))}" not in strongs:
                out.append(reason("original_language", f"Strong's {m.group(0)} isn't in this verse", section))
        for m in [*_HEBREW.finditer(prose), *_GREEK.finditer(prose)]:
            mention = _bare(m.group(0))
            if len(mention) >= 2 and not any(mention == f or mention in f for f in forms):
                out.append(reason("original_language", f"“{m.group(0)}” isn't a word in this verse", section))
        for m in _TRANSLIT_MENTION.finditer(prose):
            key = _translit_key(m["word"])
            ok = any(key == t or (min(len(key), len(t)) >= 4 and (t.startswith(key) or key.startswith(t)))
                     for t in translits)
            if key and not ok:
                out.append(reason("original_language", f"“{m['word']}” doesn't match a word in this verse", section))
    return out


def check_banned(study: dict, rules: Rules) -> list[dict]:
    out = []
    for section in PROSE_FIELDS:
        for phrase, pattern in rules.banned:
            m = pattern.search(study.get(section, ""))
            if m:
                out.append(reason("banned_phrase", f"“{m.group(0)}” (rule: {phrase})", section))
    return out


def free_checks(study: dict, bible: Bible, rules: Rules, validator: Draft202012Validator) -> list[dict]:
    reasons = check_schema(study, validator)
    if any(r["check"] == "schema" for r in reasons) and "originalLanguage" not in study:
        return reasons  # too broken to check further
    return [*reasons, *check_lengths(study, rules), *check_references(study, bible),
            *check_original_language(study, bible), *check_banned(study, rules)]


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


def apply(study: dict, outcome: Outcome) -> dict:
    """The study with the outcome written in (status + checks block). Reviewer reasons are kept."""
    updated = json.loads(json.dumps(study))
    kept = [r for r in study.get("checks", {}).get("reasons", []) if r["check"] in ("reviewer", "regenerate")]
    checks = {"checkedAt": now(), "checkerVersion": CHECKER_VERSION, "reasons": [*kept, *outcome.reasons]}
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
