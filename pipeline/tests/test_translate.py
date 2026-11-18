"""translate: engine (free checks, drift, refusals, outages), storage in plans/devotionals, and what ships. No API calls."""
import json
from types import SimpleNamespace

import anthropic
import httpx
import pytest

from pipeline.translate import content as c
from pipeline.translate.engine import (DRIFT_MODEL, FALLBACK_BETA, TRANSLATE_MODEL, AnthropicDriftChecker,
                                       AnthropicTranslator, TranslationUnavailable, Unit, free_checks, run_unit)
from pipeline.usage import Usage


# ---- Fakes ----

class FakeTranslator:
    model = TRANSLATE_MODEL

    def __init__(self, texts=None, refuse=False, error=None, retry_texts=None):
        self.texts, self.refuse, self.error, self.calls = texts or {}, refuse, error, []
        self.retry_texts, self.feedback = retry_texts, []
        self.usage = Usage()

    def translate(self, unit, lang):
        self.calls.append(dict(unit.fields))
        self.feedback.append(unit.feedback)
        if self.error:
            raise self.error
        if self.refuse:
            return None, TRANSLATE_MODEL
        texts = self.retry_texts if unit.feedback and self.retry_texts is not None else self.texts
        return {k: texts.get(k, f"ES {v}") for k, v in unit.fields.items()}, TRANSLATE_MODEL


class FakeChecker:
    model = DRIFT_MODEL

    def __init__(self, issues=(), refuse=False, then=None):
        """[issues] on the first call; [then] (default: the same) on later calls."""
        self.issues, self.then, self.refuse, self.calls = list(issues), then, refuse, 0
        self.usage = Usage()

    def check(self, unit, texts, lang):
        self.calls += 1
        issues = self.issues if self.calls == 1 or self.then is None else self.then
        return (None, DRIFT_MODEL) if self.refuse else ({"issues": list(issues)}, DRIFT_MODEL)


def unit(**fields):
    return Unit("u1", fields, {k: "somewhere" for k in fields}, {k: 200 for k in fields})


# ---- Free checks ----

def test_free_checks_catch_missing_keys_length_and_numbers():
    u = Unit("u", {"title": "John in 21 days", "hook": "Read John 3:16"}, {"title": "t", "hook": "h"}, {"title": 10, "hook": 200})
    reasons = free_checks(u, {"title": "Juan en 12 días, un plan largo"})
    checks = {(r["check"], r.get("key")) for r in reasons}
    assert ("keys", None) in checks          # hook missing
    assert ("length", "title") in checks     # over 10 characters
    assert ("numbers", "title") in checks    # 21 became 12


def test_book_names_may_change_but_reference_numbers_must_not():
    u = unit(hook="Start with John 3:16-18")
    assert free_checks(u, {"hook": "Empieza con Juan 3:16-18"}) == []
    assert free_checks(u, {"hook": "Empieza con Juan 3:17-18"})[0]["check"] == "numbers"


def test_quoted_scripture_must_be_the_target_bible_text():
    rv = "Porque de tal manera amó Dios al mundo, que ha dado á su Hijo unigénito"
    u = Unit("d", {"reflect": "x"}, {"reflect": "r"}, {"reflect": 500}, scripture=rv)
    ok = "Juan escribe: “Porque de tal manera amó Dios al mundo”. Es amor."
    made_up = "Juan escribe: “Porque Dios amó tanto al mundo que dio a su Hijo”. Es amor."
    assert free_checks(u, {"reflect": ok}) == []
    assert free_checks(u, {"reflect": made_up})[0]["check"] == "scripture"


# ---- run_unit ----

def test_clean_translation_is_auto_checked_by_a_second_model():
    checker = FakeChecker()
    out = run_unit(unit(title="Hope"), "es", FakeTranslator({"title": "Esperanza"}), checker)
    assert out.status == "auto_checked" and out.texts == {"title": "Esperanza"}
    assert out.model == TRANSLATE_MODEL and out.drift_model == DRIFT_MODEL and TRANSLATE_MODEL != DRIFT_MODEL
    assert checker.calls == 1


