"""`python -m pipeline translate ...`: see engine.py for the flow and content.py for where results live."""
from __future__ import annotations

import argparse
import json

from . import content as c
from .engine import (DRIFT_MODEL, DRIFT_SCHEMA, LANGUAGES, TRANSLATE_MODEL, AnthropicDriftChecker,
                     AnthropicTranslator, Outcome, TranslationUnavailable, Unit, drift_request, free_checks,
                     is_blocking, prompt_hash, run_unit, translation_request)


def add_parser(sub) -> None:
    tr = sub.add_parser("translate", help="localize plans/devotionals: translate (Opus 5.5) + drift check (Sonnet 5.5)")
    tr_sub = tr.add_subparsers(dest="translate_command", required=True)
    for name, text in (("plans", "translate plan titles, descriptions, day titles/intros and category names"),
                       ("devotionals", "translate drafted devotionals (needs the target Bible's text for quotes)")):
        p = tr_sub.add_parser(name, help=text)
        p.add_argument("--lang", default="es", choices=sorted(LANGUAGES))
        p.add_argument("ids", nargs="*", help="only these ids (default: all that need it)")
        p.add_argument("--force", action="store_true", help="redo checked and reviewed translations too")
        p.add_argument("--retry-flagged", action="store_true", help="redo flagged translations")
        p.add_argument("--model", default=TRANSLATE_MODEL)
        p.add_argument("--drift-model", default=DRIFT_MODEL)
        p.add_argument("--yes", action="store_true", help="don't ask before spending (the estimate is still printed)")
    est = tr_sub.add_parser("estimate", help="rough cost before running (no API calls)")
    est.add_argument("what", choices=["plans", "devotionals"])
    est.add_argument("--lang", default="es", choices=sorted(LANGUAGES))
    st = tr_sub.add_parser("status", help="translations by status")
    st.add_argument("--lang", default="es", choices=sorted(LANGUAGES))
    fl = tr_sub.add_parser("flagged", help="list flagged translations with the reasons")
    fl.add_argument("--lang", default="es", choices=sorted(LANGUAGES))
    rq = tr_sub.add_parser("requests", help="write devotional translation requests in batches (batchNN.md) for "
                                            "Claude Code subagents; no API calls")
    rq.add_argument("--lang", default="es", choices=sorted(LANGUAGES))
    rq.add_argument("--out", required=True, help="folder for batchNN.md files")
    rq.add_argument("--batch-size", type=int, default=10)
    rq.add_argument("--start", type=int, default=1, help="number of the first batch file")
    rq.add_argument("--retry-flagged", action="store_true",
                    help="only flagged devotionals, each with its problems as feedback (the one retry)")
    rq.add_argument("ids", nargs="*")
    dr = tr_sub.add_parser("drift-requests", help="write drift-check requests (drift_batchNN.md) from batchNN.json")
    dr.add_argument("--lang", default="es", choices=sorted(LANGUAGES))
    dr.add_argument("--dir", required=True, help="folder holding batchNN.json translations")
    dr.add_argument("batches", nargs="*", help="batch numbers (default: every batchNN.json without a drift file)")
    im = tr_sub.add_parser("import", help="store subagent translations + drift results (no API calls)")
    im.add_argument("what", choices=["devotionals"])
    im.add_argument("--lang", default="es", choices=sorted(LANGUAGES))
    im.add_argument("--translations", required=True, nargs="+", help="JSON {id: {key: text}}")
    im.add_argument("--drift", required=True, nargs="+", help="JSON {id: {issues: [...]}} (engine.DRIFT_SCHEMA)")
    im.add_argument("--model", required=True, help="the model the translating subagent actually ran on")
    im.add_argument("--drift-model", required=True, help="the model the drift subagent actually ran on")
    im.add_argument("--force", action="store_true", help="overwrite auto_checked and reviewed translations too")
    ap = tr_sub.add_parser("approve", help="mark translations reviewed after a person has read them")
    ap.add_argument("ids", nargs="+")
    ap.add_argument("--lang", default="es", choices=sorted(LANGUAGES))
    ap.add_argument("--reviewer", required=True)


