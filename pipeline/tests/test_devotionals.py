import copy
import csv
import gzip
import json
import sqlite3
from datetime import date

import pytest
from fastapi.testclient import TestClient

from conftest import words
from pipeline import publish as p
from pipeline.devotionals import calendar as cal
from pipeline.devotionals import calendar_ai, checks as dchecks, generate as gen
from pipeline.devotionals import publish as dpub
from pipeline.devotionals.store import DevotionalStore, validator
from pipeline.plans.ai import Draft, DraftRefused
from pipeline.plans.bible import VerseIndex
from pipeline.rubric import RubricResult, RubricUnavailable
from pipeline.store import StudyStore

INDEX = VerseIndex.load()
KJV = frozenset(INDEX.counts)
DATE = "11-09"
ANCHOR = "JHN.3.16-JHN.3.20"
GOOD = {"title": "Loved first", "context": words(80, "In John 3:16"), "reflect": words(150, "God loved"),
        "applyIt": words(50, "Today"), "prayer": words(30, "Father"), "extraVerses": ["ROM.5.8-ROM.5.8"]}


# ---- Calendar ---------------------------------------------------------------------------------------

def test_real_calendar_is_complete_and_every_anchor_exists():
    days = cal.load(verses=KJV)
    assert len(days) == 366 and days[0].date == "01-01" and days[-1].date == "12-31"
    leap = next(d for d in days if d.date == "02-29")
    assert leap.day_of_year == 60 and leap.series_length == 1
    by_date = {d.date: d for d in days}
    assert by_date["12-25"].season == "Christmas" and by_date["12-01"].season == "Advent"
    # Easter falls between 22 March and 25 April: the cross leads up to it, "Living hope" follows.
    assert by_date["03-22"].series == "The cross" and by_date["04-25"].series == "Living hope"
    b = cal.balance(days)
    assert 0.35 < b["oldTestament"] / b["days"] < 0.6


def write_calendar(tmp_path, change=None) -> "Path":
    rows = list(csv.DictReader(open(cal.CALENDAR, encoding="utf-8")))
    for r in rows:
        if r["date"] == DATE:
            r["anchor"] = ANCHOR
    if change:
        change(rows)
    path = tmp_path / "calendar.csv"
    cal.write(rows, path)
    return path


@pytest.mark.parametrize("change, message", [
    (lambda rows: rows.pop(40), "dates must be every day"),
    (lambda rows: rows[3].update(anchor="JHN.3.1-ROM.3.2"), "within one book"),
    (lambda rows: rows[3].update(anchor="JHN.30.1-JHN.30.2"), "doesn't exist"),
    (lambda rows: rows[5].update(series_day="9"), "don't count 1..n"),
    (lambda rows: rows[5].update(cover_style="neon"), "cover_style"),
    (lambda rows: rows[6].update(anchor=rows[5]["anchor"]), "already used"),
])
def test_calendar_problems_fail_loudly(tmp_path, change, message):
    with pytest.raises(cal.CalendarError, match=message):
        cal.load(write_calendar(tmp_path, change), verses=KJV)


# ---- Fixtures ---------------------------------------------------------------------------------------

@pytest.fixture
def day(tmp_path):
    return next(d for d in cal.load(write_calendar(tmp_path)) if d.date == DATE)


@pytest.fixture
def dbible(bible, source_db, bible_db):
    with sqlite3.connect(source_db) as db:
        db.executemany("INSERT INTO word (dataset, book_id, chapter, verse, native_ref, word_no, text_type, surface, "
                       "translit, strongs, lemma, gloss) VALUES ('TAGNT',43,3,16,'Jhn.3.16',?, 'N',?,?,?,?,?)", [
                           (1, "ἠγάπησεν", "ēgapēsen", "G0025", "ἀγαπάω", "loved"),
                           (2, "κόσμον,", "kosmon", "G2889", "κόσμος", "[the] world"),
                       ])
    with sqlite3.connect(bible_db) as db:
        db.execute("INSERT INTO verses VALUES ('WEB', 43, 3, 17, 'For God didn''t send his Son...')")
    return bible


class FakeDrafter:
    def __init__(self, data=None, error=None):
        self.data, self.error, self.prompts = data or GOOD, error, []

    def draft(self, system, prompt, schema, max_tokens):
        self.prompts.append(prompt)
        if self.error:
            raise self.error
        return Draft(copy.deepcopy(self.data), "claude-opus-5-5")


class FakeJudge:
    def __init__(self, result=None, error=None):
        self.result, self.error, self.subjects = result or RubricResult({"model": "h"}, []), error, []

    def judge(self, doc, text, subject=None):
        self.subjects.append(subject)
        if self.error:
            raise self.error
        return self.result


