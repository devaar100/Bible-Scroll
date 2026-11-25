"""translate requests / drift-requests / import: devotionals translated by Claude Code subagents. No API calls."""
import json
from types import SimpleNamespace

import pytest

from pipeline.devotionals.store import DevotionalStore
from pipeline.translate import cli
from pipeline.translate.engine import prompt_hash

SCRIPTURE = "PSA 119:105 Lámpara es á mis pies tu palabra, Y lumbrera á mi camino."
GOOD = {"title": "Luz", "theme": "La palabra de Dios guía", "seriesTitle": "Lámpara", "context": "c es",
        "reflect": "r es", "applyIt": "a es", "prayer": "p es"}


def devotional(did="devotional-11-18"):
    return {"id": did, "contentVersion": "v1", "date": did[-5:], "dayOfYear": 322, "title": "Light",
            "theme": "God's word guides", "series": {"title": "Lamp", "season": "Ordinary", "day": 1, "length": 5},
            "coverStyle": "dawn", "verses": [], "context": "c", "reflect": "r", "applyIt": "a", "prayer": "p",
            "status": "auto_checked", "meta": {"model": "claude-haiku-5-5"}}


@pytest.fixture
def store(tmp_path):
    s = DevotionalStore(tmp_path / "dev")
    for did in ("devotional-11-18", "devotional-11-19"):
        s.save(devotional(did))
    return s


def scripture(_d):
    return SCRIPTURE


def write(tmp_path, name, data):
    path = tmp_path / name
    path.write_text(data if isinstance(data, str) else json.dumps(data, ensure_ascii=False))
    return str(path)


def run_import(store, tmp_path, translations, drift, force=False):
    args = SimpleNamespace(lang="es", translations=[write(tmp_path, "t.json", translations)],
                           drift=[write(tmp_path, "d.json", drift)], model="claude-sonnet-5-5",
                           drift_model="claude-sonnet-5-5", force=force)
    return cli.import_devotionals(args, scripture, store)


def test_good_translation_is_auto_checked_with_honest_meta(store, tmp_path):
    lines = run_import(store, tmp_path, {"devotional-11-18": GOOD},
                       {"devotional-11-18": {"issues": [{"key": "reflect", "category": "tone", "problem": "warmer"}]}})
    loc = store.load("devotional-11-18")["i18n"]["es"]
    assert loc["status"] == "auto_checked" and loc["title"] == "Luz" and loc["sourceVersion"] == "v1"
    assert loc["meta"]["model"] == "claude-sonnet-5-5" and loc["meta"]["driftModel"] == "claude-sonnet-5-5"
    assert loc["meta"]["promptHash"] == prompt_hash("translate", "es") and not loc["meta"]["retried"]
    assert loc["checks"]["minor"][0]["category"] == "tone" and loc["checks"]["reasons"] == []
    assert "1 auto_checked" in lines[-1]


def test_blocking_drift_issue_flags(store, tmp_path):
    run_import(store, tmp_path, {"devotional-11-18": GOOD},
               {"devotional-11-18": {"issues": [{"key": "theme", "category": "meaning_changed", "problem": "x"}]}})
    loc = store.load("devotional-11-18")["i18n"]["es"]
    assert loc["status"] == "flagged" and loc["checks"]["reasons"][0]["category"] == "meaning_changed"


def test_partial_translation_fails_free_checks_without_needing_drift(store, tmp_path):
    partial = {k: v for k, v in GOOD.items() if k != "prayer"}
    run_import(store, tmp_path, {"devotional-11-18": {**partial, "prayer": ""}}, {})
    loc = store.load("devotional-11-18")["i18n"]["es"]
    assert loc["status"] == "flagged" and loc["checks"]["reasons"][0]["check"] == "keys"
    assert "driftModel" not in loc["meta"]


def test_missing_required_key_stores_nothing(store, tmp_path):
    lines = run_import(store, tmp_path, {"devotional-11-18": {"title": "Luz"}}, {})
    assert "es" not in store.load("devotional-11-18").get("i18n", {}) and "not stored" in lines[0]


