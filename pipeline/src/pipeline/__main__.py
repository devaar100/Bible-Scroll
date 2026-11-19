"""python -m pipeline download|build|studies|generate|check|review|progress|report|reports|publish|bundle|plans|devotionals|all  (from pipeline/, with src on the path)."""
from __future__ import annotations

import argparse
import json
import os
import re
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser(prog="pipeline", description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    dl = sub.add_parser("download", help="fetch every source into data/raw/ (skips files already present)")
    dl.add_argument("--force", action="store_true", help="re-download even if present")
    sub.add_parser("build", help="build data/source.db and out/versification_report.md from data/raw/")
    sub.add_parser("studies", help="write/refresh out/studies/*.json data sections; keeps prose and reviews")

    check = sub.add_parser("check", help="auto-check written studies: status -> auto_checked or flagged")
    check.add_argument("ids", nargs="*", help="only these study ids (e.g. JHN.3.16)")
    check.add_argument("--no-ai", action="store_true", help="skip the AI rubric (free checks only)")
    check.add_argument("--rubric-always", action="store_true", help="run the rubric even when a free check failed")
    check.add_argument("--recheck", action="store_true", help="also re-check auto_checked and flagged studies")
    check.add_argument("--limit", type=int, help="stop after checking this many studies")
    check.add_argument("--out", help="check studies in this folder instead of out/studies (e.g. out/test_haiku)")

    gen = sub.add_parser("generate", help="write study prose with Claude, one direct request per verse (status generated)")
    gen.add_argument("ids", nargs="+", help="books, chapters or verses: PHP, PHP.4, PHP.4.13")
    gen.add_argument("--model", default=None, help="Claude model id (default claude-haiku-5-5)")
    gen.add_argument("--effort", default=None, choices=["low", "medium", "high", "xhigh", "max"],
                     help="output effort (default high)")
    gen.add_argument("--out", help="write to this folder instead of out/studies (e.g. out/test_haiku)")
    gen.add_argument("--force", action="store_true", help="rewrite studies that already have prose (never reviewed/edited ones)")
    gen.add_argument("--workers", type=int, default=4, help="parallel requests (default 4)")

    review = sub.add_parser("review", help="local review tool on http://127.0.0.1:8765")
    review.add_argument("--reviewer", default=os.environ.get("PIPELINE_REVIEWER"),
                        help="your name for reviewedBy (else asked in the page)")
    review.add_argument("--port", type=int, default=8765)

    sub.add_parser("progress", help="counts by status per book (also writes out/progress.md)")

    rep = sub.add_parser("report", help="flag studies someone reported: status flagged, queued for regeneration, "
                                        "kept out of publish until regenerated, edited or reviewed")
    rep.add_argument("ids", nargs="+", help="study ids, e.g. JHN.3.16 PSA.23.1")
    rep.add_argument("--reason", required=True, help="what's wrong (stored in the study's checks and the queue)")
    rep.add_argument("--by", default=os.environ.get("PIPELINE_REVIEWER"), help="who recorded it (optional)")
    reps = sub.add_parser("reports", help="list studies flagged by `pipeline report`")
    reps.add_argument("--all", action="store_true", help="also list reports resolved since (prose regenerated, edited or reviewed)")

    publish = sub.add_parser("publish", help="package auto_checked/reviewed studies per book and deploy to Firebase Hosting")
    mode = publish.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", help="show what would change against the live site; write nothing")
    mode.add_argument("--local", action="store_true", help="build out/site/ only (for `bundle` or inspection); no deploy")
    mode.add_argument("--preview", action="store_true", help="deploy to a 7-day Firebase preview channel, not live")
    publish.add_argument("--yes", action="store_true", help="deploy without the confirmation prompt")

    sub.add_parser("bundle", help="copy Gospels + Psalms book files from out/site/ into the app's assets")

    plans = sub.add_parser("plans", help="reading plans: build | draft-topical | draft-chronological | validate | bundle")
    plans_sub = plans.add_subparsers(dest="plans_command", required=True)
    plans_sub.add_parser("build", help="(re)build the structural plans and out/plans/index.json")
    topical = plans_sub.add_parser("draft-topical", help="draft topical plans with Claude (status generated -> checked)")
    topical.add_argument("topics", nargs="*", help="topics to draft (default: the catalogue's list)")
    topical.add_argument("--days", type=int, default=7)
    topical.add_argument("--force", action="store_true", help="replace plans that were already reviewed")
    chrono = plans_sub.add_parser("draft-chronological", help="draft the chronological order with Claude (always flagged)")
    chrono.add_argument("--force", action="store_true")
    plans_sub.add_parser("validate", help="re-check every plan and rewrite index.json")
    plans_sub.add_parser("bundle", help="copy publishable plans + index.json into app/src/main/assets/plans/")
    plans_sub.add_parser("vectors", help="write assets/plans/verses.bin and core/plans splitting vectors (custom plans)")
    devo = sub.add_parser("devotionals", help="daily devotionals: calendar | redraft-series | draft | check | bundle")
    devo_sub = devo.add_subparsers(dest="devotionals_command", required=True)
    devo_sub.add_parser("calendar", help="validate devotionals/calendar.csv and show the OT/NT balance")
    redraft = devo_sub.add_parser("redraft-series", help="replace one calendar series' themes and anchors with Claude")
    redraft.add_argument("first_date", help="MM-DD of the series' first day")
    redraft.add_argument("--title", help="new series title")
    redraft.add_argument("--guidance", default="", help="what you want instead")
    redraft.add_argument("--force", action="store_true", help="even if devotionals in the series are reviewed")
    draft = devo_sub.add_parser("draft", help="draft devotionals with Claude (status generated)")
    draft.add_argument("dates", nargs="*", help="MM-DD dates or MM months (default: all 366)")
    draft.add_argument("--stale", action="store_true", help="redraft unreviewed, unedited ones whose calendar row or prompt changed")
    draft.add_argument("--force", action="store_true", help="redraft even reviewed or edited ones")
    draft.add_argument("--limit", type=int, help="stop after this many days")
    dcheck = devo_sub.add_parser("check", help="auto-check drafted devotionals: status -> auto_checked or flagged")
    dcheck.add_argument("ids", nargs="*", help="only these (devotional-MM-DD)")
    dcheck.add_argument("--no-ai", action="store_true")
    dcheck.add_argument("--reviewer-model", default=None, choices=["claude-haiku-5-5", "claude-sonnet-5-5"],
                        help="AI reviewer for the rubric (default: claude-haiku-5-5, rubric.RUBRIC_MODEL)")
    dcheck.add_argument("--rubric-always", action="store_true")
    dcheck.add_argument("--recheck", action="store_true")
    dcheck.add_argument("--limit", type=int)
    dbundle = devo_sub.add_parser("bundle", help="copy the current and next month from out/site/ into the app's assets")
    dbundle.add_argument("--date", help="YYYY-MM-DD release date (default: today)")
    batch = sub.add_parser("batch", help="study prose via the Message Batches API (claude-haiku-5-5, effort medium)")
    batch_sub = batch.add_subparsers(dest="batch_command", required=True)
    for name, text in (("estimate", "verse count and projected cost; sends nothing (--wave all: every wave)"),
                       ("submit", "submit attempt 1 for the verses, in batches of at most waves.yaml batch_size")):
        b = batch_sub.add_parser(name, help=text)
        b.add_argument("ids", nargs="*", help="books, chapters or verses (PHP, PHP.4, PHP.4.13)")
        b.add_argument("--wave", help="a wave from waves.yaml instead of ids (discover = discover.txt, in order)")
        b.add_argument("--out", help="write studies to this folder instead of out/studies")
        b.add_argument("--force", action="store_true",
                       help="include verses that already have prose or failed twice (never reviewed/edited); "
                            "they start a fresh attempt count")
        b.add_argument("--run", help="label for an ids run (default adhoc): its summaries go to "
                                     "out/batch_runs/<label>/; retries keep the label")
        b.add_argument("--limit", type=int, help="only the first N verses still to send, in list order")
        b.add_argument("--stale", action="store_true",
                       help="only verses already written with an older prompt (regenerate; fresh attempt count)")
    batch_sub.choices["estimate"].add_argument("--count-tokens", action="store_true",
                                               help="measure the fixed block with the free token-counting endpoint")
    batch_sub.add_parser("status", help="poll open batches, write the summaries")
    collect = batch_sub.add_parser("collect", help="write ended batches, run checks + Haiku rubric, submit one retry round")
    collect.add_argument("--no-retry", action="store_true", help="don't submit the retry round")
    collect.add_argument("--no-ai", action="store_true", help="free checks only (no Haiku rubric)")
    bcheck = batch_sub.add_parser("check", help="re-run the checks on collected verses (no generation, no retry)")
    bcheck.add_argument("--wave", help="only this wave")
    bcheck.add_argument("--recheck", action="store_true", help="also verses already auto_checked/flagged")
    bcheck.add_argument("--no-ai", action="store_true", help="free checks only (no Haiku rubric)")
    batch_sub.add_parser("report", help="write out/batch_runs/<wave>/generate_summary.json + check_summary.json "
                                        "and print them (no API calls)")
    bback = batch_sub.add_parser("backup", help="zip out/ and state/ into backups/ (same as `pipeline backup`)")
    bback.add_argument("--dest", help="folder for the zip (default pipeline/backups)")
    prep = sub.add_parser("prepare", help="write the exact batch requests to jobs/NAME/ (batch-NNN.jsonl, manifest.json, "
                                          "schedule.json); sends nothing")
    which = prep.add_mutually_exclusive_group(required=True)
    which.add_argument("--ids", help="file with one verse id per line (# comments allowed)")
    which.add_argument("--wave", help="a wave from waves.yaml (discover = discover.txt, in order)")
    prep.add_argument("--limit", type=int, help="only the first N verses")
    prep.add_argument("--out", required=True, help="new job folder, e.g. jobs/discover_test10/")
    prep.add_argument("--studies-out", help="where `batch collect` writes the studies (default out/studies)")
    rp = sub.add_parser("replay", help="every code check on the studies in DIR (no AI); prints limits and length bands")
    rp.add_argument("dir", help="a folder of study files, e.g. out/cc_test/round1")
    sm = sub.add_parser("submit", help="send the next pending batch file of a prepared job (refuses on prompt drift)")
    sm.add_argument("job", help="jobs/NAME")
    sm.add_argument("--next", action="store_true", required=True, help="the next pending batch file (one per call)")
    sm.add_argument("--dry-run", action="store_true", help="all checks, nothing sent (works with PIPELINE_OFFLINE=1)")
    cc = sub.add_parser("cctest", help="Claude Code test rounds (out/cc_test/roundN; test artifacts, never published)")
    cc_sub = cc.add_subparsers(dest="cc_command", required=True)
    m = cc_sub.add_parser("merge", help="one study-writer reply -> out/cc_test/roundN/ID.json")
    m.add_argument("--round", type=int, required=True)
    m.add_argument("--job", required=True, help="jobs/cc_roundN")
    m.add_argument("id")
    m.add_argument("reply", help="file holding the writer's reply")
    m.add_argument("--model", required=True, help="the model the subagent actually ran on (from its transcript)")
    m.add_argument("--attempt", type=int, default=1)
    pk = cc_sub.add_parser("packet", help="print reviewer input for studies")
    pk.add_argument("--round", type=int, required=True)
    pk.add_argument("ids", nargs="+")
    pk.add_argument("--from", dest="src", help="read the studies from this folder (canaries: out/studies)")
    pk.add_argument("--prefix", default="", help='id prefix in the packet, e.g. "canary:"')
    bl = cc_sub.add_parser("blind", help="write shuffled, unlabelled reviewer batches (review/batch-K.txt + labels.json)")
    bl.add_argument("--round", type=int, required=True)
    bl.add_argument("--canaries", help="folder of canary studies (out/canaries; read only)")
    bl.add_argument("--size", type=int, default=5)
    bl.add_argument("--seed", type=int, default=0)
    rt = cc_sub.add_parser("retry", help="reviewer packet for regenerated studies under their blind labels")
    rt.add_argument("--round", type=int, required=True)
    rt.add_argument("ids", nargs="+")
    vf = cc_sub.add_parser("verify", help="check reviewer findings' studyQuotes verbatim; write review.json")
    vf.add_argument("--round", type=int, required=True)
    vf.add_argument("findings", nargs="+", help="reviewer JSON files (retry replies last: they replace earlier reviews)")
    vf.add_argument("--canaries-from", help="folder holding the canary studies (out/studies; read only)")
    rpt = cc_sub.add_parser("report", help="print the round table")
    rpt.add_argument("--round", type=int, required=True)
    sub.add_parser("discover", help="audit discover.txt: bad ids, duplicates, verses whose Hebrew/Greek numbering differs")
    bk = sub.add_parser("backup", help="zip out/ and state/ into backups/")
    bk.add_argument("--dest", help="folder for the zip (default pipeline/backups)")
    from .translate.cli import add_parser as add_translate
    add_translate(sub)
    sub.add_parser("all", help="download, build, then studies")
    args = parser.parse_args()

    if args.command in ("download", "all"):
        from .download import download
        download(force=getattr(args, "force", False))
    if args.command in ("build", "all"):
        from .build import build
        build()
    if args.command in ("studies", "all"):
        from .studies import build_studies
        build_studies()
    if args.command == "check":
        run_check(args)
    if args.command in ("prepare", "replay", "submit"):
        run_jobs(args)
    if args.command == "cctest":
        run_cctest(args)
    if args.command == "generate":
        run_generate(args)
    if args.command == "batch":
        run_batch(args)
    if args.command == "discover":
        from .discover import audit, render
        report = audit()
        print(render(report))
        if not report.clean:
            raise SystemExit("discover.txt has problems (above); fix them before submitting or publishing.")
    if args.command == "backup":
        from . import batch as bt
        from .paths import PIPELINE
        dest = Path(args.dest) if args.dest else PIPELINE / "backups"
        target = bt.backup(dest)
        print(f"Backup: {target} ({target.stat().st_size / 1e6:.1f} MB)")
    if args.command == "review":
        from .paths import APP_BIBLE_DB, SOURCE_DB
        from .review.app import serve
        from .store import Bible, StudyStore
        from .studies import STUDIES_DIR
        serve(StudyStore(STUDIES_DIR), Bible(SOURCE_DB, APP_BIBLE_DB), args.reviewer, args.port)
    if args.command in ("report", "reports"):
        run_reports(args)
    if args.command == "publish":
        run_publish(args)
    if args.command == "bundle":
        run_bundle()
    if args.command == "plans":
        run_plans(args)
    if args.command == "devotionals":
        run_devotionals(args)
    if args.command == "translate":
        from .translate.cli import run as run_translate
        run_translate(args)
    if args.command == "progress":
        from .paths import OUT
        from .progress import count, render
        from .store import StudyStore
        from .studies import STUDIES_DIR
        table = render(count(StudyStore(STUDIES_DIR)))
        (OUT / "progress.md").write_text("# Study progress\n\n" + table + "\n")
        print(table)


def run_reports(args) -> None:
    import sys

    from . import reports as r
    from .store import StudyStore
    from .studies import STUDIES_DIR

    store = StudyStore(STUDIES_DIR)
    if args.command == "reports":
        print(r.render(r.list_reports(store, include_resolved=args.all)))
        return
    try:
        results = r.report(store, args.ids, args.reason, by=args.by, queue=r.QUEUE)
    except r.ReportError as error:
        sys.exit(str(error))
    for result in results:
        print(f"{result.id}: flagged and queued in {r.QUEUE.relative_to(r.QUEUE.parents[1])}" if result.added
              else f"{result.id}: already reported for this reason; unchanged")
    print("Held back from publish until regenerated, edited or reviewed. `pipeline publish --dry-run` lists them.")


def run_publish(args) -> None:
    import sys

    from . import publish as p
    from .store import StudyStore
    from .studies import STUDIES_DIR

    config = p.load_config()
    base_url = config["baseUrl"]
    deploying = not (args.dry_run or args.local)
    if deploying and not (config["firebaseProject"] and base_url):
        sys.exit("Set firebaseProject and baseUrl in pipeline/publish.json before deploying (see README).")
    from .devotionals.store import DevotionalStore
    from .community import load_ministries, release_problems
    try:
        community_problems = release_problems(load_ministries())
    except ValueError as error:
        community_problems = [str(error)]
    if community_problems:
        sys.exit("Not publishing: ministries.json fails the release checks:\n  " + "\n  ".join(community_problems))
    release = p.build_release(StudyStore(STUDIES_DIR), base_url, devotionals=DevotionalStore())
    live = p.fetch_live_manifest(base_url) if base_url else None
    live_discover = p.fetch_live_discover(base_url) if live else []
    d = p.diff(release, live, live_discover)
    print(p.render_diff(d, release, live))
    if args.dry_run:
        print("Dry run: nothing written or uploaded.")
        return
    kept = p.write_site(release, keep_from=live)
    print(f"Built {p.SITE} ({len(release.files)} files" + (f", plus {len(kept)} previous book file(s) kept" if kept else "") + ")")
    if args.local:
        return
    if live is not None and not d.any and not args.preview:  # first deploy always goes out (no live manifest yet)
        print("Nothing to deploy.")
        return
    if not args.yes and input(f"Deploy to Firebase project {config['firebaseProject']}{' (preview channel)' if args.preview else ''}? [y/N] ").strip().lower() != "y":
        print("Not deployed.")
        return
    print(p.deploy(config["firebaseProject"], release.manifest["release"], preview=args.preview))
    if args.preview:
        return
    p.save_state(release)
    problems = p.verify_live(base_url, release)
    print("Verified: every live file matches." if not problems else "Problems after deploy:\n  " + "\n  ".join(problems))


def run_plans(args) -> None:
    from .plans import build as plan_build
    from .plans.bible import VerseIndex

    index = VerseIndex.load()
    if args.plans_command == "build":
        print(plan_build.build_structural(index=index))
    elif args.plans_command in ("draft-topical", "draft-chronological"):
        from .plans import ai
        drafter = ai.AnthropicDrafter()
        if args.plans_command == "draft-topical":
            from .plans.catalogue import TOPICS
            for line in ai.draft_topical(args.topics or TOPICS, drafter, index, days=args.days, force=args.force):
                print(line)
        else:
            print(ai.draft_chronological(drafter, index, force=args.force))
        plan_build.write_json(plan_build.PLANS_DIR / "index.json", plan_build.build_index(plan_build.load_plans()))
    elif args.plans_command == "validate":
        from .plans import ai
        from .plans.validate import check_plan
        for pid, plan in plan_build.load_plans().items():
            if plan["origin"] == "ai_drafted" and plan["status"] != "reviewed":
                plan = ai.revalidate(plan, index)
                plan_build.write_json(plan_build.PLANS_DIR / f"{pid}.json", plan)
                print(f"{pid}: {plan['status']}")
            else:
                problems = check_plan(plan, index)
                print(f"{pid}: {'ok' if not problems else f'{len(problems)} problem(s): ' + problems[0]['message']}")
        plan_build.write_json(plan_build.PLANS_DIR / "index.json", plan_build.build_index(plan_build.load_plans()))
    elif args.plans_command == "bundle":
        shipped = plan_build.bundle()
        print(f"Bundled {len(shipped)} plans into {plan_build.APP_ASSETS}")
        from .plans.verse_data import write_all
        data, vectors = write_all(index)
        print(f"Wrote {data} ({data.stat().st_size:,} bytes) and {vectors}")
    elif args.plans_command == "vectors":
        from .plans.verse_data import write_all
        data, vectors = write_all(index)
        print(f"Wrote {data} ({data.stat().st_size:,} bytes) and {vectors}")


def select_days(days, wanted: list[str]):
    """'03-14' picks a day, '03' a month; nothing picks every day."""
    if not wanted:
        return days
    unknown = [w for w in wanted if not any(d.date == w or d.date[:2] == w for d in days)]
    if unknown:
        raise SystemExit(f"Not a calendar date (MM-DD) or month (MM): {', '.join(unknown)}")
    return [d for d in days if d.date in wanted or d.date[:2] in wanted]


def run_devotionals(args) -> None:
    from datetime import date

    from .devotionals import calendar as cal
    from .devotionals.store import DevotionalStore
    from .paths import APP_BIBLE_DB, OUT, SOURCE_DB
    from .store import Bible

    bible = Bible(SOURCE_DB, APP_BIBLE_DB)
    command = args.devotionals_command
    try:
        days = cal.load(verses=bible.verses)
    except cal.CalendarError as error:
        raise SystemExit(str(error))
    if command == "calendar":
        print(f"{cal.CALENDAR}: ok")
        print(json.dumps(cal.balance(days), indent=2))
    elif command == "redraft-series":
        from .devotionals.calendar_ai import redraft_series
        from .plans.ai import AnthropicDrafter
        for d in redraft_series(args.first_date, AnthropicDrafter(), title=args.title, guidance=args.guidance,
                                verses=bible.verses, store=DevotionalStore(), force=args.force):
            print(f"{d.date}  {d.theme}  ({d.anchor.text})")
        print("Calendar updated. Draft these days again with: devotionals draft --stale")
    elif command == "draft":
        from .devotionals.generate import default_drafter, draft_days
        from .devotionals.store import require_studies
        try:
            require_studies()
        except FileNotFoundError as error:
            raise SystemExit(str(error))
        chosen = select_days(days, args.dates)[: args.limit]
        for line in draft_days(chosen, default_drafter(), DevotionalStore(), force=args.force, stale=args.stale):
            print(line)
    elif command == "check":
        from .devotionals.checks import run
        judge = None
        if not args.no_ai:
            from .rubric import RUBRIC_MODEL, AnthropicJudge
            judge = AnthropicJudge(model=args.reviewer_model or RUBRIC_MODEL)
        summary = run(DevotionalStore(), days, bible, judge, recheck=args.recheck,
                      rubric_always=args.rubric_always, ids=args.ids or None, limit=args.limit)
        report = {"checked": summary.checked, "statuses": dict(summary.statuses), "reasons": dict(summary.reasons),
                  "skipped": summary.skipped, "rubric_unavailable": summary.rubric_unavailable}
        (OUT / "devotionals_check_summary.json").write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps(report, indent=2))
    elif command == "bundle":
        from . import publish as p
        from .devotionals.publish import ASSETS, bundle
        today = date.fromisoformat(args.date) if args.date else date.today()
        result = bundle(p.SITE, today)
        print(f"Bundled month(s) {', '.join(result.copied) or 'none'} into {ASSETS} ({result.bytes:,} bytes)")
        if result.missing:
            print(f"Nothing publishable yet for month(s): {', '.join(result.missing)}")
        if result.removed:
            print(f"Removed stale: {', '.join(result.removed)}")


