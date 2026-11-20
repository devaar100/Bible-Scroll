"""Launch-run preparation: offline mode, length bands, new checks, contextMismatch parsing, prepare/replay/submit,
and the cc_test guards. Nothing here touches the network (conftest sets PIPELINE_OFFLINE=1)."""
import copy
import json
import socket
from types import SimpleNamespace

import pytest

from conftest import put, skeleton, words
from pipeline import batch as bt
from pipeline import jobs
from pipeline import publish as p
from pipeline.checks import Rules, band, check_study, describe_limits, upper_limit
from pipeline.offline import OfflineError, anthropic_client, require_online
from pipeline.rubric import AnthropicJudge, RubricUnavailable
from pipeline.store import StudyStore
from pipeline.studies import validator

RULES = Rules.load()
VALIDATOR = validator()


def outcome(study, bible):
    return check_study(study, bible, RULES, VALIDATOR, None)


def named(o):
    return sorted({r["check"] for r in o.reasons})


# ---- Offline mode ---------------------------------------------------------------------------------------

def test_offline_refuses_api_clients_and_network():
    with pytest.raises(OfflineError, match="PIPELINE_OFFLINE"):
        anthropic_client()
    with pytest.raises(OfflineError):
        require_online("deploy to Firebase")
    with pytest.raises(OfflineError):
        socket.create_connection(("api.anthropic.com", 443), timeout=1)
    with pytest.raises(OfflineError):
        socket.socket(socket.AF_INET, socket.SOCK_STREAM).connect(("127.0.0.1", 9))


def test_offline_blocks_every_client_path():
    from pipeline.generate import StudyWriter
    with pytest.raises(OfflineError):
        StudyWriter()
    with pytest.raises(OfflineError):
        AnthropicJudge()
    with pytest.raises(OfflineError):
        p.deploy("project", "release")
    with pytest.raises(OfflineError):
        p.fetch_live_manifest("https://example.web.app")


# ---- Length bands (limits.json) -------------------------------------------------------------------------

def test_limits_file_theology_range_and_cap():
    theo = RULES.limits["theology"]
    assert (theo["min"], theo["max"], theo["hardCap"]) == (70, 95, 115)
    assert upper_limit(theo, RULES.grace) == 115            # hard cap, no 20% band on top
    assert upper_limit(RULES.limits["meaning"], RULES.grace) == 216  # 180 + 20%
    assert "hard cap 115, no grace band" in describe_limits(RULES)


@pytest.mark.parametrize("n, expected", [(69, "under range"), (70, "within range"), (95, "within range"),
                                         (96, "over range, allowed"), (115, "over range, allowed"),
                                         (116, "over limit")])
def test_theology_bands(n, expected):
    assert band(n, RULES.limits["theology"], RULES.grace) == expected


@pytest.mark.parametrize("n, ok", [(69, False), (70, True), (115, True), (116, False)])
def test_theology_cap_is_enforced(written, bible, n, ok):
    written["theology"] = words(n)
    reasons = [r for r in outcome(written, bible).reasons if r["check"] == "length"]
    assert (reasons == []) == ok
    if not ok and n > 100:
        assert "hard cap 115" in reasons[0]["message"]


def test_grace_band_is_configurable(tmp_path):
    for name in ("banned.txt", "pipeline_wording.txt", "generic_lines.txt"):
        (tmp_path / name).write_text("")
    (tmp_path / "limits.json").write_text(json.dumps({"graceShare": 0.1, "words": {
        k: {"min": 1, "max": 100} for k in RULES.limits}}))
    rules = Rules.load(tmp_path)
    assert upper_limit(rules.limits["meaning"], rules.grace) == 110
    assert band(111, rules.limits["meaning"], rules.grace) == "over limit"


def test_limits_file_rejects_a_cap_below_max(tmp_path):
    for name in ("banned.txt", "pipeline_wording.txt", "generic_lines.txt"):
        (tmp_path / name).write_text("")
    bad = {k: {"min": 1, "max": 100} for k in RULES.limits}
    bad["theology"] = {"min": 70, "max": 95, "hardCap": 90}
    (tmp_path / "limits.json").write_text(json.dumps({"graceShare": 0.2, "words": bad}))
    with pytest.raises(ValueError, match="hardCap"):
        Rules.load(tmp_path)


