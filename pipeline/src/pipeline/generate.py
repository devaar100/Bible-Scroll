"""
Study prose generator (direct mode): one Messages API request per verse, using prompts/study.md.

Claude writes only the six prose sections. The data sections (originalLanguage, crossReferences, citations)
are copied from the built study in out/studies, and the verse text comes from the app's Bible, so nothing is
recalled from memory. Results are saved with status "generated"; `check` sets auto_checked/flagged.

--out writes to another folder (e.g. out/test_haiku) so models can be compared without touching
out/studies. A study that already has prose in the target folder is skipped unless --force, and a reviewed
or hand-edited one is never overwritten. Token usage, cost and run time go to <out>/generate_summary.json.
"""
from __future__ import annotations

import json
import sqlite3
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from functools import lru_cache
from pathlib import Path

from . import context as ctx
from .books import BOOKS, BY_USFM
from .devotionals.generate import Prompt, fill
from .paths import APP_BIBLE_DB, PIPELINE
from .plans.ai import DraftFailed, DraftRefused
from .store import StudyStore, is_written, now, parse_id
from .studies import PROSE_FIELDS, STUDIES_DIR
from .usage import Usage, output_stats, request_log

PROMPT_FILE = PIPELINE / "prompts" / "study.md"
LIMITS_FILE = PIPELINE / "checks" / "limits.json"
DEFAULT_MODEL = "claude-haiku-5-5"  # launch decision 2026-11-19: Haiku 5.5 at medium for all generation
DEFAULT_EFFORT = "medium"
MAX_TOKENS = 32000

#: Models that accept the server-side refusal fallback (fallbacks="default"). Haiku 5.5 has none.
FALLBACK_MODELS = {"claude-opus-5-5", "claude-sonnet-5-5", "claude-fable-5-2"}

OUTPUT_SCHEMA = {
    "type": "object",
    "properties": {k: {"type": "string"} for k in PROSE_FIELDS},
    "required": list(PROSE_FIELDS),
    "additionalProperties": False,
}


# ---- Prompt ---------------------------------------------------------------------------------------------

def limits(path: Path = LIMITS_FILE) -> dict[str, dict[str, int]]:
    return json.loads(path.read_text())["words"]


def _book_name(book_id: int) -> str:
    """A reference names one psalm: "Psalm 23:1", not "Psalms 23:1"."""
    return "Psalm" if book_id == 19 else BOOKS[book_id - 1].name


def verse_label(key: tuple[int, int, int]) -> str:
    return f"{_book_name(key[0])} {key[1]}:{key[2]}"


def ref_label(ref: str) -> str:
    """'2CO.12.9-2CO.12.10' -> '2 Corinthians 12:9-10'."""
    start, _, end = ref.partition("-")
    b, c, v = start.split(".")
    label = f"{_book_name(BY_USFM[b].id)} {c}:{v}"
    if end:
        _, c2, v2 = end.split(".")
        label += f"-{v2}" if c2 == c else f"-{c2}:{v2}"
    return label


def _verse_rows(bible_db: Path, translation: str, book: int, chapter: int, first: int, last: int):
    with sqlite3.connect(f"file:{bible_db}?mode=ro", uri=True) as db:
        return db.execute(
            "SELECT verse, text FROM verses WHERE translation_id = ? AND book_id = ? AND chapter = ? "
            "AND verse BETWEEN ? AND ? ORDER BY verse", (translation, book, chapter, first, last)).fetchall()


def build_request(study: dict, prompt: Prompt, bible_db: Path = APP_BIBLE_DB,
                  word_limits: dict | None = None) -> str:
    return fill(prompt.request, request_values(study, bible_db, word_limits))


#: Where the Request template's fixed instructions start. Batch mode moves everything from here on into the
#: cached system block (it holds only word limits, which are the same for every verse).
FIXED_TAIL_MARKER = "Return JSON with these sections"


