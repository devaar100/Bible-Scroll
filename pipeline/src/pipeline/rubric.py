"""
Second-opinion AI rubric for a written study: factual risk, one-sided theology, tone (0-3 each).

Uses the cheaper Claude Haiku 5.5 with JSON-schema structured output. Any score >= THRESHOLD flags the
study, as does a refusal.

Studies also get a contextMismatch item: the reviewer sees the text the writer was given (generate.supplied_text)
and lists every claim about a nearby verse or about what the verse itself contains, quoting the supporting words
from that text. A claim whose support quote isn't in the text given (support_found) is reported.
For studies every reason here is advisory (checks.BLOCKING, decision 25): it never blocks or triggers a retry. API failures raise RubricUnavailable so the checker leaves the study for the
next run instead of flagging it for something that isn't its fault.
"""
from __future__ import annotations

import json
import re
import unicodedata
from dataclasses import dataclass
from typing import Protocol

import anthropic

from .offline import anthropic_client
from jsonschema import Draft202012Validator, ValidationError

from .usage import Usage

RUBRIC_MODEL = "claude-haiku-5-5"
THRESHOLD = 2
DIMENSIONS = {
    "factualRisk": "factual risk",
    "oneSidedTheology": "one-sided theology",
    "tone": "tone",
}

SYSTEM_PROMPT = """You review short Bible-study notes for a free Bible-reading app used by Christians from \
many traditions (Catholic, Orthodox, Protestant and others) and by curious non-believers. The notes explain \
one verse. Score three things from 0 to 3 and give a one-sentence reason for each score.

factualRisk: claims that could be wrong.
  0 none: nothing beyond what is well established or clearly hedged.
  1 minor: a small detail stated more firmly than it should be.
  2 likely problem: a specific date, number, name, quotation, word meaning or archaeological claim that is \
probably wrong or unsupported.
  3 clear error: something plainly false.

oneSidedTheology: contested doctrine presented as settled.
  0 balanced: points that Christian traditions disagree on are either avoided or described as views.
  1 slight lean: one tradition's wording, but nothing others would object to.
  2 one-sided: a contested position (e.g. on baptism, predestination, the end times, Mary, the \
sacraments, church authority) stated as the only correct reading.
  3 polemical: dismisses or attacks other traditions or faiths.

tone: the app's voice is warm, plain and humble.
  0 fine.
  1 a little stiff, preachy or overconfident.
  2 preachy, guilt-tripping, manipulative, condescending, or overclaiming certainty.
  3 harsh, shaming, or offensive.

Judge only what the notes say. Do not reward length or penalise brevity. Do not rewrite the notes.

Evidence: for each of the three scores, name the one section your judgment rests on (its heading exactly as \
shown after "##") and copy a short quote from that section, a few words to one sentence, character for \
character. Even a score of 0 needs a section and a quote. Base each reason only on words that are in the notes."""

_SCORE = {
    "type": "object",
    "properties": {"score": {"type": "integer", "enum": [0, 1, 2, 3]}, "reason": {"type": "string"},
                   "section": {"type": "string"}, "quote": {"type": "string"}},
    "required": ["score", "reason", "section", "quote"],
    "additionalProperties": False,
}
OUTPUT_SCHEMA = {
    "type": "object",
    "properties": {key: _SCORE for key in DIMENSIONS},
    "required": list(DIMENSIONS),
    "additionalProperties": False,
}
_CLAIM = {
    "type": "object",
    "properties": {"section": {"type": "string"}, "claim": {"type": "string"}, "support": {"type": "string"},
                   "supported": {"type": "boolean"}},
    "required": ["section", "claim", "support", "supported"],
    "additionalProperties": False,
}
#: Studies: the three scores plus contextMismatch (devotionals keep OUTPUT_SCHEMA).
STUDY_OUTPUT_SCHEMA = {
    "type": "object",
    "properties": {**OUTPUT_SCHEMA["properties"],
                   "contextMismatch": {"type": "object", "properties": {"claims": {"type": "array", "items": _CLAIM}},
                                       "required": ["claims"], "additionalProperties": False}},
    "required": [*DIMENSIONS, "contextMismatch"],
    "additionalProperties": False,
}
CONTEXT_PROMPT = """

contextMismatch (study notes only): below the verse you are given the exact text the writer had: the verse in two \
translations, the surrounding verses, the book background, the original words and the related passages. Find \
every claim the notes make about a nearby verse or about what this verse itself contains: who speaks or acts, \
which words or phrases it includes ("in the verse", "this line says"), what comes before or after it, and what \
the surrounding verses say. For each claim give: the section (its heading as shown after "##"), the claim (a \
short quote copied character for character from that section), support (the words of the text given that show \
the claim is true, copied character for character from it, a few words to one sentence; an empty string if \
nothing there supports it) and supported (true only if support plainly shows the claim). Do not use what you \
know of the Bible from elsewhere: if the text given doesn't show it, the claim is unsupported. Claims about \
meaning, theology or history are not context claims. If there are none, give an empty list."""

