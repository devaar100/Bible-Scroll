import pytest

from conftest import put, skeleton, words
from pipeline.checks import Rules, check_study, length_band, run
from pipeline.rubric import RubricResult, RubricUnavailable
from pipeline.studies import validator

RULES = Rules.load()
VALIDATOR = validator()


def outcome(study, bible, judge=None, **kw):
    return check_study(study, bible, RULES, VALIDATOR, judge, **kw)


def checks_named(o):
    return sorted({r["check"] for r in o.reasons})


def test_clean_study_passes(written, bible):
    o = outcome(written, bible)
    assert o.status == "auto_checked" and o.reasons == []


def test_length_limits(written, bible):
    written["didYouKnow"] = words(5, "ēgapēsen")
    written["meaning"] = words(400)
    o = outcome(written, bible)
    assert o.status == "flagged"
    assert {(r["check"], r["section"]) for r in o.reasons} == {("length", "didYouKnow"), ("length", "meaning")}


def test_schema_failure_flags(written, bible):
    written["crossReferences"][0]["source"] = "Wikipedia"
    assert "schema" in checks_named(outcome(written, bible))


def test_references_must_exist(written, bible):
    written["theology"] = words(80, "Compare Romans 5:8 and Romans 8:28 with John 3:99 and Hezekiah 3:5.")
    o = outcome(written, bible)
    messages = [r["message"] for r in o.reasons if r["check"] == "reference"]
    assert len(messages) == 2
    assert any("John 3:99" in m for m in messages) and any("Hezekiah" in m for m in messages)


def test_original_language_mentions(written, bible):
    written["meaning"] = words(60, "The verb ἠγάπησεν (G25) and the Greek word agapaō and θεος are all here.")
    assert outcome(written, bible).reasons == []  # accents, leading zeros and transliteration all normalised
    written["meaning"] = words(60, "The Greek word phileo (G5368) and ἔρως are not in this verse.")
    msgs = [r["message"] for r in outcome(written, bible).reasons if r["check"] == "original_language"]
    assert len(msgs) == 3, msgs


def test_hebrew_points_ignored(written, bible):
    written["originalLanguage"] = {"language": "hebrew", "textBasis": "leningrad", "words": [
        {"surface": "בְּרֵאשִׁ֖ית", "lemma": "רֵאשִׁית", "transliteration": "be.re.Shit", "strongs": "H7225G",
         "gloss": "in beginning", "morphology": "HR/Ncfsa"}]}
    written["meaning"] = words(60, "The word רֵאשִׁית (rendered from ראשית, H7225) and the Hebrew word bereshit.")
    written["didYouKnow"] = words(30, "The opening word bereshit joins in and beginning.")
    assert outcome(written, bible).reasons == []


def test_banned_phrases(written, bible):
    written["theology"] = words(80, "Scholars all agree this proves that everyone was saved.")
    msgs = [r["message"] for r in outcome(written, bible).reasons if r["check"] == "banned_phrase"]
    assert any("Scholars all agree" in m for m in msgs) and any("proves that" in m for m in msgs)


class FakeJudge:
    def __init__(self, result=None, error=None):
        self.result, self.error, self.calls = result, error, 0

    def judge(self, study, verse_text, supplied=None):
        self.calls += 1
        self.supplied = supplied
        if self.error:
            raise self.error
        return self.result


RUBRIC_OK = {"model": "claude-haiku-5-5", **{k: {"score": 0, "reason": "fine"} for k in ("factualRisk", "oneSidedTheology", "tone")}}


def test_rubric_runs_only_after_free_checks_pass(written, bible):
    judge = FakeJudge(RubricResult(RUBRIC_OK, []))
    assert outcome(written, bible, judge).rubric == RUBRIC_OK and judge.calls == 1
    written["meaning"] = words(500)
    assert outcome(written, bible, judge).rubric is None and judge.calls == 1  # saved the API call
    assert outcome(written, bible, judge, rubric_always=True).rubric == RUBRIC_OK and judge.calls == 2


def test_rubric_flags(written, bible):
    flagged = RubricResult(RUBRIC_OK, [{"check": "rubric", "message": "tone 2/3: preachy"}])
    o = outcome(written, bible, FakeJudge(flagged))
    assert o.status == "flagged" and checks_named(o) == ["rubric"]


def test_rubric_outage_leaves_study_alone(written, bible, store):
    put(store, written)
    summary = run(store, bible, FakeJudge(error=RubricUnavailable("RateLimitError")), rules=RULES)
    assert summary.checked == 0 and summary.rubric_unavailable
    assert store.load("JHN.3.16")["status"] == "generated"