# ---- Generic "traditions" lines (c) ---------------------------------------------------------------------

@pytest.mark.parametrize("sentence", [
    "Traditions differ on how this promise is lived out.",
    "Christians from many traditions find comfort here.",
    "Christians from different traditions pray these words.",
    "Many traditions read these lines at weddings.",
])
def test_generic_traditions_sentences_are_rejected(written, bible, sentence):
    written["theology"] = words(80, sentence)
    assert named(outcome(written, bible)) == ["generic_traditions"]


@pytest.mark.parametrize("sentence", [
    "Catholic and Protestant readers differ on what this says about baptism.",
    "Traditions differ about the timing of the end that this letter describes.",
    "Christians have long prayed these words.",
    "Readers in every age have read these lines at weddings.",
])
def test_specific_or_plain_wording_passes(written, bible, sentence):
    written["theology"] = words(80, sentence)
    assert outcome(written, bible).reasons == []


# ---- "Did you know?" names a word of this verse (a) ------------------------------------------------------

def test_did_you_know_must_name_a_listed_word(written, bible):
    written["didYouKnow"] = words(30, "Earlier in the passage the beloved wakes her under an apple tree.")
    o = outcome(written, bible)
    assert named(o) == ["did_you_know_word"] and o.reasons[0]["section"] == "didYouKnow"
    written["didYouKnow"] = words(30, "The noun kosmon is the object of the verb.")
    assert outcome(written, bible).reasons == []


def test_did_you_know_word_matches_the_cleaned_form(written, bible):
    written["originalLanguage"] = {"language": "hebrew", "textBasis": "leningrad", "words": [
        {"surface": "עַל", "lemma": "עַל", "transliteration": "'al-", "strongs": "H5921", "gloss": "on",
         "morphology": "HR"}]}
    written["didYouKnow"] = words(30, "The small word 'al means on.")
    assert outcome(written, bible).reasons == []


@pytest.mark.parametrize("translit, greek, written_as", [
    ("Pe.ri", False, "peri"),          # dots and stress capital
    ("pe.ri", False, "Pe.ri"),         # dotted form in the text still counts (dotted check rejects it elsewhere)
    ("he'ōs", True, "heos"),           # apostrophe and macron
    ("he.Os", True, "he'os"),          # apostrophe in the text
    ("ho.Du-", False, "hodu"),         # trailing maqqef hyphen
    ("ho.Du", False, "hodu-"),         # hyphen in the text
    ("ē.ga.Pē.sen", True, "egapesen"), # diacritics dropped
    ("'a.Lai", False, "alai"),         # ayin apostrophe optional
    ("'a.Lai", False, "ʼalai"),        # other apostrophe glyphs
])
def test_did_you_know_word_normalises(written, bible, translit, greek, written_as):
    written["originalLanguage"] = {"language": "greek" if greek else "hebrew", "textBasis": "x", "words": [
        {"surface": "x", "lemma": "y", "transliteration": translit, "strongs": "H1", "gloss": "g",
         "morphology": "HR"}]}
    written["didYouKnow"] = words(30, f"The word {written_as} matters here.")
    assert "did_you_know_word" not in named(outcome(written, bible))


@pytest.mark.parametrize("written_as", ["מַה", "מָה", "מה"])
def test_did_you_know_word_accepts_surface_or_lemma(written, bible, written_as):
    written["originalLanguage"] = {"language": "hebrew", "textBasis": "leningrad", "words": [
        {"surface": "מַה־", "lemma": "מָה", "transliteration": "mah-", "strongs": "H4100", "gloss": "why?",
         "morphology": "HPi"}]}
    written["didYouKnow"] = words(30, f"The word {written_as} asks why.")
    assert "did_you_know_word" not in named(outcome(written, bible))


