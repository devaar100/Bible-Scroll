# pipeline/: notes for Claude

Offline Python tool that builds `data/source.db`. Separate from the Android app; see README.md.

- Runtime deps (requirements.txt): standard library + jsonschema, anthropic (rubric), fastapi + uvicorn
  (review tool). No pandas/requests. Python 3.11+.
  Tests: `.venv/bin/python -m pytest` (output tests skip until `build` and `studies` have run).
- Run: `PYTHONPATH=src .venv/bin/python -m pipeline download|build|studies|check|review|progress|all`
  from `pipeline/`.
- `studies` must never lose work: it merges (keeps prose, status, meta, checks) and only refreshes data
  sections. Reviewed/edited studies live only in out/studies (gitignored): back them up.
- Checks: rules in `checks/` (banned.txt, limits.json); bump `CHECKER_VERSION` in checks.py when a check
  changes. The AI rubric (rubric.py, claude-haiku-5-5) runs only after the free checks pass; API outages
  must leave a study unchanged, never flag it. Tests never call the API (fake clients).
- Review tool binds to 127.0.0.1 only; every write is schema-validated and atomic.
- Plans (`python -m pipeline plans build|draft-topical|draft-chronological|validate|bundle`):
  schema/plan.json. Structural plans come from `plans/catalogue.py` and ship once they validate; AI-drafted
  plans (topical, chronological) ship only when status is "reviewed". Never overwrite a reviewed plan without
  --force. Day lengths use WEB word counts at 200 wpm; no day may exceed 1.5x estimatedMinutesPerDay.
- Publishing: only auto_checked/reviewed studies go public, never check reasons or reviewer/editor names.
  Book files must stay byte-deterministic (canonical_gzip) so unchanged books keep their URL and hash.
  Always `publish --dry-run` first; `publish` deploys to Firebase project biblescroll-content
  (publish.json) and must never enable Google Analytics. `bundle` copies MAT/MRK/LUK/JHN/PSA into the app.
- Study format: `schema/study.json` (schemaVersion 1). Change the schema → bump `schemaVersion`;
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
