import copy

import pytest

from pipeline.plans.bible import VerseIndex
from pipeline.plans.validate import check_plan, verse_coverage

INDEX = VerseIndex.load()

PLAN = {
    "id": "test-plan", "schemaVersion": 1, "contentVersion": "0123456789ab", "title": "Test", "shortDescription": "s",
    "description": "d", "category": "topics-and-seasons", "difficulty": "easy", "estimatedMinutesPerDay": 3,
    "days": [{"dayNumber": 1, "passages": [{"book": "JHN", "startChapter": 3, "startVerse": 1, "endChapter": 3, "endVerse": 21}]},
             {"dayNumber": 2, "passages": [{"book": "PSA", "startChapter": 23, "startVerse": 1, "endChapter": 23, "endVerse": 6}]}],
    "status": "generated", "origin": "ai_drafted", "credits": "c",
    "meta": {"model": "claude-opus-5-5", "generatedAt": "2026-11-18T00:00:00+00:00"},
}


def checks(plan):
    return [(r["check"], r.get("day")) for r in check_plan(plan, INDEX)]


def test_valid_plan():
    assert checks(PLAN) == []


@pytest.mark.parametrize("change, expected", [
    (lambda p: p["days"][0]["passages"][0].update(endVerse=99), ("passage", 1)),       # John 3:99
    (lambda p: p["days"][1]["passages"][0].update(book="XYZ"), ("passage", 2)),
    (lambda p: p["days"][0]["passages"][0].update(startVerse=21, endVerse=1), ("passage", 1)),  # backwards
    (lambda p: p["days"][1].update(passages=[]), ("empty_day", 2)),
    (lambda p: p["days"][1].update(dayNumber=3), ("day_numbers", None)),
    (lambda p: p.update(estimatedMinutesPerDay=1), ("day_length", 1)),                  # John 3:1-21 is ~3 min
])
def test_problems(change, expected):
    plan = copy.deepcopy(PLAN)
    change(plan)
    assert expected in checks(plan)


def test_coverage_names_missing_and_doubled_chapters():
    plan = copy.deepcopy(PLAN)
    expected = {k for k in INDEX.order if k[0] == 43 and k[1] == 3} | {k for k in INDEX.order if k[0] == 19 and k[1] in (23, 24)}
    plan["days"][1]["passages"].append(dict(plan["days"][1]["passages"][0]))
    messages = [r["message"] for r in verse_coverage(plan, INDEX, expected)]
    assert "Psalms 24: verses missing" in messages and "Psalms 23: verses read twice" in messages
