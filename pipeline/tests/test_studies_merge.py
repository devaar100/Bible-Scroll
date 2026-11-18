import copy

from pipeline.studies import Stats, merge


def fresh_from(study, **changes):
    f = copy.deepcopy(study)
    for k in ("meaning", "historicalContext", "lifeInBibleTimes", "theology", "didYouKnow", "applyIt"):
        f[k] = ""
    f["status"], f["meta"], f["contentVersion"] = "generated", {"model": None, "generatedAt": "2026-12-01T00:00:00+00:00"}, "fffffffffff0"
    f.pop("checks", None)
    f.update(changes)
    return f


def test_rebuild_keeps_reviewed_work(written):
    written.update(status="reviewed", contentVersion="0123456789ab.2")
    written["meta"].update(reviewedBy="Sam", reviewedAt="2026-11-20T00:00:00+00:00")
    stats = Stats()
    merged = merge(written, fresh_from(written), stats)
    assert merged["meaning"] == written["meaning"] and merged["status"] == "reviewed"
    assert merged["meta"]["reviewedBy"] == "Sam" and merged["contentVersion"] == "0123456789ab.2"
    assert stats.kept_existing_work == 1 and stats.reset_for_recheck == 0


def test_changed_data_sends_study_back_for_checks(written):
    written.update(status="reviewed")
    written["meta"].update(reviewedBy="Sam", reviewedAt="2026-11-20T00:00:00+00:00")
    fresh = fresh_from(written, crossReferences=[{"ref": "ROM.8.28", "source": "OpenBible"}])
    stats = Stats()
    merged = merge(written, fresh, stats)
    assert merged["status"] == "generated" and merged["crossReferences"] == fresh["crossReferences"]
    assert merged["meaning"] == written["meaning"] and merged["meta"]["reviewedBy"] == "Sam"
    assert merged["contentVersion"] == "fffffffffff0" and stats.reset_for_recheck == 1


def test_new_verse_is_the_fresh_skeleton(written):
    fresh = fresh_from(written)
    assert merge(None, fresh, Stats()) is fresh
