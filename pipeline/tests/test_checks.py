import pytest

from conftest import put, skeleton, words
from pipeline.checks import Rules, check_study, run
from pipeline.rubric import RubricResult, RubricUnavailable
from pipeline.studies import validator

RULES = Rules.load()
VALIDATOR = validator()


def outcome(study, bible, judge=None, **kw):
    return check_study(study, bible, RULES, VALIDATOR, judge, **kw)


def checks_named(o):
    return sorted({r["check"] for r in o.reasons})


def test_clean_study_passes(written, bible):
    o = outcome(written, bible)
    assert o.status == "auto_checked" and o.reasons == []


def test_length_limits(written, bible):
    written["didYouKnow"] = words(5)
    written["meaning"] = words(400)
    o = outcome(written, bible)
    assert o.status == "flagged"
    assert {(r["check"], r["section"]) for r in o.reasons} == {("length", "didYouKnow"), ("length", "meaning")}


def test_schema_failure_flags(written, bible):
    written["crossReferences"][0]["source"] = "Wikipedia"
    assert "schema" in checks_named(outcome(written, bible))


def test_references_must_exist(written, bible):
    written["theology"] = words(50, "Compare Romans 5:8 and Romans 8:28 with John 3:99 and Hezekiah 3:5.")
    o = outcome(written, bible)
    messages = [r["message"] for r in o.reasons if r["check"] == "reference"]
    assert len(messages) == 2
    assert any("John 3:99" in m for m in messages) and any("Hezekiah" in m for m in messages)


def test_original_language_mentions(written, bible):
    written["meaning"] = words(60, "The verb ἠγάπησεν (G25) and the Greek word agapaō and θεος are all here.")
    assert outcome(written, bible).reasons == []  # accents, leading zeros and transliteration all normalised
    written["meaning"] = words(60, "The Greek word phileo (G5368) and ἔρως are not in this verse.")
    msgs = [r["message"] for r in outcome(written, bible).reasons if r["check"] == "original_language"]
    assert len(msgs) == 3, msgs


def test_hebrew_points_ignored(written, bible):
    written["originalLanguage"] = {"language": "hebrew", "textBasis": "leningrad", "words": [
        {"surface": "בְּרֵאשִׁ֖ית", "lemma": "רֵאשִׁית", "transliteration": "be.re.Shit", "strongs": "H7225G",
         "gloss": "in beginning", "morphology": "HR/Ncfsa"}]}
    written["meaning"] = words(60, "The word רֵאשִׁית (rendered from ראשית, H7225) and the Hebrew word bereshit.")
    assert outcome(written, bible).reasons == []


def test_banned_phrases(written, bible):
    written["theology"] = words(60, "Scholars all agree this proves that everyone was saved.")
    msgs = [r["message"] for r in outcome(written, bible).reasons if r["check"] == "banned_phrase"]
    assert any("Scholars all agree" in m for m in msgs) and any("proves that" in m for m in msgs)


class FakeJudge:
    def __init__(self, result=None, error=None):
        self.result, self.error, self.calls = result, error, 0

    def judge(self, study, verse_text):
        self.calls += 1
        if self.error:
            raise self.error
        return self.result


RUBRIC_OK = {"model": "claude-haiku-5-5", **{k: {"score": 0, "reason": "fine"} for k in ("factualRisk", "oneSidedTheology", "tone")}}


def test_rubric_runs_only_after_free_checks_pass(written, bible):
    judge = FakeJudge(RubricResult(RUBRIC_OK, []))
    assert outcome(written, bible, judge).rubric == RUBRIC_OK and judge.calls == 1
    written["meaning"] = words(500)
    assert outcome(written, bible, judge).rubric is None and judge.calls == 1  # saved the API call
    assert outcome(written, bible, judge, rubric_always=True).rubric == RUBRIC_OK and judge.calls == 2


def test_rubric_flags(written, bible):
    flagged = RubricResult(RUBRIC_OK, [{"check": "rubric", "message": "tone 2/3: preachy"}])
    o = outcome(written, bible, FakeJudge(flagged))
    assert o.status == "flagged" and checks_named(o) == ["rubric"]


def test_rubric_outage_leaves_study_alone(written, bible, store):
    put(store, written)
    summary = run(store, bible, FakeJudge(error=RubricUnavailable("RateLimitError")), rules=RULES)
    assert summary.checked == 0 and summary.rubric_unavailable
    assert store.load("JHN.3.16")["status"] == "generated"


def test_run_writes_status_and_skips_unwritten(written, bible, store):
    put(store, written)
    put(store, skeleton("JHN.3.17"))
    bad = dict(written, id="JHN.3.18", applyIt=words(3))
    put(store, bad)
    summary = run(store, bible, None, rules=RULES)
    assert (summary.checked, summary.skipped_not_written) == (2, 1)
    good, flagged = store.load("JHN.3.16"), store.load("JHN.3.18")
    assert good["status"] == "auto_checked" and good["checks"]["reasons"] == []
    assert flagged["status"] == "flagged" and flagged["checks"]["reasons"][0]["section"] == "applyIt"
    assert not list(VALIDATOR.iter_errors(good)) and not list(VALIDATOR.iter_errors(flagged))
    assert store.load("JHN.3.17")["status"] == "generated"
    # Already-checked studies are left alone unless --recheck.
    assert run(store, bible, None, rules=RULES).checked == 0
    assert run(store, bible, None, rules=RULES, recheck=True).checked == 2


def test_recheck_keeps_reviewer_flags(written, bible, store):
    written["status"] = "flagged"
    written["checks"] = {"checkedAt": "2026-11-18T00:00:00+00:00", "checkerVersion": "review",
                         "reasons": [{"check": "reviewer", "message": "Check the date", "by": "A"}]}
    put(store, written)
    run(store, bible, None, rules=RULES, recheck=True)
    s = store.load("JHN.3.16")
    assert s["status"] == "flagged" and [r["check"] for r in s["checks"]["reasons"]] == ["reviewer"]


def test_banned_file_and_limits_load():
    assert RULES.banned and set(RULES.limits) >= {"meaning", "applyIt"}
