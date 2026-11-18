from pipeline.kjv import Kjv
from pipeline.versification import WordMapper


def kjv(*verses, titled=()):
    return Kjv(frozenset(verses), frozenset(titled), {})


def kinds(mapper):
    return {(r.native, r.kjv, r.kind) for r in mapper.rows({1})}


def test_identical_renumbered_title():
    m = WordMapper("T", kjv((1, 4, 1), (1, 4, 2), (1, 5, 1), titled=[(1, 5)]))
    m.add((1, 4, 1), (1, 4, 1), (1, 4, 1))      # same numbers
    m.add((1, 3, 19), (1, 4, 2), (1, 4, 2))     # Hebrew 3:19 = KJV 4:2
    m.add((1, 5, 1), (1, 5, 0), (1, 5, 0))      # Hebrew v1 = KJV title
    m.add((1, 5, 2), (1, 5, 1), (1, 5, 1))
    got = kinds(m)
    assert ((1, 4, 1), (1, 4, 1), "identical") in got
    assert ((1, 3, 19), (1, 4, 2), "renumbered") in got
    assert ((1, 5, 1), (1, 5, 0), "title") in got
    assert ((1, 5, 2), (1, 5, 1), "renumbered") in got


def test_split_merged_and_gaps():
    m = WordMapper("T", kjv((1, 1, 1), (1, 1, 2), (1, 1, 3), (1, 1, 4)))
    m.add((1, 1, 1), (1, 1, 1), (1, 1, 1))
    m.add((1, 1, 1), (1, 1, 2), (1, 1, 2))      # native 1:1 spills into KJV 1:2
    m.add((1, 1, 3), (1, 1, 3), (1, 1, 3))
    m.add((1, 1, 9), (1, 1, 3), (1, 1, 3))      # two native verses into KJV 1:3
    assert m.add((1, 2, 1), (1, 2, 1), (1, 2, 1)) is None  # no KJV home
    got = kinds(m)
    assert ((1, 1, 1), (1, 1, 1), "split") in got and ((1, 1, 1), (1, 1, 2), "split") in got
    assert ((1, 1, 3), (1, 1, 3), "merged") in got and ((1, 1, 9), (1, 1, 3), "merged") in got
    assert ((1, 2, 1), None, "source_only") in got
    assert ((1, 1, 4), (1, 1, 4), "kjv_only") in got
    assert m.unmapped_words == 1


def test_english_to_kjv_picks_majority():
    m = WordMapper("T", kjv((3, 1, 14), (3, 1, 15)))
    for _ in range(5):
        m.add((3, 1, 15), (3, 1, 15), (3, 1, 14))
    m.add((3, 1, 15), (3, 1, 15), (3, 1, 15))
    assert m.english_to_kjv()[(3, 1, 15)] == (3, 1, 14)