@pytest.mark.parametrize("category", ["meaning_changed", "claim_added", "claim_dropped", "reference_changed",
                                      "name_changed", "number_changed", "theology_term"])
def test_blocking_categories_flag_after_one_retry(category):
    translator = FakeTranslator()
    out = run_unit(unit(t="May help"), "es", translator, FakeChecker([{"key": "t", "category": category, "problem": "may -> will"}]))
    assert out.status == "flagged" and out.retried and len(translator.calls) == 2
    assert translator.feedback == [None, ["t: may -> will"]]  # the retry is told what was wrong
    assert out.reasons[0]["category"] == category


@pytest.mark.parametrize("category", ["tone", "nuance", "quotation", "style", "other"])
def test_non_blocking_notes_are_logged_not_blocked(category):
    translator = FakeTranslator()
    out = run_unit(unit(t="May help"), "es", translator, FakeChecker([{"key": "t", "category": category, "problem": "x"}]))
    assert out.status == "auto_checked" and not out.retried and len(translator.calls) == 1
    assert out.minor == [{"check": "drift", "key": "t", "category": category, "message": "x"}]


def test_retry_that_fixes_the_problem_ships():
    checker = FakeChecker([{"key": "t", "category": "theology_term", "problem": "grace became favour"}], then=[])
    out = run_unit(unit(t="Saved by grace"), "es", FakeTranslator({"t": "Salvos por favor"}, retry_texts={"t": "Salvos por gracia"}), checker)
    assert out.status == "auto_checked" and out.retried and out.texts == {"t": "Salvos por gracia"} and checker.calls == 2


def test_free_check_failures_also_get_the_retry():
    translator = FakeTranslator({"t": "Día tres"}, retry_texts={"t": "Día 3"})
    out = run_unit(unit(t="Day 3"), "es", translator, FakeChecker())
    assert out.status == "auto_checked" and out.retried and "Numbers differ" in translator.feedback[1][0]


def test_old_style_issues_still_read_by_severity():
    from pipeline.translate.engine import is_blocking
    assert is_blocking({"severity": "major"}) and not is_blocking({"severity": "minor"})


def test_prompt_hashes_are_recorded():
    import hashlib
    from pipeline.translate.engine import _prompt
    out = run_unit(unit(t="x"), "es", FakeTranslator(), FakeChecker())
    assert out.prompt_hash == hashlib.sha256(_prompt("translate", "es").encode()).hexdigest()
    assert out.drift_prompt_hash == hashlib.sha256(_prompt("drift", "es").encode()).hexdigest()


def test_refusals_flag_for_a_person():
    translator = FakeTranslator(refuse=True)
    out = run_unit(unit(t="x"), "es", translator, FakeChecker())
    assert out.status == "flagged" and out.texts == {} and out.reasons[0]["check"] == "refusal"
    assert len(translator.calls) == 1  # a refusal isn't retried
    out = run_unit(unit(t="x"), "es", FakeTranslator(), FakeChecker(refuse=True))
    assert out.status == "flagged" and out.reasons[0]["check"] == "drift"


def test_failing_free_checks_skip_the_drift_call():
    checker = FakeChecker()
    out = run_unit(unit(t="Day 3"), "es", FakeTranslator({"t": "Día tres"}), checker)
    assert out.status == "flagged" and out.retried and checker.calls == 0


def test_identical_english_reuses_the_approved_wording_without_a_request():
    translator = FakeTranslator()
    out = run_unit(unit(title="Mark", credits="By Bible Scroll"), "es", translator, FakeChecker(),
                   reuse={"By Bible Scroll": "Por Bible Scroll"})
    assert translator.calls == [{"title": "Mark"}]
    assert out.texts["credits"] == "Por Bible Scroll"


# ---- Anthropic clients (fake SDK) ----