@pytest.fixture
def drafted(day, dbible):
    return gen.from_draft(day, Draft(copy.deepcopy(GOOD), "claude-opus-5-5"), gen.Prompt.load())


# ---- Generator --------------------------------------------------------------------------------------

def test_request_has_passage_text_words_and_limits(day, dbible):
    request = gen.build_request(day, gen.Prompt.load(), dbible.bible_db, dbible.source_db)
    assert "John 3:16-20" in request and "[16] For God so loved" in request
    # Each devotional stands alone: no date and no "day X of Y" in the request.
    assert "November" not in request and " of 14" not in request and "day 9" not in request.lower()
    assert "ēgapēsen (ἠγάπησεν) = loved" in request and "kosmon (κόσμον) = the world" in request
    assert "120-180" in request and "{{" not in request


def test_draft_is_schema_valid_and_takes_metadata_from_calendar(drafted, day):
    assert not list(validator().iter_errors(drafted))
    assert drafted["id"] == "devotional-11-09" and drafted["dayOfYear"] == day.day_of_year
    assert drafted["verses"][0] == {"book": "JHN", "startChapter": 3, "startVerse": 16, "endChapter": 3, "endVerse": 20}
    assert drafted["verses"][1]["book"] == "ROM" and drafted["status"] == "generated"


def test_empty_prayer_is_left_out(day, dbible):
    d = gen.from_draft(day, Draft(dict(GOOD, prayer=" "), "m"), gen.Prompt.load())
    assert "prayer" not in d and not list(validator().iter_errors(d))


def test_draft_never_overwrites_reviewed_or_edited_without_force(tmp_path, day, dbible, drafted):
    store = DevotionalStore(tmp_path / "devotionals")
    store.save(dict(drafted, status="reviewed", contentVersion="000000000000"))
    run = lambda **kw: list(gen.draft_days([day], FakeDrafter(), store, bible_db=dbible.bible_db,
                                           source_db=dbible.source_db, **kw))
    assert "skipped" in run(stale=True)[0]
    assert "drafted" in run(force=True)[0] and store.load(day.id)["status"] == "generated"


def test_stale_redrafts_only_changed_rows(tmp_path, day, dbible, drafted):
    store = DevotionalStore(tmp_path / "devotionals")
    store.save(drafted)
    drafter = FakeDrafter()
    kw = dict(bible_db=dbible.bible_db, source_db=dbible.source_db)
    assert "skipped" in list(gen.draft_days([day], drafter, store, stale=True, **kw))[0]  # up to date
    store.save(dict(drafted, contentVersion="000000000000"))
    assert "drafted" in list(gen.draft_days([day], drafter, store, stale=True, **kw))[0]


def test_refusal_is_reported_and_nothing_saved(tmp_path, day, dbible):
    store = DevotionalStore(tmp_path / "devotionals")
    out = list(gen.draft_days([day], FakeDrafter(error=DraftRefused("no")), store,
                              bible_db=dbible.bible_db, source_db=dbible.source_db))
    assert "refused" in out[0] and store.ids() == []


# ---- Checks -----------------------------------------------------------------------------------------

def check(doc, day, bible, judge=None):
    return dchecks.check_devotional(doc, day, bible, dchecks.load_rules(), validator(), judge)


def test_good_draft_passes_and_rubric_gets_devotional_subject(drafted, day, dbible):
    judge = FakeJudge()
    outcome = check(drafted, day, dbible, judge)
    assert outcome.status == "auto_checked", outcome.reasons
    assert "Theme: " in judge.subjects[0].heading and "Reflect" in judge.subjects[0].sections.values()
    assert "daily devotionals" in judge.subjects[0].system