def test_run_writes_status_and_skips_unwritten(written, bible, store):
    put(store, written)
    put(store, skeleton("JHN.3.17"))
    bad = dict(written, id="JHN.3.18", applyIt=words(3))
    put(store, bad)
    summary = run(store, bible, None, rules=RULES)
    assert (summary.checked, summary.skipped_not_written) == (2, 1)
    good, flagged = store.load("JHN.3.16"), store.load("JHN.3.18")
    assert good["status"] == "auto_checked" and good["checks"]["reasons"] == []
    assert flagged["status"] == "flagged" and flagged["checks"]["reasons"][0]["section"] == "applyIt"
    assert not list(VALIDATOR.iter_errors(good)) and not list(VALIDATOR.iter_errors(flagged))
    assert store.load("JHN.3.17")["status"] == "generated"
    # Already-checked studies are left alone unless --recheck.
    assert run(store, bible, None, rules=RULES).checked == 0
    assert run(store, bible, None, rules=RULES, recheck=True).checked == 2


def test_recheck_keeps_reviewer_flags(written, bible, store):
    written["status"] = "flagged"
    written["checks"] = {"checkedAt": "2026-11-18T00:00:00+00:00", "checkerVersion": "review",
                         "reasons": [{"check": "reviewer", "message": "Check the date", "by": "A"}]}
    put(store, written)
    run(store, bible, None, rules=RULES, recheck=True)
    s = store.load("JHN.3.16")
    assert s["status"] == "flagged" and [r["check"] for r in s["checks"]["reasons"]] == ["reviewer"]


def test_banned_file_and_limits_load():
    assert RULES.banned and set(RULES.limits) >= {"meaning", "applyIt"}


def test_english_word_after_greek_word_is_not_a_transliteration(written, bible):
    written["meaning"] = words(60, "The Greek word for loved is ēgapēsen, and the Greek word behind world is kosmon.")
    assert outcome(written, bible).reasons == []
    written["meaning"] = words(60, "The Greek word for love here is phileo.")
    assert outcome(written, bible).reasons == []  # only the word right after "Greek word" is checked
    written["meaning"] = words(60, "The Greek word phileo is not in this verse.")
    assert [r["check"] for r in outcome(written, bible).reasons] == ["original_language"]


# ---- Checker version 3 ----------------------------------------------------------------------------------

def test_length_tolerance_passes_up_to_20_percent_over(written, bible):
    written["meaning"] = words(216)  # max 180, exactly 20% over
    assert outcome(written, bible).reasons == []
    written["meaning"] = words(217)
    assert [(r["check"], r["section"]) for r in outcome(written, bible).reasons] == [("length", "meaning")]
    written["meaning"] = words(39)  # under the minimum still flags
    assert checks_named(outcome(written, bible)) == ["length"]


@pytest.mark.parametrize("n, band", [(180, None), (181, "up to 20% over"), (216, "up to 20% over"),
                                     (217, "20-50% over"), (270, "20-50% over"), (271, "more than 50% over")])
def test_length_band(n, band):
    assert length_band(n, 180) == band


def test_references_must_stay_in_the_allowed_set(written, bible):
    # The verse itself, its own chapter, listed cross references and a chapter holding one are allowed.
    written["theology"] = words(80, "See John 3:16, John 3, Romans 5:8, 1 John 4:9-10, Romans 5.")
    assert outcome(written, bible).reasons == []
    written["theology"] = words(80, "Compare Romans 8:28, John 1:12, John 3:2 and Genesis 1.")
    msgs = [r["message"] for r in outcome(written, bible).reasons if r["check"] == "reference_not_allowed"]
    assert len(msgs) == 4 and any("Romans 8:28" in m for m in msgs) and any("Genesis 1" in m for m in msgs)
    assert any("John 3:2”" in m for m in msgs)  # same chapter but not shown
    written["theology"] = words(80, "Romans 5:8-9 goes one verse past the listed reference.")
    assert checks_named(outcome(written, bible)) == ["reference_not_allowed"]


def test_surrounding_verses_are_not_cited_by_reference(written, bible):
    # Shown as context (4 either side), but described by what they say, never cited.
    written["theology"] = words(80, "See John 3:12 and John 3:20.")
    msgs = [r["message"] for r in outcome(written, bible).reasons if r["check"] == "reference_not_allowed"]
    assert len(msgs) == 2 and "describe surrounding verses" in msgs[0]


@pytest.mark.parametrize("text", [
    "Traditions differ on how worship is shaped.",
    "Christians from different traditions read this with warmth.",
    "Christians from many traditions pray it.",
    "Many traditions read these words at funerals.",
    "Not all traditions differ on how they sing it.",  # never excused by negation
])
def test_generic_traditions_lines_flag(written, bible, text):
    written["theology"] = words(80, text)
    assert checks_named(outcome(written, bible)) == ["generic_traditions"]


def test_specific_traditions_wording_passes(written, bible):
    written["theology"] = words(80, "Catholic and Protestant readers differ on what this says about baptism.")
    assert outcome(written, bible).reasons == []