def test_no_drift_result_or_bad_drift_shape_leaves_it_unchanged(store, tmp_path):
    lines = run_import(store, tmp_path, {"devotional-11-18": GOOD, "devotional-11-19": GOOD},
                       {"devotional-11-19": {"issues": [{"key": "theme", "category": "bogus", "problem": "x"}]}})
    for did in ("devotional-11-18", "devotional-11-19"):
        assert "es" not in store.load(did).get("i18n", {})
    assert "no drift result" in lines[0] and "DRIFT_SCHEMA" in lines[1]


def test_malformed_json_stops_before_storing_anything(store, tmp_path):
    with pytest.raises(SystemExit):
        run_import(store, tmp_path, "{not json", {})
    with pytest.raises(SystemExit):
        run_import(store, tmp_path, {"devotional-11-18": GOOD}, "[1, 2]")
    assert "es" not in store.load("devotional-11-18").get("i18n", {})


def test_non_string_entry_is_skipped(store, tmp_path):
    lines = run_import(store, tmp_path, {"devotional-11-18": ["Luz"]}, {})
    assert "left unchanged" in lines[0]


def test_reviewed_and_checked_are_not_overwritten_without_force(store, tmp_path):
    run_import(store, tmp_path, {"devotional-11-18": GOOD}, {"devotional-11-18": {"issues": []}})
    d = store.load("devotional-11-18")
    d["i18n"]["es"]["status"] = "reviewed"
    store.save(d)
    lines = run_import(store, tmp_path, {"devotional-11-18": {**GOOD, "title": "Otra"}}, {"devotional-11-18": {"issues": []}})
    assert store.load("devotional-11-18")["i18n"]["es"]["title"] == "Luz" and "not overwritten" in lines[0]
    run_import(store, tmp_path, {"devotional-11-18": {**GOOD, "title": "Otra"}}, {"devotional-11-18": {"issues": []}}, force=True)
    assert store.load("devotional-11-18")["i18n"]["es"]["title"] == "Otra"


def test_reimporting_a_flagged_one_counts_as_the_retry(store, tmp_path):
    run_import(store, tmp_path, {"devotional-11-18": GOOD},
               {"devotional-11-18": {"issues": [{"key": "theme", "category": "claim_added", "problem": "x"}]}})
    run_import(store, tmp_path, {"devotional-11-18": GOOD}, {"devotional-11-18": {"issues": []}})
    loc = store.load("devotional-11-18")["i18n"]["es"]
    assert loc["status"] == "auto_checked" and loc["meta"]["retried"]


def test_made_up_scripture_quote_is_blocked(store, tmp_path):
    bad = {**GOOD, "reflect": "El salmo dice: «tu palabra es una luz que nunca se apaga en mí»."}
    run_import(store, tmp_path, {"devotional-11-18": bad}, {})
    assert store.load("devotional-11-18")["i18n"]["es"]["checks"]["reasons"][0]["check"] == "scripture"


def test_requests_get_one_file_per_batch_and_retry_carries_feedback(store, tmp_path):
    out = tmp_path / "req"
    args = SimpleNamespace(lang="es", out=str(out), batch_size=1, start=1, retry_flagged=False, ids=[])
    cli.write_requests(args, scripture, store)
    assert sorted(p.name for p in out.iterdir()) == ["batch01.md", "batch02.md"]
    text = (out / "batch01.md").read_text()
    assert "=== devotional-11-18 ===" in text and "Keys to return: title, theme, seriesTitle" in text
    assert "Jehová" in text and SCRIPTURE in text
    run_import(store, tmp_path, {"devotional-11-18": GOOD},
               {"devotional-11-18": {"issues": [{"key": "theme", "category": "claim_added", "problem": "adds hope"}]}})
    retry = tmp_path / "retry"
    cli.write_requests(SimpleNamespace(lang="es", out=str(retry), batch_size=10, start=1, retry_flagged=True, ids=[]),
                       scripture, store)
    text = (retry / "batch01.md").read_text()
    assert "devotional-11-19" not in text and "- theme: adds hope" in text


def test_drift_requests_skip_free_check_failures(store, tmp_path):
    d = tmp_path / "run"
    d.mkdir()
    write(d, "batch01.json", {"devotional-11-18": GOOD, "devotional-11-19": {"title": "solo"}})
    cli.write_drift_requests(SimpleNamespace(lang="es", dir=str(d), batches=[]), scripture, store)
    text = (d / "drift_batch01.md").read_text()
    assert "=== devotional-11-18 ===" in text and "devotional-11-19" not in text and "Translation: Luz" in text
