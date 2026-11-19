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
    (lambda d: d.update(applyIt=words(50, "Before tomorrow,")), "standalone"),
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
    release = p.build_release(StudyStore(tmp_path), "https://x.web.app", discover_ids=[], devotionals=store)
    months = release.manifest["devotionals"]["months"]
    assert [m["month"] for m in months] == ["02", "11"]
    feb = json.loads(gzip.decompress(release.files[p._site_path(months[0]["url"])]))
    assert [d["date"] for d in feb["devotionals"]] == ["02-29"]  # flagged 02-28 left out
    nov = json.loads(gzip.decompress(release.files[p._site_path(months[1]["url"])]))
    text = json.dumps(nov)
    assert nov["devotionals"][0]["reviewed"] and "Sam Private" not in text and "checks" not in text
    assert months[1]["url"].startswith("https://x.web.app/devotionals/v") and months[1]["reviewedCount"] == 1
    assert release.devotionals_left_out == {"02": {"flagged": 1}, "03": {"generated": 1}}
    again = p.build_release(StudyStore(tmp_path), "https://x.web.app", discover_ids=[], devotionals=store)
    assert again.files == release.files  # byte-deterministic


def test_diff_and_site_keep_previous_month_files(tmp_path, drafted):
    store = publishable_set(tmp_path, drafted)
    first = p.build_release(StudyStore(tmp_path), "", discover_ids=[], devotionals=store)
    site = tmp_path / "site"
    p.write_site(first, site)
    d = p.diff(first, first.manifest, [])
    assert not d.any
    changed = store.load("devotional-11-09")
    store.save(dict(changed, title="New title"))
    second = p.build_release(StudyStore(tmp_path), "", discover_ids=[], devotionals=store)
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
    release = p.build_release(StudyStore(tmp_path), "", discover_ids=[], devotionals=store)
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
    assert dchecks.check_standalone(doc)[0]["check"] == "standalone"  # without the passage, still caught
