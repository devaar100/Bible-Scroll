# pipeline/: notes for Claude

Offline Python tool that builds `data/source.db`. Separate from the Android app; see README.md.

- Runtime deps (requirements.txt): standard library + jsonschema, anthropic (rubric), fastapi + uvicorn
  (review tool). No pandas/requests. Python 3.11+.
  Tests: `.venv/bin/python -m pytest` (output tests skip until `build` and `studies` have run).
- Run: `PYTHONPATH=src .venv/bin/python -m pipeline download|build|studies|generate|check|review|progress|all`
  from `pipeline/`.
- `studies` must never lose work: it merges (keeps prose, status, meta, checks) and only refreshes data
  sections. Reviewed/edited studies live only in out/studies (gitignored): back them up.
- Prose: `generate <ids> --model --effort --out --force --workers` (generate.py, prompts/study.md), direct
  Messages API, one request per verse; writes only the six prose sections, status `generated`. `--out` keeps
  test runs out of out/studies; `check --out` checks them. Prices for cost reports live in usage.py.
- Launch run (decision 2026-11-19): ALL generation and ALL checking use claude-haiku-5-5 at effort "medium". No Opus
  or Sonnet in it; `generate` defaults to Haiku/medium/32,000 too.
- Batch prose: `batch estimate|submit|status|collect|check|report|backup` (batch.py): Haiku 5.5, effort medium, cached
  1h system block (study.md System + the Request's fixed tail; keep `FIXED_TAIL_MARKER` in step with study.md).
  max_tokens 32,000; a max_tokens stop is a failed attempt. Waves in waves.yaml (waves.py, strict mini-parser, no
  YAML dependency), at most batch_size (500) requests per batch. Summaries: out/batch_runs/<wave>/generate_summary.json
  + check_summary.json (and all/). One retry round; a second failure -> status `failed` (never published). Batch ids
  live in state/ (gitignored): `backup` zips out/ + state/ before collecting. Attempts and failures are counted per
  output folder (state format 2), so a test run into its own folder doesn't use up out/studies' retry.
  `batch check --reuse-reviews` re-scores the stored Haiku reviews with the current code (no API call); use it after
  changing how a review is scored. `batch estimate` calibrates from the latest batch wave in out/batch_runs.
- Study request input: context.py is shared by the request and the reference check (4 verses each side within the
  book; whole psalm if <= 30 verses, plus its heading labelled as superscription). prompts/books.json (book
  background, textNotes) is read by the user before any batch; only a textNote allows a manuscript remark.
- Discover: discover.txt ("ID theme label" per line, display order; the label is free text up to publish.DISCOVER_THEME_MAX characters, shown on the card as written; one card design for all) feeds both `batch --wave discover` and publish's
  discover.json through publish.load_discover / load_discover_entries (publish refuses a line without a theme; the wave ignores themes). Keep them the single loader so the order can't diverge.
  `python -m pipeline discover` audits it; never edit discover.txt for the user.
- Checks (decision 25): only code checks in `checks.BLOCKING` block a study (references, lengths, verse
  numbers/positions, leaked instructions (pipeline_wording.txt) and placeholders, quoted original words in the verse,
  plus schema/generation/report); one retry, then `failed`. Everything else (banned.txt, generic_lines.txt,
  transliteration form, and the whole AI rubric, rubric.py, claude-haiku-5-5) is advisory: `checks.advisory`, listed in
  reviewer_report.md (out/batch_runs/<wave>/, or next to check_summary.json), never blocking or retried. The rubric
  runs only when no blocking check failed; API outages leave a study unchanged. "Did you know?" is optional (empty
  passes; checks.OPTIONAL_SECTIONS). Bump `CHECKER_VERSION` when a check changes. Tests never call the API.
- Frozen (decision 25): prompts/study.md, the rubric prompts/schema and .claude/agents/study-reviewer.md
  (tests/test_frozen.py). No rubric changes or test rounds without a new decision.
- Review tool binds to 127.0.0.1 only; every write is schema-validated and atomic.
- Reports (`report <ids> --reason`, `reports`, reports.py): flag + `checks.reasons` check "report" + a line in
  out/regenerate_queue.jsonl. An open report (no generatedAt/editedAt/reviewedAt after it) keeps the study out of
  publish whatever its status, and `checks.apply` keeps open report reasons. Don't publish around it by editing status.
- Plans (`python -m pipeline plans build|draft-topical|draft-chronological|validate|bundle`):
  schema/plan.json. Structural plans come from `plans/catalogue.py` and ship once they validate; AI-drafted
  plans (topical, chronological) ship only when status is "reviewed". Never overwrite a reviewed plan without
  --force. Day lengths use WEB word counts at 200 wpm; no day may exceed 1.5x estimatedMinutesPerDay.
- Plan splitting is shared with the app: `plans/splitting.py` is the reference, `core/plans/.../PlanSplitter.kt`
  its line-for-line port. Change either → change both, run `python -m pipeline plans vectors` (rewrites
  app assets/plans/verses.bin + core/plans splitting_vectors.json), and run both test suites. Paragraph
  ends = Masoretic marks (OT only); don't use TAGNT (NA28) paragraphing without a licence check.
- Devotionals (`python -m pipeline devotionals calendar|redraft-series|draft|check|bundle`): calendar in
  devotionals/calendar.csv (366 rows, MM-DD, 02-29 bonus; must validate), schema/devotional.json, prompt in
  prompts/devotional.md, limits in checks/devotional_limits.json. Calendar fields and passage data come from
  the CSV/source.db, never from AI. Never overwrite a reviewed or edited devotional without --force. Bump
  devotionals/checks.py CHECKER_VERSION when a devotional check changes. Published per month with the studies
  release; `devotionals bundle` puts the current + next month in app assets.
  Each devotional stands alone and is date-independent: the request carries no date and no "day X of Y"; checks
  (devotionals-3) block "yesterday/tomorrow/this week/as we saw", Easter/Lent/Advent date words, Paul-wrote-Hebrews,
  faith-instead-of-medical-help and pipeline wording ("the theme", "related passages", "listed"). The model may cite
  only the passage's chapter and up to 6 related passages taken from out/studies crossReferences (store.
  related_passages; `draft` refuses to run without out/studies); extraVerses must come from that list. Reviewer:
  the Haiku rubric (rubric.DEVOTIONAL_SYSTEM_PROMPT adds the health/Hebrews rules); `devotionals check
  --reviewer-model claude-sonnet-5-5` uses Sonnet instead (about 20x the price per token). Calendar seasons for "The cross"
  and "Living hope" are "The cross"/"Resurrection" (Easter moves; no Easter-date assumptions).
