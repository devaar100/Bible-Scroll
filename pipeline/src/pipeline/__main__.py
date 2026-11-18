"""python -m pipeline download|build|studies|check|review|progress|publish|bundle|all  (from pipeline/, with src on the path)."""
from __future__ import annotations

import argparse
import json
import os


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

    review = sub.add_parser("review", help="local review tool on http://127.0.0.1:8765")
    review.add_argument("--reviewer", default=os.environ.get("PIPELINE_REVIEWER"),
                        help="your name for reviewedBy (else asked in the page)")
    review.add_argument("--port", type=int, default=8765)

    sub.add_parser("progress", help="counts by status per book (also writes out/progress.md)")

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
    if args.command == "review":
        from .paths import APP_BIBLE_DB, SOURCE_DB
        from .review.app import serve
        from .store import Bible, StudyStore
        from .studies import STUDIES_DIR
        serve(StudyStore(STUDIES_DIR), Bible(SOURCE_DB, APP_BIBLE_DB), args.reviewer, args.port)
    if args.command == "publish":
        run_publish(args)
    if args.command == "bundle":
        run_bundle()
    if args.command == "plans":
        run_plans(args)
    if args.command == "progress":
        from .paths import OUT
        from .progress import count, render
        from .store import StudyStore
        from .studies import STUDIES_DIR
        table = render(count(StudyStore(STUDIES_DIR)))
        (OUT / "progress.md").write_text("# Study progress\n\n" + table + "\n")
        print(table)


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
    release = p.build_release(StudyStore(STUDIES_DIR), base_url)
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


def run_check(args) -> None:
    from .checks import run
    from .paths import APP_BIBLE_DB, OUT, SOURCE_DB
    from .store import Bible, StudyStore
    from .studies import STUDIES_DIR

    judge = None
    if not args.no_ai:
        from .rubric import AnthropicJudge
        judge = AnthropicJudge()
    summary = run(StudyStore(STUDIES_DIR), Bible(SOURCE_DB, APP_BIBLE_DB), judge,
                  recheck=args.recheck, rubric_always=args.rubric_always, ids=args.ids or None, limit=args.limit)
    report = {
        "checked": summary.checked, "statuses": dict(summary.statuses), "reasons": dict(summary.reasons),
        "skipped_not_written": summary.skipped_not_written, "skipped_already_done": summary.skipped_already_done,
        "rubric_unavailable": summary.rubric_unavailable,
    }
    (OUT / "check_summary.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
