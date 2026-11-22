# pipeline/: study content sources

Builds `data/source.db` (SQLite): tagged Hebrew and Greek words, brief lexicons and cross references,
all keyed to the app's KJV verse numbers. Runs offline on a laptop; nothing here ships in the APK directly.
Later steps (study generation, CDN files) will read `source.db`.

Read [LICENCES.md](LICENCES.md) before using any of this in the app. Some parts are flagged or blocked.

## Setup

Python 3.11+. Runtime: the standard library plus jsonschema, anthropic, fastapi and uvicorn (requirements.txt).

```sh
cd pipeline
python3 -m venv .venv
.venv/bin/pip install -r requirements-dev.txt   # runtime deps + pytest/httpx
```

## Run

```sh
cd pipeline
PYTHONPATH=src .venv/bin/python -m pipeline download   # ~140 MB into data/raw/, records SHA-256s
PYTHONPATH=src .venv/bin/python -m pipeline build      # ~10 s: data/source.db + out/versification_report.md
PYTHONPATH=src .venv/bin/python -m pipeline studies    # ~30 s: out/studies/*.json (31,102 files, ~190 MB)
PYTHONPATH=src .venv/bin/python -m pipeline generate PHP --model claude-haiku-5-5 --out out/test_haiku  # write prose
PYTHONPATH=src .venv/bin/python -m pipeline check      # auto-check written studies (see below)
PYTHONPATH=src .venv/bin/python -m pipeline review     # review tool on http://127.0.0.1:8765
PYTHONPATH=src .venv/bin/python -m pipeline progress   # counts by status per book
PYTHONPATH=src .venv/bin/python -m pipeline report JHN.3.16 --reason "..."   # pull a reported study (see below)
PYTHONPATH=src .venv/bin/python -m pipeline reports    # list reported studies (--all: resolved ones too)
PYTHONPATH=src .venv/bin/python -m pipeline publish --dry-run   # what would change on the CDN
PYTHONPATH=src .venv/bin/python -m pipeline publish    # build, confirm, deploy, verify
PYTHONPATH=src .venv/bin/python -m pipeline bundle     # Gospels + Psalms into the app's assets
PYTHONPATH=src .venv/bin/python -m pipeline all        # download + build + studies
.venv/bin/python -m pytest                             # tests
```

`download` skips files already present (`--force` re-fetches). `build` needs the app's
`app/src/main/assets/bible.db`, whose KJV verse list is the target versification.

`data/` and `out/` are gitignored. STEPBible asks that its files aren't re-posted, so don't commit or mirror them.

## Sources

| Dataset | What | From |
|---|---|---|
| TAHOT | Hebrew OT, one row per word: Strong's, lemma, transliteration, morphology | STEPBible-Data |
| TAGNT | Greek NT, one row per word, incl. which editions contain it | STEPBible-Data |
| TBESH / TBESG | Brief Hebrew / Greek lexicons by disambiguated Strong's | STEPBible-Data |
| TVTMS | Versification traditions (cross-check only) | STEPBible-Data |
| OpenBible.info | ~345k cross references with vote scores | openbible.info |
| TSK | Treasury of Scripture Knowledge cross references (~382k) | CrossWire SWORD module |

## source.db