def split_request(prompt: Prompt, word_limits: dict | None = None) -> tuple[str, str]:
    """(verse template, fixed tail filled with the word limits). Same wording as study.md, just reordered."""
    head, marker, tail = prompt.request.partition(FIXED_TAIL_MARKER)
    if not marker:
        raise ValueError(f"study.md's Request section has no {FIXED_TAIL_MARKER!r} line to split at")
    word_limits = word_limits or limits()
    values = {f"{k}_words": f"{lim['min']}-{lim['max']}" for k, lim in word_limits.items()}
    return head.rstrip(), fill(marker + tail, values)


@lru_cache(maxsize=8)
def _book_rows(bible_db: Path, translation: str, book: int) -> dict[tuple[int, int, int], str]:
    with sqlite3.connect(f"file:{bible_db}?mode=ro", uri=True) as db:
        rows = db.execute("SELECT chapter, verse, text FROM verses WHERE translation_id = ? AND book_id = ? "
                          "AND verse > 0", (translation, book)).fetchall()
    return {(book, c, v): t for c, v, t in rows}


def _superscription(bible_db: Path, book: int, chapter: int) -> str | None:
    """The psalm's heading in WEB words. Only where the KJV has a heading too: WEB stores Psalm 119's acrostic
    letters (ALEPH...) there, which aren't a heading."""
    with sqlite3.connect(f"file:{bible_db}?mode=ro", uri=True) as db:
        rows = dict(db.execute("SELECT translation_id, superscription FROM chapters WHERE book_id = ? AND chapter = ? "
                               "AND translation_id IN ('KJV', 'WEB')", (book, chapter)).fetchall())
    if not (rows.get("KJV") or "").strip():
        return None
    return (rows.get("WEB") or "").strip() or None


def context_block(key: tuple[int, int, int], bible_db: Path = APP_BIBLE_DB) -> tuple[str, str]:
    """(heading, text): the surrounding WEB verses, each with its full reference, this verse marked.
    Psalms of up to 30 verses come whole, after their heading (superscription) labelled as such."""
    rows = _book_rows(bible_db, "WEB", key[0])
    keys = ctx.context_keys(key, rows.keys())
    lines = []
    whole = ctx.whole_psalm(key, rows.keys())
    heading = (f"The whole of Psalm {key[1]} (World English Bible):" if whole
               else "Surrounding verses (World English Bible):")
    if key[0] == ctx.PSALMS:
        title = _superscription(bible_db, key[0], key[1])
        lines.append(f"Heading (superscription) of Psalm {key[1]}: {title}" if title
                     else f"Psalm {key[1]} has no heading (superscription).")
    for k in keys:
        mark = " (this verse)" if k == key else ""
        lines.append(f"{verse_label(k)}{mark}: {rows[k]}")
    return heading, "\n".join(lines)


def request_values(study: dict, bible_db: Path = APP_BIBLE_DB, word_limits: dict | None = None) -> dict:
    key = parse_id(study["id"])
    b, c, v = key
    kjv = _verse_rows(bible_db, "KJV", b, c, v, v)
    web = _verse_rows(bible_db, "WEB", b, c, v, v)
    if not kjv or not web:
        raise LookupError(f"No KJV/WEB text for {study['id']} in {bible_db}")
    heading, context_text = context_block(key, bible_db)
    words = study["originalLanguage"]["words"]
    greek = study["originalLanguage"].get("language") == "greek"
    word_limits = word_limits or limits()
    usfm = BOOKS[b - 1].usfm
    values = {
        "verse_label": verse_label(key),
        "kjv_text": kjv[0][1],
        "web_text": web[0][1],
        "context_heading": heading,
        "context_text": context_text,
        "book_background": ctx.book_background(usfm) or "(none)",
        "text_note": ctx.text_note(key) or "(none)",
        "original_words": "\n".join(f"- {ctx.clean_translit(w['transliteration'], greek)} ({w['surface']}) = "
                                    f"{w['gloss']}" for w in words) or "(none listed)",
        "cross_references": "\n".join(f"- {ref_label(x['ref'])}" for x in study["crossReferences"])
                            or "(none)",
        **{f"{k}_words": f"{lim['min']}-{lim['max']}" for k, lim in word_limits.items()},
    }
    return values


