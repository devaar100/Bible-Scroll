import copy

import pytest

from pipeline.studies import PROSE_FIELDS, validator

VALIDATOR = validator()

VALID = {
    "id": "JHN.3.16",
    "schemaVersion": 2,
    "contentVersion": "0123456789ab",
    "originalLanguage": {
        "language": "greek",
        "textBasis": "critical",
        "words": [{"surface": "Οὕτως", "lemma": "οὕτω", "transliteration": "Houtōs", "strongs": "G3779",
                   "gloss": "so", "morphology": "ADV"}],
    },
    "crossReferences": [
        {"ref": "ROM.5.8", "source": "OpenBible"},
        {"ref": "1JN.4.9-1JN.4.10", "source": "TSK", "note": "God so loved"},
    ],
    **{f: "" for f in PROSE_FIELDS},
    "citations": [{"source": "STEP Bible", "licence": "CC BY 4.0", "url": "https://stepbible.github.io/STEPBible-Data/"}],
    "status": "generated",
    "meta": {"model": None, "generatedAt": "2026-11-18T03:38:57+00:00"},
}


def errors(study):
    return [e.message for e in VALIDATOR.iter_errors(study)]


def test_valid_skeleton():
    assert errors(VALID) == []


def mutated(path, value):
    study = copy.deepcopy(VALID)
    target = study
    for key in path[:-1]:
        target = target[key]
    if value is DELETE:
        del target[path[-1]]
    else:
        target[path[-1]] = value
    return study


DELETE = object()


@pytest.mark.parametrize("path, value", [
    (["id"], "John.3.16"),                      # not a USFM code
    (["id"], "JHN.3.0"),                        # verse 0 (Psalm title) isn't a study
    (["schemaVersion"], 1),
    (["status"], "draft"),
    (["crossReferences", 0, "source"], "Nave"),
    (["crossReferences", 0, "ref"], "Romans 5:8"),
    (["originalLanguage", "words", 0, "strongs"], "7225"),
    (["originalLanguage", "words"], []),
    (["originalLanguage", "textBasis"], "NA28"),
    (["citations"], []),
    (["citations", 0, "url"], "http://insecure.example"),
    (["meta", "generatedAt"], "yesterday"),
    (["meaning"], DELETE),
    (["extra"], "not allowed"),
])
def test_invalid_studies_are_rejected(path, value):
    assert errors(mutated(path, value)), f"{path}={value!r} should be invalid"


def test_more_than_eight_cross_references_rejected():
    study = copy.deepcopy(VALID)
    study["crossReferences"] = [{"ref": f"ROM.8.{v}", "source": "OpenBible"} for v in range(1, 10)]
    assert errors(study)


def test_checked_or_reviewed_needs_prose_and_model():
    study = mutated(["status"], "auto_checked")
    assert errors(study)  # empty prose, no model
    for f in PROSE_FIELDS:
        study[f] = "Some text."
    study["meta"]["model"] = "claude-opus-5-5"
    assert errors(study) == []
    study["status"] = "reviewed"
    assert errors(study)  # reviewer missing
    study["meta"].update(reviewedBy="A reviewer", reviewedAt="2026-12-01T10:00:00+00:00")
    assert errors(study) == []