def run_bundle() -> None:
    from . import publish as p
    from .bundle import ASSETS, bundle

    result = bundle()
    config = p.load_config()
    local = json.loads((p.SITE / "manifest.json").read_text())
    live = p.fetch_live_manifest(config["baseUrl"]) if config["baseUrl"] else None
    if not live or live.get("release") != local["release"]:
        print("Warning: these files are from a local release that isn't the live one. Deploy it first, "
              "or the app will re-download these books on first run.")
    print(f"Bundled {', '.join(result.copied) or 'nothing'} into {ASSETS} ({result.bytes:,} bytes)")
    if result.missing:
        print(f"No publishable verses yet for: {', '.join(result.missing)}")
    if result.removed:
        print(f"Removed stale: {', '.join(result.removed)}")


def out_dir(value: str | None):
    """--out: a folder for test runs; relative paths are taken from pipeline/. None -> out/studies."""
    from pathlib import Path

    from .paths import PIPELINE
    from .studies import STUDIES_DIR
    if not value:
        return STUDIES_DIR
    path = Path(value)
    return path if path.is_absolute() else PIPELINE / path


def run_generate(args) -> None:
    from . import generate as g

    writer = g.StudyWriter(model=args.model or g.DEFAULT_MODEL, effort=args.effort or g.DEFAULT_EFFORT)
    target = out_dir(args.out)
    ids = g.select_ids(g.StudyStore(g.STUDIES_DIR), args.ids)
    print(f"Generating {len(ids)} studies with {writer.model} (effort {writer.effort}, direct) into {target}")
    result = g.generate(ids, writer, target, force=args.force, workers=args.workers)
    print(json.dumps(json.loads((target / "generate_summary.json").read_text()), indent=2))
    if result.stopped:
        raise SystemExit(f"Stopped early ({result.stopped}). Fix that, then re-run: written studies are skipped.")
    if result.failed:
        raise SystemExit(f"{len(result.failed)} failed; re-run the same command to retry them.")


