"""`python -m pipeline translate ...`: see engine.py for the flow and content.py for where results live."""
from __future__ import annotations

import argparse
import json

from . import content as c
from .engine import (DRIFT_MODEL, LANGUAGES, TRANSLATE_MODEL, AnthropicDriftChecker, AnthropicTranslator,
                     TranslationUnavailable, Unit, run_unit, translation_request)


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
    return f"plans ({lang}): " + ", ".join(f"{k} {v}" for k, v in sorted(counts.items()))


def flagged(lang: str) -> list[str]:
    _, plans = _plans()
    out = []
    for pid, p in plans.items():
        loc = p.get("i18n", {}).get(lang)
        if loc and loc["status"] == "flagged":
            out.append(f"{pid}:")
            out += [f"  - {r.get('key', '')} [{r['check']}] {r['message']}" for r in loc.get("checks", {}).get("reasons", [])]
    return out or [f"No flagged {lang} plan translations."]


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
        out = translate_devotionals(args)
    elif cmd == "estimate":
        out = [estimate(args.what, args.lang)]
    elif cmd == "status":
        out = [status(args.lang)]
    elif cmd == "flagged":
        out = flagged(args.lang)
    else:
        out = approve(args)
    print("\n".join(out))