| Table | Contents |
|---|---|
| `source_file` | each downloaded file: URL, size, SHA-256, date |
| `book` | ids 1-66 (the app's) with USFM, STEP and OSIS codes |
| `kjv_verse`, `kjv_superscription` | the target verse list; titled chapters (verse 0) |
| `word` | every TAHOT/TAGNT word with its KJV key (`verse = 0` is a Psalm title) and its `native_ref` |
| `lexicon` | TBESH/TBESG entries by dStrong (e.g. `H0430G`) |
| `xref` | cross references, from/to as KJV keys; `votes` (OpenBible), `keyword` and `note` (TSK) |
| `versification_map` | how every source verse lands on KJV verses, with a `kind` |

Example: the original-language words (with glosses) of the verses TSK cross-references from John 1:1:

```sql
SELECT w.surface, w.translit, l.gloss
FROM xref x JOIN word w ON w.book_id = x.to_book AND w.chapter = x.to_chapter AND w.verse = x.to_verse
LEFT JOIN lexicon l ON l.strongs = w.strongs
WHERE x.dataset = 'TSK' AND x.from_book = 43 AND x.from_chapter = 1 AND x.from_verse = 1;
```

## Versification

The app uses KJV/English verse numbers. Sources differ:
- **Hebrew (TAHOT):** Psalm titles are verse 1 (sometimes 1-2); Malachi has no chapter 4; Joel has 4
  chapters; plus other chapter breaks and split verses.
- **NRSV (TAGNT, OpenBible):** a few NT verses are split or numbered differently from KJV (e.g. 3 John
  14/15, 2 Corinthians 13:12-14, Philippians 1:16-17).

How each is mapped:
- **TAHOT** words carry both English and Hebrew references; the English one is the KJV key, and Psalm
  titles map to verse 0 (the app's chapter superscription).
- **TAGNT** marks KJV differences in `[...]`.
- **OpenBible** endpoints not in KJV move to the KJV verse holding most of that verse's words.
- **TSK** is already KJV.
- The OT mapping is cross-checked against STEPBible's TVTMS.

`out/versification_report.md` lists every verse that doesn't map one-to-one, by kind (title,
renumbered, split, merged, source-only, KJV-only), plus dropped or unparsed cross references, the TVTMS
results, and Strong's numbers missing from the lexicons. `out/versification_issues.csv` has the same rows,
one per verse.

## Studies (B8 Verse study / D2 Deep study)

`python -m pipeline studies` writes one JSON file per KJV verse to `out/studies/<USFM>.<chapter>.<verse>.json`
(e.g. `JHN.3.16.json`). Every file is validated against [`schema/study.json`](schema/study.json); the build fails
on any error. A summary goes to `out/studies_summary.json`.

Filled from `source.db`, with no AI:

- **originalLanguage**: `language` (hebrew / aramaic / greek), `textBasis`, and `words`
  (surface, lemma, transliteration, Strong's, gloss, morphology).
  - Hebrew: every TAHOT word except the 152 Septuagint reconstructions (`X`) and 14 empty Qere slots.
  - Greek: the critical text (words marked N). The 40 KJV-only verses (e.g. Matthew 17:21) use the
    KJV's own Greek (Scrivener TR) and say so in `textBasis`.
  - Glosses are each source's in-context English for that word. TBESH definitions are not used (see
    LICENCES.md).
- **crossReferences** (up to 8):
  - Order: OpenBible references with more than 0 votes, highest first; then TSK in the Treasury's
    order, with `note` = the word or phrase it annotates.
  - Skipped: references to the verse itself, Psalm-title targets, and a second reference starting at
    the same verse.
  - 1,546 verses get none. Almost all of them (1,475) have only TSK entries that point back at the
    verse itself; 61 have nothing in either source.
- **citations**: only the datasets that verse used (STEP Bible TAHOT or TAGNT, OpenBible.info, TSK),
  with licence and URL.

The prose sections (`meaning`, `historicalContext`, `lifeInBibleTimes`, `theology`, `didYouKnow`,
`applyIt`) are empty strings. Status and metadata:
- `status` is `generated` and `meta.model` is `null` until the AI step runs.
- The schema requires a model and non-empty prose for `auto_checked`, `flagged` and `reviewed`, and
  `reviewedBy`/`reviewedAt` for `reviewed`.
- `contentVersion` is a hash of the source files, the builder rules and the schema, so it changes only
  when output would.

Re-running `studies` is safe once prose exists: it refreshes the data sections and keeps prose, status,
checks and review fields. If a checked or reviewed study's data changes, it goes back to `generated` to be
checked again (its review fields stay in `meta` as history).

**Where the work lives.** Studies with prose and reviews are stored only in `out/studies/`, which is
gitignored. Back that folder up, or decide on a durable home, before reviewing at scale.

**Schema changelog.** `schemaVersion` is bumped only when the published shape changes (the fields that reach
the CDN book files and app bundle). Pipeline-only fields (`meta`, `checks`) can grow optional fields without a
bump, since a bump changes every published book's hash and URL.
There are two numbers. The **published** `schemaVersion` (book files and manifest.json: `publish.SCHEMA_VERSION`,
`schema/book.json`, `schema/manifest.json`) is **2**, and the app reads up to `ContentVersions.MAX_SCHEMA_VERSION`
= 2. Internal study files in out/studies are `schemaVersion` **3** (`studies.SCHEMA_VERSION`, `schema/study.json`).
3 added only pipeline-side things (status `failed`, `meta.promptVersion`/`batchId`/`attempts`), so the
published shape didn't change.
- Published 2: current published shape (manifest gained optional `devotionals` and `community`; the app defaults both).
- Internal 3: status `failed`; `meta.promptVersion`, `batchId`, `attempts` (unpublished).
- 3, 2026-11-18 (no bump): optional unpublished `meta.effort`, `meta.maxTokens` (with `meta.promptVersion` =
  sha256(prompts/study.md)[:12]); rubric judgments gain `section`, `quote` and `verified`.
- 3, 2026-11-19 (no bump): `checks.reasons[].check` may be `"report"` (from `pipeline report`; unpublished).
- 3, 2026-11-19 (no bump): optional unpublished `meta.source` (`"claude-code-subagent"`: a Claude Code test-round
  study in out/cc_test/, refused by publish and bundle) and `meta.round`.
- 3, 2026-11-19 (no bump): optional unpublished `meta.promptHash` (sha256 of the cached system block sent),
  `meta.booksHash` (sha256 of prompts/books.json) and `meta.wave`; check names `verse_position` and `dotted_translit`.
- Published 2 / internal 3, 2026-11-22 (no bump): `didYouKnow` may be an empty string (the writer leaves it out when
  it isn't confident the fact is about this verse's own words; the key stays, and the app already hides an empty
  section). Unpublished `checks.advisory` (non-blocking reasons); check name `placeholder`.
- 3, 2026-11-22 (no bump): check names `did_you_know_word`, `placeholder` and `translit_capitals` (the code produced them
  since decision 25 / checker 12, but the schema rejected them).
- 3, 2026-11-22 (no bump): optional unpublished `meta.editNote` (what a person changed outside the review tool, e.g.
  `pipeline fixes apply`).

## Hand fixes (`fixes apply`)

`python -m pipeline fixes apply fixes/FILE.json --by NAME [--note TEXT] [--dry-run]` applies a `studyFixes/1` file
(`{"studies": {ID: {section: {"before", "after"}}}}`) to out/studies. A section is replaced only when its current text
equals `before`; anything else is listed as skipped. A changed study is marked edited like the review tool's Edit
(`meta.editedBy`/`editedAt`, `contentVersion` `.N`) plus `meta.editNote`; the model fields stay. `editedBy` makes it
protected, so `batch submit`/`collect` and the retry round never overwrite it. The code checks re-run (no AI) and set
auto_checked or flagged. Sends nothing (works with PIPELINE_OFFLINE=1).

Devotionals use the same command with a `devotionalFixes/1` file (`{"devotionals": {devotional-MM-DD: {section:
{"before", "after"}}}}`; sections title, context, reflect, applyIt, prayer) on out/devotionals: same matching, same
editedBy/editedAt/editNote and contentVersion `.N`, then the devotional code checks (no AI). An AI reviewer reason
isn't kept, so run `devotionals check --recheck ID` to have edited text reviewed again.

## Reported studies (`report`, `reports`)

When someone reports a problem with a published study (the app's Report a problem sheet sends an email):

```sh
PYTHONPATH=src .venv/bin/python -m pipeline report JHN.3.16 PSA.23.1 --reason "Says Herod died in 1 BC" [--by NAME]
PYTHONPATH=src .venv/bin/python -m pipeline reports [--all]
PYTHONPATH=src .venv/bin/python -m pipeline publish --dry-run   # lists the reported studies it leaves out
```

- `report` sets each study to `flagged` with a `checks.reasons` entry `{"check": "report", "message", "at", "by"?}` and
  appends a line to `out/regenerate_queue.jsonl` (the review tool's Regenerate queue) with `"source": "report"`,
  `contentVersion`, `promptVersion` and the previous status. All ids are checked first; a bad id changes nothing. A
  study with no prose can't be reported. Reporting the same study for the same reason again is a no-op.
- The report stays **open** until the prose is regenerated, edited or reviewed after it (`meta.generatedAt`, `editedAt`
  or `reviewedAt` later than the report). While open, `publish` leaves the study out whatever its status, and
  `check --recheck` keeps the reason, so nothing puts the reported text back on the CDN by accident.
- The next `publish` drops reported verses from their book files (the book gets a new hash and URL). Run
  `publish --dry-run` first: it lists each reported study with its reason.

## Generating prose (`generate`)

`python -m pipeline generate <ids> [--model M] [--effort E] [--out DIR] [--force] [--workers N]` writes the six
prose sections with Claude, one direct Messages API request per verse (`src/pipeline/generate.py`, prompt in
[`prompts/study.md`](prompts/study.md)).

- `ids`: books, chapters or verses (`PHP`, `PHP.4`, `PHP.4.13`). Run `studies` first: the data sections are copied
  from `out/studies`, and the prompt gets the KJV and WEB verse, three verses either side, the verse's original
  words and its chosen cross references. Claude never supplies data sections.
- `--model` (default `claude-opus-5-5`), `--effort` (default `high`). The server-side refusal fallback is on for
  models that have one (not Haiku 5.5).
- `--out DIR` writes to another folder (relative to `pipeline/`) so models can be compared without touching
  `out/studies`; `check --out DIR` checks that folder.
- Studies that already have prose are skipped unless `--force`; reviewed or edited ones are never overwritten.
  Refusals and unusable responses are listed and the run carries on; re-run to retry.
- Token use, cost (USD, from the price table in `usage.py`), run time and failures go to
  `<out>/generate_summary.json`. `check` adds the rubric's token use and cost to its summary.

## Batch generation (`batch`)

`python -m pipeline batch estimate|submit [ids | --wave NAME] [--limit N] [--out DIR] [--force]`, then `batch status`,
`batch collect`, `batch check`, `batch report` and `batch backup`, writes prose through the Message Batches API
(`src/pipeline/batch.py`): `claude-haiku-5-5`, effort `medium`, `max_tokens` 32,000, one request per verse, half the
direct price. The reviewer is the same model at the same effort: no Opus or Sonnet in the launch run
(docs/decisions.md, 2026-11-19).

**Waves** (`waves.yaml`, read by `waves.py`): discover (`discover.txt`, in file order), mark, matthew, luke, john,
psalms, run top to bottom. `batch_size` (500) caps the requests per batch; a wave is sent as several batches.
Finished verses are skipped (written, reviewed/edited, failed twice, in an open batch, or in an earlier wave when
estimating several), and so are verses with no WEB text (Luke 17:36), which need a person. `--wave discover` uses
the same loader as `publish`, so `discover.json` lists the same verses in the same order (minus any not yet
auto_checked/reviewed). `batch estimate --wave all` prints a table for every wave.

**Request input** (generate.py, context.py, prompts/books.json): KJV and WEB text, then the context: 4 verses
either side within the book (crossing chapters), each with its full reference; Psalms of up to 30 verses come
whole, and every psalm's heading is shown labelled as its superscription. Then the book background and any note on
the text from `prompts/books.json` (read by a person before any batch; only those notes allow a manuscript
remark), the original words with clean transliterations (no syllable dots or stress capitals), and the cross
references.

`python -m pipeline discover` audits `discover.txt` (one id per line, e.g. `PHP.4.13`; `#` comments allowed). It
reports malformed ids, verses missing from source.db or the app's KJV/WEB text, duplicates, and verses whose
Hebrew/Greek source numbers them differently or splits or merges their words (e.g. PSA.46.10 is Hebrew Ps 46:11).
It never edits the file. Malformed ids or duplicates stop `--wave discover` and `publish`.

- **Caching:** every request starts with the same system block, which is study.md's System section plus the
  Request section's fixed output instructions ("Return JSON with these sections" onward, filled with the word
  limits). It carries `cache_control` with the 1-hour TTL, because a batch can take more than 5 minutes between
  requests. The verse data follows as the user message. The prompt's wording is unchanged; only the order differs
  from `generate`. `submit` counts the block's tokens first (free endpoint) and refuses if it is under 512, Haiku
  5.5's cache minimum.
- `estimate`: verse count, the fixed block's size, and projected cost (best case all cache hits, worst case no
  hits, plus expected retries and the rubric). The projection is calibrated from the newest `out/*/` direct run
  with the same model and effort. Sends nothing; `--count-tokens` measures the block exactly (free).
- `submit`: attempt 1. `custom_id` is the verse id with `.` → `_` (`PHP_2_17`), since the API allows only
  `[A-Za-z0-9_-]`.
- `status`: polls open batches.
- `check`: re-runs the checks on collected verses (`--wave`, `--recheck`, `--no-ai`); no generation, no retry.
- Reviewer concurrency (`check` and `collect`, `--workers N`, default 6): code checks run on the main thread; studies
  that pass go to the Haiku reviewer on up to N threads (normal Messages API requests, SDK `max_retries` 6 with
  backoff on 429/5xx). Each study is saved atomically as its review comes in; rubric tokens are counted from each
  call's own usage; state/ is saved every 25 studies and when the run stops (Ctrl-C too). An interrupted or
  unavailable review leaves the study as it was, so re-running carries on.
- `report`: rewrites the summary files from state/ and the study files, without calling the API.
- `backup`: same as `python -m pipeline backup`.
- `collect`: writes ended batches (never over a reviewed or edited study), runs the free checks and the Haiku
  rubric, then submits **one** retry round for verses that were flagged or produced nothing usable. That covers
  `max_tokens`, refusal, bad JSON, errored and expired results. A second failure sets status `failed`, which is
  never published and goes into the review queue with `flagged`. Use `--no-retry` to hold the retry and `--no-ai`
  to run free checks only.
- Every step except estimate writes `out/batch_runs/<wave>/generate_summary.json` and `check_summary.json` (and
  `all/`), then prints a short summary. generate_summary: counts by status and outcome, retries (verses and
  batches), output tokens (max, median, above 8,000, thinking max/median, `max_tokens` stops), cache reads and
  writes, cost, prompt versions and prompt hashes, per batch. check_summary: counts by status, reasons by check,
  sections over their maximum by band (up to 20%, 20-50%, more than 50% over) in total and per section, rubric
  reviews/unverified quotes/cost, how many historicalContext sections open with "This verse"/"The verse"/"Verse",
  and the 5 commonest first four words per section (report only).
- Each collected batch keeps a per-request log in the state file: stop reason, thinking tokens, written tokens
  (output minus thinking), cache reads and writes. Direct-mode `generate` writes the same log and stats to
  `generate_summary.json`.
- Each study's `meta` records model, effort, maxTokens, promptVersion (sha256 of study.md), promptHash (sha256 of
  the system block sent), booksHash, batchId, wave and attempts.
