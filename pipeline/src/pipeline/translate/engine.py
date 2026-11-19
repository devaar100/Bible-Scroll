"""
Translate app content (plans, devotionals) and check it.

One unit (a plan, a devotional, the category list) = a few named English texts. For each unit:
  1. the translator model returns one translation per key (JSON-schema structured output);
  2. free checks: every key present, length limits, the same numbers and Bible-reference numbers as the English,
     and quoted Scripture must be the target Bible's text (when the unit has Scripture);
  3. a second, different model compares each translation with its English for meaning drift.
Result: auto_checked (all passed) or flagged (a person looks). A refusal flags; API outages raise
TranslationUnavailable so the unit is left exactly as it was for the next run. Tests use fake clients.
"""
from __future__ import annotations

import hashlib
import json
import re
import unicodedata
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Protocol

import anthropic

from ..offline import anthropic_client
from jsonschema import Draft202012Validator, ValidationError

from ..paths import PIPELINE
from ..usage import Usage

TRANSLATE_MODEL = "claude-opus-5-5"
#: A different model from the translator, so the drift check is a second opinion, not a self-review.
DRIFT_MODEL = "claude-sonnet-5-5"
#: On a policy decline the API reruns the request on a fallback model (recorded in meta via response.model).
FALLBACK_BETA = "server-side-fallback-2026-07-01"
CHECKER_VERSION = "translate-2"

#: Drift categories that block publishing (your rule, 2026-11-18). Anything else is logged and counted, not blocking.
BLOCKING = frozenset({"meaning_changed", "claim_added", "claim_dropped", "reference_changed", "name_changed",
                      "number_changed", "theology_term"})
NON_BLOCKING = ("tone", "nuance", "quotation", "style", "other")
#: Free-check failures are blocking too: wrong numbers or references, missing keys, over-long text, made-up Scripture.

LANGUAGES = {
    "es": {
        "language": "Spanish",
        "style": "Neutral Latin American Spanish, informal \"tú\". Gender-neutral wording for the reader where "
                 "it reads naturally (\"cuando quieras\", not \"cuando estés listo\").",
        "example_reference": "Juan 3:16",
        "bible_name": "Reina-Valera 1909",
        "glossary": "Study = Estudio, Devotional = Devocional, Streak = Racha, Notes = Notas, Liked = Me gusta, "
                    "Bookmark = Marcador, Discover = Descubrir, Community = Comunidad, Reading plan = Plan de lectura, "
                    "Scripture = las Escrituras, Psalms = Salmos, Song of Solomon = Cantares, James = Santiago, "
                    "Revelation = Apocalipsis, Gospels = Evangelios",
    },
}


def now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


@dataclass
class Unit:
    """English texts to translate together. [where] says, per key, where it shows and its length limit."""
    id: str
    fields: dict[str, str]
    where: dict[str, str]
    limits: dict[str, int]
    #: The target-language Bible text of the passage, when the English may quote Scripture (devotionals).
    scripture: str | None = None
    #: Problems found in the previous attempt, sent with the one retry.
    feedback: list[str] | None = None


@dataclass
class Outcome:
    status: str                      # auto_checked | flagged (flagged = not shipped: the app shows English)
    texts: dict[str, str]            # key -> translation (empty if the translator refused)
    reasons: list[dict] = field(default_factory=list)   # blocking problems only
    minor: list[dict] = field(default_factory=list)     # non-blocking drift notes, logged and counted
    drift: dict | None = None
    model: str | None = None
    drift_model: str | None = None
    prompt_hash: str | None = None
    drift_prompt_hash: str | None = None
    retried: bool = False


class TranslationUnavailable(Exception):
    """Network, rate limit, server error or an unusable response. The unit is left unchanged; retry later."""


# ---- Structured-output schemas ----

TRANSLATION_SCHEMA = {
    "type": "object", "additionalProperties": False, "required": ["translations"],
    "properties": {"translations": {"type": "array", "items": {
        "type": "object", "additionalProperties": False, "required": ["key", "text"],
        "properties": {"key": {"type": "string"}, "text": {"type": "string"}}}}},
}
DRIFT_SCHEMA = {
    "type": "object", "additionalProperties": False, "required": ["issues"],
    "properties": {
        "issues": {"type": "array", "items": {
            "type": "object", "additionalProperties": False, "required": ["key", "category", "problem"],
            "properties": {"key": {"type": "string"},
                           "category": {"type": "string", "enum": sorted(BLOCKING) + list(NON_BLOCKING)},
                           "problem": {"type": "string"}}}},
    },
}