@pytest.mark.parametrize("change, check_name", [
    (lambda d: d.update(reflect=words(40)), "length"),
    (lambda d: d.update(context=words(80, "See John 3:99")), "reference"),
    (lambda d: d.update(reflect=words(150, "The Greek word agape")), "original_language"),
    (lambda d: d.update(reflect=words(150, "Here ἀγάπη")), "original_language"),
    (lambda d: d.update(theme="Something else"), "calendar"),
    (lambda d: d["verses"].append({"book": "ROM", "startChapter": 99, "startVerse": 1, "endChapter": 99, "endVerse": 2}), "reference"),
    (lambda d: d.update(prayer=words(5)), "length"),
    (lambda d: d.update(reflect=words(150, "As we saw yesterday,")), "standalone"),
    (lambda d: d.update(applyIt=words(50, "Tomorrow's reading picks this up.")), "standalone"),
    (lambda d: d.update(context=words(80, "On Good Friday")), "standalone"),
    (lambda d: d.update(reflect=words(150, "This is day 3 of 14.")), "standalone"),
    (lambda d: d.update(reflect=words(150, "Paul's letter to the Hebrews says")), "authorship"),
    (lambda d: d.update(applyIt=words(50, "Pray instead of seeing a doctor.")), "health"),
    (lambda d: d.update(reflect=words(150, "The theme here is love.")), "pipeline_wording"),
    (lambda d: d.update(reflect=words(150, "The cross references show")), "pipeline_wording"),
    (lambda d: d.update(context=words(80, "Compare Romans 8:28.")), "reference_not_allowed"),
    (lambda d: d["verses"].append({"book": "OBA", "startChapter": 1, "startVerse": 3, "endChapter": 1, "endVerse": 3}), "reference_not_allowed"),
])
def test_problems_are_flagged(drafted, day, dbible, change, check_name):
    change(drafted)
    judge = FakeJudge()
    outcome = check(drafted, day, dbible, judge)
    assert outcome.status == "flagged" and check_name in {r["check"] for r in outcome.reasons}
    assert judge.subjects == []  # rubric skipped once a free check fails


def test_passage_words_count_as_mentions(drafted, day, dbible):
    drafted["reflect"] = words(150, "The Greek word ēgapēsen")
    assert check(drafted, day, dbible).status == "auto_checked"


def test_rubric_outage_leaves_devotional_unchanged(tmp_path, drafted, day, dbible):
    store = DevotionalStore(tmp_path / "devotionals")
    store.save(drafted)
    summary = dchecks.run(store, [day], dbible, FakeJudge(error=RubricUnavailable("down")))
    assert summary.checked == 0 and summary.rubric_unavailable and store.load(day.id)["status"] == "generated"
    summary = dchecks.run(store, [day], dbible, None)
    saved = store.load(day.id)
    assert saved["status"] == "auto_checked" and saved["checks"]["checkerVersion"] == dchecks.CHECKER_VERSION


# ---- Publishing -------------------------------------------------------------------------------------

def publishable_set(tmp_path, drafted):
    store = DevotionalStore(tmp_path / "devotionals")
    reviewed = copy.deepcopy(drafted)
    reviewed["meta"].update(reviewedBy="Sam Private", editedBy="Ed")
    store.save(dict(reviewed, status="reviewed"))
    store.save(dict(copy.deepcopy(drafted), id="devotional-02-29", date="02-29", dayOfYear=60, status="auto_checked"))
    store.save(dict(copy.deepcopy(drafted), id="devotional-02-28", date="02-28", dayOfYear=59, status="flagged"))
    store.save(dict(copy.deepcopy(drafted), id="devotional-03-01", date="03-01", dayOfYear=61))  # generated
    return store


def test_release_has_one_file_per_month_with_only_publishable_days(tmp_path, drafted):
    store = publishable_set(tmp_path, drafted)
    release = p.build_release(StudyStore(tmp_path), "https://x.web.app", discover=[], devotionals=store)
    months = release.manifest["devotionals"]["months"]
    assert [m["month"] for m in months] == ["02", "11"]
    feb = json.loads(gzip.decompress(release.files[p._site_path(months[0]["url"])]))
    assert [d["date"] for d in feb["devotionals"]] == ["02-29"]  # flagged 02-28 left out
    nov = json.loads(gzip.decompress(release.files[p._site_path(months[1]["url"])]))
    text = json.dumps(nov)
    assert nov["devotionals"][0]["reviewed"] and "Sam Private" not in text and "checks" not in text
    assert months[1]["url"].startswith("https://x.web.app/devotionals/v") and months[1]["reviewedCount"] == 1
    assert release.devotionals_left_out == {"02": {"flagged": 1}, "03": {"generated": 1}}
    again = p.build_release(StudyStore(tmp_path), "https://x.web.app", discover=[], devotionals=store)
    assert again.files == release.files  # byte-deterministic


def test_diff_and_site_keep_previous_month_files(tmp_path, drafted):
    store = publishable_set(tmp_path, drafted)
    first = p.build_release(StudyStore(tmp_path), "", discover=[], devotionals=store)
    site = tmp_path / "site"
    p.write_site(first, site)
    d = p.diff(first, first.manifest, [])
    assert not d.any
    changed = store.load("devotional-11-09")
    store.save(dict(changed, title="New title"))
    second = p.build_release(StudyStore(tmp_path), "", discover=[], devotionals=store)
    d = p.diff(second, first.manifest, [])
    assert d.any and [new["month"] for _, new in d.months_changed] == ["11"]
    assert "~ devotionals 11" in p.render_diff(d, second, first.manifest)
    kept = p.write_site(second, site, keep_from=first.manifest)
    assert any(k.startswith("devotionals/") and k.endswith("/11.json.gz") for k in kept)