- State lives in `state/batches.json` (gitignored). It holds batch ids, attempts per verse, failures and usage, and
  is saved before and right after every API call.

`python -m pipeline backup [--dest DIR]` zips `out/` and `state/` into `backups/biblescroll-pipeline-<UTC>.zip`
(gitignored). Run it before every `collect`.

## Checking (`check`)

Only studies with prose (`meta.model` set) and status `generated` are checked; `--recheck` also re-checks
`auto_checked` and `flagged`. Each gets `status` `auto_checked` or `flagged`, plus a `checks` block (schema
v3) listing every reason with its check name and section.

| Check | What fails |
|---|---|
| schema | anything `schema/study.json` rejects |
| length | a section under its minimum, or more than 20% over its maximum, in `checks/limits.json` (up to 20% over passes) |
| reference | a Bible reference in the prose that doesn't parse or isn't a KJV verse (e.g. "John 3:99", "Hezekiah 3:5", a backwards range). Relative "v. 17" is checked against the study's own chapter |
| reference_not_allowed | a real reference outside the verses the request showed (the verse and its context, `context.context_keys`) and its listed cross references. A whole-chapter reference passes if it is the verse's chapter or contains a listed reference |
| verse_position | a verse named by number: "verse 5", "verses 3 to 5", "vv. 3-4", "v. 5", "verse three". Position wording ("the verses before it", "the lines after it", "the next verse") passes (checker 13) |
| dotted_translit | a transliteration with syllable dots ("be.re.Shit"); e.g./i.e.-style abbreviations pass |
| original_language | a Strong's number, a Hebrew/Greek word, or a "Greek word *x*" transliteration that isn't in this verse's word list (points, accents and leading zeros ignored) |
| banned_phrase | any line of `checks/banned.txt` (plain phrases, or `re:` regexes), unless negated: "not", "no", "never", "nor", "without" or "n't" within the 4 words before it in the same clause |
| pipeline_wording | any line of `checks/pipeline_wording.txt` ("cross references", "supplied", "the data", "this version"); never excused by negation |
| rubric | the AI second opinion scores factual risk, one-sided theology or tone at 2 or more out of 3, or declines to answer |