def response(payload=None, stop_reason="end_turn", model=TRANSLATE_MODEL):
    content = [SimpleNamespace(type="text", text=payload)] if payload is not None else []
    return SimpleNamespace(content=content, stop_reason=stop_reason, stop_details=None, model=model,
                           usage=SimpleNamespace(input_tokens=100, output_tokens=50, cache_creation_input_tokens=0,
                                                 cache_read_input_tokens=0, cache_creation=None), _request_id="req")


class FakeMessages:
    def __init__(self, result):
        self.result, self.kwargs = result, None

    def create(self, **kwargs):
        self.kwargs = kwargs
        if isinstance(self.result, Exception):
            raise self.result
        return self.result


def client(result):
    messages = FakeMessages(result)
    return SimpleNamespace(beta=SimpleNamespace(messages=messages)), messages


def test_translator_request_uses_structured_output_and_refusal_fallback():
    fake, messages = client(response(json.dumps({"translations": [{"key": "title", "text": " Esperanza "}]})))
    texts, served = AnthropicTranslator(client=fake).translate(unit(title="Hope"), "es")
    assert texts == {"title": "Esperanza"} and served == TRANSLATE_MODEL
    k = messages.kwargs
    assert k["model"] == "claude-opus-5-5" and k["betas"] == [FALLBACK_BETA] and k["fallbacks"] == "default"
    assert k["output_config"]["format"]["type"] == "json_schema"
    assert "Spanish" in k["system"] and "Reina-Valera 1909" in k["system"] and "{" not in k["system"]
    assert "Hope" in k["messages"][0]["content"]


def test_drift_checker_is_a_different_model_and_records_who_answered():
    fake, messages = client(response(json.dumps({"issues": []}), model="claude-sonnet-5"))
    data, served = AnthropicDriftChecker(client=fake).check(unit(t="x"), {"t": "y"}, "es")
    assert messages.kwargs["model"] == "claude-sonnet-5-5" and data["issues"] == []
    assert "claim_dropped" in json.dumps(messages.kwargs["output_config"]["format"]["schema"])
    assert served == "claude-sonnet-5"  # a fallback model answered: recorded, not hidden


def test_refusal_returns_none():
    fake, _ = client(response(stop_reason="refusal"))
    assert AnthropicTranslator(client=fake).translate(unit(t="x"), "es")[0] is None


@pytest.mark.parametrize("bad", [response("not json"), response(json.dumps({"translations": "no"})), response(None),
                                 response("{}", stop_reason="max_tokens")])
def test_unusable_responses_leave_things_unchanged(bad):
    fake, _ = client(bad)
    with pytest.raises(TranslationUnavailable):
        AnthropicTranslator(client=fake).translate(unit(t="x"), "es")


def test_rate_limit_is_retryable():
    request = httpx.Request("POST", "https://api.anthropic.com/v1/messages")
    fake, _ = client(anthropic.RateLimitError("slow", response=httpx.Response(429, request=request), body=None))
    with pytest.raises(TranslationUnavailable):
        AnthropicTranslator(client=fake).translate(unit(t="x"), "es")


# ---- Plans: storage, staleness, what ships ----

@pytest.fixture
def plan():
    return {
        "id": "john-3-days", "schemaVersion": 1, "contentVersion": "abcdef123456", "title": "John in 3 days",
        "shortDescription": "Meet Jesus in John's Gospel", "description": "Three days in John.",
        "category": "gospels-and-acts", "difficulty": "easy", "estimatedMinutesPerDay": 5, "status": "auto_checked",
        "origin": "structural", "credits": "Reading plan by Bible Scroll.", "meta": {"generatedAt": "2026-11-18T00:00:00+00:00"},
        "days": [{"dayNumber": 1, "title": "The Word", "intro": "In the beginning.",
                  "passages": [{"book": "JHN", "startChapter": 1, "startVerse": 1, "endChapter": 1, "endVerse": 18}]},
                 {"dayNumber": 2, "passages": [{"book": "JHN", "startChapter": 2, "startVerse": 1, "endChapter": 2, "endVerse": 12}]}],
    }