def is_blocking(issue: dict) -> bool:
    """By category (translate-2). Issues stored by translate-1 have only a severity: major blocked then too."""
    if "category" in issue:
        return issue["category"] in BLOCKING
    return issue.get("severity") == "major"


def _prompt(name: str, lang: str) -> str:
    text = (PIPELINE / "prompts" / f"{name}.md").read_text()
    text = text.split("\n", 2)[2] if text.startswith("#") else text  # drop the "# ..." heading line
    return text.format(**LANGUAGES[lang]).strip()


def prompt_hash(name: str, lang: str) -> str:
    """SHA-256 of the exact system prompt sent (recorded in each translation's meta)."""
    return hashlib.sha256(_prompt(name, lang).encode()).hexdigest()


def translation_request(unit: Unit) -> str:
    parts = [f"{key} ({unit.where[key]}; at most {unit.limits[key]} characters):\n{text}"
             for key, text in unit.fields.items()]
    if unit.scripture:
        parts.append(f"Scripture (quote only this wording):\n{unit.scripture}")
    if unit.feedback:
        parts.append("A check of your previous translation found these problems. Fix them, keeping everything else:\n"
                     + "\n".join(f"- {f}" for f in unit.feedback))
    return "\n\n".join(parts)


def drift_request(unit: Unit, texts: dict[str, str]) -> str:
    parts = [f"{key} ({unit.where[key]})\nEnglish: {unit.fields[key]}\nTranslation: {texts.get(key, '')}" for key in unit.fields]
    if unit.scripture:
        parts.append(f"Scripture the translation may quote:\n{unit.scripture}")
    return "\n\n".join(parts)


# ---- Free checks ----

_NUMBERS = re.compile(r"\d+")
_QUOTE = re.compile(r"[“«\"]([^”»\"]{20,})[”»\"]")


def _fold(text: str) -> str:
    text = unicodedata.normalize("NFKD", text)
    return re.sub(r"[^a-z0-9]+", " ", "".join(c for c in text if not unicodedata.combining(c)).lower()).strip()


def free_checks(unit: Unit, texts: dict[str, str]) -> list[dict]:
    reasons = []
    missing = [k for k in unit.fields if not texts.get(k, "").strip()]
    extra = [k for k in texts if k not in unit.fields]
    if missing:
        reasons.append({"check": "keys", "message": f"No translation for: {', '.join(missing)}"})
    if extra:
        reasons.append({"check": "keys", "message": f"Unexpected keys: {', '.join(extra)}"})
    for key, english in unit.fields.items():
        text = texts.get(key, "")
        if not text:
            continue
        if len(text) > unit.limits[key]:
            reasons.append({"check": "length", "key": key, "message": f"{len(text)} characters, limit {unit.limits[key]}"})
        if sorted(_NUMBERS.findall(english)) != sorted(_NUMBERS.findall(text)):
            reasons.append({"check": "numbers", "key": key,
                            "message": f"Numbers differ: English {_NUMBERS.findall(english)}, translation {_NUMBERS.findall(text)}"})
        bible = _fold(unit.scripture or "")
        for quoted in _QUOTE.findall(text):
            if not bible or _fold(quoted) not in bible:
                reasons.append({"check": "scripture", "key": key,
                                "message": f"Quoted text isn't the target Bible's wording: “{quoted[:80]}”"})
    return reasons


# ---- Model clients ----

class Translator(Protocol):
    usage: Usage
    def translate(self, unit: Unit, lang: str) -> tuple[dict[str, str] | None, str]: ...


class DriftChecker(Protocol):
    usage: Usage
    def check(self, unit: Unit, texts: dict[str, str], lang: str) -> tuple[dict | None, str]: ...


def _call(client: anthropic.Anthropic, model: str, system: str, content: str, schema: dict, usage: Usage):
    """One structured-output request with server-side refusal fallback. Returns (parsed JSON or None if refused, model)."""
    try:
        response = client.beta.messages.create(
            model=model,
            max_tokens=16000,
            betas=[FALLBACK_BETA],
            fallbacks="default",
            system=system,
            messages=[{"role": "user", "content": content}],
            output_config={"effort": "high", "format": {"type": "json_schema", "schema": schema}},
        )
    except (anthropic.RateLimitError, anthropic.APIConnectionError, anthropic.InternalServerError) as error:
        # Already retried by the SDK. Other API errors (400/401/403/404) are setup problems: let them stop the run.
        raise TranslationUnavailable(f"{type(error).__name__}: {error}") from error
    if getattr(response, "usage", None) is not None:
        usage.add(response.usage)
    served_by = getattr(response, "model", None) or model
    if response.stop_reason == "refusal":
        return None, served_by
    if response.stop_reason == "max_tokens":
        raise TranslationUnavailable("Response hit max_tokens")
    text = next((b.text for b in response.content if b.type == "text"), None)
    if text is None:
        raise TranslationUnavailable(f"No text in response (request {getattr(response, '_request_id', '?')})")
    try:
        data = json.loads(text)
        Draft202012Validator(schema).validate(data)
    except (json.JSONDecodeError, ValidationError) as error:
        raise TranslationUnavailable(f"Unusable response: {error}") from error
    return data, served_by


