from pipeline.books import BOOKS, BY_OSIS, BY_STEP, BY_TSK, BY_USFM


def test_66_books_with_unique_codes():
    assert [b.id for b in BOOKS] == list(range(1, 67))
    assert len(BY_STEP) == len(BY_OSIS) == len(BY_USFM) == 66
    assert len(BY_TSK) == sum(len(b.tsk) for b in BOOKS)