# ---- plans ----

def _plans():
    from ..plans import build as pb
    return pb, pb.load_plans()


def confirm(args, what: str) -> bool:
    """Print the estimate; ask unless --yes. A non-interactive run without --yes spends nothing."""
    print(estimate(what, args.lang))
    if getattr(args, "yes", False):
        return True
    try:
        return input("Proceed and spend this? [y/N] ").strip().lower() in ("y", "yes")
    except EOFError:
        return False


def summary(outcomes: list) -> str:
    """auto_checked / flagged (English shown) / retried, and non-blocking notes counted by category."""
    from collections import Counter
    status = Counter(o.status for o in outcomes)
    minor = Counter(m.get("category", "other") for o in outcomes for m in o.minor)
    retried = sum(1 for o in outcomes if o.retried)
    return (f"Done: {status['auto_checked']} auto_checked, {status['flagged']} flagged (English shown), {retried} needed the retry. "
            f"Minor notes: {sum(minor.values())}" + (f" ({', '.join(f'{k} {v}' for k, v in sorted(minor.items()))})" if minor else ""))


def translate_plans(args, translator=None, checker=None) -> list[str]:
    pb, plans = _plans()
    lines = []
    wanted = [pid for pid, p in plans.items()
              if (not args.ids or pid in args.ids) and pb.publishable(p)
              and c.needs_translation(p, args.lang, args.force, args.retry_flagged)]
    if translator is None and (wanted or args.force) and not confirm(args, "plans"):
        return ["Nothing sent."]
    translator = translator or AnthropicTranslator(model=args.model)
    checker = checker or AnthropicDriftChecker(model=args.drift_model)
    outcomes = []
    for pid in wanted:
        plan = plans[pid]
        try:
            outcome = run_unit(c.plan_unit(plan), args.lang, translator, checker, reuse=c.plan_reuse(plans, args.lang))
            outcomes.append(outcome)
        except TranslationUnavailable as error:
            lines.append(f"{pid}: left unchanged ({error})")
            continue
        if not c.apply_plan(plan, args.lang, outcome):
            lines.append(f"{pid}: not stored ({outcome.reasons[0]['message'] if outcome.reasons else 'no text'})")
            continue
        errors = list(pb.schema_validator().iter_errors(plan))
        if errors:
            plan["i18n"].pop(args.lang)
            lines.append(f"{pid}: not stored, fails schema: {errors[0].message}")
            continue
        pb.write_json(pb.PLANS_DIR / f"{pid}.json", plan)
        lines.append(f"{pid}: {outcome.status}" + (" after retry" if outcome.retried else "")
                     + (f" ({outcome.reasons[0]['message']})" if outcome.reasons else ""))
    lines += translate_categories(args, translator, checker)
    lines.append(summary(outcomes))
    pb.write_json(pb.PLANS_DIR / "index.json", pb.build_index(pb.load_plans()))
    lines.append(_cost_line(translator, checker))
    return lines


def translate_categories(args, translator, checker) -> list[str]:
    from ..plans import build as pb
    from ..plans.catalogue import CATEGORIES
    store = c.load_categories_i18n(pb.CATEGORIES_I18N)
    version = c.categories_version(CATEGORIES)
    loc = store.get(args.lang)
    up_to_date = loc and loc["sourceVersion"] == version
    if up_to_date and not args.force and not (args.retry_flagged and loc["status"] == "flagged"):
        return []
    try:
        outcome = run_unit(c.categories_unit(CATEGORIES), args.lang, translator, checker)
    except TranslationUnavailable as error:
        return [f"categories: left unchanged ({error})"]
    if not outcome.texts:
        return [f"categories: not stored ({outcome.reasons[0]['message']})"]
    meta, checks = c._record(outcome)
    store[args.lang] = {"status": outcome.status, "sourceVersion": version,
                        "titles": {k: outcome.texts[k] for k in c.categories_unit(CATEGORIES).fields if k in outcome.texts},
                        "meta": meta, "checks": checks}
    pb.write_json(pb.CATEGORIES_I18N, store)
    return [f"categories: {outcome.status}"]


