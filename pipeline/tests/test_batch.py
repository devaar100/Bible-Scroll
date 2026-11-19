"""Batch mode: request shape, caching, state, collect, retry, failed status, backup. Never calls the API."""
import json
import zipfile
from types import SimpleNamespace

import pytest

from conftest import put, skeleton
from pipeline import batch as bt
from pipeline.rubric import RubricResult
from pipeline.store import StudyStore
from pipeline.studies import validator
from pipeline.usage import Usage

PROSE_KEYS = ("meaning", "historicalContext", "lifeInBibleTimes", "theology", "didYouKnow", "applyIt")
RUBRIC_OK = {"model": "claude-haiku-5-5", **{k: {"score": 0, "reason": "fine"} for k in ("factualRisk", "oneSidedTheology", "tone")}}


def usage(write=0, read=0):
    return SimpleNamespace(input_tokens=300, output_tokens=900, cache_creation_input_tokens=write,
                           cache_read_input_tokens=read,
                           cache_creation=SimpleNamespace(ephemeral_1h_input_tokens=write, ephemeral_5m_input_tokens=0))


def ok(prose, write=0, read=0):
    msg = SimpleNamespace(content=[SimpleNamespace(type="text", text=json.dumps(prose))], stop_reason="end_turn",
                          model="claude-haiku-5-5", usage=usage(write, read))
    return SimpleNamespace(type="succeeded", message=msg)


def cut_off():
    msg = SimpleNamespace(content=[], stop_reason="max_tokens", model="claude-haiku-5-5", usage=usage())
    return SimpleNamespace(type="succeeded", message=msg)


class FakeBatches:
    def __init__(self):
        self.created, self.results_by_batch = [], {}

    def create(self, requests):
        self.created.append(requests)
        return SimpleNamespace(id=f"msgbatch_{len(self.created)}", processing_status="in_progress")

    def retrieve(self, batch_id):
        counts = SimpleNamespace(processing=0, succeeded=1, errored=0, canceled=0, expired=0)
        return SimpleNamespace(id=batch_id, processing_status="ended", request_counts=counts)

    def results(self, batch_id):
        return [SimpleNamespace(custom_id=c, result=r) for c, r in self.results_by_batch[batch_id].items()]


class FakeClient:
    def __init__(self, fixed_tokens=600):
        self.fixed_tokens = fixed_tokens
        self.messages = SimpleNamespace(batches=FakeBatches(), count_tokens=self._count)

    def _count(self, model, messages, system=None):
        return SimpleNamespace(input_tokens=10 + (self.fixed_tokens if system else 0))


class FakeJudge:
    def __init__(self, result):
        self.result, self.usage, self.model = result, Usage(), "claude-haiku-5-5"

    def judge(self, study, verse_text, supplied=None):
        self.usage.add(SimpleNamespace(input_tokens=2000, output_tokens=500))
        return self.result


@pytest.fixture
def setup(store, tmp_path, written, bible, bible_db):
    put(store, skeleton("JHN.3.16"))
    target = StudyStore(tmp_path / "out")
    target.directory.mkdir()
    state = bt.State(tmp_path / "state" / "batches.json")
    prose = {k: written[k] for k in PROSE_KEYS}
    return SimpleNamespace(source=store, target=target, state=state, prose=prose, bible=bible, bible_db=bible_db,
                           fixed=bt.load_fixed(), client=FakeClient())


def submit(s, ids=("JHN.3.16",), round_=1):
    return bt.submit(s.client, list(ids), round_, s.target, s.state, s.fixed, s.source, s.bible_db, log=lambda _: None)


def collect(s, judge):
    bt.refresh(s.client, s.state, log=lambda _: None)
    return bt.collect(s.client, s.state, s.source, s.bible, judge, log=lambda _: None)


def test_custom_id_is_the_verse_id_in_api_safe_form():
    assert bt.custom_id("JHN.3.16") == "JHN_3_16" and bt.study_id_of("JHN_3_16") == "JHN.3.16"
    assert bt.custom_id("1CO.13.4") == "1CO_13_4"


