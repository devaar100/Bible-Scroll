import pytest

from pipeline.prose_refs import check_reference, find_references

HERE = (43, 3, 16)


def refs(text):
    return [(r.book_id, r.start, r.end, r.problem) for r in find_references(text, HERE)]


@pytest.mark.parametrize("text, expected", [
    ("See John 3:16.", [(43, (3, 16), (3, 16), None)]),
    ("Jn 3:16-18", [(43, (3, 16), (3, 18), None)]),
    ("1 Cor. 13:4–7", [(46, (13, 4), (13, 7), None)]),
    ("First John 4:8", [(62, (4, 8), (4, 8), None)]),
    ("Psalm 23 says", [(19, (23, None), (23, None), None)]),
    ("Psalms 23-24", [(19, (23, None), (24, None), None)]),
    ("Gen 1:1-2:3", [(1, (1, 1), (2, 3), None)]),
    ("Romans 5:8; 8:32, 35", [(45, (5, 8), (5, 8), None), (45, (8, 32), (8, 32), None), (45, (8, 35), (8, 35), None)]),
    ("Rom 5:8, 10-12", [(45, (5, 8), (5, 8), None), (45, (5, 10), (5, 12), None)]),
    ("In v. 17 and verses 15–17", [(43, (3, 17), (3, 17), None), (43, (3, 15), (3, 17), None)]),
])
def test_finds_references(text, expected):
    assert refs(text) == expected


def test_one_john_is_not_john():
    assert refs("1 John 4:8")[0][0] == 62


def test_unknown_book_is_reported():
    (found,) = find_references("as Hezekiah 3:5 says", HERE)
    assert found.problem and "Hezekiah" in found.problem


def test_ordinary_prose_has_no_references():
    assert refs("At the time, people met at the well. Jesus loved them; he was 30.") == []


def test_existence(bible):
    def problem(text):
        (found,) = find_references(text, HERE)
        return check_reference(found, bible.verses, bible.chapters)

    assert problem("John 3:16") is None
    assert problem("Psalm 23") is None
    assert "does not exist" in problem("John 3:99")
    assert "no chapter" in problem("Psalm 151")
    assert "does not exist" in problem("Romans 8:28-40")
    assert problem("Romans 8:39-28") == "range runs backwards"
