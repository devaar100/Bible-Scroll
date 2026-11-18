"""The splitting algorithm shared with the app (core/plans). Vectors and verses.bin must match this code."""
import json

import pytest

from pipeline.books import BY_USFM
from pipeline.paths import SOURCE_DB
from pipeline.plans import splitting
from pipeline.plans.bible import VerseIndex
from pipeline.plans.build import APP_ASSETS
from pipeline.plans.catalogue import CATALOGUE
from pipeline.plans.split import boundary_tiers

INDEX = VerseIndex.load()
needs_source = pytest.mark.skipif(not SOURCE_DB.exists(), reason="needs data/source.db (run download + build)")


def test_cuts_prefer_chapter_then_paragraph_then_sentence():
    words = [10] * 10
    #             0  1  2  3  4  5  6  7  8  9
    tiers = [0, 0, 1, 0, 2, 0, 0, 0, 0, 3]
    assert splitting.split_cuts(words, tiers, 2, 0.35) == [4]      # paragraph end at 50 words: exactly half
    tiers[5] = 3
    assert splitting.split_cuts(words, tiers, 2, 0.35) == [5]      # a chapter end within reach wins
    assert splitting.split_cuts(words, [0, 0, 1] + [0] * 7, 2, 0.35) == [4]  # sentence too far: nearest verse


def test_ties_go_to_the_earliest_index():
    assert splitting.split_cuts([10, 10, 10, 10], [0, 0, 0, 0], 2, 0.0) == [1]
    assert splitting.split_cuts([5, 10, 10, 5], [0, 0, 0, 0], 2, 0.0) == [1]


def test_every_day_gets_a_verse_and_bad_requests_fail():
    assert splitting.split_cuts([100, 1, 1], [0, 0, 0], 3, 0.35) == [0, 1]
    with pytest.raises(ValueError):
        splitting.split_cuts([1, 2], [0, 0], 3, 0.35)
    with pytest.raises(ValueError):
        splitting.split_cuts([1], [0], 0, 0.35)


def test_days_for_minutes_rounds_up_and_stays_in_range():
    assert splitting.days_for_minutes(4000, 10, 500) == 2
    assert splitting.days_for_minutes(4001, 10, 500) == 3
    assert splitting.days_for_minutes(10, 20, 500) == 1
    assert splitting.days_for_minutes(10_000, 1, 3) == 3


def test_plan_cuts_takes_a_chapter_a_day_when_it_fits():
    words = [10, 10, 10, 10, 10, 10]
    tiers = [0, 3, 0, 3, 0, 3]
    assert splitting.plan_cuts(words, tiers, 3) == [1, 3]


@pytest.mark.parametrize("plan", [p for p in CATALOGUE if len(p.tracks) == 1], ids=lambda p: p.id)
def test_shared_plan_cuts_reproduce_the_bundled_single_track_plans(plan):
    """The app's algorithm (plan_cuts, chapter ends only) gives the same days as the pipeline's structural plans."""
    bundled = json.loads((APP_ASSETS / f"{plan.id}.json").read_text())
    sequence = INDEX.books(list(plan.tracks[0]))
    words = [v.words for v in sequence]
    cuts = splitting.plan_cuts(words, boundary_tiers(sequence, INDEX.chapter_ends), plan.days)
    bounds = [-1, *cuts, len(sequence) - 1]
    ours = [[sequence[bounds[i] + 1].key, sequence[bounds[i + 1]].key] for i in range(plan.days)]
    theirs = []
    for day in bundled["days"]:
        first, last = day["passages"][0], day["passages"][-1]
        theirs.append([(BY_USFM[first["book"]].id, first["startChapter"], first["startVerse"]),
                       (BY_USFM[last["book"]].id, last["endChapter"], last["endVerse"])])
    assert ours == theirs


@needs_source
def test_committed_vectors_and_verse_data_match_this_code():
    from pipeline.plans import verse_data

    paragraphs, sentences = verse_data.paragraph_ends(), verse_data.sentence_ends()
    sequence, words, tiers = verse_data.whole_bible(INDEX, paragraphs, sentences)
    committed = verse_data.VERSES_BIN.read_bytes()
    assert committed[:5] == verse_data.MAGIC + bytes([verse_data.VERSION])
    per_verse = committed[-2 * len(sequence):]
    assert list(per_verse[0::2]) == words and list(per_verse[1::2]) == tiers, \
        "verses.bin is stale: run `python -m pipeline plans vectors`"
    expected = verse_data.build_vectors(INDEX, paragraphs, sentences, committed)
    assert json.loads(verse_data.VECTORS.read_text()) == json.loads(json.dumps(expected)), \
        "splitting_vectors.json is stale: run `python -m pipeline plans vectors`"


@needs_source
def test_paragraphs_are_old_testament_only_and_plentiful():
    from pipeline.plans import verse_data

    paragraphs = verse_data.paragraph_ends()
    assert len(paragraphs) > 2500 and all(b <= 39 for b, _, _ in paragraphs)
    assert (1, 1, 5) in paragraphs  # Genesis 1:5 ends with petuchah
