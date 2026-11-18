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
PYTHONPATH=src .venv/bin/python -m pipeline check      # auto-check written studies (see below)
PYTHONPATH=src .venv/bin/python -m pipeline review     # review tool on http://127.0.0.1:8765
PYTHONPATH=src .venv/bin/python -m pipeline progress   # counts by status per book
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

## Checking (`check`)

Only studies with prose (`meta.model` set) and status `generated` are checked; `--recheck` also re-checks
`auto_checked` and `flagged`. Each gets `status` `auto_checked` or `flagged`, plus a `checks` block (schema
v2) listing every reason with its check name and section.

| Check | What fails |
|---|---|
| schema | anything `schema/study.json` rejects |
| length | a section outside its word limits in `checks/limits.json` |
| reference | a Bible reference in the prose that doesn't parse or isn't a KJV verse (e.g. "John 3:99", "Hezekiah 3:5", a backwards range). Relative "v. 17" is checked against the study's own chapter |
| original_language | a Strong's number, a Hebrew/Greek word, or a "Greek word *x*" transliteration that isn't in this verse's word list (points, accents and leading zeros ignored) |
| banned_phrase | any line of `checks/banned.txt` (plain phrases, or `re:` regexes) |
| rubric | the AI second opinion scores factual risk, one-sided theology or tone at 2 or more out of 3, or declines to answer |

About the rubric (`rubric.py`):
- It uses Claude Haiku 5.5 with a fixed prompt and a JSON-schema answer.
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
- **Regenerate:** the prose generator doesn't exist yet. This appends the request to
  `out/regenerate_queue.jsonl` and flags the study until the generator runs.

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

`plans bundle` copies the publishable plans (without review notes or reviewer names) and `index.json` into
`app/src/main/assets/plans/`, about 370 KB for the 29 structural plans. Run it before release builds.