def test_request_shape_and_identical_cached_block(setup):
    s = setup
    put(s.source, skeleton("JHN.3.17"))
    a = bt.params(s.source.load("JHN.3.16"), s.fixed, s.bible_db)
    b = bt.params(s.source.load("JHN.3.16"), bt.load_fixed(), s.bible_db)
    assert a["system"] == b["system"]  # byte-identical prefix on every request
    assert a["system"][0]["cache_control"] == {"type": "ephemeral", "ttl": "1h"}
    assert a["model"] == "claude-haiku-5-5" and a["max_tokens"] == 32000
    assert a["output_config"]["effort"] == "medium"
    user = a["messages"][0]["content"]
    assert "John 3:16" in user and "Return JSON" not in user and "{{" not in user
    system = a["system"][0]["text"]
    assert system.startswith("You write short study notes") and "Return JSON with these sections" in system
    assert "meaning: 40-180 words" in system and "Verse:" not in system


def test_submit_refuses_a_block_too_short_to_cache(setup):
    s = setup
    s.client.fixed_tokens = 400
    with pytest.raises(SystemExit, match="under the 512-token"):
        submit(s)
    assert s.client.messages.batches.created == [] and s.state.batches == []


def test_submit_saves_batch_id_and_attempts(setup):
    s = setup
    submit(s)
    saved = json.loads(s.state.path.read_text())
    assert saved["batches"][0]["id"] == "msgbatch_1" and saved["attempts"] == {"JHN.3.16": 1}
    request = s.client.messages.batches.created[0][0]
    assert request["custom_id"] == "JHN_3_16"
    assert bt.plan(["JHN.3.16"], s.target, s.state, bible_db=s.bible_db)[1]["in an open batch"] == 1


def test_collect_writes_checks_and_logs_cache(setup):
    s = setup
    submit(s)
    s.client.messages.batches.results_by_batch["msgbatch_1"] = {"JHN_3_16": ok(s.prose, write=600)}
    assert collect(s, FakeJudge(RubricResult(RUBRIC_OK, []))) == []
    study = s.target.load("JHN.3.16")
    assert study["status"] == "auto_checked"
    assert study["meta"]["promptVersion"] == s.fixed.prompt_version and study["meta"]["batchId"] == "msgbatch_1"
    assert study["meta"]["attempts"] == 1
    assert list(validator().iter_errors(study)) == []
    assert study["meta"]["promptHash"] == s.fixed.system_hash and study["meta"]["maxTokens"] == 32000
    summary = bt.summary(s.state)
    assert summary["statuses"] == {"auto_checked": 1}
    assert summary["rubricUsd"] > 0 and summary["generationUsd"] > 0
    gen = bt.generate_summary(s.state)
    assert gen["batches"][0]["cacheWriteTokens"] == 600 and gen["promptVersions"] == {s.fixed.prompt_version: 1}
    assert gen["outputTokens"]["requests"] == 1 and "medianThinkingTokens" in gen["outputTokens"]


def test_max_tokens_retries_once_then_fails(setup):
    s = setup
    submit(s)
    s.client.messages.batches.results_by_batch["msgbatch_1"] = {"JHN_3_16": cut_off()}
    retry = collect(s, None)
    assert retry == ["JHN.3.16"]
    assert not s.target.path("JHN.3.16").exists()  # nothing usable written on attempt 1
    submit(s, retry, 2)
    s.client.messages.batches.results_by_batch["msgbatch_2"] = {"JHN_3_16": cut_off()}
    assert collect(s, None) == []
    study = s.target.load("JHN.3.16")
    assert study["status"] == "failed" and "max_tokens" in study["checks"]["reasons"][-1]["message"]
    assert list(validator().iter_errors(study)) == []