_PROSE = ("meaning", "historicalContext", "lifeInBibleTimes", "theology", "didYouKnow", "applyIt")
_TITLES = {"meaning": "Meaning", "historicalContext": "Historical context", "lifeInBibleTimes": "Life in Bible times",
           "theology": "Theology", "didYouKnow": "Did you know?", "applyIt": "Apply it"}


class RubricUnavailable(Exception):
    """The rubric couldn't be obtained (network, rate limit, server error, bad response). Retry later."""


@dataclass(frozen=True)
class RubricResult:
    rubric: dict | None
    reasons: list[dict]

    @property
    def unverified(self) -> int:
        """Judgments (and context claims) whose quote isn't verbatim in the section they name."""
        if not self.rubric:
            return 0
        claims = (self.rubric.get("contextMismatch") or {}).get("claims", [])
        return sum(1 for k in DIMENSIONS if not self.rubric[k].get("verified", True)) + \
            sum(1 for c in claims if not c.get("verified", True))


DEVOTIONAL_SYSTEM_PROMPT = SYSTEM_PROMPT.replace(
    "short Bible-study notes", "short daily devotionals").replace(
    "The notes explain \none verse.", "Each devotional reflects on one \npassage.").replace(
    "The notes explain one verse.", "Each devotional reflects on one passage.") + (
    "\n\nFor devotionals also: any suggestion that faith, prayer or Bible reading replaces medical, mental-health "
    "or other professional help is tone 3. Saying Paul wrote Hebrews, or claims about ancient manuscripts, is "
    "factualRisk 2 or more.")


@dataclass(frozen=True)
class Subject:
    """What is being judged: its passage line, its sections (key -> heading) and the system prompt."""
    heading: str
    sections: dict[str, str]
    system: str = SYSTEM_PROMPT


class Judge(Protocol):
    def judge(self, study: dict, verse_text: str, subject: Subject | None = None,
              supplied: str | None = None) -> RubricResult: ...


def build_prompt(study: dict, verse_text: str, subject: Subject | None = None, supplied: str | None = None) -> str:
    """supplied: the text the writer was given (studies); it enables the contextMismatch item."""
    if subject is None:
        subject = Subject(f"Verse: {study['id']} (KJV)", {k: _TITLES[k] for k in _PROSE})
    sections = "\n\n".join(f"## {title}\n{study[k]}" for k, title in subject.sections.items() if study.get(k))
    given = f"\n\nText the writer was given:\n<<<\n{supplied}\n>>>" if supplied else ""
    return f"{subject.heading}: {verse_text}{given}\n\nNotes to review:\n\n{sections}"


def _normalise(text: str) -> str:
    return " ".join(text.split())


def section_key(name: str, sections: dict[str, str]) -> str | None:
    """The study key for a section the reviewer named by heading ("Did you know?") or key ("didYouKnow")."""
    wanted = _normalise(name).strip("#: ").lower()
    for key, title in sections.items():
        if wanted in (key.lower(), title.lower()):
            return key
    return None


def quote_verified(study: dict, section: str | None, quote: str) -> bool:
    """True if [quote] (outer quote marks trimmed, whitespace normalised) appears exactly in that section."""
    q = _normalise(quote).strip("\"“”'‘’")
    return bool(section and q and q in _normalise(study.get(section, "")))


