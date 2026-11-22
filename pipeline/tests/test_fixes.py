import json

import pytest

from conftest import put, skeleton, words
from pipeline import batch as bt
from pipeline import fixes as fx
from pipeline.studies import validator

FIXED = words(50, "Fixed text")


def fixes_file(tmp_path, studies):
    path = tmp_path / "fixes.json"
    path.write_text(json.dumps({"format": fx.FORMAT, "studies": studies}))
    return path


@pytest.fixture
def setup(store, written):
    put(store, written)                                                                      # JHN.3.16, generated
    put(store, dict(json.loads(json.dumps(written)), id="JHN.3.17", status="reviewed",
                    meta={**written["meta"], "reviewedBy": "Sam", "reviewedAt": "2026-11-18T00:00:00+00:00"}))
    return store


def test_applies_only_matching_before_and_marks_edited(setup, bible, written, tmp_path):
    path = fixes_file(tmp_path, {"JHN.3.16": {
        "lifeInBibleTimes": {"before": written["lifeInBibleTimes"], "after": FIXED},
        "theology": {"before": "something else", "after": "never applied"}}})
    [result] = fx.apply_fixes(path, setup, bible, by="Rob")
    assert result.applied == ["lifeInBibleTimes"]
    assert result.skipped == [("theology", "current text differs from 'before'")]
    saved = setup.load("JHN.3.16")
    assert saved["lifeInBibleTimes"] == FIXED and saved["theology"] == written["theology"]
    assert saved["meta"]["model"] == written["meta"]["model"]                     # model info kept
    assert saved["meta"]["editedBy"] == "Rob" and saved["meta"]["editedAt"]
    assert saved["meta"]["editNote"] == "Hand-edited from fixes.json: lifeInBibleTimes"
    assert saved["contentVersion"] == "0123456789ab.1"
    assert saved["status"] == "auto_checked" and saved["checks"]["reasons"] == []
    assert not list(validator().iter_errors(saved))
    assert bt.protected(saved)                                                   # batch retries skip it


def test_rerun_is_a_no_op_and_reviewed_or_missing_studies_are_skipped(setup, bible, written, tmp_path):
    path = fixes_file(tmp_path, {
        "JHN.3.16": {"applyIt": {"before": written["applyIt"], "after": words(40, "Do this")}},
        "JHN.3.17": {"applyIt": {"before": written["applyIt"], "after": "x"}},
        "JHN.3.18": {"applyIt": {"before": "", "after": "x"}}})
    fx.apply_fixes(path, setup, bible, by="Rob")
    again = fx.apply_fixes(path, setup, bible, by="Rob")
    assert [r.skipped for r in again] == [[("applyIt", "already applied")], [("applyIt", "study is reviewed")],
                                          [("applyIt", "no study file")]]
    assert setup.load("JHN.3.16")["contentVersion"] == "0123456789ab.1"


def test_dry_run_writes_nothing_and_still_reports_checks(setup, bible, written, tmp_path):
    path = fixes_file(tmp_path, {"JHN.3.16": {"meaning": {"before": written["meaning"], "after": words(900)}}})
    [result] = fx.apply_fixes(path, setup, bible, by="Rob", dry_run=True)
    assert result.status == "flagged" and result.reasons[0]["check"] == "length"
    assert setup.load("JHN.3.16") == written


def test_rejects_wrong_format_and_non_prose_sections(tmp_path):
    bad = tmp_path / "bad.json"
    bad.write_text(json.dumps({"format": "other", "studies": {}}))
    with pytest.raises(fx.FixesError):
        fx.load(bad)
    with pytest.raises(fx.FixesError):
        fx.load(fixes_file(tmp_path, {"JHN.3.16": {"crossReferences": {"before": "", "after": ""}}}))


def test_the_committed_fixes_file_loads():
    from pipeline.paths import PIPELINE
    data = fx.load(PIPELINE / "fixes" / "discover_fixes.json")
    assert sum(len(s) for s in data["studies"].values()) == 16
