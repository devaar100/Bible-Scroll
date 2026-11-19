import copy
import hashlib
import json

import pytest

from pipeline import publish as p
from pipeline.community import MINISTRIES_FILE, featured, load_ministries

GOOD = {
    "formatVersion": 1,
    "ministries": [
        {"id": "river-translation", "name": "River", "description": {"en": "Bible translation.", "es": "Traducción."},
         "website": "https://example.org/r", "give_url": "https://example.org/r/give", "months_featured": ["2026-12", "2027-01"]},
        {"id": "test-harbor", "name": "Harbor (test)", "description": {"en": "Test."},
         "website": "https://example.org/h", "give_url": "https://example.org/h/give", "months_featured": ["2026-12"], "test": True},
    ],
}


def write(tmp_path, data):
    path = tmp_path / "ministries.json"
    path.write_text(json.dumps(data))
    return path


def test_the_repo_file_is_valid():
    assert load_ministries(MINISTRIES_FILE) is not None


def test_missing_file_is_none(tmp_path):
    assert load_ministries(tmp_path / "nope.json") is None


@pytest.mark.parametrize("change, problem", [
    (lambda d: d["ministries"][0].update(id="Bad Id"), "does not match"),
    (lambda d: d["ministries"][0].update(website="http://example.org"), "does not match"),
    (lambda d: d["ministries"][0].update(months_featured=["2026-13"]), "does not match"),
    (lambda d: d["ministries"][0]["description"].pop("en"), "'en' is a required property"),
    (lambda d: d["ministries"][0].update(amount="$5"), "Additional properties"),
    (lambda d: d["ministries"][1].update(id="river-translation"), "used twice"),
    (lambda d: d["ministries"][1].update(id="harbor"), "must have ids starting with test-"),
])
def test_bad_files_fail_loudly(tmp_path, change, problem):
    data = copy.deepcopy(GOOD)
    change(data)
    with pytest.raises(ValueError, match=problem):
        load_ministries(write(tmp_path, data))


def test_featured_filters_by_month_and_test():
    assert [m["id"] for m in featured(GOOD, "2026-12")] == ["river-translation"]
    assert [m["id"] for m in featured(GOOD, "2026-12", include_test=True)] == ["river-translation", "test-harbor"]
    assert [m["id"] for m in featured(GOOD, "2027-01")] == ["river-translation"]
    assert featured(GOOD, "2027-02") == []


def test_publish_pins_ministries_json_in_the_manifest(store, tmp_path):
    release = p.build_release(store, "https://x.web.app", discover_ids=[], ministries_path=write(tmp_path, GOOD))

    data = release.files["ministries.json"]
    assert json.loads(data) == GOOD  # published as-is: the app filters test entries itself
    assert release.manifest["community"] == {
        "url": "https://x.web.app/ministries.json", "count": 2,
        "sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data)}
    assert json.loads(release.files["manifest.json"]) == release.manifest  # passed schema/manifest.json


def test_no_ministries_file_leaves_community_out(store, tmp_path):
    release = p.build_release(store, "", discover_ids=[], ministries_path=tmp_path / "none.json")
    assert "community" not in release.manifest and "ministries.json" not in release.files


def test_dry_run_shows_a_ministries_change(store, tmp_path):
    release = p.build_release(store, "", discover_ids=[], ministries_path=write(tmp_path, GOOD))
    live = {"books": [], "devotionals": {"months": []}, "release": "old"}

    d = p.diff(release, live)
    assert d.community_changed and d.any
    assert "~ ministries.json: 2 ministries" in p.render_diff(d, release, live)

    same = dict(live, community=release.manifest["community"])
    assert not p.diff(release, same).community_changed