About the rubric (`rubric.py`):
- It uses Claude Haiku 5.5 with a fixed prompt and a JSON-schema answer.
- Input: the rubric system prompt, then the verse id, the KJV verse text and the six prose sections. Nothing else
  (no original-language words, cross references, context verses, WEB text or books.json).
- Each judgment names the section it rests on and gives a short exact quote from it. Code checks the quote appears
  verbatim in that section (whitespace normalised) and stores `verified`; unverified reviews are counted in the
  summaries but don't change a study's status yet.
- To save money it runs only when the free checks pass. `--rubric-always` runs it anyway; `--no-ai` skips it.
- Rate limits, outages and unusable answers leave the study unchanged for the next run. They're listed in
  `out/check_summary.json`.
- It needs Anthropic credentials (`ANTHROPIC_API_KEY`, or `ant auth login`).
- At Haiku 5.5 prices, all 31,102 verses should cost roughly $15–20. The Message Batches API would halve that.

## Reviewing (`review`)

`python -m pipeline review --reviewer "Your name"` serves http://127.0.0.1:8765 (localhost only, no login).

**What the page shows:** one study at a time, next to the verse in KJV and WEB, the word table, the chosen
cross references with their verse text, and every candidate cross reference with votes.

**Buttons:**
- **Approve:** sets status `reviewed`, `reviewedBy` and `reviewedAt`.
- **Edit:** inline. Saving re-runs the free checks, records `editedBy`/`editedAt`, and bumps
  `contentVersion` (`<hash>.1`, `.2`, …). Approving is still a separate step.
