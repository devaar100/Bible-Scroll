"""
Package publishable studies into one gzip JSON file per book, write manifest.json + discover.json, show
what would change against the live site, and deploy to Firebase Hosting.

Layout (out/site/ = the Firebase public directory):
  manifest.json                         short cache; lists every book and devotionals month file
  discover.json                         short cache; curated Discover verses in order
  studies/v{contentVersion}/{BOOK}.json.gz   immutable; contentVersion = sha256(file)[:12]
  devotionals/v{contentVersion}/{MM}.json.gz immutable; one per month (devotionals/publish.py)
"""
from __future__ import annotations

import gzip
import hashlib
import io
import json
import os
import shutil
import subprocess
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path

from jsonschema import Draft202012Validator
from referencing import Registry, Resource

from .books import BOOKS, BY_USFM
from .offline import require_online
from .paths import OUT, PIPELINE
from .reports import open_reports
from .store import StudyStore, parse_id

PUBLISHABLE = ("auto_checked", "reviewed")
#: Studies written by Claude Code subagents in a test round (out/cc_test/roundN/) are test artifacts: publish and
#: bundle refuse a folder under cc_test and any study whose meta.source marks it, whatever its status.
CC_TEST_PART = "cc_test"
CC_TEST_SOURCE = "claude-code-subagent"


class TestArtifactError(ValueError):
    pass


def refuse_test_path(path: Path) -> None:
    if CC_TEST_PART in Path(path).resolve().parts:
        raise TestArtifactError(f"{path} is under {CC_TEST_PART}/: test-round studies are never published or bundled")


def refuse_test_study(study: dict) -> None:
    if (study.get("meta") or {}).get("source") == CC_TEST_SOURCE:
        raise TestArtifactError(f"{study.get('id')}: meta.source is {CC_TEST_SOURCE!r} (a test artifact); "
                                "it must never be published")
SITE = OUT / "site"
STATE = OUT / "publish_state.json"
CONFIG = PIPELINE / "publish.json"
DISCOVER_FILE = PIPELINE / "discover.txt"
SCHEMAS = PIPELINE / "schema"
FORMAT_VERSION = 1
SCHEMA_VERSION = 2
PROSE = ("meaning", "historicalContext", "lifeInBibleTimes", "theology", "didYouKnow", "applyIt")


# ---- Building ---------------------------------------------------------------------------------------