# ---- devotionals ----

def target_scripture(lang: str, db=None):
    """
    devotional -> the target Bible's text of its passages (verses, then any extraVerses), one "BOOK c:v text" line
    per verse, read from the app's bible.db, so quoted Scripture is the real {bible_name} wording. A passage with no
    verses in that Bible fails loudly rather than sending a request without its Scripture.
    """
    import sqlite3
    from ..books import BY_USFM
    from ..paths import APP_BIBLE_DB
    translation = LANGUAGES[lang]["bible_id"]
    con = sqlite3.connect(f"file:{db or APP_BIBLE_DB}?mode=ro", uri=True)

    def scripture(devotional: dict) -> str:
        lines = []
        for p in [*devotional["verses"], *(devotional.get("extraVerses") or [])]:
            book = BY_USFM[p["book"]]
            rows = con.execute(
                "SELECT chapter, verse, text FROM verses WHERE translation_id = ? AND book_id = ? "
                "AND (chapter > ? OR (chapter = ? AND verse >= ?)) AND (chapter < ? OR (chapter = ? AND verse <= ?)) "
                "ORDER BY ordinal",
                (translation, book.id, p["startChapter"], p["startChapter"], p["startVerse"],
                 p["endChapter"], p["endChapter"], p["endVerse"])).fetchall()
            if not rows:
                raise ValueError(f"{devotional['id']}: no {translation} text for {p}")
            lines += [f"{p['book']} {ch}:{v} {' '.join(t.split())}" for ch, v, t in rows if t.strip()]
        return "\n".join(lines)
    return scripture


def translate_devotionals(args, translator=None, checker=None, scripture=None) -> list[str]:
    from ..devotionals.store import DevotionalStore
    store = DevotionalStore()
    ids = [i for i in store.ids() if not args.ids or i in args.ids]
    if not ids:
        return ["No devotionals drafted yet (out/devotionals/ is empty). Run `devotionals draft` first; translate after."]
    if scripture is None:
        return [f"Needs the {LANGUAGES[args.lang]['bible_name']} text so quoted Scripture is the real Bible text, not a "
                "model's translation. It isn't in the pipeline yet (Session 30a); nothing was sent."]
    if translator is None and not confirm(args, "devotionals"):
        return ["Nothing sent."]
    translator = translator or AnthropicTranslator(model=args.model)
    checker = checker or AnthropicDriftChecker(model=args.drift_model)
    from ..devotionals.publish import PUBLISHABLE
    outcomes = []
    lines = []
    for did in ids:
        devotional = store.load(did)
        if devotional.get("status") not in PUBLISHABLE or not c.needs_translation(devotional, args.lang, args.force, args.retry_flagged):
            continue
        try:
            outcome = run_unit(c.devotional_unit(devotional, scripture(devotional)), args.lang, translator, checker)
            outcomes.append(outcome)
        except TranslationUnavailable as error:
            lines.append(f"{did}: left unchanged ({error})")
            continue
        if not c.apply_devotional(devotional, args.lang, outcome):
            lines.append(f"{did}: not stored ({outcome.reasons[0]['message'] if outcome.reasons else 'no text'})")
            continue
        store.save(devotional)
        lines.append(f"{did}: {outcome.status}" + (" after retry" if outcome.retried else ""))
    lines.append(summary(outcomes))
    lines.append(_cost_line(translator, checker))
    return lines


# ---- devotionals through Claude Code subagents (no API): requests -> subagents -> drift-requests -> import ----

def _batch_files(directory, prefix: str, numbers=None) -> list:
    import re
    from pathlib import Path
    found = sorted(Path(directory).glob(f"{prefix}[0-9][0-9].json"))
    if numbers:
        wanted = {int(n) for n in numbers}
        found = [p for p in found if int(re.search(r"(\d+)\.json$", p.name).group(1)) in wanted]
    return found


