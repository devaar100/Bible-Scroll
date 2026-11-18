import pytest

from pipeline.review_queue import load_priority, queue_order


def s(study_id, status):
    return {"id": study_id, "status": status}


@pytest.fixture(autouse=True)
def no_discover(tmp_path, monkeypatch):
    """Keep the real discover.txt out of these tests (load_priority puts Discover verses first)."""
    empty = tmp_path / "discover.txt"
    empty.write_text("")
    from pipeline import publish
    monkeypatch.setattr(publish, "DISCOVER_FILE", empty)


def test_order_flagged_then_priority_then_bible_order(tmp_path):
    priority = tmp_path / "priority.txt"
    priority.write_text("# Discover\nROM.8.28\n\nJHN.3   # chapter\nPSA\n")
    studies = [
        s("GEN.1.1", "auto_checked"), s("PSA.23.1", "auto_checked"), s("JHN.3.16", "auto_checked"),
        s("ROM.8.28", "auto_checked"), s("REV.22.21", "flagged"), s("GEN.1.2", "flagged"),
        s("JHN.1.1", "auto_checked"), s("JHN.3.17", "reviewed"), s("ACT.1.1", "generated"),
    ]
    order = [x["id"] for x in queue_order(studies, load_priority(priority))]
    assert order == [
        "GEN.1.2", "REV.22.21",                    # flagged first, Bible order between them
        "ROM.8.28", "JHN.3.16", "PSA.23.1",        # priority lines in file order
        "GEN.1.1", "JHN.1.1",                      # everything else in Bible order
    ]                                              # reviewed and unchecked ("generated") aren't in the queue


def test_priority_applies_within_flagged(tmp_path):
    priority = tmp_path / "p.txt"
    priority.write_text("PSA\n")
    order = queue_order([s("GEN.1.1", "flagged"), s("PSA.1.1", "flagged")], load_priority(priority))
    assert [x["id"] for x in order] == ["PSA.1.1", "GEN.1.1"]


@pytest.mark.parametrize("line", ["Psalms", "JHN.0", "JHN.3.16.1", "XYZ.1"])
def test_bad_priority_lines_raise(tmp_path, line):
    path = tmp_path / "p.txt"
    path.write_text(line + "\n")
    with pytest.raises(ValueError):
        load_priority(path)


def test_shipped_priority_file_parses():
    assert load_priority()


def test_discover_verses_come_first(tmp_path):
    priority, discover = tmp_path / "priority.txt", tmp_path / "discover.txt"
    priority.write_text("JHN\n")
    discover.write_text("PSA.23.1\n")
    studies = [s("JHN.3.16", "auto_checked"), s("PSA.23.1", "auto_checked"), s("GEN.1.1", "flagged")]
    order = [x["id"] for x in queue_order(studies, load_priority(priority, discover))]
    assert order == ["GEN.1.1", "PSA.23.1", "JHN.3.16"]