@pytest.mark.parametrize("today, months", [(date(2027, 3, 5), ("03", "04")), (date(2027, 12, 31), ("12", "01"))])
def test_bundled_months_wrap(today, months):
    assert dpub.bundled_months(today) == months


def test_bundle_copies_current_and_next_month(tmp_path, drafted):
    store = publishable_set(tmp_path, drafted)
    release = p.build_release(StudyStore(tmp_path), "", discover=[], devotionals=store)
    site, assets = tmp_path / "site", tmp_path / "assets"
    p.write_site(release, site)
    assets.mkdir()
    (assets / "07.json.gz").write_bytes(b"old")
    result = dpub.bundle(site, date(2027, 2, 10), assets)
    assert result.copied == ["02"] and result.missing == ["03"] and result.removed == ["07.json.gz"]
    manifest = json.loads((assets / "manifest.json").read_text())
    assert manifest["months"][0]["url"] == "02.json.gzip"
    assert (assets / "02.json.gzip").exists() and not list(assets.glob("*.json.gz"))


# ---- Review tool ------------------------------------------------------------------------------------

@pytest.fixture
def review(tmp_path, dbible, drafted, store):
    devos = DevotionalStore(tmp_path / "devotionals")
    devos.save(dict(drafted, reflect=words(40)))  # too short: blocks approval
    plans = tmp_path / "plans"
    plans.mkdir()
    from pipeline.review.app import create_app
    app = create_app(store, dbible, plans_dir=plans, verse_index=INDEX, devotionals_dir=devos.directory,
                     calendar_path=tmp_path / "calendar.csv", regenerate_log=tmp_path / "regen.jsonl")
    return TestClient(app), devos


def test_review_list_and_view(review):
    client, _ = review
    data = client.get("/api/devotionals").json()
    assert len(data["devotionals"]) == 366 and data["devotionals"][0]["id"] == "devotional-11-09"
    view = client.get("/api/devotional/devotional-11-09").json()
    assert not view["canApprove"] and view["calendar"]["anchor"] == ANCHOR
    assert view["passages"][0]["label"] == "John 3:16-20" and "loved" in view["passages"][0]["text"]
    assert client.get("/api/devotional/devotional-13-01").status_code == 404
    assert client.get("/api/devotional/..%2Fsecret").status_code == 404


def test_review_approve_blocked_until_edit_fixes_it(review):
    client, devos = review
    assert client.post("/api/devotional/devotional-11-09/approve", json={"reviewer": "Sam"}).status_code == 409
    r = client.post("/api/devotional/devotional-11-09/edit",
                    json={"reviewer": "Sam", "fields": {"reflect": words(150), "prayer": ""}})
    assert r.status_code == 200 and r.json()["status"] == "auto_checked" and "prayer" not in r.json()
    assert r.json()["contentVersion"].endswith(".1")
    assert client.post("/api/devotional/devotional-11-09/approve", json={"reviewer": "Sam"}).status_code == 200
    saved = devos.load("devotional-11-09")
    assert saved["status"] == "reviewed" and saved["meta"]["reviewedBy"] == "Sam"
    assert client.post("/api/devotional/devotional-11-09/edit",
                       json={"reviewer": "Sam", "fields": {"theme": "x"}}).status_code == 400


def test_review_flag_and_regenerate(review, tmp_path):
    client, devos = review
    assert client.post("/api/devotional/devotional-11-09/flag", json={"reviewer": "Sam"}).status_code == 400
    client.post("/api/devotional/devotional-11-09/regenerate", json={"reviewer": "Sam", "note": "Warmer"})
    assert devos.load("devotional-11-09")["status"] == "flagged"
    assert json.loads((tmp_path / "regen.jsonl").read_text().splitlines()[0])["kind"] == "devotional"


# ---- Calendar redraft -------------------------------------------------------------------------------

def test_redraft_series_replaces_only_that_series(tmp_path):
    path = write_calendar(tmp_path)
    days = cal.load(path, KJV)
    first = next(d for d in days if d.series == "Gratitude" and d.series_day == 1)
    n = first.series_length
    new = [{"theme": f"Thanks {i}", "anchor": f"PSA.{100 + i}.1-PSA.{100 + i}.2"} for i in range(n)]
    new[0]["anchor"] = "PSA.145.1-PSA.145.3"
    drafter = FakeDrafter({"days": new})
    changed = calendar_ai.redraft_series(first.date, drafter, title="Thankful", path=path, verses=KJV)
    assert len(changed) == n and changed[1].theme == "Thanks 1" and changed[0].series == "Thankful"
    after = cal.load(path, KJV)
    assert sum(1 for d in after if d.series == "Thankful") == n and len(after) == 366
    assert [d for d in after if d.series == "Love one another"] == [d for d in days if d.series == "Love one another"]


