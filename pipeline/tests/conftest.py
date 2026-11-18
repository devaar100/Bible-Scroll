"""Shared fixtures: a tiny source.db and bible.db, and a factory for written (AI-filled) studies."""
import copy
import json
import sqlite3

import pytest

from pipeline.books import BOOKS
from pipeline.schema import SCHEMA
from pipeline.store import Bible, StudyStore

VERSES = {
    (1, 1): 31, (43, 1): 51, (43, 3): 36, (45, 5): 21, (45, 8): 39, (19, 23): 6, (46, 13): 13, (62, 4): 21,
}


@pytest.fixture
def source_db(tmp_path):
    path = tmp_path / "source.db"
    db = sqlite3.connect(path)
    db.executescript(SCHEMA)
    db.executemany("INSERT INTO book VALUES (?,?,?,?,?)", [(b.id, b.usfm, b.step, b.osis, b.name) for b in BOOKS])
    db.executemany("INSERT INTO kjv_verse VALUES (?,?,?)",
                   [(b, c, v) for (b, c), n in VERSES.items() for v in range(1, n + 1)])
    db.executemany("INSERT INTO lexicon (strongs, dataset, lemma, translit) VALUES (?,?,?,?)", [
        ("G0025", "TBESG", "ἀγαπάω", "agapaō"), ("G2889", "TBESG", "κόσμος", "kosmos"),
        ("G2316", "TBESG", "θεός", "theos"),
    ])
    db.executemany(
        "INSERT INTO xref (dataset, from_book, from_chapter, from_verse, to_book, to_chapter, to_verse, to_book_end, "
        "to_chapter_end, to_verse_end, votes, keyword, native) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", [
            ("OpenBible", 43, 3, 16, 45, 5, 8, 45, 5, 8, 90, None, "Rom.5.8"),
            ("TSK", 43, 3, 16, 62, 4, 9, 62, 4, 10, None, "God", "1Jo 4:9,10"),
        ])
    db.commit()
    db.close()
    return path


@pytest.fixture
def bible_db(tmp_path):
    path = tmp_path / "bible.db"
    db = sqlite3.connect(path)
    db.execute("CREATE TABLE verses (translation_id TEXT, book_id INT, chapter INT, verse INT, text TEXT)")
    db.executemany("INSERT INTO verses VALUES (?,?,?,?,?)", [
        ("KJV", 43, 3, 16, "For God so loved the world..."), ("WEB", 43, 3, 16, "For God so loved the world..."),
        ("KJV", 45, 5, 8, "But God commendeth his love toward us..."),
        ("KJV", 62, 4, 9, "In this was manifested the love of God..."), ("KJV", 62, 4, 10, "Herein is love..."),
    ])
    db.commit()
    db.close()
    return path


@pytest.fixture
def bible(source_db, bible_db):
    return Bible(source_db, bible_db)


def words(n: int, start: str = "This verse") -> str:
    return " ".join([start] + ["word"] * (n - len(start.split())))


WRITTEN = {
    "id": "JHN.3.16",
    "schemaVersion": 3,
    "contentVersion": "0123456789ab",
    "originalLanguage": {
        "language": "greek",
        "textBasis": "critical",
        "words": [
            {"surface": "ἠγάπησεν", "lemma": "ἀγαπάω", "transliteration": "ēgapēsen", "strongs": "G0025",
             "gloss": "loved", "morphology": "V-AAI-3S"},
            {"surface": "θεὸς", "lemma": "θεός", "transliteration": "theos", "strongs": "G2316",
             "gloss": "God", "morphology": "N-NSM"},
            {"surface": "κόσμον", "lemma": "κόσμος", "transliteration": "kosmon", "strongs": "G2889",
             "gloss": "world", "morphology": "N-ASM"},
        ],
    },
    "crossReferences": [{"ref": "ROM.5.8", "source": "OpenBible"},
                        {"ref": "1JN.4.9-1JN.4.10", "source": "TSK", "note": "God"}],
    "meaning": words(60),
    "historicalContext": words(60),
    "lifeInBibleTimes": words(50),
    "theology": words(60),
    "didYouKnow": words(30),
    "applyIt": words(40),
    "citations": [{"source": "STEP Bible", "licence": "CC BY 4.0", "url": "https://stepbible.github.io/STEPBible-Data/"}],
    "status": "generated",
    "meta": {"model": "claude-opus-5-5", "generatedAt": "2026-11-18T00:00:00+00:00"},
}


@pytest.fixture
def written():
    """A fresh copy of a valid, AI-written study for John 3:16 that passes every free check."""
    return copy.deepcopy(WRITTEN)


@pytest.fixture
def store(tmp_path):
    directory = tmp_path / "studies"
    directory.mkdir()
    return StudyStore(directory)


def put(store: StudyStore, study: dict) -> dict:
    store.save(study)
    return study


def skeleton(study_id: str) -> dict:
    s = copy.deepcopy(WRITTEN)
    s["id"] = study_id
    for f in ("meaning", "historicalContext", "lifeInBibleTimes", "theology", "didYouKnow", "applyIt"):
        s[f] = ""
    s["meta"] = {"model": None, "generatedAt": "2026-11-18T00:00:00+00:00"}
    return s


@pytest.fixture(autouse=True)
def no_real_studies(tmp_path_factory, monkeypatch):
    """Devotional related passages read out/studies; tests use a tiny stand-in, not this machine's output."""
    import json
    from pipeline.devotionals import store
    studies = tmp_path_factory.mktemp("devotional-related-studies")
    for v in range(16, 21):  # the devotional fixture's passage, John 3:16-20
        (studies / f"JHN.3.{v}.json").write_text(json.dumps(
            {"crossReferences": [{"ref": "ROM.5.8", "source": "OpenBible"}, {"ref": "1JN.4.9-1JN.4.10", "source": "TSK"}]}))
    monkeypatch.setattr(store, "STUDIES_DIR", studies)