def supplied_text(study: dict, bible_db: Path = APP_BIBLE_DB, prompt_file: Path = PROMPT_FILE) -> str:
    """The verse part of the request (verse, KJV/WEB text, surrounding verses, book background, original words,
    related passages), exactly as the writer saw it in batch mode, without the fixed instructions. The rubric's
    contextMismatch item quotes its support from this text."""
    head, _ = split_request(Prompt.load(prompt_file))
    return fill(head, request_values(study, bible_db))


def parse_sections(text: str | None) -> dict[str, str]:
    """The six prose sections from a response's JSON text. Raises DraftFailed if unusable."""
    if text is None:
        raise DraftFailed("no text in the response")
    try:
        data = json.loads(text)
    except json.JSONDecodeError as error:
        raise DraftFailed(f"not JSON: {error}") from error
    from .checks import OPTIONAL_SECTIONS
    missing = [k for k in PROSE_FIELDS if not isinstance(data.get(k), str)
               or (not data[k].strip() and k not in OPTIONAL_SECTIONS)]
    if missing:
        raise DraftFailed(f"empty sections: {missing}")
    return {k: data[k].strip() for k in PROSE_FIELDS}


# ---- Writer (one direct request per verse) -----------------------------------------------------------------

@dataclass(frozen=True)
class Written:
    sections: dict[str, str]
    model: str


class StudyWriter:
    """Direct Messages API calls. Credentials from the environment (ANTHROPIC_API_KEY or `ant auth login`)."""

    def __init__(self, model: str = DEFAULT_MODEL, effort: str = DEFAULT_EFFORT, client=None):
        from .offline import anthropic_client

        self.client = client or anthropic_client()
        self.model = model
        self.effort = effort
        self.usage = Usage()
        self.requests: list[dict] = []  # per-request token log (thinking, written, cache reads)
        self._lock = threading.Lock()

    def write(self, system: str, request: str, study_id: str = "") -> Written:
        extra = {}
        if self.model in FALLBACK_MODELS:
            extra = {"betas": ["server-side-fallback-2026-07-01"], "fallbacks": "default"}
        with self.client.beta.messages.stream(
            model=self.model,
            max_tokens=MAX_TOKENS,
            system=system,
            messages=[{"role": "user", "content": request}],
            output_config={"effort": self.effort, "format": {"type": "json_schema", "schema": OUTPUT_SCHEMA}},
            **extra,
        ) as stream:
            message = stream.get_final_message()
        with self._lock:
            self.usage.add(message.usage)
            self.requests.append(request_log(study_id, message))
        if message.stop_reason == "refusal":
            raise DraftRefused(f"declined (request {getattr(message, '_request_id', '?')})")
        if message.stop_reason == "max_tokens":
            raise DraftFailed("response was cut off at max_tokens")
        text = next((b.text for b in message.content if b.type == "text"), None)
        return Written(parse_sections(text), message.model)


def _api_message(error) -> str:
    body = getattr(error, "body", None)
    if isinstance(body, dict):
        return (body.get("error") or {}).get("message") or str(error)
    return str(error)


# ---- Selection and run --------------------------------------------------------------------------------------

def select_ids(source: StudyStore, wanted: list[str]) -> list[str]:
    """'PHP' picks a book, 'PHP.4' a chapter, 'PHP.4.13' a verse. Nothing is not allowed (too costly)."""
    if not wanted:
        raise SystemExit("Name the books, chapters or verses to generate (e.g. PHP, PHP.4, PHP.4.13).")
    all_ids = source.ids()
    chosen, unknown = [], []
    for w in wanted:
        hits = [i for i in all_ids if i == w or i.startswith(w + ".")]
        (chosen.extend(hits) if hits else unknown.append(w))
    if unknown:
        raise SystemExit(f"No built studies match: {', '.join(unknown)} (run `studies` first?)")
    return list(dict.fromkeys(chosen))