def test_redraft_with_bad_anchor_leaves_calendar_untouched(tmp_path):
    path = write_calendar(tmp_path)
    before = path.read_text()
    first = next(d for d in cal.load(path) if d.series == "Gratitude" and d.series_day == 1)
    bad = [{"theme": "x", "anchor": "PSA.200.1-PSA.200.2"}] * first.series_length
    with pytest.raises(cal.CalendarError):
        calendar_ai.redraft_series(first.date, FakeDrafter({"days": bad}), path=path, verses=KJV)
    assert path.read_text() == before and not path.with_suffix(".draft.csv").exists()


def test_standalone_words_from_the_passage_itself_are_allowed():
    passage = "Therefore don't be anxious for tomorrow, for tomorrow will be anxious for itself."
    doc = {"reflect": "Jesus doesn't promise that tomorrow will be easy.", "title": "Enough"}
    assert dchecks.check_standalone(doc, passage) == []
    assert dchecks.check_standalone({"reflect": "As we saw yesterday, he cares.", "title": "x"}, passage)[0]["check"] == "standalone"
    assert dchecks.check_standalone(doc) == []  # devotionals-5: everyday "tomorrow" in a sentence passes anyway
    # A still-blocked pattern is allowed when the day's own passage uses it.
    blocked = {"reflect": "Yesterday's bread was enough.", "title": "x"}
    assert dchecks.check_standalone(blocked)[0]["check"] == "standalone"
    assert dchecks.check_standalone(blocked, "He gave them yesterday's bread and today's.") == []


def test_devotionals_are_drafted_by_haiku_at_medium_effort(monkeypatch):
    """Decision 26: every devotional call (draft, redraft-series) uses Haiku 5.5 at effort medium; plans keep theirs."""
    from pipeline.plans import ai
    seen = {}

    class Recorder:
        def __init__(self, **kwargs):
            seen.update(kwargs)

    monkeypatch.setattr(gen, "AnthropicDrafter", Recorder)
    gen.default_drafter()
    assert seen == {"model": "claude-haiku-5-5", "effort": "medium", "max_retries": 6} and gen.MAX_TOKENS == 32000
    assert ai.MODEL == "claude-opus-5-5"  # plans unchanged


def test_drafter_sends_the_effort_it_was_given():
    from types import SimpleNamespace
    from pipeline.plans import ai
    sent = {}

    class Stream:
        def __enter__(self):
            return self

        def __exit__(self, *a):
            return False

        def get_final_message(self):
            return SimpleNamespace(stop_reason="end_turn", model="claude-haiku-5-5",
                                   content=[SimpleNamespace(type="text", text="{}")])

    client = SimpleNamespace(beta=SimpleNamespace(messages=SimpleNamespace(
        stream=lambda **kw: (sent.update(kw), Stream())[1])))
    draft = ai.AnthropicDrafter(client=client, model="claude-haiku-5-5", effort="medium").draft("s", "p", {}, 32000)
    assert sent["model"] == "claude-haiku-5-5" and sent["output_config"]["effort"] == "medium"
    assert sent["max_tokens"] == 32000 and draft.model == "claude-haiku-5-5"


# ---- Parallel drafting and checking (--workers) --------------------------------------------------------

def many_days(day, n=20):
    import dataclasses
    return [dataclasses.replace(day, date=f"11-{i:02d}") for i in range(1, n + 1)]


class SlowDrafter:
    """Thread-safe fake: random delay so answers come back out of order; fails or interrupts on chosen days."""

    def __init__(self, fail=(), refuse=(), interrupt_after=None, concurrent=False):
        import threading
        self.fail, self.refuse, self.interrupt_after = set(fail), set(refuse), interrupt_after
        self.lock, self.calls, self.threads = threading.Lock(), 0, set()
        # concurrent: the first two calls wait for each other, so passing proves two ran at the same time.
        self.barrier = threading.Barrier(2, timeout=10) if concurrent else None

    def draft(self, system, prompt, schema, max_tokens):
        import random
        import threading
        import time
        from pipeline.plans.ai import DraftFailed
        time.sleep(random.uniform(0, 0.01))
        with self.lock:
            self.calls += 1
            self.threads.add(threading.get_ident())
            n = self.calls
        if self.barrier is not None and n <= 2:
            self.barrier.wait()
        if self.interrupt_after is not None and n > self.interrupt_after:
            raise KeyboardInterrupt
        marker = next((d for d in self.fail | self.refuse if f"[{d}]" in prompt), None)
        if marker in self.refuse:
            raise DraftRefused("no")
        if marker in self.fail:
            raise DraftFailed("cut off")
        return Draft(copy.deepcopy(GOOD), "claude-haiku-5-5")


