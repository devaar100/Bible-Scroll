import pytest

from pipeline.refs import Span, parse_osis_ref, parse_step_ref, parse_tsk_refs


def test_tahot_english_and_hebrew():
    r = parse_step_ref("Mal.4.1(3.19)#01=L", "TAHOT")
    assert (r.book_id, r.chapter, r.verse, r.hebrew, r.word_no, r.text_type) == (39, 4, 1, (3, 19), 1, "L")


def test_psalm_title_is_verse_zero():
    r = parse_step_ref("Psa.3.0(3.1)#02=L", "TAHOT")
    assert (r.book_id, r.chapter, r.verse, r.hebrew) == (19, 3, 0, (3, 1))


def test_numbered_book_codes_parse():
    # Regression: "1Sa"/"1Co" have only two letters after the digit.
    assert parse_step_ref("1Sa.21.1(21.2)#01=L", "TAHOT").book_id == 9
    assert parse_step_ref("1Co.1.1#01=NKO", "TAGNT").book_id == 46


def test_tagnt_kjv_brackets():
    r = parse_step_ref("Act.19.41[19.40]#03=NKO", "TAGNT")
    assert (r.chapter, r.verse, r.kjv, r.na) == (19, 41, (19, 40), None)
    r = parse_step_ref("Rom.16.25{14.24}#01=NKO", "TAGNT")
    assert (r.kjv, r.other) == (None, (14, 24))


def test_bad_step_ref_raises():
    with pytest.raises(ValueError):
        parse_step_ref("Xyz.1.1#01=L", "TAHOT")


def test_osis_ranges():
    assert parse_osis_ref("Gen.1.1") == Span(1, 1, 1, 1, 1)
    assert parse_osis_ref("Ps.8.3-Ps.8.5") == Span(19, 8, 3, 8, 5)
    cross = parse_osis_ref("Lev.27.34-Num.1.1")
    assert (cross.book_id, cross.end_book, cross.chapter_end, cross.verse_end) == (3, 4, 1, 1)


def spans(body, book=1, chapter=1):
    parsed = parse_tsk_refs(body, book, chapter)
    return [(r.span.book_id, r.span.chapter, r.span.verse, r.span.chapter_end, r.span.verse_end) for r in parsed.refs], parsed


def test_tsk_bare_verses_use_the_annotated_chapter():
    got, _ = spans("10,12,18; Ec 2:13; 11:7", book=1, chapter=1)
    assert got == [(1, 1, 10, 1, 10), (1, 1, 12, 1, 12), (1, 1, 18, 1, 18), (21, 2, 13, 2, 13), (21, 11, 7, 11, 7)]


def test_tsk_book_carries_over_and_ranges():
    got, _ = spans("Ps 33:6,9; 148:5; Isa 40:12-14; Jos 14:1-21:45; 21-29:15")
    assert got == [
        (19, 33, 6, 33, 6), (19, 33, 9, 33, 9), (19, 148, 5, 148, 5),
        (23, 40, 12, 40, 14), (6, 14, 1, 21, 45), (6, 21, 21, 29, 15),  # bare verse = last chapter named
    ]


def test_tsk_title_and_markers():
    got, parsed = spans("Nu 23:7; Ps 60:1; *title")
    assert got[-1] == (19, 60, 0, 60, 0)
    got, parsed = spans("Ps 69:34; *marg:")
    assert parsed.refs[-1].note == "marg" and not parsed.unparsed
    got, parsed = spans("Ps 1:1; *title Joh 10:22")
    assert got == [(19, 1, 1, 1, 1), (19, 1, 0, 1, 0), (43, 10, 22, 10, 22)]


def test_tsk_notes_without_digits_are_not_failures():
    _, parsed = spans("Ge 38:29; * Judah, Pharez, Zarah:")
    assert parsed.unparsed == []


def test_tsk_typos_are_reported():
    _, parsed = spans("22:14; 28:17:19; 32:30")
    assert parsed.unparsed == ["28:17:19"]
    _, parsed = spans("Heb 9:21-15")  # reversed range: can't tell what was meant
    assert parsed.unparsed == ["Heb 9:21-15"] and parsed.refs == []


def test_reversed_osis_range_rejected():
    with pytest.raises(ValueError):
        parse_osis_ref("Matt.26.6-Matt.26.5")
