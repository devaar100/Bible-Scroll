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
