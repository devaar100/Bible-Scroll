"""
The bundled bible.db (tools/build_bible_db.py): four translations under KJV-style keys. Verse counts, spot checks,
RV1909's remapped versification, metadata, and the builder's rules. Reads the committed asset; never the network.
"""
import json
import sqlite3
import sys
from pathlib import Path

import pytest

REPO = Path(__file__).resolve().parents[2]
BIBLE_DB = REPO / "app/src/main/assets/bible.db"
ROOM_SCHEMA = REPO / "app/schemas/aarnav100.developer.biblescroll.data.bible.BibleDatabase/1.json"
sys.path.insert(0, str(REPO / "tools"))
import build_bible_db as builder  # noqa: E402

ALL = ("WEB", "KJV", "BSB", "RV1909")
JHN, PSA, ACT, JOB, JON, HOS, NUM, MAL, JOL, ONE_JN = 43, 19, 44, 18, 32, 28, 4, 39, 29, 62


@pytest.fixture(scope="module")
def db():
    conn = sqlite3.connect(f"file:{BIBLE_DB}?mode=ro", uri=True)
    yield conn
    conn.close()


def text(db, tr, book, c, v):
    row = db.execute("SELECT text FROM verses WHERE translation_id=? AND book_id=? AND chapter=? AND verse=?", (tr, book, c, v)).fetchone()
    return row[0] if row else None


def title(db, tr, book, c):
    return db.execute("SELECT superscription FROM chapters WHERE translation_id=? AND book_id=? AND chapter=?", (tr, book, c)).fetchone()[0]


def verse_count(db, tr, book, c):
    return db.execute("SELECT verse_count FROM chapters WHERE translation_id=? AND book_id=? AND chapter=?", (tr, book, c)).fetchone()[0]


# ---- Shape ----

def test_four_translations_in_order(db):
    assert [r[0] for r in db.execute("SELECT id FROM translations ORDER BY sort_order")] == list(ALL)
    assert dict(db.execute("SELECT id, language_code FROM translations")) == {"WEB": "en", "KJV": "en", "BSB": "en", "RV1909": "es"}


@pytest.mark.parametrize("tr, verses", [("WEB", 31098), ("KJV", 31102), ("BSB", 31086), ("RV1909", 31084)])
def test_verse_and_chapter_counts(db, tr, verses):
    assert db.execute("SELECT COUNT(*) FROM verses WHERE translation_id=?", (tr,)).fetchone()[0] == verses
    assert db.execute("SELECT COUNT(*) FROM chapters WHERE translation_id=?", (tr,)).fetchone()[0] == 1189
    # The feed reads by ordinal: 1..N with no gaps, in Bible order.
    ordinals = [r[0] for r in db.execute(
        "SELECT ordinal FROM verses WHERE translation_id=? ORDER BY book_id, chapter, verse", (tr,))]
    assert ordinals == list(range(1, verses + 1))


def test_no_extra_keys_beyond_kjv_except_web_romans(db):
    extra = db.execute(
        "SELECT translation_id, book_id, chapter, verse FROM verses t WHERE translation_id <> 'KJV' AND NOT EXISTS "
        "(SELECT 1 FROM verses k WHERE k.translation_id='KJV' AND k.book_id=t.book_id AND k.chapter=t.chapter AND k.verse=t.verse)"
    ).fetchall()
    assert extra == [("WEB", 45, 14, 24), ("WEB", 45, 14, 25), ("WEB", 45, 14, 26)]  # WEB's Romans 16:25-27 placement


def test_room_tables_are_unchanged(db):
    """The asset must still match the app's Room schema exactly (extra metadata lives in translation_info)."""
    schema = json.loads(ROOM_SCHEMA.read_text())["database"]
    assert db.execute("SELECT identity_hash FROM room_master_table").fetchone()[0] == schema["identityHash"]
    columns = [r[1] for r in db.execute("PRAGMA table_info(translations)")]
    assert columns == ["id", "name", "language_code", "licence", "sort_order"]


# ---- Spot checks ----

@pytest.mark.parametrize("tr, start", [
    ("WEB", "For God so loved the world"), ("KJV", "For God so loved the world"),
    ("BSB", "For God so loved the world"), ("RV1909", "Porque de tal manera amó Dios al mundo"),
])
def test_john_3_16(db, tr, start):
    assert text(db, tr, JHN, 3, 16).startswith(start)


