from conftest import put, skeleton
from pipeline.progress import count, render


def test_counts_by_book_and_stage(store, written):
    put(store, skeleton("GEN.1.1"))
    put(store, dict(written, id="JHN.3.16", status="reviewed"))
    put(store, dict(written, id="JHN.3.17", status="flagged",
                    checks={"checkedAt": "2026-11-18T00:00:00+00:00", "checkerVersion": "1",
                            "reasons": [{"check": "length", "message": "x"}]}))
    put(store, dict(written, id="JHN.3.18"))  # written but not yet checked
    by_book = count(store)
    assert dict(by_book[1]) == {"not_written": 1}
    assert dict(by_book[43]) == {"reviewed": 1, "flagged": 1, "generated": 1}
    table = render(by_book)
    assert "| Genesis | 1 | 0 | 0 | 0 | 0 | 0 | 1 | 0.0 |" in table
    assert "| John | 0 | 1 | 0 | 1 | 0 | 1 | 3 | 33.3 |" in table
    assert "| **Total** | 1 | 1 | 0 | 1 | 0 | 1 | 4 | 25.0 |" in table
