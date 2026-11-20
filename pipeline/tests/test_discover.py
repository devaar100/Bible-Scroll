"""Discover list: audit report, and one loader/order shared by the batch wave and discover.json."""
import json
import sqlite3

import pytest

from pipeline import publish as p
from pipeline import waves
from pipeline.discover import audit, render

LIST = """JHN.3.16
# a comment
PSA.23.1
John 3:16
ROM.5.8
JHN.3.16
JHN.3.99
ROM.5.8   # trailing comment
"""


@pytest.fixture
def dbs(source_db, bible_db):
    with sqlite3.connect(source_db) as db:
        db.executemany("INSERT INTO versification_map (dataset, native_ref, kjv_book, kjv_chapter, kjv_verse, kind, "
                       "words, note) VALUES (?,?,?,?,?,?,?,?)", [
                           ("TAGNT", "Jhn.3.16", 43, 3, 16, "identical", 25, None),
                           ("TAHOT", "Psa.23.1", 19, 23, 1, "split", 4, "native verse spread over Psa.23.title, Psa.23.1"),
                           ("TAGNT", "Rom.5.8", 45, 5, 8, "identical", 15, None),
                       ])
    with sqlite3.connect(bible_db) as db:
        db.executemany("INSERT INTO verses VALUES (?,?,?,?,?)",
                       [("KJV", 19, 23, 1, "The LORD is my shepherd"), ("WEB", 19, 23, 1, "Yahweh is my shepherd"),
                        ("WEB", 45, 5, 8, "But God commends his own love")])
    return source_db, bible_db


def test_audit_reports_each_problem_without_raising(dbs, tmp_path):
    path = tmp_path / "discover.txt"
    path.write_text(LIST)
    report = audit(path, *dbs)
    assert report.ids == ["JHN.3.16", "PSA.23.1", "ROM.5.8"]  # file order, first occurrence kept
    assert [l.text for l in report.malformed] == ["John 3:16"]
    assert [(l.text, l.number, first) for l, first in report.duplicates] == [("JHN.3.16", 6, 1), ("ROM.5.8", 8, 5)]
    assert [(l.text, why) for l, why in report.missing] == [("JHN.3.99", "no such KJV verse")]
    assert [(l.text, rows[0]["kind"]) for l, rows in report.mapping] == [("PSA.23.1", "split")]
    assert not report.clean and "Duplicates (first position kept): 2" in render(report, path)


def test_renumbered_psalm_is_reported(dbs, tmp_path):
    with sqlite3.connect(dbs[0]) as db:
        db.execute("INSERT INTO versification_map VALUES ('TAHOT','Psa.23.5',19,23,4,'renumbered',9,NULL)")
    with sqlite3.connect(dbs[1]) as db:
        db.executemany("INSERT INTO verses VALUES (?,?,?,?,?)", [("KJV", 19, 23, 4, "x"), ("WEB", 19, 23, 4, "x")])
    path = tmp_path / "discover.txt"
    path.write_text("PSA.23.4\n")
    (line, rows), = audit(path, *dbs).mapping
    assert line.text == "PSA.23.4" and rows[0]["native"] == "Psa.23.5" and rows[0]["kind"] == "renumbered"


def test_wave_and_discover_json_use_the_same_list_in_order(monkeypatch, tmp_path, store, written):
    from conftest import put
    path = tmp_path / "discover.txt"
    path.write_text("ROM.5.8 love\nJHN.3.16 love\nGEN.1.1 trust\n")
    monkeypatch.setattr(p, "DISCOVER_FILE", path)
    monkeypatch.setattr(p.load_discover, "__defaults__", (path,))
    monkeypatch.setattr(p.load_discover_entries, "__defaults__", (path,))
    assert waves.wave_ids(waves.load().get("discover"), []) == ["ROM.5.8", "JHN.3.16", "GEN.1.1"]
    for study_id in ("JHN.3.16", "ROM.5.8"):
        put(store, {**written, "id": study_id, "status": "auto_checked"})
    release = p.build_release(store, "")  # discover defaults to load_discover_entries()
    assert json.loads(release.files["discover.json"]) == {  # unpublished dropped
        "formatVersion": 2, "verses": [{"id": "ROM.5.8", "theme": "love"}, {"id": "JHN.3.16", "theme": "love"}]}


def test_audit_reports_missing_and_unknown_themes(dbs, tmp_path):
    path = tmp_path / "discover.txt"
    path.write_text("JHN.3.16 love\nPSA.23.1\nROM.5.8 joy\n")
    report = audit(path, *dbs)
    assert [(l.text, why) for l, why in report.themes] == [("PSA.23.1", "no theme"), ("ROM.5.8 joy", "unknown theme 'joy'")]
    assert not report.clean and "Theme missing or unknown" in render(report, path)


def test_wave_loader_allows_a_missing_theme_but_publish_does_not(tmp_path):
    path = tmp_path / "discover.txt"
    path.write_text("JHN.3.16 love\nPSA.23.1\n")
    assert p.load_discover(path) == ["JHN.3.16", "PSA.23.1"]
    with pytest.raises(ValueError, match="has no theme"):
        p.load_discover_entries(path)
    path.write_text("JHN.3.16 joy\n")
    with pytest.raises(ValueError, match="unknown theme"):
        p.load_discover(path)


def test_strict_loader_refuses_duplicates(tmp_path):
    path = tmp_path / "discover.txt"
    path.write_text("JHN.3.16\nJHN.3.16\n")
    with pytest.raises(ValueError, match="listed twice"):
        p.load_discover(path)


def test_real_discover_file_is_clean():
    if not p.DISCOVER_FILE.exists() or not p.load_discover():
        pytest.skip("discover.txt is empty")
    assert len(p.load_discover()) == len(set(p.load_discover()))
