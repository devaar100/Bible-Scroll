import json

import pytest
from fastapi.testclient import TestClient

from conftest import put, skeleton, words
from pipeline.review.app import create_app
from pipeline.studies import validator

VALIDATOR = validator()


@pytest.fixture
def setup(store, bible, written, tmp_path):
    written["status"] = "auto_checked"
    put(store, written)
    flagged = dict(written, id="JHN.3.17", status="flagged",
                   checks={"checkedAt": "2026-11-18T00:00:00+00:00", "checkerVersion": "1",
                           "reasons": [{"check": "length", "message": "too short", "section": "applyIt"}]})
    put(store, flagged)
    put(store, skeleton("JHN.3.18"))
    priority = tmp_path / "priority.txt"
    priority.write_text("")
    log = tmp_path / "regenerate_queue.jsonl"
    app = create_app(store, bible, reviewer=None, priority_path=priority, regenerate_log=log)
    return TestClient(app), store, log


def test_page_and_queue(setup):
    client, _, _ = setup
    assert "Study review" in client.get("/").text
    q = client.get("/api/queue").json()
    assert [i["id"] for i in q["items"]] == ["JHN.3.17", "JHN.3.16"]  # flagged first; skeleton not listed


def test_study_view_has_verse_and_source_data(setup):
    client, _, _ = setup
    data = client.get("/api/study/JHN.3.16").json()
    assert data["verse"]["KJV"].startswith("For God so loved")
    assert data["crossReferenceText"]["1JN.4.9-1JN.4.10"].startswith("In this was manifested")
    assert {c["dataset"] for c in data["candidates"]} == {"OpenBible", "TSK"}
    assert data["schemaErrors"] == [] and data["queue"]["position"] == 1


def test_approve_writes_reviewer_and_time(setup):
    client, store, _ = setup
    assert client.post("/api/study/JHN.3.16/approve", json={}).status_code == 400  # name required
    r = client.post("/api/study/JHN.3.16/approve", json={"reviewer": "Sam"})
    assert r.status_code == 200
    saved = store.load("JHN.3.16")
    assert saved["status"] == "reviewed" and saved["meta"]["reviewedBy"] == "Sam" and saved["meta"]["reviewedAt"]
    assert not list(VALIDATOR.iter_errors(saved))
    assert "JHN.3.16" not in [i["id"] for i in client.get("/api/queue").json()["items"]]


def test_edit_bumps_version_and_rechecks(setup):
    client, store, _ = setup
    r = client.post("/api/study/JHN.3.17/edit", json={"reviewer": "Sam", "fields": {"applyIt": words(40)}})
    assert r.status_code == 200
    saved = store.load("JHN.3.17")
    assert saved["contentVersion"] == "0123456789ab.1" and saved["meta"]["editedBy"] == "Sam"
    assert saved["status"] == "auto_checked" and saved["checks"]["reasons"] == []  # length fixed
    r = client.post("/api/study/JHN.3.17/edit", json={"reviewer": "Sam", "fields": {"meaning": words(500)}})
    assert store.load("JHN.3.17")["status"] == "flagged" and store.load("JHN.3.17")["contentVersion"].endswith(".2")
    assert client.post("/api/study/JHN.3.17/edit",
                       json={"reviewer": "Sam", "fields": {"crossReferences": "x"}}).status_code == 400


def test_flag_with_note(setup):
    client, store, _ = setup
    assert client.post("/api/study/JHN.3.16/flag", json={"reviewer": "Sam"}).status_code == 400  # note required
    client.post("/api/study/JHN.3.16/flag", json={"reviewer": "Sam", "note": "Date in v2 looks wrong"})
    saved = store.load("JHN.3.16")
    assert saved["status"] == "flagged"
    assert saved["checks"]["reasons"][-1] | {"at": None} == {"check": "reviewer", "message": "Date in v2 looks wrong",
                                                            "by": "Sam", "at": None}


def test_regenerate_queues_request_and_flags(setup):
    client, store, log = setup
    client.post("/api/study/JHN.3.16/regenerate", json={"reviewer": "Sam", "note": "Too generic"})
    entry = json.loads(log.read_text().splitlines()[0])
    assert (entry["id"], entry["note"], entry["by"]) == ("JHN.3.16", "Too generic", "Sam")
    assert store.load("JHN.3.16")["status"] == "flagged"


def test_unwritten_and_bad_ids(setup):
    client, _, _ = setup
    assert client.post("/api/study/JHN.3.18/approve", json={"reviewer": "Sam"}).status_code == 409
    assert client.get("/api/study/..%2Fsecrets").status_code == 404
    assert client.get("/api/study/XYZ.1.1").status_code == 404


def test_invalid_change_is_refused(setup):
    client, store, _ = setup
    s = store.load("JHN.3.16")
    s["meaning"] = ""
    store.save(s)  # prose emptied outside the tool: approving would break the schema
    r = client.post("/api/study/JHN.3.16/approve", json={"reviewer": "Sam"})
    assert r.status_code == 409 and store.load("JHN.3.16")["status"] == "auto_checked"