@pytest.mark.parametrize("tr, verse, heading", [
    ("WEB", "The LORD is my shepherd; I shall lack nothing.", "A Psalm by David."),
    ("KJV", "The LORD is my shepherd; I shall not want.", "A Psalm of David."),
    ("BSB", "The LORD is my shepherd; I shall not want.", "A Psalm of David."),
    ("RV1909", "JEHOVÁ es mi pastor; nada me faltará.", "Salmo de David."),
])
def test_psalm_23_1_and_its_title(db, tr, verse, heading):
    assert text(db, tr, PSA, 23, 1) == verse
    assert title(db, tr, PSA, 23) == heading


def test_acts_8_37_only_where_the_text_has_it(db):
    assert text(db, "KJV", ACT, 8, 37).startswith("And Philip said, If thou believest")
    assert text(db, "RV1909", ACT, 8, 37).startswith("Y Felipe dijo: Si crees de todo corazón")
    assert text(db, "WEB", ACT, 8, 37) is None
    assert text(db, "BSB", ACT, 8, 37) is None


def test_1_john_5_7_comma_in_the_textus_receptus_translations(db):
    assert "the Father, the Word, and the Holy Ghost" in text(db, "KJV", ONE_JN, 5, 7)
    assert "el Padre, el Verbo, y el Espíritu Santo" in text(db, "RV1909", ONE_JN, 5, 7)
    assert text(db, "WEB", ONE_JN, 5, 7) == "For there are three who testify:"
    assert text(db, "BSB", ONE_JN, 5, 7) == "For there are three that testify:"


@pytest.mark.parametrize("tr", ALL)
def test_psalm_51_title_split_from_verse_one(db, tr):
    assert verse_count(db, tr, PSA, 51) == 19
    assert "Nath" in title(db, tr, PSA, 51)  # every title names Nathan
    first = text(db, tr, PSA, 51, 1)
    assert "Nath" not in first and ("mercy" in first or first.startswith("TEN piedad"))


@pytest.mark.parametrize("tr", ALL)
def test_malachi_has_four_chapters_and_4_6(db, tr):
    assert db.execute("SELECT MAX(chapter) FROM chapters WHERE translation_id=? AND book_id=?", (tr, MAL)).fetchone()[0] == 4
    assert verse_count(db, tr, MAL, 4) == 6
    assert ("Elijah" if tr != "RV1909" else "Elías") in text(db, tr, MAL, 4, 5)


@pytest.mark.parametrize("tr", ALL)
def test_joel_2_and_3_follow_kjv(db, tr):
    assert (verse_count(db, tr, JOL, 2), verse_count(db, tr, JOL, 3)) == (32, 21)
    assert ("pour out" if tr != "RV1909" else "derramaré mi Espíritu") in text(db, tr, JOL, 2, 28).replace("My", "my")


# ---- RV1909 versification ----

@pytest.mark.parametrize("book, c, v, start", [
    (JON, 1, 17, "MAS Jehová había prevenido un gran pez"),
    (JON, 2, 1, "Y oró Jonás desde el vientre del pez"),
    (JOB, 38, 39, "¿CAZARÁS tú la presa para el león?"),
    (JOB, 39, 1, "¿Sabes tú el tiempo en que paren las cabras monteses"),  # KJV 39:1: the wild goats
    (JOB, 40, 6, "ENTONCES respondió Jehová á Job desde la oscuridad"),
    (JOB, 41, 1, "¿SACARÁS tú al leviathán con el anzuelo"),
    (HOS, 11, 12, "CERCÓME Ephraim con mentira"),
    (NUM, 12, 16, "Y DESPUÉS movió el pueblo de Haseroth"),
    (NUM, 13, 1, "Y Jehová habló á Moisés, diciendo:"),
])
def test_rv1909_verses_sit_under_their_kjv_reference(db, book, c, v, start):
    t = text(db, "RV1909", book, c, v)
    assert t is not None and t.startswith(start)