class AnthropicTranslator:
    def __init__(self, client: anthropic.Anthropic | None = None, model: str = TRANSLATE_MODEL):
        self.client = client or anthropic_client()
        self.model = model
        self.usage = Usage()

    def translate(self, unit: Unit, lang: str) -> tuple[dict[str, str] | None, str]:
        data, served_by = _call(self.client, self.model, _prompt("translate", lang), translation_request(unit),
                                TRANSLATION_SCHEMA, self.usage)
        if data is None:
            return None, served_by
        return {item["key"]: item["text"].strip() for item in data["translations"]}, served_by


class AnthropicDriftChecker:
    def __init__(self, client: anthropic.Anthropic | None = None, model: str = DRIFT_MODEL):
        self.client = client or anthropic_client()
        self.model = model
        self.usage = Usage()

    def check(self, unit: Unit, texts: dict[str, str], lang: str) -> tuple[dict | None, str]:
        return _call(self.client, self.model, _prompt("drift", lang), drift_request(unit, texts), DRIFT_SCHEMA, self.usage)


# ---- One unit, end to end ----

def run_unit(unit: Unit, lang: str, translator: Translator, checker: DriftChecker,
             reuse: dict[str, str] | None = None) -> Outcome:
    """
    Translate and check one unit. Blocking problems (free-check failures, or drift in a BLOCKING category) get one
    retry with the problems fed back; if they remain, the unit is flagged and the app keeps showing English for it.
    Non-blocking drift notes are kept in Outcome.minor. [reuse] maps English text -> an approved translation of
    exactly that text (e.g. the shared plan credits line), so identical English gets identical wording.
    """
    reuse = reuse or {}
    reused = {k: reuse[v] for k, v in unit.fields.items() if v in reuse}
    outcome = _attempt(unit, reused, lang, translator, checker, feedback=None)
    if outcome.status == "flagged" and outcome.texts:  # blocking problems, not a refusal: one retry
        feedback = [f"{r.get('key', '')}: {r['message']}".lstrip(": ") for r in outcome.reasons]
        outcome = _attempt(unit, reused, lang, translator, checker, feedback=feedback)
        outcome.retried = True
    outcome.prompt_hash = prompt_hash("translate", lang)
    outcome.drift_prompt_hash = prompt_hash("drift", lang)
    return outcome


def _attempt(unit: Unit, reused: dict[str, str], lang: str, translator: Translator, checker: DriftChecker,
             feedback: list[str] | None) -> Outcome:
    todo = Unit(unit.id, {k: v for k, v in unit.fields.items() if k not in reused}, unit.where, unit.limits,
                unit.scripture, feedback)
    texts = dict(reused)
    model = None
    if todo.fields:
        translated, model = translator.translate(todo, lang)
        if translated is None:
            return Outcome("flagged", {}, [{"check": "refusal", "message": "The translator declined this text. Needs a person."}],
                           model=model)
        texts.update(translated)
    texts = {k: texts[k] for k in unit.fields if k in texts} | {k: v for k, v in texts.items() if k not in unit.fields}

    reasons = free_checks(unit, texts)
    minor: list[dict] = []
    drift, drift_model = None, None
    if not reasons:  # don't spend a drift check on text that already fails
        drift, drift_model = checker.check(unit, texts, lang)
        if drift is None:
            reasons.append({"check": "drift", "message": "The drift checker declined to review this text. Needs a person."})
        else:
            for issue in drift["issues"]:
                entry = {"check": "drift", "key": issue["key"], "category": issue.get("category", issue.get("severity", "other")),
                         "message": issue["problem"]}
                (reasons if is_blocking(issue) else minor).append(entry)
    return Outcome("flagged" if reasons else "auto_checked", texts, reasons, minor, drift, model, drift_model)