def tagged(monkeypatch):
    """Put each day's date into its request so the fake drafter can tell days apart."""
    real = gen.build_request
    monkeypatch.setattr(gen, "build_request", lambda d, *a, **k: f"[{d.date}] " + real(d, *a, **k))


def saved(store):
    out = {}
    for i in store.ids():
        d = store.load(i)
        d["meta"].pop("generatedAt", None)
        out[i] = d
    return out


def test_parallel_drafting_matches_sequential(tmp_path, day, dbible, monkeypatch):
    tagged(monkeypatch)
    days = many_days(day)
    one, eight = DevotionalStore(tmp_path / "one"), DevotionalStore(tmp_path / "eight")
    kw = dict(bible_db=dbible.bible_db, source_db=dbible.source_db)
    lines_1 = list(gen.draft_days(days, SlowDrafter(), one, workers=1, **kw))
    drafter = SlowDrafter(concurrent=True)
    lines_8 = list(gen.draft_days(days, drafter, eight, workers=8, **kw))
    assert sorted(lines_1) == sorted(lines_8) and len(lines_8) == 20
    assert saved(one) == saved(eight) and len(saved(eight)) == 20
    assert len(drafter.threads) > 1


def test_a_failed_or_refused_day_does_not_stop_the_run(tmp_path, day, dbible, monkeypatch):
    tagged(monkeypatch)
    store = DevotionalStore(tmp_path / "d")
    lines = list(gen.draft_days(many_days(day), SlowDrafter(fail={"11-04"}, refuse={"11-09"}), store,
                                bible_db=dbible.bible_db, source_db=dbible.source_db, workers=8))
    assert "11-04: failed (cut off); safe to retry" in lines
    assert "11-09: refused (no); needs a person to write it" in lines
    assert len(store.ids()) == 18 and not store.exists("devotional-11-04")


def test_interrupted_drafting_keeps_saved_days_and_a_rerun_finishes(tmp_path, day, dbible, monkeypatch):
    tagged(monkeypatch)
    store = DevotionalStore(tmp_path / "d")
    kw = dict(bible_db=dbible.bible_db, source_db=dbible.source_db)
    with pytest.raises(KeyboardInterrupt):
        list(gen.draft_days(many_days(day), SlowDrafter(interrupt_after=7), store, workers=2, **kw))
    done = set(store.ids())
    assert 0 < len(done) <= 7
    rest = SlowDrafter()
    lines = list(gen.draft_days(many_days(day), rest, store, workers=8, **kw))
    assert rest.calls == 20 - len(done) and len(store.ids()) == 20
    assert sum("skipped (exists)" in line for line in lines) == len(done)


def test_parallel_checking_matches_sequential(tmp_path, day, dbible):
    import threading
    days = many_days(day)
    stores = []
    for name in ("one", "eight"):
        store = DevotionalStore(tmp_path / name)
        for d in days:
            store.save(gen.from_draft(d, Draft(copy.deepcopy(GOOD), "claude-haiku-5-5"), gen.Prompt.load()))
        stores.append(store)
    bad = stores[1].load("devotional-11-05")  # a code-check failure: never sent to the reviewer
    bad["reflect"] = "Too short."
    stores[1].save(bad)
    stores[0].save(dict(stores[0].load("devotional-11-05"), reflect="Too short."))

    class Judge:
        def __init__(self, unavailable=(), concurrent=False):
            self.unavailable, self.seen, self.threads, self.lock = set(unavailable), [], set(), threading.Lock()
            self.barrier = threading.Barrier(2, timeout=10) if concurrent else None

        def judge(self, doc, text, subject=None):
            import random
            import time
            time.sleep(random.uniform(0.001, 0.01))  # so the work spreads over several threads
            with self.lock:
                self.seen.append(doc["id"])
                self.threads.add(threading.get_ident())
                n = len(self.seen)
            if self.barrier is not None and n <= 2:
                self.barrier.wait()  # two reviews in flight at once, or this times out
            if doc["id"] in self.unavailable:
                raise RubricUnavailable("529")
            note = [{"check": "rubric", "message": "tone 2/3"}] if doc["id"].endswith("7") else []
            return RubricResult({"model": "h"}, note)

    s1 = dchecks.run(stores[0], days, dbible, Judge(), workers=1)
    judge = Judge(unavailable={"devotional-11-12"}, concurrent=True)
    s8 = dchecks.run(stores[1], days, dbible, judge, workers=8)
    strip = lambda d: {k: v for k, v in d.items() if k != "checks"} | {
        "reasons": d.get("checks", {}).get("reasons"), "status": d["status"]}
    for i in stores[0].ids():
        if i != "devotional-11-12":
            assert strip(stores[0].load(i)) == strip(stores[1].load(i)), i
    assert stores[1].load("devotional-11-12")["status"] == "generated"   # left for the next run
    assert s8.rubric_unavailable == ["devotional-11-12: 529"] and s8.checked == 19
    assert s1.statuses["flagged"] == s8.statuses["flagged"] == 3  # 11-05 (length), 11-07 and 11-17 (rubric)
    assert "devotional-11-05" not in judge.seen and len(judge.threads) > 1