@pytest.mark.parametrize("surface, written_as", [("δίδοτε,", "δίδοτε"), ("שָֽׁמָּה׃ ס", "שמה"), ("ὑμῖν.", "ὑμῖν")])
def test_did_you_know_word_ignores_surface_punctuation(written, bible, surface, written_as):
    written["originalLanguage"] = {"language": "greek", "textBasis": "x", "words": [
        {"surface": surface, "lemma": "zz", "transliteration": "qq", "strongs": "G1", "gloss": "g", "morphology": "V"}]}
    written["didYouKnow"] = words(30, f"The word {written_as} matters here.")
    assert "did_you_know_word" not in named(outcome(written, bible))


def test_did_you_know_word_needs_a_whole_word(written, bible):
    written["originalLanguage"] = {"language": "hebrew", "textBasis": "x", "words": [
        {"surface": "x", "lemma": "y", "transliteration": "ho.Du", "strongs": "H1", "gloss": "g", "morphology": "HR"}]}
    written["didYouKnow"] = words(30, "The word hodumah is not listed.")
    assert "did_you_know_word" in named(outcome(written, bible))


def test_dyk_norm_examples():
    from pipeline.checks import dyk_norm
    assert dyk_norm("Pe.ri") == dyk_norm("peri") == "peri"
    assert dyk_norm("he'ōs") == dyk_norm("heos") == "heos"
    assert dyk_norm("hodu-") == "hodu"
    assert dyk_norm("מַה־") == "מה"


# ---- Rubric contextMismatch parser (e): fake client --------------------------------------------------------

def _response(payload):
    return SimpleNamespace(content=[SimpleNamespace(type="text", text=json.dumps(payload))], stop_reason="end_turn",
                           stop_details=None, _request_id="req_test",
                           usage=SimpleNamespace(input_tokens=1, output_tokens=1))


def _judge(payload):
    seen = {}

    def create(**kwargs):
        seen.update(kwargs)
        return _response(payload)
    return AnthropicJudge(client=SimpleNamespace(messages=SimpleNamespace(create=create))), seen


def _scores(claims=None, drop_context=False):
    one = {"score": 0, "reason": "fine", "section": "Meaning", "quote": "word word word"}
    out = {"factualRisk": one, "oneSidedTheology": one, "tone": one}
    if not drop_context:
        out["contextMismatch"] = {"claims": claims or []}
    return out


SUPPLIED = "John 3:16\nWorld English Bible:\nFor God so loved the world, that he gave his one and only Son."


def test_context_claim_with_a_supported_quote_passes(written):
    written["meaning"] = words(60, "The verse says God so loved the world.")
    judge, seen = _judge(_scores([{"section": "Meaning", "claim": "God so loved the world",
                                   "support": "For God so loved the world", "supported": True}]))
    result = judge.judge(written, "For God so loved", supplied=SUPPLIED)
    assert result.reasons == [] and result.unverified == 0
    claim = result.rubric["contextMismatch"]["claims"][0]
    assert claim["verified"] and claim["supportFound"] and claim["section"] == "meaning"
    assert "contextMismatch" in seen["output_config"]["format"]["schema"]["required"]
    assert "Text the writer was given" in seen["messages"][0]["content"]


def test_fabricated_support_quote_flags_and_fabricated_claim_quote_counts_unverified(written):
    written["meaning"] = words(60, "His own people did not receive him.")
    judge, _ = _judge(_scores([
        {"section": "Meaning", "claim": "His own people did not receive him",
         "support": "He came to his own, and those who were his own didn't receive him", "supported": True},
        {"section": "Meaning", "claim": "the apple tree where she was born",  # not in the study
         "support": "For God so loved the world", "supported": True},
    ]))
    result = judge.judge(written, "", supplied=SUPPLIED)
    claims = result.rubric["contextMismatch"]["claims"]
    assert claims[0]["supportFound"] is False and claims[0]["verified"] is True
    assert claims[1]["verified"] is False and result.unverified == 1
    assert [r["check"] for r in result.reasons] == ["context_mismatch"]
    assert "isn't in the text given" in result.reasons[0]["message"]