def run_batch(args) -> None:
    from . import batch as bt
    from . import generate as g
    from . import waves as wv
    from .paths import APP_BIBLE_DB, PIPELINE, SOURCE_DB
    from .store import Bible, StudyStore

    if args.batch_command == "backup":
        target = bt.backup(Path(args.dest) if args.dest else PIPELINE / "backups")
        print(f"Backed up out/ and state/ to {target}")
        return

    state = bt.State()
    source = StudyStore(g.STUDIES_DIR)
    plan_cfg = wv.load()

    def show(step: str) -> None:
        folders = bt.write_summaries(state)
        print(f"\n== {step} summary (files: {', '.join(str(f.relative_to(PIPELINE)) for f in folders.values())})")
        print(json.dumps(bt.summary(state), indent=2))

    def wave_list(name: str) -> list[str]:
        try:
            ids = wv.wave_ids(plan_cfg.get(name), source.ids())
        except KeyError as error:
            raise SystemExit(str(error))
        except ValueError as error:
            raise SystemExit(f"{error}. Run `python -m pipeline discover` for the full list of problems.")
        if not ids:
            raise SystemExit(f"The {name} wave has no verses.")
        return ids

    if args.batch_command in ("estimate", "submit"):
        if bool(args.ids) == bool(args.wave):
            raise SystemExit("Give either verse ids or --wave (e.g. --wave discover).")
        if args.run and (args.wave or not re.fullmatch(r"[A-Za-z0-9_-]+", args.run) or args.run in plan_cfg.names
                         or args.run == "all"):
            raise SystemExit("--run takes a label of letters, digits, _ and - for an ids run, not a wave name.")
        if args.wave == "all" and args.batch_command == "submit":
            raise SystemExit("Submit one wave at a time (--wave all is for estimate only).")
        target = StudyStore(out_dir(args.out))
        fixed = bt.load_fixed()
        if args.count_tokens if args.batch_command == "estimate" else False:
            from .offline import anthropic_client
            fixed_tokens, how = bt.count_fixed_tokens(anthropic_client(), fixed), "counted"
        else:
            # English prose runs about 4 characters per token; submit counts the block exactly before sending.
            fixed_tokens, how = round(len(fixed.system) / 4), "approx (chars/4; --count-tokens for exact)"

        def planned(ids: list[str], earlier: set[str]) -> tuple[list[str], dict]:
            if args.stale and args.force:
                raise SystemExit("--stale and --force don't mix: --stale already picks written verses.")
            todo, skipped = bt.plan(ids, target, state, force=args.force,
                                    stale_prompt=fixed.prompt_version if args.stale else None)
            dup = [i for i in todo if i in earlier]
            if dup:
                skipped["in an earlier wave"] = len(dup)
                todo = [i for i in todo if i not in earlier]
            if args.limit is not None:
                if args.limit < 1:
                    raise SystemExit("--limit must be at least 1")
                if len(todo) > args.limit:
                    skipped[f"beyond --limit {args.limit}"] = len(todo) - args.limit
                    todo = todo[:args.limit]
            return todo, skipped

    if args.batch_command == "estimate":
        cal = bt.find_calibration()
        names = plan_cfg.names if args.wave == "all" else [args.wave] if args.wave else [None]
        rows, earlier = [], set()
        for name in names:
            ids = wave_list(name) if name else g.select_ids(source, args.ids)
            todo, skipped = planned(ids, earlier)
            earlier.update(todo)
            rows.append(bt.estimate_row(name or "ids", ids, todo, skipped, fixed, fixed_tokens, cal, source,
                                        plan_cfg.batch_size, APP_BIBLE_DB))
        report = {
            "model": bt.MODEL, "effort": bt.EFFORT, "maxTokens": bt.MAX_TOKENS, "cacheTtl": bt.CACHE_TTL,
            "batchSize": plan_cfg.batch_size, "promptVersion": fixed.prompt_version, "promptHash": fixed.system_hash,
            "booksHash": fixed.books_hash,
            "fixedBlock": {"tokens": fixed_tokens, "how": how, "chars": len(fixed.system),
                           "minimum": bt.MIN_CACHE_TOKENS, "ok": fixed_tokens >= bt.MIN_CACHE_TOKENS},
            "calibration": None if cal is None else {
                "from": str(cal.folder.relative_to(g.PIPELINE)), "verses": cal.verses,
                "outputPerVerse": round(cal.output_per_verse),
                "flaggedShare": None if cal.retry_rate is None else round(cal.retry_rate, 3)},
            "waves": rows,
        }
        print(json.dumps(report, indent=2))
        print()
        print(bt.estimate_table(rows))
        print("Nothing was sent." if not args.count_tokens else "Only token counting was called (free).")
        return

    if args.batch_command == "report":
        show("report")
        return

    from .offline import anthropic_client
    client = anthropic_client()
    if args.batch_command == "submit":
        ids = wave_list(args.wave) if args.wave else g.select_ids(source, args.ids)
        todo, skipped = planned(ids, set())
        target.directory.mkdir(parents=True, exist_ok=True)
        print(f"{len(todo)} to send; skipped {dict(skipped)}")
        if (args.stale or args.force) and todo:
            bt.restart_attempts(state, todo, target)
        bt.submit(client, todo, 1, target, state, fixed, source, APP_BIBLE_DB, wave=args.wave or args.run or "adhoc",
                  batch_size=plan_cfg.batch_size)
        show("submit")
    elif args.batch_command == "status":
        bt.refresh(client, state)
        show("status")
    elif args.batch_command == "check":
        judge = None
        if not args.no_ai:
            from .rubric import AnthropicJudge
            judge = AnthropicJudge()
        statuses = bt.check_collected(state, Bible(SOURCE_DB, APP_BIBLE_DB), judge, wave=args.wave,
                                      recheck=args.recheck)
        print(f"Checked: {dict(statuses)}")
        show("check")
    elif args.batch_command == "collect":
        bt.refresh(client, state)
        judge = None
        if not args.no_ai:
            from .rubric import AnthropicJudge
            judge = AnthropicJudge()
        retry = bt.collect(client, state, source, Bible(SOURCE_DB, APP_BIBLE_DB), judge)
        show("collect")
        if retry and not args.no_retry:
            groups: dict[tuple[str, str], list[str]] = {}
            placed: set[str] = set()
            for record in state.batches:
                for i in record["ids"]:
                    if i in retry and i not in placed:
                        groups.setdefault((record["outDir"], record.get("wave") or "adhoc"), []).append(i)
                        placed.add(i)
            for (folder, wave), ids in groups.items():
                print(f"Retrying {len(ids)} verses (attempt 2, wave {wave})")
                bt.submit(client, ids, 2, StudyStore(Path(folder)), state, bt.load_fixed(), source, APP_BIBLE_DB,
                          wave=wave, batch_size=plan_cfg.batch_size)
            show("retry submit")
        elif retry:
            print(f"{len(retry)} verses need a retry; run collect without --no-retry to submit them.")