def write_requests(args, scripture, store=None) -> list[str]:
    """
    batchNN.md = the exact translate system prompt + one request per devotional ("=== id ===", keys to return,
    engine.translation_request). Every batch gets its own file names so parallel subagents never share one.
    With --retry-flagged: only flagged devotionals, with their blocking problems as Unit.feedback.
    """
    from pathlib import Path
    from ..devotionals.publish import PUBLISHABLE
    from ..devotionals.store import DevotionalStore
    from .engine import _prompt
    store = store or DevotionalStore()
    units = []
    for did in store.ids():
        if args.ids and did not in args.ids:
            continue
        d = store.load(did)
        if d.get("status") not in PUBLISHABLE:
            continue
        loc = d.get("i18n", {}).get(args.lang)
        if args.retry_flagged:
            if not (loc and loc["status"] == "flagged" and loc["sourceVersion"] == d["contentVersion"]):
                continue
        elif not c.needs_translation(d, args.lang):
            continue
        unit = c.devotional_unit(d, scripture(d))
        if args.retry_flagged:
            unit.feedback = [f"{r.get('key', '')}: {r['message']}".lstrip(": ") for r in loc["checks"]["reasons"]]
        units.append(unit)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    system = _prompt("translate", args.lang)
    lines = []
    for n, start in enumerate(range(0, len(units), args.batch_size), args.start):
        chunk = units[start:start + args.batch_size]
        body = [f"# System prompt (follow it exactly)\n\n{system}\n"]
        body += [f"=== {u.id} ===\nKeys to return: {', '.join(u.fields)}\n\n{translation_request(u)}\n" for u in chunk]
        (out / f"batch{n:02d}.md").write_text("\n".join(body))
        lines.append(f"batch{n:02d}.md: {chunk[0].id} .. {chunk[-1].id} ({len(chunk)})")
    return lines or ["Nothing needs translating."]


def _load_json(path) -> dict:
    from pathlib import Path
    try:
        data = json.loads(Path(path).read_text())
    except json.JSONDecodeError as error:
        raise SystemExit(f"{path}: not valid JSON ({error})")
    if not isinstance(data, dict):
        raise SystemExit(f"{path}: expected an object keyed by devotional id")
    return data


def _texts_or_error(entry) -> tuple[dict | None, str | None]:
    if not isinstance(entry, dict) or not all(isinstance(k, str) and isinstance(v, str) for k, v in entry.items()):
        return None, "translation entry isn't {key: text}"
    return {k: v.strip() for k, v in entry.items()}, None


def write_drift_requests(args, scripture, store=None) -> list[str]:
    """drift_batchNN.md from batchNN.json: the exact drift system prompt + engine.drift_request per devotional."""
    from ..devotionals.store import DevotionalStore
    from .engine import _prompt
    store = store or DevotionalStore()
    system = _prompt("drift", args.lang)
    lines = []
    for path in _batch_files(args.dir, "batch", args.batches):
        target = path.with_name(f"drift_{path.stem}.md")
        if not args.batches and target.exists():
            continue
        body = [f"# System prompt (follow it exactly)\n\n{system}\n"]
        for did, entry in _load_json(path).items():
            texts, error = _texts_or_error(entry)
            if error or not store.exists(did):
                lines.append(f"{path.name} {did}: skipped ({error or 'no such devotional'})")
                continue
            d = store.load(did)
            unit = c.devotional_unit(d, scripture(d))
            if free_checks(unit, texts):
                continue  # fails a free check: no drift check spent on it (same as run_unit)
            body.append(f"=== {did} ===\n\n{drift_request(unit, texts)}\n")
        target.write_text("\n".join(body))
        lines.append(f"{target.name}: {len(body) - 1} devotionals")
    return lines or ["No new batchNN.json files."]