- **Flag with note:** status `flagged`, with your note as the reason.
- **Regenerate:** appends the request to `out/regenerate_queue.jsonl` and flags the study. The queue isn't read
  by `generate` yet; regenerate those verses with `generate <id> --force`.

**Queue order:** flagged first, then `priority.txt` (verse, chapter or whole-book lines, top first), then
Bible order. Every write is checked against the schema and saved atomically. Use **Reload** after running
`check` in another terminal.

## Progress (`progress`)

Counts per book of `not_written` (no prose yet), `generated` (written, unchecked), `auto_checked`,
`flagged` and `reviewed`, with totals. Printed and written to `out/progress.md`.

## Publishing (`publish`)

Study content is served by Firebase Hosting from **https://biblescroll-content.web.app** (project
`biblescroll-content`, free Spark plan; set in `publish.json` and `.firebaserc`). Google Analytics is not
enabled on the project, and nothing here may enable it.

| Path | Contents | Cache |
|---|---|---|
| `manifest.json` | per book: `url`, `contentVersion`, `verseCount`, `reviewedCount`, `sha256`, `bytes`; plus `release` and the discover file's hash | 5 min |
| `discover.json` | the Discover feed's verses, in `discover.txt` order | 5 min |
| `studies/v{contentVersion}/{BOOK}.json.gz` | one gzip JSON per book (`schema/book.json`) | 1 year, immutable |
| `devotionals/v{contentVersion}/{MM}.json.gz` | one gzip JSON per month (`schema/devotional_month.json`); listed under `devotionals.months` in the manifest | 1 year, immutable |