# ---- applyIt action types and repeated openings ---------------------------------------------------------

def test_apply_it_action_rotates_and_never_repeats_on_consecutive_days():
    import dataclasses
    days = cal.load(verses=KJV)
    actions = [gen.apply_it_action(d) for d in days]
    assert set(actions) == set(gen.APPLY_IT_ACTIONS) and len(gen.APPLY_IT_ACTIONS) == 8
    assert all(a != b for a, b in zip(actions, actions[1:]))          # 01-01 .. 12-31 in a leap year
    assert actions[-1] != actions[0]                                   # 12-31 -> 01-01
    by_date = {d.date: gen.apply_it_action(d) for d in days}
    assert by_date["02-28"] != by_date["03-01"]                        # non-leap years skip 02-29


def test_request_names_the_action_and_the_prompt_avoids_opening_with_today(day, dbible):
    request = gen.build_request(day, gen.Prompt.load(), dbible.bible_db, dbible.source_db)
    assert f"of this kind: {gen.apply_it_action(day)}." in request and "{{" not in request
    assert 'Don\'t begin it with the word "Today"' in request


def test_repeated_openings_are_reported_per_month_and_section():
    def dv(date, apply_it, title="A title here"):
        return {"date": date, "title": title, "applyIt": apply_it}
    devotionals = [dv("01-01", "Today, take a breath."), dv("01-02", "Today take a walk."),
                   dv("01-03", "today, take a minute."), dv("01-04", "Pause before lunch."),
                   dv("02-01", "Today, take a breath."), dv("02-02", "Today, take a breath.")]
    found = dchecks.repeated_openings(devotionals)
    by_key = {(f["month"], f["section"], f["opening"]): f for f in found}
    assert by_key[("01", "applyIt", "today take a")]["count"] == 3                 # more than twice in January
    assert by_key[("01", "applyIt", "today take a")]["dates"] == ["01-01", "01-02", "01-03"]
    assert ("02", "applyIt", "today take a") not in by_key                          # twice in February: fine
    assert by_key[("01", "title", "a title here")]["count"] == 4 and len(found) == 2   # titles too (Feb: twice, fine)
    assert dchecks.opening("Two words") is None


def test_repeated_openings_never_change_a_status(tmp_path, day, dbible):
    import dataclasses
    store = DevotionalStore(tmp_path / "d")
    for i in range(1, 5):
        d = gen.from_draft(dataclasses.replace(day, date=f"11-{i:02d}"), Draft(copy.deepcopy(GOOD), "h"),
                           gen.Prompt.load())
        store.save(d)
    summary = dchecks.run(store, many_days(day, 4), dbible, None)
    assert summary.statuses == {"auto_checked": 4}                                  # same openings, still passing
    assert dchecks.repeated_openings([store.load(i) for i in store.ids()])          # but reported


# ---- devotionals-5: fewer false positives (real January sentences) ---------------------------------------

REAL_ALLOWED = [
    ("context", "Eli tells him what to say next time."),                                                  # 01-11
    ("applyIt", "Next time you feel too small, too far behind, or too busy to look up, pause for one slow breath "
                "and picture Jesus stopping to say your name."),                                         # 01-17
    ("applyIt", "Next time someone's words or actions stir up anger, pause and quietly pray for that person by "
                "name, even just one short sentence."),                                                   # 01-27
    ("reflect", "We lie awake running through bills, health, work, and what might go wrong next week."),  # 01-31
    ("applyIt", "If it helps, say the sentence out loud, letting it be a gift rather than something you must earn."),  # 01-21
]


@pytest.mark.parametrize("section, sentence", REAL_ALLOWED)
def test_real_january_sentences_now_pass(drafted, day, dbible, section, sentence):
    lengths = {"context": 80, "reflect": 150, "applyIt": 50}
    drafted[section] = words(lengths[section], sentence)
    outcome = check(drafted, day, dbible)
    assert outcome.status == "auto_checked", outcome.reasons