def test_unsupported_claim_flags(written):
    written["meaning"] = words(60, "God is named three ways here.")
    judge, _ = _judge(_scores([{"section": "Meaning", "claim": "God is named three ways here",
                                "support": "", "supported": False}]))
    result = judge.judge(written, "", supplied=SUPPLIED)
    assert [r["check"] for r in result.reasons] == ["context_mismatch"]
    assert "nothing in the text given supports it" in result.reasons[0]["message"]


def test_missing_context_mismatch_is_an_unusable_response(written):
    judge, _ = _judge(_scores(drop_context=True))
    with pytest.raises(RubricUnavailable, match="Unusable"):
        judge.judge(written, "", supplied=SUPPLIED)


# ---- prepare / replay / submit --next (h) -----------------------------------------------------------------

class FakeBatches:
    def __init__(self):
        self.created = []

    def create(self, requests):
        self.created.append(requests)
        return SimpleNamespace(id=f"msgbatch_{len(self.created)}", processing_status="in_progress")


def fake_client():
    batches = FakeBatches()
    count = lambda model, messages, system=None: SimpleNamespace(input_tokens=10 + (2000 if system else 0))  # noqa: E731
    return SimpleNamespace(messages=SimpleNamespace(batches=batches, count_tokens=count)), batches


@pytest.fixture
def job(store, tmp_path, bible_db):
    put(store, skeleton("JHN.3.16"))
    out = tmp_path / "jobs" / "launch_test"
    manifest = jobs.prepare(["JHN.3.16", "JHN.3.99"], out, source=store, bible_db=bible_db, batch_size=500,
                            studies_out=tmp_path / "studies_out", log=lambda _: None)
    return SimpleNamespace(dir=out, manifest=manifest, state=bt.State(tmp_path / "state.json"))


def test_prepare_writes_exact_requests_manifest_and_schedule(job, store, bible_db):
    lines = (job.dir / "batch-001.jsonl").read_text().splitlines()
    assert len(lines) == 1
    req = json.loads(lines[0])
    assert req == json.loads(json.dumps({"custom_id": "JHN_3_16",
                                         "params": bt.params(store.load("JHN.3.16"), bt.load_fixed(), bible_db)}))
    m = job.manifest
    assert m["ids"] == ["JHN.3.16"] and "JHN.3.99" in m["skipped"]
    assert {"promptVersion", "systemHash", "booksHash", "limitsHash"} <= set(m) and m["limitsHash"] == jobs.limits_hash()
    assert m["model"] == "claude-haiku-5-5" and m["effort"] == "medium" and not m["testOnly"]
    schedule = json.loads((job.dir / "schedule.json").read_text())
    assert schedule["batches"][0] | {"ids": None} == {"file": "batch-001.jsonl", "count": 1, "ids": None,
                                                     "status": "pending", "batchId": None, "submittedAt": None}
    system, user = jobs.request_text(req)
    assert "Return JSON with these sections" in system and "John 3:16" in user


def test_prepare_splits_into_files_of_at_most_batch_size(store, tmp_path, bible_db, monkeypatch):
    for i in ("JHN.3.16", "JHN.3.17", "JHN.3.18"):
        put(store, skeleton(i))
    monkeypatch.setattr(bt, "params", lambda study, fixed, db: {"model": bt.MODEL, "id": study["id"]})
    m = jobs.prepare(["JHN.3.16", "JHN.3.17", "JHN.3.18"], tmp_path / "j", source=store, bible_db=bible_db,
                     batch_size=2, log=lambda _: None)
    assert sorted(m["files"]) == ["batch-001.jsonl", "batch-002.jsonl"]


def test_submit_dry_run_sends_nothing_and_works_offline(job):
    info = jobs.submit_next(job.dir, dry_run=True, state=job.state, log=lambda _: None)
    assert info["count"] == 1 and info["first"] == "JHN.3.16"
    assert json.loads((job.dir / "schedule.json").read_text())["batches"][0]["status"] == "pending"


def test_submit_without_a_client_is_refused_offline(job):
    with pytest.raises(OfflineError):
        jobs.submit_next(job.dir, state=job.state, log=lambda _: None)