How it works:
- **What's published:** only studies whose status is `auto_checked` or `reviewed`. Flagged, unchecked and
  unwritten verses are left out, and so is any book with nothing publishable. Public files drop the check
  reasons and reviewer/editor names; each verse has `reviewed` (true/false) and `model` for the
  "AI-assisted" label.
- **Versions:** `contentVersion` is the first 12 hex characters of the book file's SHA-256. Builds are
  byte-identical for identical content, so only changed books get new URLs. The app compares each book's
  `contentVersion` with what it has.
- **Retention:** each deploy also keeps the previous live version of every changed book file, so a phone
  that fetched the old manifest a moment earlier can still finish downloading.

Commands:
- **`publish --dry-run`:** builds in memory and compares against the live manifest. It lists books added,
  changed (with verse and reviewed-count changes), removed and unchanged; Discover changes; Discover verses
  left out because they aren't publishable; flagged verses left out; and bytes to upload. Nothing is
  written or uploaded.
- **`publish`:** shows the same diff, asks to confirm (`--yes` skips the question), writes `out/site/` and
  runs `firebase deploy --only hosting`. It then downloads every book file and checks SHA-256,
  `Content-Type: application/gzip`, no `Content-Encoding` and the immutable `Cache-Control`.
- **`publish --preview`:** deploys to a 7-day preview channel instead of the live site.
- **`publish --local`:** only builds `out/site/`.

Firebase console housekeeping: set **Hosting → Release history → retain** to a small number (e.g. 5).
Retained releases count against the 10 GB free storage. On Spark, transfer is free up to 360 MB/day; on
Blaze, transfer beyond that costs $0.15/GB. What Spark does when the daily quota runs out isn't checked
here, so look at the Firebase docs before launch traffic arrives.

## Offline bundle (`bundle`)

`bundle` copies Matthew, Mark, Luke, John and Psalms from the last built release (`out/site/`) into
`app/src/main/assets/studies/`, as `{BOOK}.json.gz`, plus a `manifest.json` with just those books (same
`contentVersion` and `sha256`). The app can then skip downloading them. Run it before every release build,
after `publish`. It warns if the local release isn't the live one. It needs no Gradle; see
`tools/README.md` for the release checklist.

