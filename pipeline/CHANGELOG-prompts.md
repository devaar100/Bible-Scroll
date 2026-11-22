# Prompt and rubric changes from the Claude Code test rounds

Rounds run with Claude Code subagents (writer: Haiku 5.5 medium; reviewer: Sonnet 5.5 medium), studies under
out/cc_test/roundN/ only. Each entry: what failed, the smallest change, and the round that tests it.

## Round 1 → round 2 (2026-11-19)

Round 1: NOT READY. Canary PSA.43.5 was flagged with WARNs only (criterion 1); WARN 11 > 4 (criterion 3), 7 of them in
"Life in Bible times" (invented-image, factual-claim). 2 of 20 drafts used more than 16,000 output tokens.

- prompts/study.md, lifeInBibleTimes: added "Only well-established facts, and hedge anything less certain
  ("probably", "often"). Add no scenes, feelings or motives that the text doesn't give."
- .claude/agents/study-reviewer.md: rule breaks are always ERROR even when the claim is true (reference outside the
  list, numbered verse, stock traditions line, Did-you-know not about a word of this verse); new category
  did-you-know-scope (also in cctest.CATEGORIES).
- batch.py / generate.py: MAX_TOKENS 16,000 → 32,000 (docs/decisions.md updated).

## Round 2 result (2026-11-19)

NOT READY. All 3 canaries flagged ERROR (PSA.43.5 now 3 ERRORs). 20/20 written in one attempt, 0 format failures,
20/20 pass code checks. Round studies: ERROR 1 (PHP.4.13 leak: "The book background says..."), WARN 5 (JHN.3.16 x2
and PSA.37.5 x2 context-claim/invented-image, ROM.8.28 one-sided-theology). Criterion 3 (ERROR=0, WARN<=4) failed
for the second round in a row: stopped to ask before round 3. Max writer output 16,591 tokens (PSA.37.5), so the
32,000 limit was needed.

## Round 2 → round 3 (2026-11-19)

Decisions: PHP.4.13 retried within round 2; the JHN.3.16 Israel/Egypt context-claim and the ROM.8.28 one-sided-theology
WARNs are discounted (well-established background; a dispute the writer is told to name).

- prompts/study.md, historicalContext and lifeInBibleTimes: added "Mention only places, events and the parts of the
  passage that the verses given show; don't say how a psalm or passage ends unless its end is shown." (PSA.37.5 said
  the psalm ends with inheriting the land; the writer saw only verses 1-9.)
- checks/pipeline_wording.txt: "book background" (PHP.4.13: "The book background says..."). CHECKER_VERSION 9.
- .claude/agents/study-reviewer.md: well-established background about a person, place or event the text names is
  not a context-claim; naming views fairly is not one-sided when the verse touches a dispute listed in the writer's
  rule 2 (baptism, predestination, end times, Mary, sacraments, church authority, creation and science).
- cctest retry: re-review regenerated studies under their blind labels; a later review replaces the earlier one.

## Round 2 retry and round 3 result (2026-11-19)

Round 2 retry: PHP.4.13 attempt 2 passed the code checks (checker 9) and the reviewer found nothing, so round 2
ended with ERROR 0, WARN 5 (3 after the two discounted findings).

Round 3 (final allowed round): NOT READY. All 3 canaries flagged ERROR; 20/20 written; 0 format failures; 20/20
pass code checks (PSA.100.4 needed its retry: attempt 1 wrote "The book background places...", caught by the new
check). Reviewer: ERROR 1 (ECC.9.10 Did-you-know says la'asot and 'aseh come "twice in a row" though bekhochakha
sits between them), WARN 6 (ISA.53.5 x2, JAS.1.5, PSA.43.5, SNG.8.6, JER.17.8). WARN > 4 for the third round in a
row. Max writer output 16,356 tokens. Context-claim WARNs in "Life in Bible times" fell from 7 (round 1) to 1.

## Round 3 under the new bar (2026-11-19): READY

Decision 21 (docs/decisions.md): only ERRORs block, WARN <= 6 per 20; reversed roles, misattached phrases, bad
references, leaks/placeholders and an off-verse Did-you-know are always ERROR (reviewer rubric updated first).
ECC.9.10 attempt 2 passed the code checks and the reviewer found nothing. Round 3: ERROR 0, WARN 6, all 3 canaries
flagged ERROR, 20/20 pass after at most one retry (PSA.100.4, ECC.9.10), 0 format failures, every Did-you-know on
its own verse.

## 10-verse API run (discover_test10, 2026-11-20) and checker 10 (2026-11-22)

API run (Haiku 5.5 batch): 10/10 written, 0 max_tokens stops, median output 4,265 tokens (max 7,218). 9 of 10 were
`failed`, 32 context_mismatch findings and 1 rubric factual risk (MAT.18.21). No retry round ran: attempts were
counted per verse across folders, so the earlier out/studies attempts used up this run's retry.

- rubric.py (checker 10): a support quote is matched word by word, ignoring verse labels, list bullets, punctuation,
  quote marks and diacritics, so a quote may run across verses; a quote of 6+ words may skip up to 8 words of the
  text. A claim the reviewer marked unsupported but backed with a quote that is in the text passes (8 of 9 such
  claims in the run were true).
- batch.py: attempts/failures per output folder; `batch check --reuse-reviews`; estimate calibrates from batch runs.

Re-checked from the stored reviews (no API call): 5 auto_checked, 5 flagged; 12 context_mismatch findings left, 11 of
them with no support at all (claims about passages outside the text given, the book's structure, or what a related
passage says).

## Decision 25: pipeline simplified, prompts frozen (2026-11-22)

- prompts/study.md: "Did you know?" is optional; the writer returns an empty string unless it is confident the fact is
  about this verse's own words. This is the last change: the writer prompt and the reviewer rubric are frozen
  (tests/test_frozen.py). No more rubric changes or test rounds.
- Checker 11: only code checks in checks.BLOCKING block (references, lengths, verse numbers, leaked instructions and
  placeholders, quoted words in the verse); one retry for those. The AI reviewer and the other code checks are
  advisory (checks.advisory, reviewer_report.md per wave).
- Offline results: round 3 (20 studies) 20/20 pass the blocking checks; the 10 API studies 10/10 pass, and 5 of them
  are in the reviewer report (context_mismatch 5, factual risk 1).

## Checker 12 (2026-11-22)

Raw transliterations block (one retry, then failed): `dotted_translit` (syllable dots inside one word, "be.re.Shit")
and the new `translit_capitals` (a capital after a lower-case letter inside one word, "bereShit"; Mc/Mac surnames
allowed). Neither fires on any of the 499 written studies in out/. `translit_form` (a listed word's raw form,
"he'ōs") stays advisory.