def test_submit_next_sends_the_file_unchanged_and_records_it(job):
    client, batches = fake_client()
    info = jobs.submit_next(job.dir, client=client, state=job.state, log=lambda _: None)
    sent = batches.created[0]
    assert sent == jobs.load_requests(job.dir, "batch-001.jsonl")
    assert info["batchId"] == "msgbatch_1" and job.state.attempts("JHN.3.16") == 1
    assert job.state.batches[0]["wave"] == "launch_test" and job.state.batches[0]["jobFile"] == "batch-001.jsonl"
    schedule = json.loads((job.dir / "schedule.json").read_text())
    assert schedule["batches"][0]["status"] == "submitted"
    assert jobs.submit_next(job.dir, client=client, state=job.state, log=lambda _: None) is None  # nothing left


def test_submit_refuses_when_the_prompt_changed(job, monkeypatch):
    real = bt.load_fixed()
    monkeypatch.setattr(bt, "load_fixed", lambda *a: bt.Fixed(real.system + "x", real.verse_template, "0" * 12,
                                                              "1" * 12, real.books_hash))
    with pytest.raises(SystemExit, match="prompts/study.md changed since prepare"):
        jobs.submit_next(job.dir, dry_run=True, state=job.state, log=lambda _: None)


def test_submit_refuses_an_edited_batch_file(job):
    path = job.dir / "batch-001.jsonl"
    path.write_text(path.read_text().replace("John", "Jon"))
    with pytest.raises(SystemExit, match="edited after prepare"):
        jobs.submit_next(job.dir, dry_run=True, state=job.state, log=lambda _: None)


def test_test_round_jobs_are_never_submitted(store, tmp_path, bible_db):
    put(store, skeleton("JHN.3.16"))
    jobs.prepare(["JHN.3.16"], tmp_path / "cc_round1", source=store, bible_db=bible_db, log=lambda _: None)
    with pytest.raises(SystemExit, match="never submitted"):
        jobs.submit_next(tmp_path / "cc_round1", dry_run=True, state=bt.State(tmp_path / "s.json"),
                         log=lambda _: None)


def test_replay_runs_every_code_check_without_ai(tmp_path, written, bible):
    folder = StudyStore(tmp_path / "round")
    folder.directory.mkdir()
    put(folder, written)
    bad = copy.deepcopy(written)
    bad["id"] = "JHN.3.17"
    bad["theology"] = words(130, "Traditions differ on how this is read.")
    put(folder, bad)
    report = jobs.replay(folder.directory, bible=bible, log=lambda _: None)
    assert report["passed"] == 1 and report["rejected"] == ["JHN.3.17"]
    assert report["reasons"] == {"length": 1, "generic_traditions": 1}
    assert report["bands"]["bySection"]["theology"]["over limit"] == 1
    assert json.loads((folder.directory / "replay.json").read_text())["rejected"] == ["JHN.3.17"]


# ---- cc_test artifacts are never published -----------------------------------------------------------------

def test_publish_refuses_a_cc_test_folder(tmp_path, written):
    folder = StudyStore(tmp_path / "out" / "cc_test" / "round1")
    folder.directory.mkdir(parents=True)
    put(folder, dict(written, status="auto_checked"))
    with pytest.raises(p.TestArtifactError):
        p.build_release(folder, "", discover=[])


def test_publish_refuses_a_subagent_study_anywhere(store, written):
    put(store, dict(written, status="auto_checked",
                    meta=dict(written["meta"], source="claude-code-subagent", round=1)))
    with pytest.raises(p.TestArtifactError, match="test artifact"):
        p.build_release(store, "", discover=[])


def test_bundle_and_write_site_refuse_cc_test(tmp_path):
    from pipeline.bundle import bundle
    with pytest.raises(p.TestArtifactError):
        bundle(tmp_path / "cc_test" / "site", tmp_path / "assets")
    with pytest.raises(p.TestArtifactError):
        p.write_site(SimpleNamespace(files={}, manifest={}), tmp_path / "cc_test" / "site")


