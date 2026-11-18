import json

import pytest

from pipeline.plans.bible import NT_CODES, VerseIndex
from pipeline.plans.build import build_structural, bundle, load_plans, publishable
from pipeline.plans.catalogue import CATALOGUE, START_HERE
from pipeline.plans.validate import check_plan, verse_coverage

INDEX = VerseIndex.load()


@pytest.fixture(scope="module")
def built(tmp_path_factory):
    directory = tmp_path_factory.mktemp("plans")
    stats = build_structural(directory, INDEX)
    return directory, stats, load_plans(directory)


def test_at_least_25_valid_structural_plans(built):
    _, stats, plans = built
    assert stats["structural"] >= 25 and len(plans) == len(CATALOGUE)
    for plan in plans.values():
        assert check_plan(plan, INDEX) == [], plan["id"]
        assert plan["status"] == "auto_checked" and publishable(plan)


def test_whole_bible_plans_read_every_verse_exactly_once(built):
    plans = built[2]
    assert verse_coverage(plans["bible-in-a-year"], INDEX) == []
    nt = {k for k in INDEX.order if k[0] >= 40}
    assert verse_coverage(plans["new-testament-90-days"], INDEX, nt) == []
    assert len(plans["bible-in-a-year"]["days"]) == 365
    # Two tracks: every day has Old and New Testament reading.
    assert all({p["book"] in NT_CODES for p in d["passages"]} == {False, True} for d in plans["bible-in-a-year"]["days"])


def test_chapter_a_day_where_it_fits(built):
    proverbs = built[2]["proverbs-31-days"]["days"]
    assert [(d["passages"][0]["startChapter"], d["passages"][0]["startVerse"]) for d in proverbs] == [(c, 1) for c in range(1, 32)]
    pp = built[2]["psalms-and-proverbs-month"]["days"]
    assert [next(p for p in d["passages"] if p["book"] == "PRO")["startChapter"] for d in pp] == list(range(1, 32))


def test_index_groups_publishable_plans(built):
    directory, _, plans = built
    index = json.loads((directory / "index.json").read_text())
    listed = [pid for c in index["categories"] for pid in c["plans"]]
    assert sorted(listed) == sorted(plans) and index["startHere"] == START_HERE
    assert {p["id"] for p in index["plans"]} == set(plans)


def test_rebuild_is_stable(built, tmp_path):
    directory, _, plans = built
    build_structural(directory, INDEX)
    again = load_plans(directory)
    assert {k: v["contentVersion"] for k, v in again.items()} == {k: v["contentVersion"] for k, v in plans.items()}
    assert again["john-21-days"]["meta"] == plans["john-21-days"]["meta"]  # unchanged plan keeps its meta


def test_bundle_ships_only_publishable_without_review_fields(built, tmp_path):
    directory, _, plans = built
    draft = dict(plans["john-21-days"], id="anxiety-7-days", origin="ai_drafted", status="auto_checked",
                 meta={"model": "claude-opus-5-5", "generatedAt": "2026-11-18T00:00:00+00:00"})
    (directory / "anxiety-7-days.json").write_text(json.dumps(draft))
    assets = tmp_path / "assets"
    shipped = bundle(directory, assets)
    assert "anxiety-7-days" not in shipped and len(shipped) == len(CATALOGUE)  # AI drafts need review first
    one = json.loads((assets / "john-21-days.json").read_text())
    assert "meta" not in one and "checks" not in one and one["reviewed"] is False
    (directory / "anxiety-7-days.json").unlink()