def test_rv1909_merged_verses_are_kept_whole_under_the_first_reference(db):
    assert text(db, "RV1909", JON, 2, 10) is None
    assert text(db, "RV1909", JON, 2, 9).endswith("Y mandó Jehová al pez, y vomitó á Jonás en tierra.")
    job = text(db, "RV1909", JOB, 39, 27)  # KJV 39:27-30 and 40:1-5 in one verse in the source
    assert job.startswith("¿Se remonta el águila") and "He aquí que yo soy vil" in job
    assert all(text(db, "RV1909", JOB, c, v) is None for c, v in [(39, 28), (39, 29), (39, 30), (40, 1), (40, 5)])
    assert db.execute("SELECT COUNT(*) FROM verses WHERE translation_id='RV1909' AND text=''").fetchone()[0] == 0


def test_rv1909_psalm_119_letters_removed_and_hallelujah_psalms_untouched(db):
    assert text(db, "RV1909", PSA, 119, 1).startswith("BIENAVENTURADOS los perfectos")
    assert text(db, "RV1909", PSA, 119, 9).startswith("¿Con qué limpiará el joven")
    for c in (111, 112, 113, 135, 146, 148, 149, 150):
        assert text(db, "RV1909", PSA, c, 1).startswith("Aleluya.")
        assert title(db, "RV1909", PSA, c) is None
    titled = {r[0] for r in db.execute("SELECT chapter FROM chapters WHERE translation_id=? AND book_id=19 AND superscription IS NOT NULL", ("RV1909",))}
    kjv = {r[0] for r in db.execute("SELECT chapter FROM chapters WHERE translation_id='KJV' AND book_id=19 AND superscription IS NOT NULL")}
    assert titled == kjv


# ---- Metadata and red letters ----

def test_translation_info_complete(db):
    rows = {r[0]: r for r in db.execute("SELECT id, attribution, has_red_letters, description_en, description_es, source_url, source_sha256, edition FROM translation_info")}
    assert set(rows) == set(ALL)
    for tr, row in rows.items():
        assert all(isinstance(x, str) and x.strip() for x in (row[1], row[3], row[4], row[5], row[7])), tr
        assert len(row[6]) == 64
        licence = db.execute("SELECT licence FROM translations WHERE id=?", (tr,)).fetchone()[0]
        assert licence.strip() and "Source:" in licence and licence.split("Source:")[1].strip()  # S8 splits here


@pytest.mark.parametrize("tr, expected", [("WEB", 1), ("KJV", 1), ("BSB", 0), ("RV1909", 0)])
def test_has_red_letters_matches_the_data(db, tr, expected):
    flag = db.execute("SELECT has_red_letters FROM translation_info WHERE id=?", (tr,)).fetchone()[0]
    marked = db.execute("SELECT COUNT(*) FROM verses WHERE translation_id=? AND jesus_ranges IS NOT NULL", (tr,)).fetchone()[0]
    assert flag == expected and (marked > 0) == bool(expected)


def test_no_empty_superscriptions(db):
    assert db.execute("SELECT COUNT(*) FROM chapters WHERE superscription = ''").fetchone()[0] == 0


# ---- Builder rules (no sources needed) ----

def test_rv1909_rules_are_consistent():
    moves, absorbed = builder.rv1909_key_map()
    assert len(absorbed) == 18
    targets = [(k[0],) + v for k, v in moves.items()]
    assert len(targets) == len(set(targets))
    assert moves[("JON", 2, 1)] == (1, 17) and absorbed[("JON", 2, 10)] == ("JON", 2, 9)


@pytest.mark.parametrize("verse_one, expected_title, expected_rest", [
    ("Salmo de David. A TI, oh Jehová, levantaré mi alma.", "Salmo de David.", "A TI, oh Jehová, levantaré mi alma."),
    ("Salmo de David, cuando huía. ¡OH Jehová, cuánto", "Salmo de David, cuando huía.", "¡OH Jehová, cuánto"),
    ("BIENAVENTURADO el varón que no anduvo", None, "BIENAVENTURADO el varón que no anduvo"),
    ("Aleluya. ALABARÉ á Jehová", "Aleluya.", "ALABARÉ á Jehová"),
])
def test_split_title(verse_one, expected_title, expected_rest):
    assert builder.split_title(verse_one) == (expected_title, expected_rest)