def test_subagent_meta_is_valid_study_schema(written):
    written["meta"].update(source="claude-code-subagent", round=2)
    assert list(VALIDATOR.iter_errors(written)) == []


# ---- Reviewer findings: studyQuote verified in code ----------------------------------------------------------

def test_reviewer_quotes_must_be_verbatim(written):
    from pipeline import cctest
    written["meaning"] = words(60, "His own people did not receive him.")
    finding = {"section": "Meaning", "severity": "ERROR", "category": "context-claim",
               "supportQuote": "NONE", "explanation": "x"}
    review = cctest.verify(1, {"studies": [{"id": "JHN.3.16", "didYouKnowAboutThisVerse": True, "findings": [
        dict(finding, studyQuote="His own people did not receive him"),
        dict(finding, studyQuote="the apple tree"),                      # fabricated
        dict(finding, studyQuote="His own people", severity="FATAL"),    # malformed
    ]}]}, {"JHN.3.16": written})
    assert len(review["findings"]["JHN.3.16"]) == 1
    assert [d["why"].split(" ")[0] for d in review["discarded"]] == ["studyQuote", "malformed"]
    assert review["didYouKnow"]["JHN.3.16"]["about"] is True



def test_retry_review_replaces_the_earlier_review(written):
    from pipeline import cctest
    written["meaning"] = words(60, "The new text after the retry.")
    finding = {"section": "Meaning", "severity": "ERROR", "category": "leak", "supportQuote": "NONE",
               "explanation": "x"}
    first = {"id": "S12", "didYouKnowAboutThisVerse": False, "findings": [dict(finding, studyQuote="old text")]}
    retry = {"id": "S12", "didYouKnowAboutThisVerse": True,
             "findings": [dict(finding, studyQuote="The new text", severity="WARN")]}
    review = cctest.verify(2, {"studies": [first, retry]}, {"S12": written})
    assert [f["severity"] for f in review["findings"]["S12"]] == ["WARN"]
    assert review["discarded"] == []  # the first review's quotes belong to the replaced text
    assert review["didYouKnow"]["S12"]["about"] is True
    review = cctest.verify(2, {"studies": [retry, dict(retry, findings=[])]}, {"S12": written})
    assert review["findings"] == {}

# ---- Blind review batches ---------------------------------------------------------------------------------

def test_blind_batches_keep_a_verse_out_of_its_own_batch():
    from pipeline import cctest
    entries = [("round", f"V.{i}") for i in range(20)] + [("canary", "V.3"), ("canary", "V.7"), ("canary", "V.11")]
    for seed in range(25):
        batches = cctest.blind_batches(entries, 5, seed)
        assert sorted(e for b in batches for e in b) == sorted(entries)
        assert all(len(b) <= 5 for b in batches)
        assert all(len({e[1] for e in b}) == len(b) for b in batches)
    assert cctest.blind_batches(entries, 5, 1) == cctest.blind_batches(entries, 5, 1)
    assert cctest.blind_batches(entries, 5, 1) != cctest.blind_batches(entries, 5, 2)


def test_packet_label_hides_the_id(written):
    from pipeline import cctest
    from pipeline.paths import APP_BIBLE_DB
    if not APP_BIBLE_DB.exists():
        pytest.skip("needs the app bible.db")
    text = cctest.packet(written, label="S07")
    assert "=== STUDY S07 (" in text and "=== END STUDY S07 ===" in text
    assert "canary" not in text.lower() and f"STUDY {written['id']}" not in text


@pytest.mark.parametrize("text, flagged", [
    ("The World English Bible puts it plainly: 'I shall lack nothing.' The King James differs.", False),
    ("Look at sunergei, the verb rendered 'works together.' It is singular.", False),
    ("The word ’together.’ ends here.", False),
    ("The word be.re.Shit opens the book.", True),
    ("The word ro.'I is the shepherd word.", True),
    ("Here pe.Ri means fruit.", True),
])
def test_dotted_translit_ignores_sentence_end_in_quotes(written, bible, text, flagged):
    written["meaning"] = words(60, text)
    assert ("dotted_translit" in named(outcome(written, bible))) is flagged
