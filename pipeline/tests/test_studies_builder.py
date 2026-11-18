import pytest

from pipeline.studies import (
    CITATIONS, MAX_CROSS_REFERENCES, Stats, WordRow, XrefRow, format_ref, make_study, rank_cross_references,
    select_words, validator,
)

USFM = {1: "GEN", 19: "PSA", 43: "JHN", 45: "ROM", 62: "1JN"}
HERE = (43, 3, 16)


def ob(to, votes, end=None):
    return XrefRow("OpenBible", to, end or to, votes, None)


def tsk(to, keyword=None, end=None):
    return XrefRow("TSK", to, end or to, None, keyword)


def refs(chosen):
    return [(x["ref"], x["source"]) for x in chosen]


def test_openbible_by_votes_then_tsk_in_order():
    chosen = rank_cross_references(HERE, [
        tsk((62, 4, 9), "loved"), ob((45, 5, 8), 40), ob((45, 8, 32), 90), tsk((45, 5, 1)),
    ], USFM)
    assert refs(chosen) == [("ROM.8.32", "OpenBible"), ("ROM.5.8", "OpenBible"),
                            ("1JN.4.9", "TSK"), ("ROM.5.1", "TSK")]
    assert chosen[2]["note"] == "loved" and "note" not in chosen[3]


def test_downvoted_self_titles_and_duplicates_skipped():
    chosen = rank_cross_references(HERE, [
        ob((45, 1, 1), 0), ob((45, 1, 2), -3),        # not positively voted
        tsk(HERE), tsk((43, 3, 14), end=(43, 3, 18)),  # self, and a range containing self
        tsk((19, 3, 0)),                               # Psalm title: not a verse in the app
        ob((45, 5, 8), 10), tsk((45, 5, 8), end=(45, 5, 10)),  # same start verse twice
    ], USFM)
    assert refs(chosen) == [("ROM.5.8", "OpenBible")]


def test_capped_at_eight():
    chosen = rank_cross_references(HERE, [ob((45, 8, v), 100 - v) for v in range(1, 20)], USFM)
    assert len(chosen) == MAX_CROSS_REFERENCES
    assert chosen[0]["ref"] == "ROM.8.1"


def test_ref_format():
    assert format_ref((43, 1, 1), (43, 1, 1), USFM) == "JHN.1.1"
    assert format_ref((43, 1, 1), (43, 1, 3), USFM) == "JHN.1.1-JHN.1.3"
    assert format_ref((1, 50, 26), (19, 1, 1), USFM) == "GEN.50.26-PSA.1.1"


def heb(text_type="L", surface="אֱלֹהִים", lemma="אֱלֹהִים", morph="HNcmpa", strongs="H0430G"):
    return WordRow("TAHOT", text_type, surface, "'E.lo.Him", strongs, lemma, morph, "God")


def grk(text_type, surface):
    return WordRow("TAGNT", text_type, surface, "x", "G0001", "α", "N-NSF", "g")


def test_hebrew_skips_lxx_and_empty_slots_and_falls_back_to_lexicon_lemma():
    stats = Stats()
    block = select_words([heb(), heb("X"), heb("Q(K)", surface=""), heb(lemma=None, strongs="H9033")],
                         {"H9033": "הוּא"}, stats)
    assert block["textBasis"] == "leningrad" and block["language"] == "hebrew"
    assert [w["lemma"] for w in block["words"]] == ["אֱלֹהִים", "הוּא"]
    assert (stats.lxx_words_skipped, stats.empty_slots_skipped, stats.lemma_from_lexicon) == (1, 1, 1)


def test_aramaic_majority():
    block = select_words([heb(morph="ANcmsa"), heb(morph="AVqp3ms"), heb()], {}, Stats())
    assert block["language"] == "aramaic"


def test_greek_prefers_critical_text_then_tr():
    critical = select_words([grk("NKO", "a"), grk("K", "b"), grk("no", "c"), grk("O", "d")], {}, Stats())
    assert (critical["textBasis"], [w["surface"] for w in critical["words"]]) == ("critical", ["a", "c"])
    stats = Stats()
    tr = select_words([grk("K", "b"), grk("ko", "e"), grk("O", "d")], {}, stats)
    assert (tr["textBasis"], [w["surface"] for w in tr["words"]], stats.tr_fallback_verses) == \
        ("textus_receptus", ["b", "e"], 1)


def test_incomplete_word_raises():
    with pytest.raises(ValueError):
        select_words([heb(lemma=None, strongs="H9999")], {}, Stats())


def test_citations_only_for_sources_used():
    original = select_words([heb()], {}, Stats())
    study = make_study((1, 1, 1), USFM, original, [{"ref": "JHN.1.1", "source": "TSK"}], "TAHOT",
                       "0123456789ab", "2026-11-18T00:00:00+00:00")
    assert study["citations"] == [CITATIONS["TAHOT"], CITATIONS["TSK"]]
    assert list(validator().iter_errors(study)) == []
