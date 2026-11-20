import copy
import json
import sys

import pytest

from conftest import put, skeleton
from pipeline import publish as p
from pipeline import reports as r
from pipeline.checks import Outcome, apply
from pipeline.studies import validator

REPORTED_AT = "2026-11-19T10:00:00+00:00"


@pytest.fixture
def queue(tmp_path):
    return tmp_path / "regenerate_queue.jsonl"


@pytest.fixture
def studies(store, written):
    reviewed = copy.deepcopy(written)
    reviewed["meta"].update(reviewedBy="Sam", reviewedAt="2026-11-18T12:00:00+00:00", promptVersion="62631d6c8e7e")
    put(store, dict(reviewed, status="reviewed"))                                             # JHN.3.16
    put(store, dict(copy.deepcopy(written), id="JHN.3.17", status="auto_checked",
                    checks={"checkedAt": "2026-11-18T00:00:00+00:00", "checkerVersion": "1", "reasons": []}))
    put(store, skeleton("JHN.3.18"))                                                          # not written
    return store


def queued(queue):
    return [json.loads(line) for line in queue.read_text().splitlines()] if queue.exists() else []


def test_report_flags_the_study_with_reason_and_date(studies, queue):
    results = r.report(studies, ["JHN.3.16"], "  Says Herod died in 1 BC;\n check the date ", by="Rob", queue=queue, at=REPORTED_AT)

    assert [(x.id, x.added) for x in results] == [("JHN.3.16", True)]
    study = studies.load("JHN.3.16")
    assert study["status"] == "flagged"
    assert study["checks"]["reasons"][-1] == {
        "check": "report", "message": "Says Herod died in 1 BC; check the date", "at": REPORTED_AT, "by": "Rob"}
    assert study["meta"]["reviewedBy"] == "Sam"  # history kept
    assert not list(validator().iter_errors(study))


def test_report_without_existing_checks_creates_the_block(studies, queue):
    r.report(studies, ["JHN.3.16"], "Offensive wording", queue=queue, at=REPORTED_AT)
    reasons = studies.load("JHN.3.16")["checks"]["reasons"]
    assert reasons == [{"check": "report", "message": "Offensive wording", "at": REPORTED_AT}]  # no "by" when not given


def test_report_appends_to_the_regenerate_queue(studies, queue):
    queue.write_text(json.dumps({"id": "PSA.23.1", "note": "from the review tool"}) + "\n")  # existing lines stay

    r.report(studies, ["JHN.3.16", "JHN.3.17"], "Wrong verse text", by="Rob", queue=queue, at=REPORTED_AT)

    lines = queued(queue)
    assert lines[0]["id"] == "PSA.23.1"
    assert lines[1] == {"kind": "study", "source": "report", "id": "JHN.3.16", "note": "Wrong verse text", "at": REPORTED_AT,
                        "contentVersion": "0123456789ab", "promptVersion": "62631d6c8e7e", "previousStatus": "reviewed",
                        "by": "Rob"}
    assert lines[2]["id"] == "JHN.3.17" and lines[2]["previousStatus"] == "auto_checked" and lines[2]["promptVersion"] is None


def test_reporting_twice_for_the_same_reason_changes_nothing(studies, queue):
    r.report(studies, ["JHN.3.16"], "Factual error", queue=queue, at=REPORTED_AT)
    again = r.report(studies, ["JHN.3.16", "JHN.3.16"], "Factual error", queue=queue, at="2026-11-20T00:00:00+00:00")

    assert [(x.id, x.added) for x in again] == [("JHN.3.16", False)]
    assert len(queued(queue)) == 1
    assert len(r.all_reports(studies.load("JHN.3.16"))) == 1

    r.report(studies, ["JHN.3.16"], "Also one-sided", queue=queue, at="2026-11-20T00:00:00+00:00")
    assert len(queued(queue)) == 2
    assert [x["message"] for x in r.open_reports(studies.load("JHN.3.16"))] == ["Factual error", "Also one-sided"]


@pytest.mark.parametrize("ids, problem", [
    (["JHN.3.99"], "no study file"),
    (["John 3:16"], "not a study id"),
    (["JHN.3.18"], "has no prose yet"),
])
def test_bad_ids_change_nothing_at_all(studies, queue, ids, problem):
    before = studies.load("JHN.3.16")
    with pytest.raises(r.ReportError, match=problem):
        r.report(studies, ["JHN.3.16", *ids], "Factual error", queue=queue)
    assert studies.load("JHN.3.16") == before  # the valid id wasn't touched either
    assert not queue.exists()


def test_a_reason_is_required(studies, queue):
    with pytest.raises(r.ReportError, match="reason"):
        r.report(studies, ["JHN.3.16"], "   ", queue=queue)


