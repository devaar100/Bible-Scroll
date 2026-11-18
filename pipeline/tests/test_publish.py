import copy
import gzip
import json

import pytest

from conftest import put, skeleton
from pipeline import publish as p
from pipeline.store import StudyStore


def flagged(study):
    return dict(study, status="flagged", checks={"checkedAt": "2026-11-18T00:00:00+00:00", "checkerVersion": "1",
                                                 "reasons": [{"check": "length", "message": "x"}]})


@pytest.fixture
def studies(store, written):
    written["meta"].update(reviewedBy="Sam Private", reviewedAt="2026-11-20T00:00:00+00:00", editedBy="Ed")
    put(store, dict(copy.deepcopy(written), status="reviewed"))                               # JHN.3.16
    put(store, dict(copy.deepcopy(written), id="JHN.3.17", status="auto_checked",
                    checks={"checkedAt": "2026-11-18T00:00:00+00:00", "checkerVersion": "1", "reasons": []}))
    put(store, flagged(dict(copy.deepcopy(written), id="JHN.3.18")))                          # left out
    put(store, dict(copy.deepcopy(written), id="JHN.3.19"))                                   # written, unchecked
    put(store, skeleton("JHN.3.20"))                                                          # not written
    put(store, dict(copy.deepcopy(written), id="ROM.5.8", status="auto_checked"))
    put(store, skeleton("GEN.1.1"))                                                           # book with nothing publishable
    return store


def book_payload(release, book):
    entry = next(b for b in release.manifest["books"] if b["book"] == book)
    return json.loads(gzip.decompress(release.files[p._site_path(entry["url"])]))


def test_only_auto_checked_and_reviewed_are_published(studies):
    release = p.build_release(studies, "https://x.web.app", discover_ids=[])
    assert [b["book"] for b in release.manifest["books"]] == ["JHN", "ROM"]  # GEN has nothing publishable
    jhn = next(b for b in release.manifest["books"] if b["book"] == "JHN")
    assert (jhn["verseCount"], jhn["reviewedCount"]) == (2, 1)
    assert [v["id"] for v in book_payload(release, "JHN")["verses"]] == ["JHN.3.16", "JHN.3.17"]
    assert release.left_out["JHN"] == {"flagged": 1, "generated": 1, "not_written": 1}


def test_public_files_hide_internal_fields(studies):
    release = p.build_release(studies, "", discover_ids=[])
    raw = json.dumps(book_payload(release, "JHN"))
    for secret in ("Sam Private", "reviewedBy", "editedBy", "checks", "checkedAt"):
        assert secret not in raw
    first = book_payload(release, "JHN")["verses"][0]
    assert first["reviewed"] is True and first["model"] == "claude-opus-5-5"


def test_manifest_entries_and_urls(studies):
    release = p.build_release(studies, "https://x.web.app/", discover_ids=[])
    entry = release.manifest["books"][0]
    path = f"studies/v{entry['contentVersion']}/JHN.json.gz"
    assert entry["url"] == f"https://x.web.app/{path}" and path in release.files
    assert entry["sha256"].startswith(entry["contentVersion"]) and entry["bytes"] == len(release.files[path])
    assert json.loads(release.files["manifest.json"]) == release.manifest
    assert release.files[path][:2] == b"\x1f\x8b"  # gzip


def test_builds_are_byte_identical_and_versions_are_per_book(studies):
    a = p.build_release(studies, "", discover_ids=[])
    b = p.build_release(studies, "", discover_ids=[])
    assert a.files == b.files and a.manifest["release"] == b.manifest["release"]
    s = studies.load("ROM.5.8")
    s["meaning"] = s["meaning"] + " Changed."
    studies.save(s)
    c = p.build_release(studies, "", discover_ids=[])
    old = {x["book"]: x["contentVersion"] for x in a.manifest["books"]}
    new = {x["book"]: x["contentVersion"] for x in c.manifest["books"]}
    assert old["JHN"] == new["JHN"] and old["ROM"] != new["ROM"] and a.manifest["release"] != c.manifest["release"]


def test_discover_order_and_unpublished_dropped(studies, tmp_path):
    path = tmp_path / "discover.txt"
    path.write_text("# feed\nROM.5.8\nJHN.3.18  # flagged\nJHN.3.16\n")
    release = p.build_release(studies, "", discover_ids=p.load_discover(path))
    assert json.loads(release.files["discover.json"])["verses"] == ["ROM.5.8", "JHN.3.16"]
    assert release.discover_dropped == ["JHN.3.18"] and release.manifest["discover"]["count"] == 2


@pytest.mark.parametrize("text", ["John 3:16\n", "JHN.3.16\nJHN.3.16\n"])
def test_bad_discover_file(tmp_path, text):
    path = tmp_path / "discover.txt"
    path.write_text(text)
    with pytest.raises(ValueError):
        p.load_discover(path)


def test_diff_against_live(studies):
    first = p.build_release(studies, "", discover_ids=[])
    assert [b["book"] for b in p.diff(first, None).added] == ["JHN", "ROM"]
    s = studies.load("JHN.3.18")
    s["status"] = "reviewed"
    s["meta"]["reviewedBy"] = "Sam"
    s["meta"]["reviewedAt"] = "2026-11-21T00:00:00+00:00"
    studies.save(s)
    second = p.build_release(studies, "", discover_ids=["JHN.3.16"])
    d = p.diff(second, first.manifest, live_discover=[])
    assert [new["book"] for _, new in d.changed] == ["JHN"] and [b["book"] for b in d.unchanged] == ["ROM"]
    text = p.render_diff(d, second, first.manifest)
    assert "JHN: 2 -> 3 verses (+1), reviewed 1 -> 2 (+1)" in text and "Discover: 0 -> 1" in text
    assert not p.diff(second, second.manifest, ["JHN.3.16"]).any


def test_write_site_keeps_previous_versions_for_in_flight_clients(studies, tmp_path):
    site = tmp_path / "site"
    first = p.build_release(studies, "https://x.web.app", discover_ids=[])
    p.write_site(first, site)
    s = studies.load("ROM.5.8")
    s["applyIt"] += " More."
    studies.save(s)
    second = p.build_release(studies, "https://x.web.app", discover_ids=[])
    kept = p.write_site(second, site, keep_from=first.manifest)
    old_rom = p._site_path(next(b for b in first.manifest["books"] if b["book"] == "ROM")["url"])
    assert kept == [old_rom] and (site / old_rom).exists()
    third = p.build_release(studies, "https://x.web.app", discover_ids=[])
    p.write_site(third, site, keep_from=second.manifest)  # two releases on: the oldest file goes
    assert not (site / old_rom).exists()
    assert json.loads((site / "manifest.json").read_text()) == third.manifest


def test_firebase_config_headers():
    hosting = json.loads((p.PIPELINE / "firebase.json").read_text())["hosting"]
    assert hosting["public"] == "out/site"
    rules = {h["source"]: {x["key"]: x["value"] for x in h["headers"]} for h in hosting["headers"]}
    assert rules["/studies/**"]["Content-Type"] == "application/gzip"
    assert "immutable" in rules["/studies/**"]["Cache-Control"]
    assert rules["/*.json"]["Cache-Control"] == "public, max-age=300"


def test_shipped_discover_file_parses():
    assert isinstance(p.load_discover(), list)
