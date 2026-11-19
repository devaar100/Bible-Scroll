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