@pytest.mark.parametrize("section, sentence, check_name", [
    ("applyIt", "Tomorrow's devotional picks this up again.", "standalone"),
    ("reflect", "Yesterday's reading showed the same promise.", "standalone"),
    ("reflect", "Last time we looked at the shepherd.", "standalone"),
    ("applyIt", "Next time we will see how he answers.", "standalone"),
    ("context", "This week's readings follow one letter.", "standalone"),
    ("applyIt", "Come back tomorrow for the next step.", "standalone"),
    ("reflect", "In this season we wait for his coming.", "standalone"),
    ("reflect", "As we saw, he keeps his word.", "standalone"),
    ("applyIt", "You must forgive them before you sleep.", "banned_phrase"),
    ("applyIt", "Breathe slowly. You need to let it go now.", "banned_phrase"),
    ("applyIt", "God will not fail; you must pray more.", "banned_phrase"),
    ("applyIt", "Pause after the word count and let the sentence rest for a moment.", "pipeline_wording"),  # 01-05
])
def test_real_violations_still_block(drafted, day, dbible, section, sentence, check_name):
    lengths = {"context": 80, "reflect": 150, "applyIt": 50}
    drafted[section] = words(lengths[section], sentence)
    assert check_name in {r["check"] for r in check(drafted, day, dbible).reasons}


def test_word_count_before_a_quoted_word_is_not_the_prompt_term(drafted, day, dbible):
    drafted["reflect"] = words(150, "Psalm 90 asks God to teach us to number our days; the word count “number” here.")
    assert "pipeline_wording" not in {r["check"] for r in check(drafted, day, dbible).reasons}


def test_every_devotional_check_name_is_allowed_by_the_schema():
    """A check name missing from schema/devotional.json makes every devotional that fails it invalid (and unsavable)."""
    import re
    schema = json.loads((gen.PIPELINE / "schema" / "devotional.json").read_text())
    allowed = set(schema["properties"]["checks"]["properties"]["reasons"]["items"]["properties"]["check"]["enum"])
    source = open(dchecks.__file__).read()
    written = set(dchecks.BLOCKING) | set(re.findall(r'(?:reason|_scan)\([^)]*?"([a-z_]+)"', source))
    assert written <= allowed, sorted(written - allowed)


# ---- Hand fixes for devotionals (fixes apply, devotionalFixes/1) ------------------------------------------

def test_devotional_hand_fixes_apply_only_matching_text_and_mark_the_edit(tmp_path, drafted, day, dbible):
    from pipeline import fixes as fx
    store = DevotionalStore(tmp_path / "d")
    drafted["checks"] = {"checkedAt": "2026-11-22T00:00:00+00:00", "checkerVersion": "devotionals-5",
                         "reasons": [{"check": "rubric", "message": "factual risk 2/3"}]}
    drafted["status"] = "flagged"
    store.save(drafted)
    new_apply = words(50, "Read it twice, pausing after the word “count.”")
    path = tmp_path / "fixes.json"
    path.write_text(json.dumps({"format": fx.DEVOTIONAL_FORMAT, "devotionals": {drafted["id"]: {
        "applyIt": {"before": drafted["applyIt"], "after": new_apply},
        "reflect": {"before": "not the current text", "after": "x"}}}}))
    [result] = fx.apply_devotional_fixes(path, store, dbible, [day], by="claude-sonnet-5-5")
    assert result.applied == ["applyIt"] and result.skipped == [("reflect", "current text differs from 'before'")]
    saved = store.load(drafted["id"])
    assert saved["applyIt"] == new_apply and saved["reflect"] == drafted["reflect"]
    assert saved["meta"]["model"] == drafted["meta"]["model"] and saved["meta"]["editedBy"] == "claude-sonnet-5-5"
    assert saved["meta"]["editNote"] == "Hand-edited from fixes.json: applyIt"
    assert saved["contentVersion"] == drafted["contentVersion"] + ".1"
    assert saved["status"] == "auto_checked" and saved["checks"]["reasons"] == []   # rubric reason not kept
    assert not list(validator().iter_errors(saved))
    again = fx.apply_devotional_fixes(path, store, dbible, [day], by="x")[0]
    assert ("applyIt", "already applied") in again.skipped


def test_devotional_hand_fixes_reject_other_fields(tmp_path):
    from pipeline import fixes as fx
    path = tmp_path / "fixes.json"
    path.write_text(json.dumps({"format": fx.DEVOTIONAL_FORMAT, "devotionals": {
        "devotional-01-01": {"theme": {"before": "", "after": ""}}}}))
    with pytest.raises(fx.FixesError):
        fx.load_devotional_fixes(path)