def canonical_gzip(data: object) -> bytes:
    """Same data -> same bytes: sorted keys, compact JSON, gzip with no timestamp or file name."""
    raw = json.dumps(data, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()
    buffer = io.BytesIO()
    with gzip.GzipFile(filename="", mode="wb", compresslevel=9, fileobj=buffer, mtime=0) as f:
        f.write(raw)
    return buffer.getvalue()


def canonical_json(data: object) -> bytes:
    return (json.dumps(data, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode()


def public_study(study: dict) -> dict:
    """What the app gets: no check reasons, no reviewer or editor names, no internal timestamps."""
    return {
        "id": study["id"],
        "contentVersion": study["contentVersion"],
        "originalLanguage": study["originalLanguage"],
        "crossReferences": study["crossReferences"],
        **{k: study[k] for k in PROSE},
        "citations": study["citations"],
        "reviewed": study["status"] == "reviewed",
        "model": study["meta"]["model"],
    }


def validators() -> tuple[Draft202012Validator, Draft202012Validator]:
    study = json.loads((SCHEMAS / "study.json").read_text())
    registry = Registry().with_resource(study["$id"], Resource.from_contents(study))
    book = Draft202012Validator(json.loads((SCHEMAS / "book.json").read_text()), registry=registry)
    manifest = Draft202012Validator(json.loads((SCHEMAS / "manifest.json").read_text()))
    return book, manifest


# Each Discover card's theme label, shown on the card as written (any text; one card design for all). The app drops a
# card whose theme is blank or longer than this (DiscoverRepository.THEME_MAX).
DISCOVER_THEME_MAX = 40
# discover.json v2: {"formatVersion": 2, "verses": [{"id": "JER.17.8", "theme": "Trust and guidance"}, ...]}
DISCOVER_FORMAT_VERSION = 2


def _discover_lines(path: Path, require_theme: bool) -> list[tuple[str, str | None]]:
    entries: list[tuple[str, str | None]] = []
    seen: set[str] = set()
    for number, line in enumerate(path.read_text().splitlines() if path.exists() else [], start=1):
        text = line.split("#", 1)[0].strip()
        if not text:
            continue
        parts = text.split()
        try:
            parse_id(parts[0])
        except ValueError:
            raise ValueError(f"{path.name}:{number}: not a study id: {parts[0]!r}") from None
        theme = " ".join(parts[1:]) or None
        if theme is not None and len(theme) > DISCOVER_THEME_MAX:
            raise ValueError(f"{path.name}:{number}: theme {theme!r} is longer than {DISCOVER_THEME_MAX} characters")
        if theme is None and require_theme:
            raise ValueError(f"{path.name}:{number}: {parts[0]} has no theme; every Discover card needs one, "
                             f"e.g. \"{parts[0]} Trust and guidance\"")
        if parts[0] in seen:
            raise ValueError(f"{path.name}:{number}: {parts[0]} is listed twice")
        seen.add(parts[0])
        entries.append((parts[0], theme))
    return entries


def load_discover_entries(path: Path = DISCOVER_FILE) -> list[tuple[str, str]]:
    """discover.txt lines "JER.17.8 Trust and guidance" -> [(id, theme)], in file order. Strict: every line needs a theme
    (publish uses this, so discover.json never carries a card without one)."""
    return [(i, t) for i, t in _discover_lines(path, require_theme=True) if t is not None]


def load_discover(path: Path = DISCOVER_FILE) -> list[str]:
    """The Discover ids in order (the batch wave and review queue). A missing theme is allowed here, a too-long one isn't."""
    return [i for i, _ in _discover_lines(path, require_theme=False)]


@dataclass
class Release:
    manifest: dict
    files: dict[str, bytes]              # site-relative path -> bytes
    left_out: dict[str, dict[str, int]]  # book -> {status: count} of studies not published
    discover_dropped: list[str]          # discover.txt ids that aren't publishable
    reported: list[tuple[str, str]] = field(default_factory=list)  # (id, reason) held back by an open `pipeline report`
    devotionals_left_out: dict[str, dict[str, int]] = field(default_factory=dict)  # month -> {status: count}


def build_release(store: StudyStore, base_url: str = "", discover: list[tuple[str, str]] | None = None,
                  devotionals=None, ministries_path: Path | None = None, history_path: Path | None = None) -> Release:
    """
    devotionals: a DevotionalStore, or None for none (the manifest then lists no months).
    ministries_path: community/ministries.json by default; a missing file leaves manifest.community out.
    history_path: community/history.json by default; a missing file leaves manifest.history out.
    """
    from .community import HISTORY_FILE, MINISTRIES_FILE, load_history, load_ministries
    from .devotionals.publish import collect, month_files

    refuse_test_path(store.directory)
    book_validator, manifest_validator = validators()
    by_book: dict[str, list[dict]] = {}
    left_out: dict[str, dict[str, int]] = {}
    published: set[str] = set()
    reported: list[tuple[str, str]] = []
    for study_id in store.ids():  # Bible order
        study = store.load(study_id)
        refuse_test_study(study)
        book = study_id.split(".")[0]
        reports = open_reports(study)
        if reports:
            # Held back whatever its status says: only regenerating, editing or reviewing it again clears a report.
            reported.append((study_id, reports[-1]["message"]))
            left_out.setdefault(book, {}).setdefault("reported", 0)
            left_out[book]["reported"] += 1
        elif study.get("status") in PUBLISHABLE and study.get("meta", {}).get("model"):
            by_book.setdefault(book, []).append(public_study(study))
            published.add(study_id)
        else:
            status = study.get("status", "generated") if study.get("meta", {}).get("model") else "not_written"
            left_out.setdefault(book, {}).setdefault(status, 0)
            left_out[book][status] += 1

    prefix = base_url.rstrip("/") + "/" if base_url else ""
    files: dict[str, bytes] = {}
    books = []
    for book in sorted(by_book, key=lambda b: BY_USFM[b].id):
        payload = {"formatVersion": FORMAT_VERSION, "book": book, "schemaVersion": SCHEMA_VERSION,
                   "verses": by_book[book]}
        errors = [f"{e.json_path}: {e.message}" for e in book_validator.iter_errors(payload)]
        if errors:
            raise ValueError(f"{book}: published book fails schema/book.json: {errors[:5]}")
        data = canonical_gzip(payload)
        digest = hashlib.sha256(data).hexdigest()
        path = f"studies/v{digest[:12]}/{book}.json.gz"
        files[path] = data
        books.append({
            "book": book, "bookId": BY_USFM[book].id, "url": prefix + path, "contentVersion": digest[:12],
            "verseCount": len(by_book[book]), "reviewedCount": sum(1 for v in by_book[book] if v["reviewed"]),
            "sha256": digest, "bytes": len(data),
        })

    wanted = discover if discover is not None else load_discover_entries()
    for i, theme in wanted:
        if not theme or not theme.strip() or len(theme) > DISCOVER_THEME_MAX:
            raise ValueError(f"Discover verse {i} needs a theme of 1-{DISCOVER_THEME_MAX} characters, got {theme!r}")
    discover_file = {"formatVersion": DISCOVER_FORMAT_VERSION,
                     "verses": [{"id": i, "theme": theme} for i, theme in wanted if i in published]}
    files["discover.json"] = canonical_json(discover_file)
    discover_entry = {"url": prefix + "discover.json", "count": len(discover_file["verses"]),
                      "sha256": hashlib.sha256(files["discover.json"]).hexdigest(), "bytes": len(files["discover.json"])}

    months = collect(devotionals)
    month_data, month_entries = month_files(months, prefix, canonical_gzip)
    files.update(month_data)

    body = {"books": books, "discover": discover_entry, "devotionals": {"months": month_entries}}
    ministries = load_ministries(ministries_path or MINISTRIES_FILE)
    if ministries is not None:
        files["ministries.json"] = canonical_json(ministries)
        body["community"] = {"url": prefix + "ministries.json", "count": len(ministries["ministries"]),
                             "sha256": hashlib.sha256(files["ministries.json"]).hexdigest(),
                             "bytes": len(files["ministries.json"])}
    history = load_history(history_path or HISTORY_FILE)
    if history is not None:
        files["history.json"] = canonical_json(history)
        body["history"] = {"url": prefix + "history.json", "count": len(history["donations"]),
                           "sha256": hashlib.sha256(files["history.json"]).hexdigest(),
                           "bytes": len(files["history.json"])}
    release = hashlib.sha256(json.dumps(body, sort_keys=True).encode()).hexdigest()[:12]
    manifest = {"formatVersion": FORMAT_VERSION, "schemaVersion": SCHEMA_VERSION, "release": release, **body}
    errors = [f"{e.json_path}: {e.message}" for e in manifest_validator.iter_errors(manifest)]
    if errors:
        raise ValueError(f"manifest fails schema/manifest.json: {errors[:5]}")
    files["manifest.json"] = canonical_json(manifest)
    return Release(manifest, files, left_out, [i for i, _ in wanted if i not in published], reported, months.left_out)


def write_site(release: Release, site: Path = SITE, keep_from: dict | None = None) -> list[str]:
    refuse_test_path(site)
    """
    Write the release to [site]. Book files referenced by [keep_from] (the live manifest) are kept too,
    so clients holding the previous manifest can still download. Anything else is removed.
    Returns the paths kept from the previous release.
    """
    previous = [*(keep_from or {}).get("books", []), *(keep_from or {}).get("devotionals", {}).get("months", [])]
    keep = {_site_path(entry["url"]) for entry in previous}
    site.mkdir(parents=True, exist_ok=True)
    wanted = set(release.files)
    kept = []
    for existing in site.rglob("*"):
        if existing.is_file():
            rel = existing.relative_to(site).as_posix()
            if rel in keep and rel not in wanted:
                kept.append(rel)
            elif rel not in wanted:
                existing.unlink()
    for rel, data in release.files.items():
        target = site / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists() or target.read_bytes() != data:
            target.write_bytes(data)
    for directory in sorted((d for d in site.rglob("*") if d.is_dir()), reverse=True):
        if not any(directory.iterdir()):
            directory.rmdir()
    missing = [k for k in keep if k not in wanted and not (site / k).exists()]
    if missing:
        print(f"Note: {len(missing)} previous book file(s) aren't in {site} any more, so they'll stop being served: "
              + ", ".join(sorted(missing)[:5]))
    return sorted(kept)


def _site_path(url: str) -> str:
    """'https://x.web.app/studies/v1/JHN.json.gz' or 'studies/v1/JHN.json.gz' -> 'studies/v1/JHN.json.gz'."""
    for folder in ("studies/", "devotionals/"):
        index = url.find(folder)
        if index >= 0:
            return url[index:]
    return url


# ---- Comparing with what's live ---------------------------------------------------------------------

def fetch_live_manifest(base_url: str) -> dict | None:
    """The deployed manifest, or None if nothing is deployed yet."""
    if not base_url:
        return None
    request = urllib.request.Request(base_url.rstrip("/") + "/manifest.json",
                                     headers={"User-Agent": "study-publisher/1", "Cache-Control": "no-cache"})
    try:
        require_online("fetch the live manifest")
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise


@dataclass
class Diff:
    added: list[dict] = field(default_factory=list)
    changed: list[tuple[dict, dict]] = field(default_factory=list)
    removed: list[dict] = field(default_factory=list)
    unchanged: list[dict] = field(default_factory=list)
    discover_before: list[str] = field(default_factory=list)
    discover_after: list[str] = field(default_factory=list)
    months_added: list[dict] = field(default_factory=list)
    months_changed: list[tuple[dict, dict]] = field(default_factory=list)
    months_removed: list[dict] = field(default_factory=list)
    community_changed: bool = False
    history_changed: bool = False
    upload_bytes: int = 0

    @property
    def any(self) -> bool:
        return bool(self.added or self.changed or self.removed or self.discover_before != self.discover_after
                    or self.months_added or self.months_changed or self.months_removed or self.community_changed
                    or self.history_changed)


def diff(release: Release, live: dict | None, live_discover: list[str] | None = None) -> Diff:
    old = {b["book"]: b for b in (live or {}).get("books", [])}
    new = {b["book"]: b for b in release.manifest["books"]}
    d = Diff()
    for book, entry in new.items():
        if book not in old:
            d.added.append(entry)
            d.upload_bytes += entry["bytes"]
        elif old[book]["sha256"] != entry["sha256"]:
            d.changed.append((old[book], entry))
            d.upload_bytes += entry["bytes"]
        else:
            d.unchanged.append(entry)
    d.removed = [b for name, b in old.items() if name not in new]
    old_months = {m["month"]: m for m in (live or {}).get("devotionals", {}).get("months", [])}
    new_months = {m["month"]: m for m in release.manifest["devotionals"]["months"]}
    for month, entry in new_months.items():
        if month not in old_months:
            d.months_added.append(entry)
        elif old_months[month]["sha256"] != entry["sha256"]:
            d.months_changed.append((old_months[month], entry))
        else:
            continue
        d.upload_bytes += entry["bytes"]
    d.months_removed = [m for name, m in old_months.items() if name not in new_months]
    d.community_changed = (live or {}).get("community", {}).get("sha256") != release.manifest.get("community", {}).get("sha256")
    d.history_changed = (live or {}).get("history", {}).get("sha256") != release.manifest.get("history", {}).get("sha256")
    d.discover_before = live_discover or []
    d.discover_after = discover_keys(json.loads(release.files["discover.json"])["verses"])
    return d


def discover_keys(verses: list) -> list[str]:
    """discover.json verses as "ID theme" strings, so a theme change shows in the diff. v1 files hold bare ids."""
    return [v if isinstance(v, str) else f"{v['id']} {v['theme']}" for v in verses]


def fetch_live_discover(base_url: str) -> list[str]:
    try:
        require_online("fetch the live discover.json")
        with urllib.request.urlopen(base_url.rstrip("/") + "/discover.json", timeout=30) as response:
            return discover_keys(json.loads(response.read()).get("verses", []))
    except (urllib.error.URLError, ValueError):
        return []


def render_diff(d: Diff, release: Release, live: dict | None) -> str:
    lines = []
    if live is None:
        lines.append("Nothing is deployed yet (no live manifest.json): everything below is new.")
    else:
        lines.append(f"Live release {live.get('release')} -> local release {release.manifest['release']}")
    if not release.manifest["books"] and live is None:
        lines.append("Nothing to publish yet: no study is auto_checked or reviewed.")
    elif not d.any:
        lines.append("No changes: the live site already matches.")
    for b in d.added:
        lines.append(f"  + {b['book']}: {b['verseCount']} verses ({b['reviewedCount']} reviewed), {b['bytes']:,} bytes")
    for old, new in d.changed:
        dv, dr = new["verseCount"] - old["verseCount"], new["reviewedCount"] - old["reviewedCount"]
        lines.append(f"  ~ {new['book']}: {old['verseCount']} -> {new['verseCount']} verses ({dv:+}), "
                     f"reviewed {old['reviewedCount']} -> {new['reviewedCount']} ({dr:+}), {new['bytes']:,} bytes")
    for b in d.removed:
        lines.append(f"  - {b['book']}: no publishable verses any more (was {b['verseCount']})")
    if d.unchanged:
        lines.append(f"  = {len(d.unchanged)} book(s) unchanged")
    if d.discover_before != d.discover_after:
        added = [v for v in d.discover_after if v not in d.discover_before]
        removed = [v for v in d.discover_before if v not in d.discover_after]
        lines.append(f"  Discover: {len(d.discover_before)} -> {len(d.discover_after)} verses"
                     + (f"; added {', '.join(added[:10])}" if added else "")
                     + (f"; removed {', '.join(removed[:10])}" if removed else "")
                     + ("; order changed" if not added and not removed else ""))
    for m in d.months_added:
        lines.append(f"  + devotionals {m['month']}: {m['count']} days ({m['reviewedCount']} reviewed), {m['bytes']:,} bytes")
    for old, new in d.months_changed:
        lines.append(f"  ~ devotionals {new['month']}: {old['count']} -> {new['count']} days, "
                     f"reviewed {old['reviewedCount']} -> {new['reviewedCount']}, {new['bytes']:,} bytes")
    for m in d.months_removed:
        lines.append(f"  - devotionals {m['month']}: nothing publishable any more (was {m['count']})")
    if d.community_changed:
        entry = release.manifest.get("community")
        lines.append(f"  ~ ministries.json: {entry['count']} ministries, {entry['bytes']:,} bytes" if entry
                     else "  - ministries.json: no longer published")
    if d.history_changed:
        entry = release.manifest.get("history")
        lines.append(f"  ~ history.json: {entry['count']} donation months, {entry['bytes']:,} bytes" if entry
                     else "  - history.json: no longer published")
    flagged_days = {m: c["flagged"] for m, c in release.devotionals_left_out.items() if c.get("flagged")}
    if flagged_days:
        lines.append(f"  Devotionals left out as flagged: {sum(flagged_days.values())} in month(s) "
                     + ", ".join(f"{m} ({n})" for m, n in flagged_days.items()))
    if release.discover_dropped:
        lines.append(f"  Discover verses left out (not auto_checked/reviewed yet): {', '.join(release.discover_dropped[:20])}")
    flagged = {b: c["flagged"] for b, c in release.left_out.items() if c.get("flagged")}
    if flagged:
        lines.append(f"  Left out as flagged: {sum(flagged.values())} verse(s) in "
                     + ", ".join(f"{b} {n}" for b, n in flagged.items()))
    if release.reported:
        lines.append(f"  Left out as reported (`pipeline reports` lists them): {len(release.reported)} verse(s)")
        lines.extend(f"    {study_id}: {reason}" for study_id, reason in release.reported)
    total = sum(b["verseCount"] for b in release.manifest["books"])
    days = sum(m["count"] for m in release.manifest["devotionals"]["months"])
    lines.append(f"Publishing {total:,} verses in {len(release.manifest['books'])} book(s) and {days} devotional(s); "
                 f"{d.upload_bytes:,} bytes of content files to upload.")
    return "\n".join(lines)


# ---- Deploying (Firebase Hosting) -------------------------------------------------------------------

def load_config(path: Path = CONFIG) -> dict:
    config = json.loads(path.read_text())
    return {"firebaseProject": config.get("firebaseProject", ""), "baseUrl": config.get("baseUrl", "")}


def deploy(project: str, release: str, preview: bool = False) -> str:
    """Runs the Firebase CLI from pipeline/ (firebase.json there). Returns its output."""
    if preview:
        command = ["firebase", "hosting:channel:deploy", "preview", "--expires", "7d", "--project", project]
    else:
        command = ["firebase", "deploy", "--only", "hosting", "--project", project,
                   "--message", f"study release {release}"]
    require_online("deploy to Firebase")
    # One upload at a time: the CLI gives a file ~20 ms per KB, and with its default 200 parallel uploads the big
    # book files (1-3 MB) time out on a slow uplink (2026-11-24: ~100 KB/s to upload-firebasehosting).
    env = {**os.environ, "FIREBASE_HOSTING_UPLOAD_CONCURRENCY": os.environ.get("FIREBASE_HOSTING_UPLOAD_CONCURRENCY", "1")}
    result = subprocess.run(command, cwd=PIPELINE, capture_output=True, text=True, timeout=1800, env=env)
    if result.returncode != 0:
        raise RuntimeError(f"{' '.join(command)} failed:\n{result.stdout}\n{result.stderr}")
    return result.stdout


def verify_live(base_url: str, release: Release) -> list[str]:
    """Download the live manifest and every book file; check hashes and headers. Returns problems."""
    problems = []
    live = fetch_live_manifest(base_url)
    if live != release.manifest:
        problems.append("live manifest.json doesn't match the local release (CDN cache? try again in 5 minutes)")
    files = [*release.manifest["books"],
             *({"book": f"devotionals {m['month']}", **m} for m in release.manifest["devotionals"]["months"])]
    for book in files:
        request = urllib.request.Request(book["url"], headers={"Accept-Encoding": "identity"})
        require_online("verify the live release")
        with urllib.request.urlopen(request, timeout=60) as response:
            data = response.read()
            content_type = response.headers.get("Content-Type", "")
            encoding = response.headers.get("Content-Encoding")
            cache = response.headers.get("Cache-Control", "")
        if hashlib.sha256(data).hexdigest() != book["sha256"]:
            problems.append(f"{book['book']}: SHA-256 mismatch")
        if "gzip" not in content_type or encoding:
            problems.append(f"{book['book']}: served as {content_type!r} with Content-Encoding {encoding!r}")
        if "immutable" not in cache:
            problems.append(f"{book['book']}: Cache-Control is {cache!r}")
    return problems


def save_state(release: Release) -> None:
    STATE.write_text(json.dumps({"release": release.manifest["release"], "manifest": release.manifest}, indent=2) + "\n")
