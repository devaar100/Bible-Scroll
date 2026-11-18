"""
Package publishable studies into one gzip JSON file per book, write manifest.json + discover.json, show
what would change against the live site, and deploy to Firebase Hosting.

Layout (out/site/ = the Firebase public directory):
  manifest.json                         short cache; lists every book file
  discover.json                         short cache; curated Discover verses in order
  studies/v{contentVersion}/{BOOK}.json.gz   immutable; contentVersion = sha256(file)[:12]
"""
from __future__ import annotations

import gzip
import hashlib
import io
import json
import shutil
import subprocess
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path

from jsonschema import Draft202012Validator
from referencing import Registry, Resource

from .books import BOOKS, BY_USFM
from .paths import OUT, PIPELINE
from .store import StudyStore, parse_id

PUBLISHABLE = ("auto_checked", "reviewed")
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


def load_discover(path: Path = DISCOVER_FILE) -> list[str]:
    ids = []
    for number, line in enumerate(path.read_text().splitlines() if path.exists() else [], start=1):
        text = line.split("#", 1)[0].strip()
        if not text:
            continue
        try:
            parse_id(text)
        except ValueError:
            raise ValueError(f"{path.name}:{number}: not a study id: {text!r}") from None
        if text in ids:
            raise ValueError(f"{path.name}:{number}: {text} is listed twice")
        ids.append(text)
    return ids


@dataclass
class Release:
    manifest: dict
    files: dict[str, bytes]              # site-relative path -> bytes
    left_out: dict[str, dict[str, int]]  # book -> {status: count} of studies not published
    discover_dropped: list[str]          # discover.txt ids that aren't publishable


def build_release(store: StudyStore, base_url: str = "", discover_ids: list[str] | None = None) -> Release:
    book_validator, manifest_validator = validators()
    by_book: dict[str, list[dict]] = {}
    left_out: dict[str, dict[str, int]] = {}
    published: set[str] = set()
    for study_id in store.ids():  # Bible order
        study = store.load(study_id)
        book = study_id.split(".")[0]
        if study.get("status") in PUBLISHABLE and study.get("meta", {}).get("model"):
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

    wanted = discover_ids if discover_ids is not None else load_discover()
    discover = {"formatVersion": FORMAT_VERSION, "verses": [i for i in wanted if i in published]}
    files["discover.json"] = canonical_json(discover)
    discover_entry = {"url": prefix + "discover.json", "count": len(discover["verses"]),
                      "sha256": hashlib.sha256(files["discover.json"]).hexdigest(), "bytes": len(files["discover.json"])}

    body = {"books": books, "discover": discover_entry}
    release = hashlib.sha256(json.dumps(body, sort_keys=True).encode()).hexdigest()[:12]
    manifest = {"formatVersion": FORMAT_VERSION, "schemaVersion": SCHEMA_VERSION, "release": release, **body}
    errors = [f"{e.json_path}: {e.message}" for e in manifest_validator.iter_errors(manifest)]
    if errors:
        raise ValueError(f"manifest fails schema/manifest.json: {errors[:5]}")
    files["manifest.json"] = canonical_json(manifest)
    return Release(manifest, files, left_out, [i for i in wanted if i not in published])


def write_site(release: Release, site: Path = SITE, keep_from: dict | None = None) -> list[str]:
    """
    Write the release to [site]. Book files referenced by [keep_from] (the live manifest) are kept too,
    so clients holding the previous manifest can still download. Anything else is removed.
    Returns the paths kept from the previous release.
    """
    keep = {_site_path(b["url"]) for b in (keep_from or {}).get("books", [])}
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
    index = url.find("studies/")
    return url[index:] if index >= 0 else url


# ---- Comparing with what's live ---------------------------------------------------------------------

def fetch_live_manifest(base_url: str) -> dict | None:
    """The deployed manifest, or None if nothing is deployed yet."""
    if not base_url:
        return None
    request = urllib.request.Request(base_url.rstrip("/") + "/manifest.json",
                                     headers={"User-Agent": "study-publisher/1", "Cache-Control": "no-cache"})
    try:
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
    upload_bytes: int = 0

    @property
    def any(self) -> bool:
        return bool(self.added or self.changed or self.removed or self.discover_before != self.discover_after)


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
    d.discover_before = live_discover or []
    d.discover_after = json.loads(release.files["discover.json"])["verses"]
    return d


def fetch_live_discover(base_url: str) -> list[str]:
    try:
        with urllib.request.urlopen(base_url.rstrip("/") + "/discover.json", timeout=30) as response:
            return json.loads(response.read()).get("verses", [])
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
    if release.discover_dropped:
        lines.append(f"  Discover verses left out (not auto_checked/reviewed yet): {', '.join(release.discover_dropped[:20])}")
    flagged = {b: c["flagged"] for b, c in release.left_out.items() if c.get("flagged")}
    if flagged:
        lines.append(f"  Left out as flagged: {sum(flagged.values())} verse(s) in "
                     + ", ".join(f"{b} {n}" for b, n in flagged.items()))
    total = sum(b["verseCount"] for b in release.manifest["books"])
    lines.append(f"Publishing {total:,} verses in {len(release.manifest['books'])} book(s); "
                 f"{d.upload_bytes:,} bytes of book files to upload.")
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
    result = subprocess.run(command, cwd=PIPELINE, capture_output=True, text=True, timeout=900)
    if result.returncode != 0:
        raise RuntimeError(f"{' '.join(command)} failed:\n{result.stdout}\n{result.stderr}")
    return result.stdout


def verify_live(base_url: str, release: Release) -> list[str]:
    """Download the live manifest and every book file; check hashes and headers. Returns problems."""
    problems = []
    live = fetch_live_manifest(base_url)
    if live != release.manifest:
        problems.append("live manifest.json doesn't match the local release (CDN cache? try again in 5 minutes)")
    for book in release.manifest["books"]:
        request = urllib.request.Request(book["url"], headers={"Accept-Encoding": "identity"})
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
