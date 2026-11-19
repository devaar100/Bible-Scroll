"""Checks on the real output. Skipped until `python -m pipeline build` and `studies` have been run."""
import json
import sqlite3
import unicodedata

import pytest

from pipeline.paths import SOURCE_DB
from pipeline.studies import STUDIES_DIR, validator

pytestmark = pytest.mark.skipif(
    not (SOURCE_DB.exists() and STUDIES_DIR.exists()), reason="run `pipeline build` and `pipeline studies` first"
)


def nfc(text):
    """Sources store combining marks in different orders; compare canonically."""
    return unicodedata.normalize("NFC", text)


def study(study_id):
    return json.loads((STUDIES_DIR / f"{study_id}.json").read_text())


@pytest.fixture(scope="module")
def all_studies():
    return [json.loads(p.read_text()) for p in sorted(STUDIES_DIR.glob("*.json"))]


@pytest.fixture(scope="module")
def db():
    connection = sqlite3.connect(f"file:{SOURCE_DB}?mode=ro", uri=True)
    yield connection
    connection.close()


def test_one_study_per_kjv_verse(all_studies, db):
    usfm = dict(db.execute("SELECT id, usfm FROM book"))
    expected = {f"{usfm[b]}.{c}.{v}" for b, c, v in db.execute("SELECT book_id, chapter, verse FROM kjv_verse")}
    assert len(expected) == 31102
    assert {s["id"] for s in all_studies} == expected


def test_every_file_valid(all_studies):
    check = validator()
    bad = [(s["id"], e.message) for s in all_studies for e in check.iter_errors(s)]
    assert bad == []


def test_every_cross_reference_points_to_an_existing_verse(all_studies, db):
    usfm_to_id = {u: i for i, u in db.execute("SELECT id, usfm FROM book")}
    verses = set(db.execute("SELECT book_id, chapter, verse FROM kjv_verse"))

    def key(text):
        book, chapter, verse = text.split(".")
        return usfm_to_id[book], int(chapter), int(verse)

    missing = []
    for s in all_studies:
        for x in s["crossReferences"]:
            start, _, end = x["ref"].partition("-")
            for part in filter(None, (start, end)):
                if key(part) not in verses:
                    missing.append((s["id"], x["ref"]))
            if end:
                assert key(start) < key(end), (s["id"], x["ref"])
            assert x["ref"].split("-")[0] != s["id"], "self reference"
    assert missing == []


def test_genesis_1_1():
    s = study("GEN.1.1")
    words = s["originalLanguage"]["words"]
    assert s["originalLanguage"]["language"] == "hebrew"
    assert len(words) == 7
    assert (words[0]["strongs"], nfc(words[0]["lemma"]), words[0]["transliteration"]) == \
        ("H7225G", nfc("רֵאשִׁית"), "be.re.Shit")
    assert words[2]["strongs"] == "H0430G" and words[2]["gloss"] == "God"
    assert s["crossReferences"][0]["source"] == "OpenBible"
    assert [c["licence"] for c in s["citations"]][0] == "CC BY 4.0"


def test_john_3_16_greek_and_top_reference(db):
    s = study("JHN.3.16")
    words = s["originalLanguage"]["words"]
    assert s["originalLanguage"] == {**s["originalLanguage"], "language": "greek", "textBasis": "critical"}
    assert nfc(words[0]["lemma"]).startswith(nfc("οὕτω"))
    assert any(nfc(w["lemma"]) == nfc("ἀγαπάω") for w in words)
    best = db.execute("SELECT max(votes) FROM xref WHERE dataset='OpenBible' AND from_book=43 AND from_chapter=3 "
                      "AND from_verse=16").fetchone()[0]
    top = db.execute("SELECT to_book, to_chapter, to_verse FROM xref WHERE dataset='OpenBible' AND from_book=43 "
                     "AND from_chapter=3 AND from_verse=16 AND votes=?", (best,)).fetchone()
    first = s["crossReferences"][0]["ref"].split("-")[0].split(".")
    assert (int(first[1]), int(first[2])) == top[1:]


def test_malachi_4_1_uses_hebrew_3_19():
    words = study("MAL.4.1")["originalLanguage"]["words"]
    assert nfc(words[0]["lemma"]) == nfc("כִּי") and nfc(words[1]["lemma"]) == nfc("הִנֵּה")


def test_psalm_3_1_is_hebrew_verse_2_not_the_title():
    words = study("PSA.3.1")["originalLanguage"]["words"]
    assert words[0]["strongs"].startswith("H3068")  # "LORD, how are they increased…" (Hebrew 3:2)


def test_kjv_only_verse_uses_textus_receptus():
    block = study("MAT.17.21")["originalLanguage"]
    assert block["textBasis"] == "textus_receptus" and block["words"]


def test_aramaic_in_daniel():
    assert study("DAN.2.4")["originalLanguage"]["language"] in ("aramaic", "hebrew")
    assert study("DAN.3.1")["originalLanguage"]["language"] == "aramaic"


def test_prose_status_and_model_agree(all_studies):
    """Holds before and after `generate`/`batch` write prose: a study is either unwritten or fully written."""
    from pipeline.studies import PROSE_FIELDS

    problems = []
    for s in all_studies:
        filled = [bool(s[name]) for name in PROSE_FIELDS]
        model = s["meta"]["model"]
        if not any(filled):
            # Unwritten: only "generated" (never written) or "failed" (the writer gave up); never checked or reviewed.
            if s["status"] not in ("generated", "failed"):
                problems.append((s["id"], "no prose but status " + s["status"]))
            if s["status"] == "generated" and model is not None:
                problems.append((s["id"], "no prose but a model is recorded"))
        elif all(filled):
            if model is None:
                problems.append((s["id"], "prose without meta.model"))
        elif s["status"] != "failed":
            problems.append((s["id"], "only some prose sections filled"))
    assert not problems, problems[:20]