def import_devotionals(args, scripture, store=None) -> list[str]:
    """
    Store subagent results exactly like run_unit would: free checks, then the drift issues (blocking by
    engine.is_blocking). auto_checked if nothing blocks, else flagged. A devotional whose free checks pass but has no
    drift result is left unchanged. Never overwrites reviewed (or auto_checked) without --force.
    """
    from jsonschema import Draft202012Validator
    from ..devotionals.publish import PUBLISHABLE
    from ..devotionals.store import DevotionalStore
    store = store or DevotionalStore()
    translations, drift = {}, {}
    for path in args.translations:
        translations.update(_load_json(path))
    for path in args.drift:
        drift.update(_load_json(path))
    drift_validator = Draft202012Validator(DRIFT_SCHEMA)
    outcomes, lines = [], []
    for did, entry in translations.items():
        if not store.exists(did):
            lines.append(f"{did}: no such devotional")
            continue
        d = store.load(did)
        loc = d.get("i18n", {}).get(args.lang)
        if d.get("status") not in PUBLISHABLE:
            lines.append(f"{did}: English isn't publishable ({d.get('status')}); skipped")
            continue
        if loc and loc["sourceVersion"] == d["contentVersion"] and loc["status"] in PUBLISHABLE and not args.force:
            lines.append(f"{did}: already {loc['status']}; not overwritten (--force)")
            continue
        texts, error = _texts_or_error(entry)
        if error:
            lines.append(f"{did}: left unchanged ({error})")
            continue
        unit = c.devotional_unit(d, scripture(d))
        texts = {k: texts[k] for k in unit.fields if k in texts} | {k: v for k, v in texts.items() if k not in unit.fields}
        reasons, minor, result = free_checks(unit, texts), [], None
        if not reasons:
            result = drift.get(did)
            if result is None:
                lines.append(f"{did}: left unchanged (no drift result)")
                continue
            errors = list(drift_validator.iter_errors(result))
            if errors:
                lines.append(f"{did}: left unchanged (drift result fails DRIFT_SCHEMA: {errors[0].message})")
                continue
            for issue in result["issues"]:
                item = {"check": "drift", "key": issue["key"], "category": issue["category"], "message": issue["problem"]}
                (reasons if is_blocking(issue) else minor).append(item)
        retried = bool(loc and loc["status"] == "flagged" and loc["sourceVersion"] == d["contentVersion"])
        outcome = Outcome("flagged" if reasons else "auto_checked", texts, reasons, minor, result,
                          model=args.model, drift_model=args.drift_model if result is not None else None,
                          prompt_hash=prompt_hash("translate", args.lang),
                          drift_prompt_hash=prompt_hash("drift", args.lang), retried=retried)
        if not c.apply_devotional(d, args.lang, outcome):
            lines.append(f"{did}: not stored ({reasons[0]['message'] if reasons else 'no text'})")
            continue
        store.save(d)
        outcomes.append(outcome)
        lines.append(f"{did}: {outcome.status}" + (" after retry" if retried else "")
                     + (f" ({reasons[0].get('key', '')}: {reasons[0]['message'][:100]})" if reasons else ""))
    lines.append(summary(outcomes))
    return lines


# ---- reporting ----

def _cost_line(translator, checker) -> str:
    parts = []
    for name, client in (("translate", translator), ("drift", checker)):
        usage = getattr(client, "usage", None)
        model = getattr(client, "model", None)
        if usage and usage.requests:
            cost = usage.cost(model)
            parts.append(f"{name} {model}: {usage.requests} requests, {usage.input_tokens:,} in / {usage.output_tokens:,} out"
                         + (f", ${cost:.2f}" if cost is not None else ""))
    return "Cost: " + ("; ".join(parts) if parts else "no API calls")