def run_jobs(args) -> None:
    from . import jobs
    from .store import StudyStore

    def here(p: str) -> Path:
        path = Path(p)
        return path if path.is_absolute() else (Path.cwd() / path)

    if args.command == "prepare":
        from . import waves as wv
        from .generate import STUDIES_DIR
        source = StudyStore(STUDIES_DIR)
        ids = jobs.read_ids(here(args.ids)) if args.ids else jobs.wave_ids(args.wave, source)
        if args.limit is not None:
            if args.limit < 1:
                raise SystemExit("--limit must be at least 1")
            ids = ids[:args.limit]
        manifest = jobs.prepare(ids, here(args.out), source=source, batch_size=wv.load().batch_size,
                                studies_out=here(args.studies_out) if args.studies_out else None)
        print(json.dumps({k: v for k, v in manifest.items() if k not in ("ids", "files")}, indent=2))
    elif args.command == "replay":
        report = jobs.replay(here(args.dir))
        if report["rejected"]:
            raise SystemExit(f"{len(report['rejected'])} rejected: {', '.join(report['rejected'])}")
    elif args.command == "submit":
        info = jobs.submit_next(here(args.job), dry_run=args.dry_run)
        if info and not args.dry_run:
            print(f"Submitted {info['file']} as {info['batchId']}; then `pipeline batch status` / `batch collect`.")