def translated(plan, status="auto_checked"):
    out = run_unit(c.plan_unit(plan), "es", FakeTranslator({"title": "Juan en 3 días", "day.1.title": "La Palabra"}), FakeChecker())
    out.status = status
    assert c.apply_plan(plan, "es", out)
    return plan


def test_plan_unit_covers_every_text_field(plan):
    assert set(c.plan_unit(plan).fields) == {"title", "shortDescription", "description", "credits", "day.1.title", "day.1.intro"}


def test_stored_translation_is_schema_valid_and_ships_without_review_data(plan):
    from pipeline.plans import build as pb
    translated(plan)
    assert list(pb.schema_validator().iter_errors(plan)) == []
    shipped = pb.public_plan(plan)
    assert shipped["i18n"]["es"]["title"] == "Juan en 3 días"
    assert shipped["i18n"]["es"]["days"] == [{"dayNumber": 1, "title": "La Palabra", "intro": "ES In the beginning."}]
    assert not {"status", "meta", "checks", "sourceVersion"} & set(shipped["i18n"]["es"])


def test_flagged_or_stale_translations_dont_ship(plan):
    from pipeline.plans import build as pb
    assert "i18n" not in pb.public_plan(translated(dict(plan), "flagged"))
    stale = translated(plan)
    stale["contentVersion"] = "000000000000"
    assert "i18n" not in pb.public_plan(stale)


def test_rebuild_keeps_translations_and_marks_changed_english_stale(plan):
    from pipeline.plans.build import content_version, finish
    translated(plan)
    plan["contentVersion"] = content_version(plan)
    plan["i18n"]["es"]["sourceVersion"] = plan["contentVersion"]
    same = finish({k: v for k, v in plan.items() if k not in ("i18n", "contentVersion")}, index=None, existing=plan)
    assert same["i18n"]["es"]["status"] == "auto_checked"
    changed = {k: v for k, v in plan.items() if k not in ("i18n", "contentVersion")} | {"title": "John in three days"}
    rebuilt = finish(changed, index=None, existing=plan)
    assert rebuilt["i18n"]["es"]["status"] == "stale" and not c.locale_publishable(rebuilt, "es")


def test_index_carries_translated_titles_and_categories(plan, tmp_path):
    from pipeline.plans import build as pb
    from pipeline.plans.catalogue import CATEGORIES
    translated(plan)
    store = {"es": {"status": "auto_checked", "sourceVersion": c.categories_version(CATEGORIES),
                    "titles": {"gospels-and-acts": "Evangelios y Hechos"}, "meta": {}, "checks": {}}}
    path = tmp_path / "categories.i18n.json"
    path.write_text(json.dumps(store))
    index = pb.build_index({plan["id"]: plan}, categories_path=path)
    entry = next(p for p in index["plans"] if p["id"] == plan["id"])
    assert entry["i18n"] == {"es": {"title": "Juan en 3 días", "shortDescription": "ES Meet Jesus in John's Gospel"}}
    gospels = next(cat for cat in index["categories"] if cat["id"] == "gospels-and-acts")
    assert gospels["i18n"] == {"es": {"title": "Evangelios y Hechos"}}
    store["es"]["sourceVersion"] = "old"
    path.write_text(json.dumps(store))
    assert "i18n" not in next(cat for cat in pb.build_index({}, categories_path=path)["categories"])


def test_which_translations_run_again(plan):
    assert c.needs_translation(plan, "es")
    translated(plan)
    assert not c.needs_translation(plan, "es")
    plan["i18n"]["es"]["status"] = "flagged"
    assert not c.needs_translation(plan, "es") and c.needs_translation(plan, "es", retry_flagged=True)
    plan["i18n"]["es"]["status"] = "reviewed"
    assert not c.needs_translation(plan, "es", retry_flagged=True) and c.needs_translation(plan, "es", force=True)