#: A line label in the text given: "Luke 6:37: ", "Luke 6:38 (this verse): ", "1 John 4:8: " or a list bullet "- ".
_LINE_LABEL = re.compile(r"^\s*(?:- |(?:[1-3] )?[A-Z][A-Za-z ]+ \d+:\d+(?: \(this verse\))?: )")
#: A support quote of at least SUPPORT_GAP_MIN_WORDS words may skip up to SUPPORT_GAP_WORDS words of the text
#: given (a quote that runs across two verses and leaves out the words between them).
SUPPORT_GAP_MIN_WORDS = 6
SUPPORT_GAP_WORDS = 8


def _words(text: str, strip_labels: bool = False) -> list[str]:
    """Lowercase words with accents, vowel points, cantillation, punctuation and quote marks removed."""
    if strip_labels:
        text = "\n".join(_LINE_LABEL.sub("", line) for line in text.splitlines())
    text = "".join(ch for ch in unicodedata.normalize("NFD", text) if not unicodedata.category(ch).startswith("M"))
    return re.sub(r"\W+", " ", text.lower()).split()


def _in_text(quote: list[str], text: list[str]) -> bool:
    n = len(quote)
    if not n:
        return False
    if any(text[i:i + n] == quote for i in range(len(text) - n + 1)):
        return True
    if n < SUPPORT_GAP_MIN_WORDS:
        return False
    for start, word in enumerate(text):
        if word != quote[0]:
            continue
        k, skipped = 0, 0
        for w in text[start:]:
            if w == quote[k]:
                k += 1
                if k == n:
                    return True
            else:
                skipped += 1
                if skipped > SUPPORT_GAP_WORDS:
                    break
    return False


def support_found(support: str, supplied: str) -> bool:
    """True if [support] is in the text given. Compared word by word, ignoring case, punctuation, quote marks,
    diacritics, verse labels ("Luke 6:37:") and list bullets, so a quote may run across lines. A quote cut with
    "..." must match piece by piece. A piece of SUPPORT_GAP_MIN_WORDS or more words may skip up to
    SUPPORT_GAP_WORDS words of the text (a quote that joins two verses); its own words must all appear, in order."""
    text = _words(supplied, strip_labels=True)
    pieces = [_words(p) for p in support.replace("…", "...").split("...")]
    pieces = [p for p in pieces if p]
    return bool(pieces) and all(_in_text(p, text) for p in pieces)


def context_claims(found: dict, study: dict, supplied: str, sections: dict[str, str]) -> tuple[dict, list[dict]]:
    """(the contextMismatch record with verified/supportFound per claim, reasons that flag the study).
    A claim flags when its support isn't in the text given. A claim the reviewer marked unsupported but backed with
    a quote that is in the text given passes: Haiku often does that for claims its own quote shows."""
    claims, reasons = [], []
    for c in found["claims"]:
        key = section_key(c["section"], sections)
        found_in_text = support_found(c["support"], supplied)
        claims.append({**c, "section": key or c["section"], "verified": quote_verified(study, key, c["claim"]),
                       "supportFound": found_in_text})
        if not found_in_text:
            why = "nothing in the text given supports it" if not c["support"].strip() else \
                f"its support “{c['support']}” isn't in the text given"
            reason = {"check": "context_mismatch", "message": f"“{c['claim']}”: {why}"}
            if key:
                reason["section"] = key
            reasons.append(reason)
    return {"claims": claims}, reasons


def to_result(scores: dict, model: str, study: dict | None = None, subject: Subject | None = None,
              supplied: str | None = None) -> RubricResult:
    Draft202012Validator(STUDY_OUTPUT_SCHEMA if supplied else OUTPUT_SCHEMA).validate(scores)
    sections = subject.sections if subject else {k: _TITLES[k] for k in _PROSE}
    judged = {}
    for k in DIMENSIONS:
        key = section_key(scores[k]["section"], sections)
        judged[k] = {**scores[k], "section": key or scores[k]["section"],
                     "verified": quote_verified(study or {}, key, scores[k]["quote"])}
    rubric = {"model": model, **judged}
    reasons = [
        {"check": "rubric", "message": f"{label} {scores[k]['score']}/3: {scores[k]['reason']}"}
        for k, label in DIMENSIONS.items() if scores[k]["score"] >= THRESHOLD
    ]
    if supplied:
        rubric["contextMismatch"], context_reasons = context_claims(scores["contextMismatch"], study or {},
                                                                    supplied, sections)
        reasons += context_reasons
    return RubricResult(rubric, reasons)