def test_flagged_twice_becomes_failed(setup):
    s = setup
    flagged = FakeJudge(RubricResult(RUBRIC_OK, [{"check": "rubric", "message": "tone 2/3: preachy"}]))
    submit(s)
    s.client.messages.batches.results_by_batch["msgbatch_1"] = {"JHN_3_16": ok(s.prose)}
    assert collect(s, flagged) == ["JHN.3.16"]
    assert s.target.load("JHN.3.16")["status"] == "flagged"
    submit(s, ["JHN.3.16"], 2)
    s.client.messages.batches.results_by_batch["msgbatch_2"] = {"JHN_3_16": ok(s.prose)}
    collect(s, flagged)
    study = s.target.load("JHN.3.16")
    assert study["status"] == "failed" and study["meta"]["attempts"] == 2
    assert bt.needs_retry(s.state) == []


def test_collect_never_overwrites_reviewed(setup, written):
    s = setup
    submit(s)
    reviewed = dict(written, status="reviewed")
    reviewed["meta"] = {**written["meta"], "reviewedBy": "R", "reviewedAt": "2026-11-18T00:00:00+00:00"}
    put(s.target, reviewed)
    s.client.messages.batches.results_by_batch["msgbatch_1"] = {"JHN_3_16": ok({**s.prose, "meaning": "x"})}
    collect(s, None)
    assert s.target.load("JHN.3.16") == reviewed
    assert bt.plan(["JHN.3.16"], s.target, s.state, bible_db=s.bible_db)[1]["reviewed/edited"] == 1


def test_failed_is_not_published():
    from pipeline.publish import PUBLISHABLE
    assert "failed" not in PUBLISHABLE


def test_projection_cache_cases():
    cal = bt.Calibration(folder=None, verses=100, input_per_verse=1200, output_per_verse=3000,
                         retry_rate=0.1, rubric_cost_per_verse=0.0005)
    p = bt.project(100, 600, cal)
    assert p["bestUsd"] < p["noCacheUsd"] < p["worstUsd"]
    assert p["expectedRetries"] == 10 and p["rubricUsd"] == pytest.approx(0.05)


def test_backup_zips_out_and_state(tmp_path):
    out, state = tmp_path / "out", tmp_path / "state"
    (out / "studies").mkdir(parents=True)
    (out / "studies" / "JHN.3.16.json").write_text("{}")
    state.mkdir()
    (state / "batches.json").write_text("{}")
    target = bt.backup(tmp_path / "backups", (out, state))
    assert set(zipfile.ZipFile(target).namelist()) == {"out/studies/JHN.3.16.json", "state/batches.json"}


def test_submit_splits_into_batches_of_at_most_batch_size(setup, monkeypatch):
    s = setup
    monkeypatch.setattr(bt, "params", lambda study, fixed, bible_db: {"model": bt.MODEL})  # no verse text needed
    for i in ("JHN.3.17", "JHN.3.18", "JHN.3.19"):
        put(s.source, skeleton(i))
    records = bt.submit(s.client, ["JHN.3.16", "JHN.3.17", "JHN.3.18", "JHN.3.19"], 1, s.target, s.state, s.fixed,
                        s.source, s.bible_db, log=lambda _: None, wave="john", batch_size=3)
    assert [len(r["ids"]) for r in records] == [3, 1]
    assert [len(c) for c in s.client.messages.batches.created] == [3, 1]
    assert all(r["wave"] == "john" for r in records)


def test_failed_twice_is_skipped_unless_force(setup):
    s = setup
    s.state.data["attempts"]["JHN.3.16"] = bt.MAX_ATTEMPTS
    todo, skipped = bt.plan(["JHN.3.16"], s.target, s.state, bible_db=s.bible_db)
    assert todo == [] and skipped["failed twice (status failed)"] == 1
    assert bt.plan(["JHN.3.16"], s.target, s.state, force=True, bible_db=s.bible_db)[0] == ["JHN.3.16"]