- Translations (`python -m pipeline translate plans|devotionals|estimate|status|flagged|approve --lang es`,
  translate/): claude-opus-5-5 translates, claude-sonnet-5-5 (a different model on purpose) checks meaning drift,
  both with structured output and server-side refusal fallback; the model that actually answered is recorded in
  meta with promptHash/driftPromptHash (SHA-256 of the system prompts sent). Free checks (keys, lengths,
  numbers/reference numbers, quoted Scripture = target Bible text) run before the drift call. Blocking = a failed
  free check or drift in engine.BLOCKING (meaning_changed, claim_added/dropped, reference/name/number_changed,
  theology_term): one retry with the problems fed back, then flagged (the app shows English). Everything else
  (tone, nuance, quotation, style, other) is logged in checks.minor and counted, never blocks. `translate plans|
  devotionals` print the estimate and ask before spending (`--yes` to skip the question). Results live in the doc: `i18n.<lang>` {status, sourceVersion, texts, meta, checks}; category titles
  in out/plans/categories.i18n.json. Only auto_checked/reviewed with sourceVersion == contentVersion ship (bundle,
  index.json, month files strip status/meta/checks). Rebuilding English marks old translations `stale`, never
  deletes them. Never overwrite reviewed without --force; flagged only with --retry-flagged. Devotional translation
  refuses to run without the target Bible's text (RV1909, Session 30a). Prompts: prompts/translate.md, drift.md.
  Bump engine.CHECKER_VERSION when a translation check changes. Tests use fake clients.
- Community (community.py): community/ministries.json (schema/ministries.json) is published as /ministries.json and
  pinned in manifest.community. Ministry ids name Firestore docs (YYYY-MM_id): never rename or reuse one once featured.
  test:true entries need ids starting test- (debug and qa builds only). backend/scripts/seed-month.mjs reads the same file.
  load_ministries (run by every publish, incl. --dry-run) fails if website/give_url isn't https or give_url's registrable
  domain differs from website's (community.registrable_domain: built-in two-label suffix list, no PSL dependency).
- Bundled files in app assets are `*.json.gzip`, never `*.gz`: AAPT gunzips `.gz` assets and drops the suffix, so
  the app wouldn't find them (bundle.BUNDLED_SUFFIX; CDN files stay `.json.gz`).
- Publishing: only auto_checked/reviewed studies go public, never check reasons or reviewer/editor names.
  Book files must stay byte-deterministic (canonical_gzip) so unchanged books keep their URL and hash.
  Always `publish --dry-run` first; `publish` deploys to Firebase project biblescroll-content
  (publish.json) and must never enable Google Analytics. `bundle` copies MAT/MRK/LUK/JHN/PSA into the app.
- Study format: `schema/study.json` (schemaVersion 3). Bump `schemaVersion` only when the published shape changes
  (a bump changes every book file's hash/URL); optional pipeline-only fields (meta, checks) don't bump it, but get a
  line in README's schema changelog;
  change a selection rule in `studies.py` → update `BUILDER_RULES` (it feeds `contentVersion`).
- Study data sections (originalLanguage, crossReferences, citations) come only from source.db, never from AI.
  Glosses are the per-word in-context glosses from TAHOT/TAGNT, not TBESH definitions.
- Never commit `data/` or `out/` (gitignored). Never mirror raw STEPBible files (their licence asks not to).
- Target versification = KJV verses in `../app/src/main/assets/bible.db`. Verse 0 = Psalm title (chapter superscription).
- Verse keys everywhere are (book_id 1-66, chapter, verse) using the app's book ids (`books.py`).
- Parsers must fail loudly: a word or reference that can't be parsed raises, or is listed in the report.
  Never drop rows silently.
- Before adding a source: confirm its licence from the source itself and add it to LICENCES.md
  (exact licence, required attribution, URL; flag anything unconfirmed).
- Licence blockers are in LICENCES.md. Don't use TBESH Hebrew definitions in app content.
- After changes: run the tests, then `build` and `studies`, and check `out/versification_report.md` still shows 0
  `source_only`/`kjv_only` for TAHOT/TAGNT and 0 TVTMS disagreements.