class StoredJudge:
    """Re-scores the review already stored in study["checks"]["rubric"] with the current code (to_result), without
    calling the API: for re-checking after a change to how a review is scored, not to how it is asked for. A study
    with no stored review raises RubricUnavailable, so the checker leaves it unchanged."""

    def __init__(self):
        self.usage = Usage()  # always zero: nothing is sent
        self.reviews = 0
        self.unverified_reviews = 0

    def judge(self, study: dict, verse_text: str, subject: Subject | None = None,
              supplied: str | None = None) -> RubricResult:
        stored = (study.get("checks") or {}).get("rubric")
        if not stored or not all(k in stored for k in DIMENSIONS) or (supplied and "contextMismatch" not in stored):
            raise RubricUnavailable("no stored review to reuse")
        scores = {k: {f: stored[k][f] for f in ("score", "reason", "section", "quote")} for k in DIMENSIONS}
        if supplied:
            scores["contextMismatch"] = {"claims": [{f: c[f] for f in ("section", "claim", "support", "supported")}
                                                    for c in stored["contextMismatch"]["claims"]]}
        try:
            result = to_result(scores, stored.get("model", RUBRIC_MODEL), study, subject, supplied)
        except (KeyError, ValidationError) as error:
            raise RubricUnavailable(f"stored review unusable: {error}") from error
        self.reviews += 1
        self.unverified_reviews += result.unverified > 0
        return result


class AnthropicJudge:
    """Calls the Messages API. Credentials come from the environment (ANTHROPIC_API_KEY or `ant auth login`)."""

    def __init__(self, client: anthropic.Anthropic | None = None, model: str = RUBRIC_MODEL):
        self.client = client or anthropic_client()
        self.model = model
        self.usage = Usage()  # tokens across every rubric call, for cost reporting
        self.reviews = 0      # reviews returned
        self.unverified_reviews = 0  # reviews with at least one judgment whose quote isn't in the study

    def judge(self, study: dict, verse_text: str, subject: Subject | None = None,
              supplied: str | None = None) -> RubricResult:
        """supplied: the text the writer was given (studies); adds the contextMismatch item."""
        system = subject.system if subject else SYSTEM_PROMPT
        if supplied:
            system += CONTEXT_PROMPT
        try:
            response = self.client.messages.create(
                model=self.model,
                max_tokens=16000,
                system=system,
                messages=[{"role": "user", "content": build_prompt(study, verse_text, subject, supplied)}],
                output_config={
                    "effort": "medium",
                    "format": {"type": "json_schema",
                               "schema": STUDY_OUTPUT_SCHEMA if supplied else OUTPUT_SCHEMA},
                },
            )
        except (anthropic.RateLimitError, anthropic.APIConnectionError, anthropic.InternalServerError) as error:
            # Already retried by the SDK. Other API errors (400/401/403/404) are configuration problems
            # that a retry won't fix, so they propagate and stop the run.
            raise RubricUnavailable(f"{type(error).__name__}: {error}") from error
        if getattr(response, "usage", None) is not None:
            self.usage.add(response.usage)

        if response.stop_reason == "refusal":
            category = getattr(response.stop_details, "category", None) if response.stop_details else None
            return RubricResult(None, [{
                "check": "rubric",
                "message": f"The AI reviewer declined to assess this study (refusal, category: {category}). "
                           "Needs a human.",
            }])
        if response.stop_reason == "max_tokens":
            raise RubricUnavailable("Rubric response hit max_tokens")
        text = next((b.text for b in response.content if b.type == "text"), None)
        if text is None:
            raise RubricUnavailable(f"No text in rubric response (request {response._request_id})")
        try:
            result = to_result(json.loads(text), self.model, study, subject, supplied)
        except (json.JSONDecodeError, ValidationError) as error:
            raise RubricUnavailable(f"Unusable rubric response: {error}") from error
        self.reviews += 1
        self.unverified_reviews += result.unverified > 0
        return result
