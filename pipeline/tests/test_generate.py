"""Study prose generator: request shape, writing, skipping, failures. Never calls the API (fake client)."""
import json
from types import SimpleNamespace

import pytest

from conftest import put, skeleton
from pipeline import generate as g
from pipeline.store import StudyStore
from pipeline.usage import Usage

PROSE = {"meaning": "m", "historicalContext": "h", "lifeInBibleTimes": "l", "theology": "t", "didYouKnow": "d",
         "applyIt": "a"}


def message(data=PROSE, stop_reason="end_turn", model="claude-haiku-5-5"):
    usage = SimpleNamespace(input_tokens=1000, output_tokens=200, cache_creation_input_tokens=0,
                            cache_read_input_tokens=0)
    text = data if isinstance(data, str) else json.dumps(data)
    return SimpleNamespace(content=[SimpleNamespace(type="text", text=text)], stop_reason=stop_reason,
                           model=model, usage=usage, _request_id="req_test")


class FakeStream:
    def __init__(self, msg):
        self.msg = msg

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False

    def get_final_message(self):
        return self.msg


class FakeMessages:
    def __init__(self, msg):
        self.msg, self.calls = msg, []

    def stream(self, **kwargs):
        self.calls.append(kwargs)
        return FakeStream(self.msg)


def writer(msg=None, model="claude-haiku-5-5"):
    messages = FakeMessages(msg or message(model=model))
    return g.StudyWriter(model=model, client=SimpleNamespace(beta=SimpleNamespace(messages=messages))), messages


@pytest.fixture
def source(store):
    put(store, skeleton("JHN.3.16"))
    return store


def run(source, out_dir, w, bible_db, **kw):
    return g.generate(["JHN.3.16"], w, out_dir, source_dir=source.directory, bible_db=bible_db, workers=1,
                      log=lambda _: None, **kw)


def test_writes_prose_to_out_folder_and_leaves_source_alone(source, tmp_path, bible_db):
    w, messages = writer()
    out = tmp_path / "test_haiku"
    result = run(source, out, w, bible_db)
    assert result.written == 1
    study = StudyStore(out).load("JHN.3.16")
    assert {k: study[k] for k in PROSE} == PROSE
    assert study["status"] == "generated" and study["meta"]["model"] == "claude-haiku-5-5"
    assert study["originalLanguage"] == source.load("JHN.3.16")["originalLanguage"]
    assert source.load("JHN.3.16")["meaning"] == ""
    summary = json.loads((out / "generate_summary.json").read_text())
    assert summary["usage"]["inputTokens"] == 1000 and summary["usage"]["costUsd"] == pytest.approx(0.0002)


def test_request_uses_model_effort_schema_and_verse_data(source, tmp_path, bible_db):
    w, messages = writer()
    run(source, tmp_path / "o", w, bible_db)
    call = messages.calls[0]
    assert call["model"] == "claude-haiku-5-5"
    assert call["output_config"]["effort"] == "medium" and call["max_tokens"] == 16000
    assert call["output_config"]["format"]["schema"] == g.OUTPUT_SCHEMA
    assert "fallbacks" not in call  # Haiku 5.5 has no server-side fallback
    request = call["messages"][0]["content"]
    assert "John 3:16" in request and "ēgapēsen" in request and "1 John 4:9-10" in request
    assert "John 3:16 (this verse): For God so loved" in request  # context lines carry full references
    assert "Author: Anonymous" in request and "Note on the text:\n(none)" in request
    assert "{{" not in request


def test_psalm_request_has_whole_psalm_and_labelled_heading(tmp_path):
    import sqlite3
    db_path = tmp_path / "bible.db"
    db = sqlite3.connect(db_path)
    db.execute("CREATE TABLE verses (translation_id TEXT, book_id INT, chapter INT, verse INT, text TEXT)")
    db.execute("CREATE TABLE chapters (translation_id TEXT, book_id INT, chapter INT, superscription TEXT)")
    db.executemany("INSERT INTO verses VALUES (?,?,?,?,?)",
                   [(t, 19, 3, v, f"{t} verse {v}") for t in ("KJV", "WEB") for v in range(1, 9)])
    db.executemany("INSERT INTO chapters VALUES (?,?,?,?)",
                   [("KJV", 19, 3, "A Psalm of David."), ("WEB", 19, 3, "A Psalm by David.")])
    db.commit()
    db.close()
    study = skeleton("PSA.3.8")
    values = g.request_values(study, db_path)
    assert values["context_heading"].startswith("The whole of Psalm 3")
    lines = values["context_text"].splitlines()
    assert lines[0] == "Heading (superscription) of Psalm 3: A Psalm by David."
    assert len(lines) == 9 and lines[1].startswith("Psalm 3:1: ") and lines[8].startswith("Psalm 3:8 (this verse)")


def test_fallback_enabled_for_sonnet(source, tmp_path, bible_db):
    w, messages = writer(model="claude-sonnet-5-5")
    run(source, tmp_path / "o", w, bible_db)
    assert messages.calls[0]["fallbacks"] == "default"


def test_skips_written_unless_force_and_never_touches_reviewed(source, tmp_path, bible_db):
    out = tmp_path / "o"
    w, messages = writer()
    run(source, out, w, bible_db)
    assert run(source, out, w, bible_db).skipped == 1
    assert run(source, out, w, bible_db, force=True).written == 1
    target = StudyStore(out)
    study = target.load("JHN.3.16")
    study["status"] = "reviewed"
    target.save(study)
    assert run(source, out, w, bible_db, force=True).skipped == 1


@pytest.mark.parametrize("msg, why", [
    (message(stop_reason="refusal"), "refused"),
    (message(stop_reason="max_tokens"), "max_tokens"),
    (message(data="not json"), "not JSON"),
    (message(data={**PROSE, "theology": " "}), "empty sections"),
])
def test_failures_are_reported_not_saved(source, tmp_path, bible_db, msg, why):
    w, _ = writer(msg)
    result = run(source, tmp_path / "o", w, bible_db)
    assert result.written == 0 and why in result.failed[0]
    assert not (tmp_path / "o" / "JHN.3.16.json").exists()


def test_select_ids(source):
    put(source, skeleton("JHN.3.17"))
    assert g.select_ids(source, ["JHN.3"]) == ["JHN.3.16", "JHN.3.17"]
    assert g.select_ids(source, ["JHN.3.17"]) == ["JHN.3.17"]
    with pytest.raises(SystemExit):
        g.select_ids(source, ["PHP"])


def test_usage_cost():
    u = Usage(input_tokens=1_000_000, output_tokens=1_000_000)
    assert u.cost("claude-sonnet-5-5") == pytest.approx(12.0)
    assert u.cost("unknown-model") is None


def test_non_retryable_api_error_stops_run_and_still_writes_summary(source, tmp_path, bible_db):
    import anthropic
    import httpx

    request = httpx.Request("POST", "https://api.anthropic.com/v1/messages")
    error = anthropic.BadRequestError(
        "low credit", response=httpx.Response(400, request=request),
        body={"type": "error", "error": {"type": "invalid_request_error", "message": "credit balance is too low"}})

    class Failing(FakeMessages):
        def stream(self, **kwargs):
            raise error

    w = g.StudyWriter(model="claude-haiku-5-5",
                      client=SimpleNamespace(beta=SimpleNamespace(messages=Failing(None))))
    out = tmp_path / "o"
    result = run(source, out, w, bible_db)
    assert result.written == 0 and "credit balance is too low" in result.stopped
    summary = json.loads((out / "generate_summary.json").read_text())
    assert summary["stopped"] == result.stopped and summary["usage"]["requests"] == 0
