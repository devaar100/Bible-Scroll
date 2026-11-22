"""Decision 25 (docs/decisions.md): the writer prompt and the reviewer rubric are frozen.

A failure here means a frozen prompt changed. Don't just update the hash: changing a frozen prompt needs a new
decision first (and then the new hash here).
"""
import hashlib
import json

from pipeline import rubric
from pipeline.paths import PIPELINE

STUDY_PROMPT = "fe030a1bb34077ee45218060e9b29fdb31f591f8c2188479e7cf7ec56e49bc88"
RUBRIC = "73f448e7c7b17a30db94ad56b5130db6577bcd75a004def4678800be1ff30c33"
REVIEWER_AGENT = "b8deb72f7a5e6659c3b6bb15f7db74d21136948a2edb47bf1aa0113031695449"


def sha(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


def test_writer_prompt_is_frozen():
    assert sha((PIPELINE / "prompts" / "study.md").read_text()) == STUDY_PROMPT


def test_reviewer_rubric_is_frozen():
    frozen = (rubric.SYSTEM_PROMPT + rubric.CONTEXT_PROMPT + json.dumps(rubric.STUDY_OUTPUT_SCHEMA, sort_keys=True)
              + str(rubric.THRESHOLD))
    assert sha(frozen) == RUBRIC


def test_test_round_reviewer_is_frozen():
    assert sha((PIPELINE.parent / ".claude" / "agents" / "study-reviewer.md").read_text()) == REVIEWER_AGENT