def test_reported_studies_are_left_out_of_publish(studies, queue):
    assert [v for b in p.build_release(studies, "", discover=[]).manifest["books"] for v in [b["verseCount"]]] == [2]

    r.report(studies, ["JHN.3.16"], "Unfair or one-sided", queue=queue, at=REPORTED_AT)
    release = p.build_release(studies, "", discover=[("JHN.3.16", "love"), ("JHN.3.17", "love")])

    assert release.manifest["books"][0]["verseCount"] == 1
    assert release.reported == [("JHN.3.16", "Unfair or one-sided")]
    assert release.left_out["JHN"] == {"reported": 1, "not_written": 1}  # counted once, not also as flagged
    assert release.discover_dropped == ["JHN.3.16"]


def test_reported_study_stays_out_even_if_its_status_is_set_back(studies, queue):
    r.report(studies, ["JHN.3.17"], "Factual error", queue=queue, at=REPORTED_AT)
    study = studies.load("JHN.3.17")
    studies.save(dict(study, status="auto_checked"))  # e.g. a hand edit of the status alone

    release = p.build_release(studies, "", discover=[])
    assert [x for x, _ in release.reported] == ["JHN.3.17"]
    assert release.manifest["books"][0]["verseCount"] == 1  # only JHN.3.16


def test_dry_run_lists_the_reported_studies(studies, queue):
    r.report(studies, ["JHN.3.16", "JHN.3.17"], "Wrong verse or translation text", queue=queue, at=REPORTED_AT)
    release = p.build_release(studies, "", discover=[])
    text = p.render_diff(p.diff(release, None), release, None)

    assert "Left out as reported (`pipeline reports` lists them): 2 verse(s)" in text
    assert "    JHN.3.16: Wrong verse or translation text" in text
    assert "    JHN.3.17: Wrong verse or translation text" in text
    assert "Left out as flagged" not in text


def test_recheck_keeps_an_open_report(studies, queue):
    r.report(studies, ["JHN.3.17"], "Offensive", queue=queue, at=REPORTED_AT)
    rechecked = apply(studies.load("JHN.3.17"), Outcome("auto_checked", reasons=[]))

    assert rechecked["status"] == "flagged"
    assert [x["check"] for x in rechecked["checks"]["reasons"]] == ["report"]


@pytest.mark.parametrize("field", ["generatedAt", "editedAt", "reviewedAt"])
def test_regenerating_editing_or_reviewing_resolves_the_report(studies, queue, field):
    r.report(studies, ["JHN.3.17"], "Factual error", queue=queue, at=REPORTED_AT)
    study = studies.load("JHN.3.17")
    study["meta"][field] = "2026-11-21T09:00:00Z"  # after the report (Z form, as the batch writer may store it)
    if field == "reviewedAt":
        study["meta"]["reviewedBy"] = "Sam"
    studies.save(study)

    assert r.open_reports(study) == []
    # A recheck can now clear it, and publish no longer holds it back on the report's account.
    assert apply(study, Outcome("auto_checked", reasons=[]))["status"] == "auto_checked"
    studies.save(dict(study, status="auto_checked"))
    assert p.build_release(studies, "", discover=[]).reported == []


def test_reports_lists_open_and_optionally_resolved(studies, queue):
    r.report(studies, ["JHN.3.16"], "Factual error", by="Rob", queue=queue, at=REPORTED_AT)
    r.report(studies, ["JHN.3.17"], "One-sided", queue=queue, at=REPORTED_AT)
    resolved = studies.load("JHN.3.17")
    resolved["meta"]["generatedAt"] = "2026-11-22T00:00:00+00:00"
    studies.save(resolved)

    open_rows = r.list_reports(studies)
    assert [(x.id, x.status, x.reason, x.by, x.open) for x in open_rows] == [("JHN.3.16", "flagged", "Factual error", "Rob", True)]
    assert [x.id for x in r.list_reports(studies, include_resolved=True)] == ["JHN.3.16", "JHN.3.17"]

    text = r.render(r.list_reports(studies, include_resolved=True))
    assert text.startswith("2 reported studies:")
    assert "JHN.3.16  [flagged, open]  2026-11-19 by Rob: Factual error" in text
    assert "JHN.3.17  [flagged, resolved" in text
    assert r.render([]) == "No reported studies."


def test_cli_report_then_reports(studies, queue, monkeypatch, capsys):
    from pipeline import __main__ as cli
    from pipeline import studies as studies_module
    monkeypatch.setattr(studies_module, "STUDIES_DIR", studies.directory)
    monkeypatch.setattr(r, "QUEUE", queue)

    monkeypatch.setattr(sys, "argv", ["pipeline", "report", "JHN.3.16", "--reason", "Factual error", "--by", "Rob"])
    cli.main()
    assert "JHN.3.16: flagged and queued" in capsys.readouterr().out
    assert queued(queue)[0]["id"] == "JHN.3.16"

    monkeypatch.setattr(sys, "argv", ["pipeline", "reports"])
    cli.main()
    assert "JHN.3.16  [flagged, open]" in capsys.readouterr().out

    monkeypatch.setattr(sys, "argv", ["pipeline", "report", "JHN.3.18", "--reason", "x"])
    with pytest.raises(SystemExit, match="has no prose yet"):
        cli.main()
