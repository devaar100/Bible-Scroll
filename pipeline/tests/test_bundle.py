import copy
import json

import pytest

from conftest import put
from pipeline import publish as p
from pipeline.bundle import bundle


@pytest.fixture
def site(store, written, tmp_path):
    for study_id in ("JHN.3.16", "ROM.5.8", "PSA.23.1"):
        put(store, dict(copy.deepcopy(written), id=study_id, status="reviewed"))
    release = p.build_release(store, "https://x.web.app", discover_ids=[])
    site = tmp_path / "site"
    p.write_site(release, site)
    return site, release


def test_copies_gospels_and_psalms_only(site, tmp_path):
    site_dir, release = site
    assets = tmp_path / "assets"
    (assets).mkdir()
    (assets / "OLD.json.gz").write_bytes(b"stale")
    result = bundle(site_dir, assets)
    assert result.copied == ["JHN", "PSA"] and result.missing == ["MAT", "MRK", "LUK"]
    assert result.removed == ["OLD.json.gz"]
    assert sorted(f.name for f in assets.iterdir()) == ["JHN.json.gzip", "PSA.json.gzip", "manifest.json"]
    bundled = json.loads((assets / "manifest.json").read_text())
    assert bundled["release"] == release.manifest["release"]
    assert [(b["book"], b["url"]) for b in bundled["books"]] == [("JHN", "JHN.json.gzip"), ("PSA", "PSA.json.gzip")]
    live_jhn = next(b for b in release.manifest["books"] if b["book"] == "JHN")
    assert (assets / "JHN.json.gzip").read_bytes() == release.files[p._site_path(live_jhn["url"])]


def test_refuses_corrupt_release(site, tmp_path):
    site_dir, release = site
    entry = next(b for b in release.manifest["books"] if b["book"] == "JHN")
    (site_dir / p._site_path(entry["url"])).write_bytes(b"corrupt")
    with pytest.raises(ValueError):
        bundle(site_dir, tmp_path / "assets")


def test_needs_a_built_release(tmp_path):
    with pytest.raises(FileNotFoundError):
        bundle(tmp_path / "nothing", tmp_path / "assets")
