import copy
import json

import pytest
from fastapi.testclient import TestClient

from conftest import put
from pipeline.plans import ai
from pipeline.plans.bible import VerseIndex
from pipeline.plans.build import build_structural
from pipeline.review.app import create_app
from test_plan_ai import TOPICAL, canonical_ranges

INDEX = VerseIndex.load()


@pytest.fixture
def setup(store, bible, written, tmp_path):
    put(store, written)
    plans = tmp_path / "plans"
    plans.mkdir()
    (plans / "john-21-days.json").write_text("{}")
    build_structural(plans, INDEX)
    good = ai.plan_from_topical("anxiety", ai.Draft(TOPICAL, "claude-opus-5-5"), 7, INDEX)
    (plans / f"{good['id']}.json").write_text(json.dumps(good))
    broken_data = copy.deepcopy(TOPICAL)
    broken_data["days"][0]["passages"] = [{"book": "JHN", "startChapter": 3, "startVerse": 1, "endChapter": 3, "endVerse": 99}]
    broken = ai.plan_from_topical("grief", ai.Draft(broken_data, "claude-opus-5-5"), 7, INDEX)
    (plans / f"{broken['id']}.json").write_text(json.dumps(broken))
    chrono = ai.plan_from_chronological(ai.Draft({"ranges": canonical_ranges()}, "claude-opus-5-5"), INDEX)
    (plans / f"{chrono['id']}.json").write_text(json.dumps(chrono))
    app = create_app(store, bible, plans_dir=plans, verse_index=INDEX, regenerate_log=tmp_path / "regen.jsonl")
    return TestClient(app), plans


def test_queue_puts_flagged_drafts_first(setup):
    client, _ = setup
    data = client.get("/api/plans").json()
    order = [p["id"] for p in data["plans"]]
    assert set(order[:2]) == {"grief-7-days", "bible-chronological"}  # flagged first (by title)
    assert order[2] == "anxiety-7-days" and data["toReview"] == 3


def test_plan_view_shows_days_minutes_and_problems(setup):
    client, _ = setup
    data = client.get("/api/plan/grief-7-days").json()
    assert data["days"][0]["labels"] == ["John 3:1-99"] and data["days"][1]["minutes"] > 0
    assert not data["canApprove"] and data["problems"][0]["check"] == "passage"


def test_approve_blocked_until_fixed_then_allowed(setup):
    client, plans = setup
    assert client.post("/api/plan/grief-7-days/approve", json={"reviewer": "Sam"}).status_code == 409
    r = client.post("/api/plan/grief-7-days/edit", json={"reviewer": "Sam", "days": [
        {"dayNumber": 1, "passages": "PSA.121.1-PSA.121.8", "intro": "Fixed intro."}]})
    assert r.status_code == 200 and r.json()["status"] == "auto_checked"
    assert client.post("/api/plan/grief-7-days/approve", json={"reviewer": "Sam"}).status_code == 200
    saved = json.loads((plans / "grief-7-days.json").read_text())
    assert saved["status"] == "reviewed" and saved["meta"]["reviewedBy"] == "Sam" and saved["meta"]["editedBy"] == "Sam"
    assert "grief-7-days" in json.loads((plans / "index.json").read_text())["categories"][0]["plans"]


def test_chronological_can_be_approved_after_verification(setup):
    client, _ = setup
    view = client.get("/api/plan/bible-chronological").json()
    assert view["plan"]["status"] == "flagged" and view["canApprove"]
    assert client.post("/api/plan/bible-chronological/approve", json={"reviewer": "Sam"}).json()["status"] == "reviewed"


def test_structural_plans_are_read_only(setup):
    client, _ = setup
    assert client.post("/api/plan/john-21-days/approve", json={"reviewer": "Sam"}).status_code == 409
    assert client.post("/api/plan/john-21-days/flag", json={"reviewer": "Sam", "note": "x"}).status_code == 409


def test_bad_passage_text_and_ids(setup):
    client, _ = setup
    r = client.post("/api/plan/anxiety-7-days/edit", json={"reviewer": "Sam", "days": [{"dayNumber": 1, "passages": "John 3:16"}]})
    assert r.status_code == 400
    assert client.get("/api/plan/..%2Fsecret").status_code == 404


def test_flag_and_regenerate(setup):
    client, plans = setup
    client.post("/api/plan/anxiety-7-days/flag", json={"reviewer": "Sam", "note": "Day 3 intro too preachy"})
    assert json.loads((plans / "anxiety-7-days.json").read_text())["status"] == "flagged"
    client.post("/api/plan/anxiety-7-days/regenerate", json={"reviewer": "Sam", "note": "More Psalms"})
    log = (plans.parent / "regen.jsonl").read_text().splitlines()
    assert json.loads(log[0])["kind"] == "plan"