@dataclass
class RunResult:
    written: int = 0
    skipped: int = 0
    failed: list[str] = field(default_factory=list)
    stopped: str | None = None  # why the run stopped early
    seconds: float = 0.0


def generate(ids: list[str], writer: StudyWriter, out_dir: Path, *, source_dir: Path = STUDIES_DIR,
             force: bool = False, workers: int = 4, prompt: Prompt | None = None,
             bible_db: Path = APP_BIBLE_DB, log=print) -> RunResult:
    prompt = prompt or Prompt.load(PROMPT_FILE)
    word_limits = limits()
    source = StudyStore(source_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    target = StudyStore(out_dir)
    result = RunResult()

    todo = []
    for study_id in ids:
        existing = target.load(study_id) if target.path(study_id).exists() else None
        if existing and (existing.get("status") == "reviewed" or existing.get("meta", {}).get("editedBy")):
            log(f"{study_id}: skipped (reviewed/edited; never overwritten)")
            result.skipped += 1
        elif existing and is_written(existing) and not force:
            log(f"{study_id}: skipped (already written; use --force)")
            result.skipped += 1
        else:
            todo.append(study_id)

    def one(study_id: str) -> str:
        base = source.load(study_id)
        written = writer.write(prompt.system, build_request(base, prompt, bible_db, word_limits), study_id)
        study = dict(base)
        study.update(written.sections)
        study["status"] = "generated"
        study["meta"] = {"model": written.model, "effort": writer.effort, "maxTokens": MAX_TOKENS,
                         "generatedAt": now(), "promptVersion": prompt.version}
        study.pop("checks", None)
        target.save(study)
        return study_id

    import anthropic

    started = time.monotonic()
    try:
        with ThreadPoolExecutor(max_workers=max(1, workers)) as pool:
            futures = {pool.submit(one, i): i for i in todo}
            for future in as_completed(futures):
                study_id = futures[future]
                try:
                    future.result()
                    result.written += 1
                    log(f"{study_id}: written")
                except DraftRefused as error:
                    result.failed.append(f"{study_id}: refused ({error})")
                    log(f"{study_id}: refused ({error}); needs a person to write it")
                except (DraftFailed, LookupError) as error:
                    result.failed.append(f"{study_id}: {error}")
                    log(f"{study_id}: failed ({error}); safe to retry")
                except (anthropic.RateLimitError, anthropic.APIConnectionError,
                        anthropic.InternalServerError) as error:  # already retried by the SDK
                    result.failed.append(f"{study_id}: {type(error).__name__}")
                    log(f"{study_id}: failed ({type(error).__name__}); safe to retry")
                except anthropic.APIStatusError as error:
                    # 400/401/403 etc. (bad request, no credit, bad key): every other request would fail too.
                    result.stopped = f"{type(error).__name__} {error.status_code}: {_api_message(error)}"
                    log(f"Stopping: {result.stopped}")
                    for f in futures:
                        f.cancel()
                    break
    finally:
        result.seconds = time.monotonic() - started
        summary = {
            "model": writer.model, "effort": writer.effort, "mode": "direct", "promptVersion": prompt.version,
            "finishedAt": now(), "requested": len(ids), "written": result.written, "skipped": result.skipped,
            "failed": result.failed, "stopped": result.stopped, "seconds": round(result.seconds, 1),
            "workers": workers, "usage": writer.usage.as_dict(writer.model),
            "outputTokens": output_stats(writer.requests), "requests": writer.requests,
        }
        (out_dir / "generate_summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    return result

