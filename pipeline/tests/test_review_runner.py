"""The concurrent reviewer step of batch.check_collected (`batch check|collect --workers N`), with fake judges.
Never calls the API."""
import copy
import dataclasses
import json
import random
import threading
import time
from types import SimpleNamespace

import pytest

from pipeline import batch as bt
from pipeline.rubric import RubricResult, RubricUnavailable
from pipeline.store import StudyStore
from pipeline.usage import Usage

RUBRIC_OK = {"model": "claude-haiku-5-5", **{k: {"score": 0, "reason": "fine"} for k in ("factualRisk", "oneSidedTheology", "tone")}}
BATCH = "msgbatch_review"


def call_usage():
    u = Usage()
    u.add(SimpleNamespace(input_tokens=2000, output_tokens=500))
    return u


class Judge:
    """Thread-safe fake: deterministic answer per study, random delay so completion order differs from submit order."""

    def __init__(self, fail=(), interrupt_after=None):
        self.fail, self.interrupt_after = set(fail), interrupt_after
        self.calls, self.threads, self.lock = [], set(), threading.Lock()
        self.usage = Usage()

    def judge(self, study, verse_text, supplied=None):
        time.sleep(random.uniform(0, 0.01))
        with self.lock:
            self.calls.append(study["id"])
            self.threads.add(threading.get_ident())
            n = len(self.calls)
        if self.interrupt_after is not None and n > self.interrupt_after:
            raise KeyboardInterrupt
        if study["id"] in self.fail:
            raise RubricUnavailable("529 overloaded", call_usage())
        verse = int(study["id"].split(".")[-1])
        advisory = [{"check": "rubric", "message": f"tone 2/3: verse {verse}"}] if verse % 3 == 0 else []
        return RubricResult(RUBRIC_OK, advisory, call_usage())


def make(tmp_path, written, ids, name="out", blocking=()):
    target = StudyStore(tmp_path / name)
    target.directory.mkdir()
    state = bt.State(tmp_path / f"state-{name}" / "batches.json")
    for sid in ids:
        study = copy.deepcopy(written)
        study["id"] = sid
        study["meta"].update(batchId=BATCH, wave="w")
        if sid in blocking:
            study["applyIt"] += " Read verse three too."  # a verse number: blocking, never reviewed
        target.save(study)
        state.set_attempts(sid, target, 1)
    state.batches.append({"id": BATCH, "round": 1, "wave": "w", "ids": list(ids), "outDir": str(target.directory),
                          "collectedAt": "2026-11-22T00:00:00+00:00", "processingStatus": "ended"})
    state.save()
    return target, state


IDS = [f"JHN.3.{v}" for v in range(1, 31)]


def results(target, ids):
    out = {}
    for sid in ids:
        st = target.load(sid)
        checks = {k: v for k, v in st.get("checks", {}).items() if k != "checkedAt"}
        out[sid] = (st["status"], checks)
    return out


def test_concurrent_run_matches_sequential(tmp_path, written, bible):
    a, state_a = make(tmp_path, written, IDS, "seq", blocking={"JHN.3.7"})
    b, state_b = make(tmp_path, written, IDS, "par", blocking={"JHN.3.7"})
    seq, par = Judge(), Judge()
    statuses_a = bt.check_collected(state_a, bible, seq, workers=1, log=lambda _: None)
    statuses_b = bt.check_collected(state_b, bible, par, workers=6, log=lambda _: None)
    assert results(a, IDS) == results(b, IDS)
    assert statuses_a == statuses_b == {"auto_checked": 29, "flagged": 1}
    assert "JHN.3.7" not in par.calls and len(par.calls) == 29          # a blocking failure is never reviewed
    assert len(par.threads) > 1                                         # really ran on several threads
    assert b.load("JHN.3.9")["checks"]["advisory"][0]["message"] == "tone 2/3: verse 9"


def test_unavailable_review_leaves_the_study_for_the_next_run(tmp_path, written, bible):
    target, state = make(tmp_path, written, IDS[:10])
    statuses = bt.check_collected(state, bible, Judge(fail={"JHN.3.4"}), workers=6, log=lambda _: None)
    assert statuses == {"auto_checked": 9}
    untouched = target.load("JHN.3.4")
    assert untouched["status"] == "generated" and "checks" not in untouched
    assert state.data["rubricUsage"]["requests"] == 10                 # the failed call's tokens still count
    again = Judge()
    assert bt.check_collected(state, bible, again, workers=6, log=lambda _: None) == {"auto_checked": 1}
    assert again.calls == ["JHN.3.4"]


def test_usage_totals_add_up_under_concurrency(tmp_path, written, bible):
    target, state = make(tmp_path, written, IDS)
    state.data["rubricUsageByWave"] = {"w": {"requests": 5, "inputTokens": 100, "outputTokens": 10}}
    bt.check_collected(state, bible, Judge(), workers=8, log=lambda _: None)
    saved = json.loads(state.path.read_text())
    assert saved["rubricUsage"]["requests"] == 30 and saved["rubricUsage"]["inputTokens"] == 60_000
    assert saved["rubricUsage"]["outputTokens"] == 15_000
    assert saved["rubricUsageByWave"]["w"]["requests"] == 35 and saved["rubricUsageByWave"]["w"]["inputTokens"] == 60_100
    assert saved["rubricEvidence"] == {"reviews": 30, "unverified": 0}


def test_state_saved_every_n_studies_and_at_the_end(tmp_path, written, bible, monkeypatch):
    target, state = make(tmp_path, written, IDS)
    monkeypatch.setattr(bt, "SAVE_EVERY", 10)
    saves = []
    real = state.save
    monkeypatch.setattr(state, "save", lambda: (saves.append(1), real()))
    bt.check_collected(state, bible, Judge(), workers=6, log=lambda _: None)
    assert len(saves) == 4  # after 10, 20 and 30 studies, then the final save


def test_interrupt_keeps_finished_work_and_rerun_finishes_the_rest(tmp_path, written, bible):
    target, state = make(tmp_path, written, IDS)
    with pytest.raises(KeyboardInterrupt):
        bt.check_collected(state, bible, Judge(interrupt_after=12), workers=1, log=lambda _: None)
    done = [sid for sid in IDS if target.load(sid)["status"] == "auto_checked"]
    assert 0 < len(done) <= 12
    assert all(target.load(sid)["status"] == "generated" for sid in IDS if sid not in done)
    saved = json.loads(state.path.read_text())                       # saved in the finally block
    assert saved["rubricUsage"]["requests"] == len(done) and saved["rubricEvidence"]["reviews"] == len(done)

    rest = Judge()
    resumed = bt.check_collected(bt.State(state.path), bible, rest, workers=6, log=lambda _: None)
    assert resumed == {"auto_checked": 30 - len(done)} and not set(rest.calls) & set(done)
    assert all(target.load(sid)["status"] == "auto_checked" for sid in IDS)
