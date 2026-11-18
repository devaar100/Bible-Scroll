import copy
import json
from types import SimpleNamespace

import pytest

from pipeline.books import BOOKS
from pipeline.plans import ai
from pipeline.plans.bible import VerseIndex

INDEX = VerseIndex.load()


class FakeDrafter:
    def __init__(self, data=None, error=None):
        self.data, self.error, self.calls = data, error, []

    def draft(self, system, prompt, schema, max_tokens):
        self.calls.append(prompt)
        if self.error:
            raise self.error
        return ai.Draft(copy.deepcopy(self.data), "claude-opus-5-5")


def p(book, c1, v1, c2, v2):
    return {"book": book, "startChapter": c1, "startVerse": v1, "endChapter": c2, "endVerse": v2}


TOPICAL = {
    "title": "When you're anxious", "shortDescription": "Seven days of calm words.", "description": "For worried days.",
    "days": [{"title": f"Day {i}", "intro": "A short intro.", "passages": [p("PSA", 23, 1, 23, 6)]} for i in range(1, 8)],
}


def test_topical_draft_becomes_a_checked_plan_needing_review():
    plan = ai.plan_from_topical("anxiety", ai.Draft(TOPICAL, "claude-opus-5-5"), 7, INDEX)
    assert plan["id"] == "anxiety-7-days" and plan["origin"] == "ai_drafted"
    assert plan["status"] == "auto_checked" and plan["checks"]["reasons"] == []
    assert plan["meta"]["model"] == "claude-opus-5-5" and plan["estimatedMinutesPerDay"] >= 1
    assert plan["days"][0]["intro"] == "A short intro." and plan["category"] == "topics-and-seasons"


def test_bad_drafts_are_flagged_with_reasons():
    data = copy.deepcopy(TOPICAL)
    data["days"][2]["passages"] = [p("JHN", 3, 1, 3, 99)]
    data["days"] = data["days"][:6]
    plan = ai.plan_from_topical("anxiety", ai.Draft(data, "m"), 7, INDEX)
    assert plan["status"] == "flagged"
    assert {r["check"] for r in plan["checks"]["reasons"]} >= {"passage", "day_numbers"}


def test_refusals_and_failures_are_reported_not_saved(monkeypatch, tmp_path):
    monkeypatch.setattr(ai, "PLANS_DIR", tmp_path)
    lines = ai.draft_topical(["grief"], FakeDrafter(error=ai.DraftRefused("declined")), INDEX)
    assert lines == ["grief: not drafted (declined)"] and not list(tmp_path.iterdir())


def test_reviewed_plans_are_not_replaced_without_force(monkeypatch, tmp_path):
    monkeypatch.setattr(ai, "PLANS_DIR", tmp_path)
    monkeypatch.setattr(ai, "load_plans", lambda: {"anxiety-7-days": {"status": "reviewed", "origin": "ai_drafted"}})
    assert "skipped" in ai.draft_topical(["anxiety"], FakeDrafter(TOPICAL), INDEX)[0]


def canonical_ranges():
    return [{"book": b.usfm, "startChapter": 1, "endChapter": INDEX.chapter_count(b.usfm)} for b in BOOKS]


def test_chronological_is_always_flagged_for_verification():
    ranges = canonical_ranges()
    ranges[0], ranges[17] = ranges[17], ranges[0]  # e.g. Job first
    plan = ai.plan_from_chronological(ai.Draft({"ranges": ranges}, "m"), INDEX)
    assert plan["status"] == "flagged" and [r["check"] for r in plan["checks"]["reasons"]] == ["verification"]
    assert len(plan["days"]) == 365 and plan["days"][0]["passages"][0]["book"] == "JOB"


def test_chronological_coverage_gaps_and_duplicates_are_reported():
    ranges = canonical_ranges()
    ranges = [r for r in ranges if r["book"] != "OBA"] + [{"book": "PSA", "startChapter": 23, "endChapter": 23}]
    plan = ai.plan_from_chronological(ai.Draft({"ranges": ranges}, "m"), INDEX)
    messages = [r["message"] for r in plan["checks"]["reasons"]]
    assert "Obadiah 1 is missing" in messages and "Psalms 23 appears twice" in messages


# ---- The real client wrapper, with a fake SDK client ----

class FakeStream:
    def __init__(self, message):
        self.message = message

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False

    def get_final_message(self):
        return self.message


def client_returning(message):
    calls = []

    def stream(**kwargs):
        calls.append(kwargs)
        return FakeStream(message)

    return SimpleNamespace(beta=SimpleNamespace(messages=SimpleNamespace(stream=stream))), calls


def message(text=None, stop_reason="end_turn"):
    content = [SimpleNamespace(type="text", text=text)] if text is not None else []
    return SimpleNamespace(content=content, stop_reason=stop_reason, model="claude-opus-5-5", _request_id="req_1")


def test_anthropic_drafter_request_shape():
    client, calls = client_returning(message(json.dumps({"ranges": []})))
    draft = ai.AnthropicDrafter(client).draft("sys", "prompt", ai.CHRONO_SCHEMA, 64000)
    assert draft.data == {"ranges": []} and draft.model == "claude-opus-5-5"
    kw = calls[0]
    assert kw["model"] == "claude-opus-5-5" and kw["fallbacks"] == "default"
    assert kw["betas"] == ["server-side-fallback-2026-07-01"]
    assert kw["output_config"]["format"]["type"] == "json_schema" and kw["max_tokens"] == 64000


@pytest.mark.parametrize("msg, error", [
    (message(stop_reason="refusal"), ai.DraftRefused),
    (message("{}", stop_reason="max_tokens"), ai.DraftFailed),
    (message("not json"), ai.DraftFailed),
    (message(None), ai.DraftFailed),
])
def test_anthropic_drafter_failures(msg, error):
    client, _ = client_returning(msg)
    with pytest.raises(error):
        ai.AnthropicDrafter(client).draft("s", "p", {}, 100)