def test_translit_form(written, bible):
    written["originalLanguage"] = {"language": "greek", "textBasis": "critical", "words": [
        {"surface": "ἕως", "lemma": "ἕως", "transliteration": "he'ōs", "strongs": "G2193", "gloss": "until",
         "morphology": "ADV"}]}
    written["didYouKnow"] = words(30, "The little word he’ōs marks a limit.")
    o = outcome(written, bible)
    # it still names the word (the DYK matcher ignores apostrophes), but the spelling check rejects the raw form
    assert checks_named(o) == ["translit_form"]
    assert any("heōs" in r["message"] for r in o.reasons if r["check"] == "translit_form")
    written["didYouKnow"] = words(30, "The little word heōs marks a limit.")
    assert outcome(written, bible).reasons == []
    written["originalLanguage"] = {"language": "hebrew", "textBasis": "leningrad", "words": [
        {"surface": "הוֹדוּ־", "lemma": "יָדָה", "transliteration": "ho.Du-", "strongs": "H3034", "gloss": "give thanks",
         "morphology": "HVhv2mp"},
        {"surface": "עַל", "lemma": "עַל", "transliteration": "'al", "strongs": "H5921", "gloss": "on",
         "morphology": "HR"}]}
    written["didYouKnow"] = words(30, "The command hodu- is plural, and 'al means on.")
    o = outcome(written, bible)
    assert checks_named(o) == ["translit_form"] and "“hodu”" in o.reasons[0]["message"]
    written["didYouKnow"] = words(30, "The command hodu is plural, and 'al means on.")  # Hebrew ayin mark stays
    assert outcome(written, bible).reasons == []


def test_rubric_gets_the_text_the_writer_was_given(written, bible):
    judge = FakeJudge(RubricResult(RUBRIC_OK, []))
    outcome(written, bible, judge)
    assert judge.supplied is None or "John 3:16" in judge.supplied


def test_negated_banned_phrases_are_ignored(written, bible):
    written["applyIt"] = words(40, "This is not for guaranteed success, and there is no right answer you must reach.")
    assert outcome(written, bible).reasons == []
    written["applyIt"] = words(40, "God will not fail; you must pray more.")  # the ; ends the negated clause
    assert checks_named(outcome(written, bible)) == ["banned_phrase"]


def test_pipeline_wording_flags_even_when_negated(written, bible):
    for text in ("The cross references show this.", "The cross-reference in Romans helps.",
                 "Not in the data given.", "This version says so.", "The words supplied here.",
                 "The book background says so."):
        written["didYouKnow"] = words(30, text)
        assert "pipeline_wording" in checks_named(outcome(written, bible)), text


def test_context_crosses_chapters_but_not_psalms():
    from pipeline.context import context_keys
    verses = [(43, 1, v) for v in range(1, 52)] + [(43, 2, v) for v in range(1, 26)]
    assert context_keys((43, 2, 2), verses) == [(43, 1, 49), (43, 1, 50), (43, 1, 51), (43, 2, 1), (43, 2, 2),
                                                (43, 2, 3), (43, 2, 4), (43, 2, 5), (43, 2, 6)]
    psalms = [(19, 23, v) for v in range(1, 7)] + [(19, 24, v) for v in range(1, 11)] + \
             [(19, 119, v) for v in range(1, 177)]
    assert context_keys((19, 23, 1), psalms) == [(19, 23, v) for v in range(1, 7)]  # whole psalm
    assert context_keys((19, 119, 1), psalms) == [(19, 119, v) for v in range(1, 6)]  # long psalm: window, own psalm


def test_verse_position_wording(written, bible):
    for text in ("As verse 5 says, God acts.", "Compare vv. 3-4 here.", "The next verse explains it.",
                 "The previous two verses show this.", "A few verses later Jesus speaks.",
                 "In the verse before, he waits."):
        written["meaning"] = words(60, text)
        assert "verse_position" in checks_named(outcome(written, bible)), text
    written["meaning"] = words(60, "This verse and its neighbours speak of love; when Jesus goes on to promise life.")
    assert outcome(written, bible).reasons == []


def test_dotted_transliterations(written, bible):
    written["didYouKnow"] = words(30, "The Hebrew be.re.Shit opens the book.")
    assert "dotted_translit" in checks_named(outcome(written, bible))
    written["didYouKnow"] = words(30, "Some words, e.g. love, recur; i.e. often.")
    assert "dotted_translit" not in checks_named(outcome(written, bible))


def test_clean_translit():
    from pipeline.context import clean_translit
    assert clean_translit("be.re.Shit") == "bereshit"
    assert clean_translit("ro.'I") == "ro'i"
    assert clean_translit("Yah.weh") == "Yahweh"
    assert clean_translit("agapaō") == "agapaō"
    assert clean_translit("ho.Du-") == "hodu" and clean_translit("'al-") == "'al"  # no trailing maqqef hyphen
    assert clean_translit("he'ōs", greek=True) == "heōs"
    assert clean_translit("Hi'erosoluma", greek=True) == "Hierosoluma"
    assert clean_translit("ye.shu.'Ot") == "yeshu'ot"  # Hebrew aleph/ayin marks stay