## Reading plans (`plans`)

```sh
PYTHONPATH=src .venv/bin/python -m pipeline plans build                # 29 structural plans + index.json
PYTHONPATH=src .venv/bin/python -m pipeline plans draft-topical        # 10 topical plans with Claude (needs a key)
PYTHONPATH=src .venv/bin/python -m pipeline plans draft-chronological  # chronological order with Claude
PYTHONPATH=src .venv/bin/python -m pipeline plans validate             # re-check everything
PYTHONPATH=src .venv/bin/python -m pipeline plans bundle               # publishable plans -> app assets
```

Plans follow [`schema/plan.json`](schema/plan.json). They're written to `out/plans/{id}.json`, and
`out/plans/index.json` lists the publishable ones by category, plus a "Start here" featured list.

**Structural plans** (`plans/catalogue.py`, no AI):
- Built from word counts: World English Bible text, about 200 words a minute.
- A plan whose day count equals the book's chapters reads a chapter a day when that fits the length rule.
  Otherwise days are balanced, cutting at chapter ends when one is close.
- Two-track plans (the Bible in a year; Psalms & Proverbs) give each day a reading from each track.
- They're published once they validate.

**AI-drafted plans** (`plans/ai.py`, Claude Opus 5.5, structured output, refusal fallback on):
- Topical plans: Claude chooses passages and writes short daily intros. They're validated and saved as
  `auto_checked` or `flagged`.
- The chronological plan: Claude only proposes the order of chapter ranges. Code checks every chapter
  appears exactly once, splits the year into days, and always flags the plan for a person to verify.
- **AI-drafted plans ship only once a person approves them** in the review tool (Plans tab). There you can
  edit titles, intros and passages (one per line, `JHN.3.1-JHN.3.21`); every edit is re-validated.
  Approval is blocked while a passage, day-length or coverage problem remains.

**Validation:**
- Every passage exists in KJV numbering and runs forwards within one book.
- Days are numbered 1..N and none is empty.
- No day exceeds 1.5× `estimatedMinutesPerDay`.
- Whole-Bible plans read every verse exactly once.

**Shared with the app (custom plans, H13/H14):** `plans/splitting.py` holds the splitting rules; the app's
`core/plans` module is a line-for-line Kotlin port. `plans vectors` (also run by `plans bundle`) writes
`app/src/main/assets/plans/verses.bin` (every KJV verse's word count and boundary tier: chapter end,
Masoretic paragraph end in the Old Testament, sentence end) and `core/plans/src/test/resources/splitting_vectors.json`
(real and synthetic cases). Tests on both sides must agree with the vectors.

`plans bundle` copies the publishable plans (without review notes or reviewer names) and `index.json` into
`app/src/main/assets/plans/`, about 370 KB for the 29 structural plans. Run it before release builds.

## Devotionals (`devotionals`)

```sh
PYTHONPATH=src .venv/bin/python -m pipeline devotionals calendar             # validate calendar.csv, OT/NT balance
PYTHONPATH=src .venv/bin/python -m pipeline devotionals draft [03 | 03-14]  # draft with Claude (needs a key; --workers N, default 8)
PYTHONPATH=src .venv/bin/python -m pipeline devotionals draft --stale       # redraft rows/prompt that changed
PYTHONPATH=src .venv/bin/python -m pipeline devotionals check [--no-ai]     # auto_checked or flagged (--workers N, default 8)
PYTHONPATH=src .venv/bin/python -m pipeline devotionals redraft-series 07-16 --guidance "..."
PYTHONPATH=src .venv/bin/python -m pipeline devotionals bundle [--date 2027-03-01]
```

**Calendar** ([`devotionals/calendar.csv`](devotionals/calendar.csv), edit freely): one row per day of a leap
year, keyed by `MM-DD`, with columns `date, season, series, series_day, series_length, cover_style, theme,
anchor`. There are 365 days in 25 series, plus a standalone bonus on 02-29 (366 in total). It was drafted
with Claude and is checked by `devotionals calendar`. That check confirms every date appears once, anchors
exist in KJV numbering within one book, series count 1..n with one cover style each, and no anchor repeats.
Easter moves (22 March to 25 April), so the seasons use fixed dates:
- Lent-friendly series from mid-February.
- "The cross" from 19 March to 8 April, then "Living hope" from 9 to 29 April. Both work in the weeks
  either side of Easter.
