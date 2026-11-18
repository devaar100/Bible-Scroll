"""SQLite schema for data/source.db. Verse keys (book_id, chapter, verse) are the app's KJV keys."""

SCHEMA = """
PRAGMA foreign_keys = ON;

-- One row per downloaded file (from data/raw/manifest.json).
CREATE TABLE source_file (
    key        TEXT PRIMARY KEY,
    dataset    TEXT NOT NULL,      -- TAHOT, TAGNT, TBESH, TBESG, TVTMS, OpenBible, TSK
    url        TEXT NOT NULL,
    file       TEXT NOT NULL,
    bytes      INTEGER NOT NULL,
    sha256     TEXT NOT NULL,
    retrieved  TEXT NOT NULL
);

CREATE TABLE book (
    id    INTEGER PRIMARY KEY,     -- the app's book id, 1-66
    usfm  TEXT NOT NULL,
    step  TEXT NOT NULL,
    osis  TEXT NOT NULL,
    name  TEXT NOT NULL
);

-- Target versification: the app's KJV verses (from app/src/main/assets/bible.db).
CREATE TABLE kjv_verse (
    book_id  INTEGER NOT NULL REFERENCES book(id),
    chapter  INTEGER NOT NULL,
    verse    INTEGER NOT NULL,
    PRIMARY KEY (book_id, chapter, verse)
) WITHOUT ROWID;

-- KJV chapters that have a title (Psalm superscription), i.e. a valid verse-0 target.
CREATE TABLE kjv_superscription (
    book_id  INTEGER NOT NULL,
    chapter  INTEGER NOT NULL,
    PRIMARY KEY (book_id, chapter)
) WITHOUT ROWID;

-- Tagged original-language words. One row per word (TAHOT/TAGNT row).
CREATE TABLE word (
    id            INTEGER PRIMARY KEY,
    dataset       TEXT NOT NULL,   -- TAHOT | TAGNT
    book_id       INTEGER NOT NULL REFERENCES book(id),
    -- KJV key; verse 0 = Psalm title (superscription). NULL chapter/verse = did not map (see versification report).
    chapter       INTEGER,
    verse         INTEGER,
    native_ref    TEXT NOT NULL,   -- reference as written in the source row, e.g. Mal.4.1(3.19)
    word_no       INTEGER NOT NULL,-- word number within the source's own verse
    text_type     TEXT NOT NULL,   -- TAHOT: L/Q/K/R/X...  TAGNT: N/K/O (see LICENCES/README)
    surface       TEXT NOT NULL,   -- Hebrew/Greek as printed (affix slashes removed)
    translit      TEXT,
    strongs       TEXT,            -- root dStrong, e.g. H7225G / G0976
    strongs_all   TEXT,            -- every dStrong in the word incl. prefixes/suffixes
    lemma         TEXT,            -- dictionary form
    morph         TEXT,            -- morphology code (ETCBC/OpenScriptures for Heb, Robinson-style for Grk)
    gloss         TEXT,            -- source's English rendering of this word in context
    editions      TEXT             -- TAGNT only: editions containing the word
);
CREATE INDEX word_verse ON word(book_id, chapter, verse);
CREATE INDEX word_strongs ON word(strongs);

-- TBESH/TBESG brief lexicon entries, keyed by disambiguated Strong's (dStrong).
CREATE TABLE lexicon (
    strongs     TEXT PRIMARY KEY,  -- dStrong, e.g. H0001G
    dataset     TEXT NOT NULL,     -- TBESH | TBESG
    estrong     TEXT,
    lemma       TEXT,
    translit    TEXT,
    morph       TEXT,
    gloss       TEXT,
    definition  TEXT               -- HTML as published (<br>, <i>)
);

-- Cross references. From/to are KJV keys; to_* is a range (start = end for a single verse).
CREATE TABLE xref (
    id            INTEGER PRIMARY KEY,
    dataset       TEXT NOT NULL,   -- OpenBible | TSK
    from_book     INTEGER NOT NULL,
    from_chapter  INTEGER NOT NULL,
    from_verse    INTEGER NOT NULL,
    to_book       INTEGER NOT NULL,
    to_chapter    INTEGER NOT NULL,
    to_verse      INTEGER NOT NULL,
    to_book_end   INTEGER NOT NULL,  -- = to_book except for the few OpenBible ranges that run into the next book
    to_chapter_end INTEGER NOT NULL,
    to_verse_end  INTEGER NOT NULL,
    votes         INTEGER,         -- OpenBible only (can be negative)
    keyword       TEXT,            -- TSK only: the word/phrase the reference annotates
    note          TEXT,            -- TSK only: marg / Heb / Gr marker that follows the reference
    native        TEXT NOT NULL    -- the reference as written in the source
);
CREATE INDEX xref_from ON xref(from_book, from_chapter, from_verse);

-- Verse-level versification map: how each source verse lands on KJV keys.
CREATE TABLE versification_map (
    dataset       TEXT NOT NULL,   -- TAHOT | TAGNT | OpenBible | TSK
    native_ref    TEXT NOT NULL,   -- the source's own verse, e.g. Mal.3.19 (Hebrew) or Act.19.41 (NRSV)
    kjv_book      INTEGER,
    kjv_chapter   INTEGER,
    kjv_verse     INTEGER,         -- 0 = superscription; NULL = no KJV home
    kind          TEXT NOT NULL,   -- identical | renumbered | title | split | merged | source_only | kjv_only
    words         INTEGER,         -- words of native_ref landing on this KJV verse (TAHOT/TAGNT)
    note          TEXT
);
CREATE INDEX vmap_kind ON versification_map(dataset, kind);
"""
