"""
Second-opinion AI rubric for a written study: factual risk, one-sided theology, tone (0-3 each).

Uses the cheaper Claude Haiku 5.5 with JSON-schema structured output. Any score >= THRESHOLD flags the
study, as does a refusal. API failures raise RubricUnavailable so the checker leaves the study for the
next run instead of flagging it for something that isn't its fault.
"""
from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Protocol

import anthropic
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
        """Judgments whose quote isn't verbatim in the section they name."""
        return sum(1 for k in DIMENSIONS if self.rubric and not self.rubric[k].get("verified", True))


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
    def judge(self, study: dict, verse_text: str, subject: Subject | None = None) -> RubricResult: ...


def build_prompt(study: dict, verse_text: str, subject: Subject | None = None) -> str:
    if subject is None:
        subject = Subject(f"Verse: {study['id']} (KJV)", {k: _TITLES[k] for k in _PROSE})
    sections = "\n\n".join(f"## {title}\n{study[k]}" for k, title in subject.sections.items() if study.get(k))
    return f"{subject.heading}: {verse_text}\n\nNotes to review:\n\n{sections}"


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


def to_result(scores: dict, model: str, study: dict | None = None, subject: Subject | None = None) -> RubricResult:
    Draft202012Validator(OUTPUT_SCHEMA).validate(scores)
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
    return RubricResult(rubric, reasons)


class AnthropicJudge:
    """Calls the Messages API. Credentials come from the environment (ANTHROPIC_API_KEY or `ant auth login`)."""

    def __init__(self, client: anthropic.Anthropic | None = None, model: str = RUBRIC_MODEL):
        self.client = client or anthropic.Anthropic()
        self.model = model
        self.usage = Usage()  # tokens across every rubric call, for cost reporting
        self.reviews = 0      # reviews returned
        self.unverified_reviews = 0  # reviews with at least one judgment whose quote isn't in the study

    def judge(self, study: dict, verse_text: str, subject: Subject | None = None) -> RubricResult:
        try:
            response = self.client.messages.create(
                model=self.model,
                max_tokens=16000,
                system=subject.system if subject else SYSTEM_PROMPT,
                messages=[{"role": "user", "content": build_prompt(study, verse_text, subject)}],
                output_config={
                    "effort": "medium",
                    "format": {"type": "json_schema", "schema": OUTPUT_SCHEMA},
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
            result = to_result(json.loads(text), self.model, study, subject)
        except (json.JSONDecodeError, ValidationError) as error:
            raise RubricUnavailable(f"Unusable rubric response: {error}") from error
        self.reviews += 1
        self.unverified_reviews += result.unverified > 0
        return result