- Pentecost themes in May and early June.
- Advent from 1 December, Christmas from 25 December.

`redraft-series` asks Claude for new themes and anchors for one series (same dates). It writes the
calendar only if the whole file still validates, and refuses when a devotional in that series is
reviewed (unless `--force`).

**Format** ([`schema/devotional.json`](schema/devotional.json)):
- Files are `out/devotionals/devotional-MM-DD.json`. `dayOfYear` uses leap-year numbering (02-29 = 60); the
  app looks devotionals up by month and day.
- From the calendar: `date`, `theme`, `series`, `coverStyle` and `verses[0]` (the anchor). Cover styles are a
  fixed set of typographic/gradient designs: dawn, meadow, ocean, ember, dusk, stone, linen, night.
- From Claude: `title`, `context` (60-110 words), `reflect` (120-180), `applyIt` (40-80), optional `prayer`
  (25-60), and up to two extra `verses`.

**Drafting** (`devotionals/generate.py`, prompt in [`prompts/devotional.md`](prompts/devotional.md)):
- Model: Claude Haiku 5.5 at effort medium (max_tokens 32,000), with structured output and the refusal fallback
  (decision 26; `devotionals/generate.py` DRAFT_MODEL/DRAFT_EFFORT). `redraft-series` uses the same drafter. Plans
  keep Opus 5.5 at high effort (`plans/ai.py`).
- "Apply it" action type: each day's request names one of 8 kinds of step (`generate.APPLY_IT_ACTIONS`: pause and
  breathe, write one sentence, say a short prayer aloud, send a message to someone, reread a verse slowly, ask someone
  a question, notice something during the day, do one small thing for someone), chosen by day of year so consecutive
  days never share one. The prompt also tells the writer not to open applyIt with "Today".
- Parallel: `draft` and `check` keep up to `--workers` (default 8) API requests in flight. Only the API call runs on a
  worker thread; requests are built and each devotional is saved on the main thread as its answer comes in, so
  Ctrl-C keeps finished days and a re-run carries on. The client retries 429/5xx up to 6 times with backoff.
- The prompt gets the passage text (WEB, from the app's Bible) and the passage's Hebrew/Greek words (from
  source.db), so nothing is recalled from memory. The rules are the same as for studies: no invented facts,
  fair on disputed views, warm and plain, fixed lengths.
- Editing the prompt or a calendar row changes `contentVersion`. `draft --stale` then redrafts those days,
  but never reviewed or hand-edited ones; only `--force` touches those. Word limits live in
  `checks/devotional_limits.json`.

**Checks** (`devotionals/checks.py`, `CHECKER_VERSION` `devotionals-5`):
- Stands alone: blocks references to the series or the calendar ("tomorrow's devotional/reading", "yesterday's",
  "last time we", "next time we", "this week's", "come back tomorrow/later/again/here", "this season", "as we saw",
  "day 3 of 14", Easter/Lent/Advent dates). Everyday "next time you feel…", "next week", "tomorrow" in a sentence pass.
- Banned "you must" / "you need to" only when it opens a sentence or clause as a direct command (not "something you
  must earn", not negated); "word count" is pipeline wording unless a quoted word follows (checker 14 for studies too).
- The study checks, applied to the devotional's sections: schema, lengths, Bible references, original-language
  mentions (against the passage's words), banned phrases, then the Haiku rubric with a devotional prompt.
- A calendar check: date, theme, series, cover and anchor still match the CSV.
- An API outage leaves a devotional unchanged.
- Advisory, never blocking (checker `devotionals-4`): `check` lists every first-three-words opening (title, context,
  reflect, applyIt, prayer) used more than twice in one month, over all devotionals, under `advisory.repeatedOpenings`
  in `out/devotionals_check_summary.json`.

**Review**: the Devotionals tab in `review` shows each devotional beside its passages and calendar row, with
live word counts. Approval is blocked while a schema, length, reference, original-language, banned-phrase
or calendar problem remains. Edits re-run the free checks.

**Publishing**:
- `publish` adds one immutable file per month. Only `auto_checked` and `reviewed` devotionals are included,
  without check reasons or names, and February includes 02-29.
- `devotionals bundle` copies the current and next month (December wraps to January) from `out/site/` into
  `app/src/main/assets/devotionals/`, with a small `manifest.json`. Run it after `publish`, before each
  release build.