def test_summaries_written_per_wave_and_all(setup, tmp_path):
    s = setup
    bt.submit(s.client, ["JHN.3.16"], 1, s.target, s.state, s.fixed, s.source, s.bible_db, log=lambda _: None,
              wave="john")
    s.client.messages.batches.results_by_batch["msgbatch_1"] = {"JHN_3_16": ok(s.prose, write=600)}
    collect(s, FakeJudge(RubricResult(RUBRIC_OK, [])))
    folders = bt.write_summaries(s.state, tmp_path / "runs")
    assert set(folders) == {"john", "all"}
    gen = json.loads((tmp_path / "runs" / "john" / "generate_summary.json").read_text())
    chk = json.loads((tmp_path / "runs" / "john" / "check_summary.json").read_text())
    assert gen["statuses"] == {"auto_checked": 1} and gen["retries"] == {"verses": 0, "batches": 0}
    assert gen["promptHashes"] == {s.fixed.system_hash: 1} and gen["costUsd"] > 0
    assert chk["rubric"]["reviews"] == 1 and chk["rubric"]["costUsd"] > 0
    assert set(chk["overLength"]["total"]) == {"up to 20% over", "20-50% over", "more than 50% over"}
    assert chk["openings"]["meaning"][0]["count"] == 1


def test_waves_file_loads_in_order():
    from pipeline import waves
    w = waves.load()
    assert w.names == ["discover", "mark", "matthew", "luke", "john", "psalms"]
    assert w.batch_size == 500 and w.get("discover").source == "discover.txt" and w.get("mark").books == ("MRK",)
    ids = ["MAT.1.1", "MRK.1.1", "MRK.1.2", "PSA.1.1"]
    assert waves.wave_ids(w.get("mark"), ids) == ["MRK.1.1", "MRK.1.2"]


def test_waves_file_rejects_unknown_lines(tmp_path):
    from pipeline import waves
    bad = tmp_path / "waves.yaml"
    bad.write_text("batch_size: 500\nwaves:\n  - name: mark\n    book: [MRK]\n")
    with pytest.raises(ValueError):
        waves.load(bad)
    bad.write_text("batch_size: 900\nwaves:\n  - name: mark\n    books: [MRK]\n")
    with pytest.raises(ValueError):
        waves.load(bad)


def test_verse_without_web_text_is_skipped(setup):
    s = setup
    todo, skipped = bt.plan(["JHN.3.17"], s.target, s.state, bible_db=s.bible_db)  # fixture has only 3:16 in WEB
    assert todo == [] and skipped["no WEB text (needs a person)"] == 1


def test_stale_picks_only_old_prompt_verses_and_restarts_attempts(setup):
    s = setup
    submit(s)
    s.client.messages.batches.results_by_batch["msgbatch_1"] = {"JHN_3_16": ok(s.prose)}
    collect(s, FakeJudge(RubricResult(RUBRIC_OK, [])))
    current = s.target.load("JHN.3.16")["meta"]["promptVersion"]
    todo, skipped = bt.plan(["JHN.3.16"], s.target, s.state, bible_db=s.bible_db, stale_prompt=current)
    assert todo == [] and skipped["already on the current prompt"] == 1
    todo, _ = bt.plan(["JHN.3.16"], s.target, s.state, bible_db=s.bible_db, stale_prompt="000000000000")
    assert todo == ["JHN.3.16"]
    bt.restart_attempts(s.state, todo, s.target)
    assert s.state.attempts("JHN.3.16") == 0
    assert s.state.data["regenerated"]["JHN.3.16"][0] == {
        "fromPromptVersion": current, "previousAttempts": 1, "at": s.state.data["regenerated"]["JHN.3.16"][0]["at"]}
    submit(s, round_=1)
    assert s.state.attempts("JHN.3.16") == 1  # a flag on this attempt still gets one retry


def test_restart_attempts_is_idempotent(setup):
    s = setup
    put(s.target, {**skeleton("JHN.3.16"), "meta": {"model": "m", "generatedAt": "2026-11-18T00:00:00+00:00",
                                                    "promptVersion": "000000000000"}})
    s.state.data["attempts"]["JHN.3.16"] = 1
    bt.restart_attempts(s.state, ["JHN.3.16"], s.target)
    bt.restart_attempts(s.state, ["JHN.3.16"], s.target)  # a submit that stopped before sending, then a re-run
    assert len(s.state.data["regenerated"]["JHN.3.16"]) == 1 and s.state.attempts("JHN.3.16") == 0