def test_approving_a_stale_translation_is_refused(plan):
    translated(plan)
    c.approve(plan, "es", "Ana")
    assert plan["i18n"]["es"]["status"] == "reviewed" and plan["i18n"]["es"]["meta"]["reviewedBy"] == "Ana"
    plan["contentVersion"] = "000000000000"
    with pytest.raises(ValueError):
        c.approve(plan, "es", "Ana")


# ---- Devotionals ----

def devotional():
    return {"id": "devotional-11-18", "contentVersion": "v1", "date": "11-18", "dayOfYear": 322, "title": "Light",
            "theme": "God's word guides", "series": {"title": "Lamp", "season": "Ordinary", "day": 1, "length": 5},
            "coverStyle": "dawn", "verses": [], "context": "c", "reflect": "r", "applyIt": "a", "prayer": "p",
            "status": "auto_checked", "meta": {"model": "claude-haiku-5-5"}}


def test_devotional_translation_ships_in_the_month_file():
    from pipeline.devotionals.publish import public_devotional
    d = devotional()
    out = run_unit(c.devotional_unit(d, "Lámpara es á mis pies tu palabra"), "es", FakeTranslator(), FakeChecker())
    assert set(c.devotional_unit(d, "").fields) == {"title", "theme", "seriesTitle", "context", "reflect", "applyIt", "prayer"}
    assert c.apply_devotional(d, "es", out)
    shipped = public_devotional(d)
    assert shipped["i18n"]["es"]["seriesTitle"] == "ES Lamp" and "status" not in shipped["i18n"]["es"]


def test_devotionals_wont_run_without_the_target_bible(monkeypatch, tmp_path):
    from pipeline.devotionals import store as ds
    from pipeline.translate import cli
    s = ds.DevotionalStore(tmp_path)
    s.save(devotional())
    monkeypatch.setattr(ds, "DEVOTIONALS_DIR", tmp_path)
    monkeypatch.setattr(ds.DevotionalStore.__init__, "__defaults__", (tmp_path,))
    translator = FakeTranslator()
    args = SimpleNamespace(lang="es", ids=[], force=False, retry_flagged=False, model=TRANSLATE_MODEL, drift_model=DRIFT_MODEL)
    lines = cli.translate_devotionals(args, translator, FakeChecker(), scripture=None)
    assert "Reina-Valera 1909" in lines[0] and translator.calls == []


def test_category_translations_file_isnt_loaded_as_a_plan(tmp_path, plan):
    from pipeline.plans import build as pb
    pb.write_json(tmp_path / f"{plan['id']}.json", plan)
    pb.write_json(tmp_path / "index.json", {})
    pb.write_json(tmp_path / pb.CATEGORIES_I18N.name, {"es": {}})
    assert list(pb.load_plans(tmp_path)) == [plan["id"]]


def test_stored_meta_has_models_hashes_and_minor_log(plan):
    out = run_unit(c.plan_unit(plan), "es", FakeTranslator(), FakeChecker([{"key": "title", "category": "tone", "problem": "warmer"}]))
    assert c.apply_plan(plan, "es", out)
    loc = plan["i18n"]["es"]
    assert loc["meta"]["model"] == TRANSLATE_MODEL and loc["meta"]["driftModel"] == DRIFT_MODEL
    assert len(loc["meta"]["promptHash"]) == 64 and len(loc["meta"]["driftPromptHash"]) == 64
    assert loc["checks"]["minor"][0]["category"] == "tone" and loc["checks"]["checkerVersion"] == "translate-2"
    from pipeline.plans import build as pb
    assert list(pb.schema_validator().iter_errors(plan)) == []


def test_nothing_is_spent_without_confirmation(monkeypatch):
    from pipeline.translate import cli
    monkeypatch.setattr("builtins.input", lambda _: (_ for _ in ()).throw(EOFError()))
    monkeypatch.setattr(cli, "estimate", lambda what, lang: "estimate")
    args = SimpleNamespace(lang="es", yes=False)
    assert cli.confirm(args, "plans") is False
    assert cli.confirm(SimpleNamespace(lang="es", yes=True), "plans") is True