def run_cctest(args) -> None:
    from . import cctest as cc
    from .store import StudyStore

    if args.cc_command == "merge":
        study = cc.merge(args.round, Path(args.job), args.id, Path(args.reply).read_text(), args.model, args.attempt)
        print(f"{study['id']}: written to {cc.round_dir(args.round)} (attempt {args.attempt})")
    elif args.cc_command == "packet":
        store = StudyStore(Path(args.src)) if args.src else StudyStore(cc.round_dir(args.round))
        print("\n\n".join(cc.packet(store.load(i), prefix=args.prefix) for i in args.ids))
    elif args.cc_command == "blind":
        labels = cc.blind(args.round, Path(args.canaries) if args.canaries else None, args.size, args.seed)
        batches = sorted({v["batch"] for v in labels.values()})
        print(f"{len(labels)} studies in {len(batches)} batches: {cc.round_dir(args.round) / 'review'}")
    elif args.cc_command == "retry":
        print(cc.retry_packet(args.round, args.ids))
    elif args.cc_command == "verify":
        studies = {}
        rd = StudyStore(cc.round_dir(args.round))
        studies.update({i: rd.load(i) for i in rd.ids()})
        labels_file = cc.round_dir(args.round) / "review" / "labels.json"
        if labels_file.exists() and not args.canaries_from:  # blind review: labels -> studies, findings re-keyed
            meta = json.loads(labels_file.read_text())
            canary = StudyStore(Path(meta["canaryDir"])) if meta["canaryDir"] else None
            merged = {"studies": [s for f in args.findings for s in json.loads(Path(f).read_text())["studies"]]}
            by_label = {}
            for label, info in meta["labels"].items():
                by_label[label] = (canary if info["source"] == "canary" else rd).load(info["id"])
            review = cc.verify(args.round, merged, by_label)
            review["labels"] = meta["labels"]
            (cc.round_dir(args.round) / "review.json").write_text(json.dumps(review, indent=2, ensure_ascii=False) + "\n")
            kept = sum(len(v) for v in review["findings"].values())
            print(f"Kept {kept} findings; discarded {len(review['discarded'])}")
            for d in review["discarded"]:
                info = meta["labels"][d["id"]]
                print(f"  discarded {d['id']} ({info['source']} {info['id']}): {d['why']}: "
                      f"{d['finding'].get('studyQuote', '')[:100]!r}")
            return
        if args.canaries_from:
            canary = StudyStore(Path(args.canaries_from))
            merged = {"studies": []}
            for f in args.findings:
                merged["studies"] += json.loads(Path(f).read_text())["studies"]
            for e in merged["studies"]:
                if e["id"].startswith("canary:"):
                    studies[e["id"]] = canary.load(e["id"].removeprefix("canary:"))
        else:
            merged = {"studies": [s for f in args.findings for s in json.loads(Path(f).read_text())["studies"]]}
        review = cc.verify(args.round, merged, studies)
        (cc.round_dir(args.round) / "review.json").write_text(json.dumps(review, indent=2, ensure_ascii=False) + "\n")
        kept = sum(len(v) for v in review["findings"].values())
        print(f"Kept {kept} findings; discarded {len(review['discarded'])}")
        for d in review["discarded"]:
            print(f"  discarded {d['id']}: {d['why']}: {d['finding'].get('studyQuote', '')[:100]!r}")
    elif args.cc_command == "report":
        print(cc.report(args.round))


def run_check(args) -> None:
    import time

    from .checks import run
    from .paths import APP_BIBLE_DB, OUT, SOURCE_DB
    from .store import Bible, StudyStore

    judge = None
    if not args.no_ai:
        from .rubric import AnthropicJudge
        judge = AnthropicJudge()
    folder = out_dir(args.out)
    started = time.monotonic()
    summary = run(StudyStore(folder), Bible(SOURCE_DB, APP_BIBLE_DB), judge,
                  recheck=args.recheck, rubric_always=args.rubric_always, ids=args.ids or None, limit=args.limit)
    report = {
        "checked": summary.checked, "statuses": dict(summary.statuses), "reasons": dict(summary.reasons),
        "skipped_not_written": summary.skipped_not_written, "skipped_already_done": summary.skipped_already_done,
        "rubric_unavailable": summary.rubric_unavailable, "seconds": round(time.monotonic() - started, 1),
    }
    if judge is not None:
        report["rubric_usage"] = judge.usage.as_dict(judge.model)
    summary_file = OUT / "check_summary.json" if not args.out else folder / "check_summary.json"
    summary_file.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