def estimate(what: str, lang: str) -> str:
    """Characters / 4 for input, translation ~1.2x English, plus adaptive thinking (assume as much again). Rough."""
    from ..usage import PRICES
    if what == "plans":
        pb, plans = _plans()
        units = [c.plan_unit(p) for p in plans.values() if pb.publishable(p)]
    else:
        from ..devotionals.store import DevotionalStore
        store = DevotionalStore()
        units = [c.devotional_unit(store.load(i), "") for i in store.ids()]
    if not units:
        return f"No {what} to translate."
    system = 600  # prompt tokens
    t_in = sum(system + len(translation_request(u)) / 4 for u in units)
    t_out = sum(2 * 1.2 * sum(len(v) for v in u.fields.values()) / 4 + 50 for u in units)
    d_in = sum(system + 2.2 * sum(len(v) for v in u.fields.values()) / 4 for u in units)
    d_out = 2 * 200 * len(units)
    i, o = PRICES[TRANSLATE_MODEL][:2]
    di, do = PRICES[DRIFT_MODEL][:2]
    cost = (t_in * i + t_out * o + d_in * di + d_out * do) / 1e6
    return (f"{len(units)} {what} → {lang}: ~{int(t_in):,} in / {int(t_out):,} out ({TRANSLATE_MODEL}), "
            f"~{int(d_in):,} in / {int(d_out):,} out ({DRIFT_MODEL}). Roughly ${cost:.2f} (thinking tokens vary).")


def status(lang: str) -> str:
    from collections import Counter
    pb, plans = _plans()
    counts = Counter(p.get("i18n", {}).get(lang, {}).get("status", "missing") for p in plans.values() if pb.publishable(p))
    for p in plans.values():
        loc = p.get("i18n", {}).get(lang)
        if loc and loc["sourceVersion"] != p["contentVersion"] and loc["status"] != "stale":
            counts["stale (English changed)"] += 1
    line = f"plans ({lang}): " + ", ".join(f"{k} {v}" for k, v in sorted(counts.items()))
    from ..devotionals.store import DevotionalStore
    store = DevotionalStore()
    dcounts = Counter()
    for did in store.ids():
        d = store.load(did)
        loc = d.get("i18n", {}).get(lang)
        dcounts["missing" if not loc else "stale (English changed)" if loc["sourceVersion"] != d["contentVersion"]
                else loc["status"]] += 1
    if dcounts:
        line += f"\ndevotionals ({lang}): " + ", ".join(f"{k} {v}" for k, v in sorted(dcounts.items()))
    return line


def flagged(lang: str) -> list[str]:
    _, plans = _plans()
    out = []
    for pid, p in plans.items():
        loc = p.get("i18n", {}).get(lang)
        if loc and loc["status"] == "flagged":
            out.append(f"{pid}:")
            out += [f"  - {r.get('key', '')} [{r['check']}] {r['message']}" for r in loc.get("checks", {}).get("reasons", [])]
    from ..devotionals.store import DevotionalStore
    store = DevotionalStore()
    for did in store.ids():
        loc = store.load(did).get("i18n", {}).get(lang)
        if loc and loc["status"] == "flagged":
            out.append(f"{did}:")
            out += [f"  - {r.get('key', '')} [{r.get('category', r['check'])}] {r['message']}"
                    for r in loc.get("checks", {}).get("reasons", [])]
    return out or [f"No flagged {lang} translations."]


def approve(args) -> list[str]:
    pb, plans = _plans()
    lines = []
    for pid in args.ids:
        if pid not in plans or args.lang not in plans[pid].get("i18n", {}):
            lines.append(f"{pid}: no {args.lang} translation")
            continue
        c.approve(plans[pid], args.lang, args.reviewer)
        pb.write_json(pb.PLANS_DIR / f"{pid}.json", plans[pid])
        lines.append(f"{pid}: reviewed")
    pb.write_json(pb.PLANS_DIR / "index.json", pb.build_index(pb.load_plans()))
    return lines


def run(args: argparse.Namespace) -> None:
    cmd = args.translate_command
    if cmd == "plans":
        out = translate_plans(args)
    elif cmd == "devotionals":
        out = translate_devotionals(args, scripture=target_scripture(args.lang))
    elif cmd == "requests":
        out = write_requests(args, target_scripture(args.lang))
    elif cmd == "drift-requests":
        out = write_drift_requests(args, target_scripture(args.lang))
    elif cmd == "import":
        out = import_devotionals(args, target_scripture(args.lang))
    elif cmd == "estimate":
        out = [estimate(args.what, args.lang)]
    elif cmd == "status":
        out = [status(args.lang)]
    elif cmd == "flagged":
        out = flagged(args.lang)
    else:
        out = approve(args)
    print("\n".join(out))
