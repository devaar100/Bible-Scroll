import pytest

from pipeline.plans.bible import Verse
from pipeline.plans.split import split_days, to_passages


def seq(chapters):
    """chapters: list of lists of word counts -> verses of book 1."""
    out, ends = [], set()
    for c, words in enumerate(chapters, start=1):
        for v, w in enumerate(words, start=1):
            out.append(Verse((1, c, v), w))
        ends.add((1, c, len(words)))
    return out, ends


def test_covers_every_verse_once_in_order_and_no_empty_days():
    verses, ends = seq([[10] * 30, [10] * 5, [10] * 40, [10] * 25])
    days = split_days(verses, 7, ends)
    assert len(days) == 7 and all(days)
    assert [v for d in days for v in d] == verses


def test_prefers_chapter_ends_when_close():
    verses, ends = seq([[10] * 20, [10] * 20, [10] * 20])
    days = split_days(verses, 3, ends)
    assert [d[-1].key for d in days] == [(1, 1, 20), (1, 2, 20), (1, 3, 20)]


def test_splits_a_long_chapter_when_it_must():
    verses, ends = seq([[10] * 100, [10] * 5])
    days = split_days(verses, 3, ends, tolerance=0.0)
    sizes = [sum(v.words for v in d) for d in days]
    assert max(sizes) - min(sizes) <= 60  # close to equal, not "chapter 1 then two scraps"


def test_too_many_days_raises():
    verses, ends = seq([[10] * 3])
    with pytest.raises(ValueError):
        split_days(verses, 4, ends)


def test_passages_split_at_book_changes_and_jumps():
    position = {}
    keys = [(19, 3, 1), (19, 3, 2), (19, 51, 1), (19, 51, 2), (9, 16, 1)]
    canonical = sorted(set(keys) | {(19, 4, 1)})
    position = {k: i for i, k in enumerate(canonical)}
    passages = to_passages([Verse(k, 1) for k in keys], position)
    assert [(p["book"], p["startChapter"], p["endChapter"]) for p in passages] == [("PSA", 3, 3), ("PSA", 51, 51), ("1SA", 16, 16)]
