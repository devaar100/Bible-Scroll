import json
from types import SimpleNamespace

import anthropic
import httpx
import pytest

from pipeline.rubric import RUBRIC_MODEL, THRESHOLD, AnthropicJudge, RubricUnavailable, build_prompt


def response(text=None, stop_reason="end_turn", category=None):
    content = [SimpleNamespace(type="text", text=text)] if text is not None else []
    details = SimpleNamespace(category=category) if stop_reason == "refusal" else None
    return SimpleNamespace(content=content, stop_reason=stop_reason, stop_details=details, _request_id="req_test")


class FakeMessages:
    def __init__(self, result):
        self.result, self.kwargs = result, None

    def create(self, **kwargs):
        self.kwargs = kwargs
        if isinstance(self.result, Exception):
            raise self.result
        return self.result


def judge_with(result):
    messages = FakeMessages(result)
    return AnthropicJudge(client=SimpleNamespace(messages=messages)), messages


def scores(f=0, t=0, o=0, quote="This verse word"):
    def one(score, reason):
        return {"score": score, "reason": reason, "section": "Meaning", "quote": quote}
    return json.dumps({"factualRisk": one(f, "r1"), "oneSidedTheology": one(t, "r2"), "tone": one(o, "r3")})


def test_request_shape(written):
    judge, messages = judge_with(response(scores()))
    result = judge.judge(written, "For God so loved the world")
    assert messages.kwargs["model"] == RUBRIC_MODEL == "claude-haiku-5-5"
    assert messages.kwargs["output_config"]["format"]["type"] == "json_schema"
    assert "For God so loved" in messages.kwargs["messages"][0]["content"]
    assert result.reasons == [] and result.rubric["model"] == RUBRIC_MODEL


def test_scores_at_threshold_flag(written):
    judge, _ = judge_with(response(scores(t=THRESHOLD, o=1)))
    result = judge.judge(written, "")
    assert [r["message"] for r in result.reasons] == ["one-sided theology 2/3: r2"]


def test_refusal_flags_for_a_human(written):
    judge, _ = judge_with(response(stop_reason="refusal", category="general_harms"))
    result = judge.judge(written, "")
    assert result.rubric is None and "declined" in result.reasons[0]["message"]


@pytest.mark.parametrize("bad", [response("not json"), response(json.dumps({"tone": 1})), response(None),
                                 response(scores(), stop_reason="max_tokens")])
def test_unusable_responses_are_retryable(written, bad):
    judge, _ = judge_with(bad)
    with pytest.raises(RubricUnavailable):
        judge.judge(written, "")


def test_rate_limit_is_retryable_but_auth_errors_stop(written):
    request = httpx.Request("POST", "https://api.anthropic.com/v1/messages")
    limited = anthropic.RateLimitError("slow down", response=httpx.Response(429, request=request), body=None)
    judge, _ = judge_with(limited)
    with pytest.raises(RubricUnavailable):
        judge.judge(written, "")
    denied = anthropic.AuthenticationError("bad key", response=httpx.Response(401, request=request), body=None)
    judge, _ = judge_with(denied)
    with pytest.raises(anthropic.AuthenticationError):
        judge.judge(written, "")


def test_prompt_contains_every_section(written):
    prompt = build_prompt(written, "text")
    for title in ("Meaning", "Historical context", "Life in Bible times", "Theology", "Did you know?", "Apply it"):
        assert f"## {title}" in prompt


def test_quotes_are_verified_against_the_section_but_never_change_status(written):
    judge, _ = judge_with(response(scores()))
    good = judge.judge(written, "")
    assert all(good.rubric[k]["verified"] and good.rubric[k]["section"] == "meaning"
               for k in ("factualRisk", "oneSidedTheology", "tone"))
    assert good.unverified == 0 and good.reasons == []

    judge, _ = judge_with(response(scores(quote="custodia militaris")))
    bad = judge.judge(written, "")
    assert bad.unverified == 3 and bad.reasons == []  # logged, not flagged
    assert judge.reviews == 1 and judge.unverified_reviews == 1


def test_quote_must_come_from_the_named_section(written):
    written["applyIt"] = "Pray for one person today."
    judge, _ = judge_with(response(scores(quote="Pray for one person")))
    assert judge.judge(written, "").unverified == 3  # it is in applyIt, not Meaning
