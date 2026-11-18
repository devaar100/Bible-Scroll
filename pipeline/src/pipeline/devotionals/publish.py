"""
Devotionals on the CDN: one immutable gzip JSON file per month, listed in manifest.json, plus bundling the
current and next month into the app at release time.

  devotionals/v{sha256[:12]}/{MM}.json.gz     February includes the 02-29 bonus.

Only auto_checked and reviewed devotionals are published; public files carry no check reasons or names.
"""
from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from datetime import date
from pathlib import Path

from jsonschema import Draft202012Validator

from ..paths import PIPELINE, REPO
from .store import DevotionalStore
from ..translate.content import public_devotional_i18n

PUBLISHABLE = ("auto_checked", "reviewed")
FORMAT_VERSION = 1
SCHEMA_VERSION = 1
MONTH_SCHEMA = PIPELINE / "schema" / "devotional_month.json"
ASSETS = REPO / "app/src/main/assets/devotionals"
PUBLIC_FIELDS = ("id", "contentVersion", "date", "dayOfYear", "title", "theme", "series", "coverStyle", "verses",
                 "context", "reflect", "applyIt", "prayer")


def public_devotional(devotional: dict) -> dict:
    out = {k: devotional[k] for k in PUBLIC_FIELDS if k in devotional}
    out["reviewed"] = devotional["status"] == "reviewed"
    out["model"] = devotional["meta"]["model"]
    localized = public_devotional_i18n(devotional)  # publishable, up-to-date translations only
    if localized:
        out["i18n"] = localized
    return out


def month_validator() -> Draft202012Validator:
    from referencing import Registry, Resource

    from .store import SCHEMA_PATH
    devotional = json.loads(SCHEMA_PATH.read_text())
    registry = Registry().with_resource(devotional["$id"], Resource.from_contents(devotional))
    return Draft202012Validator(json.loads(MONTH_SCHEMA.read_text()), registry=registry)


@dataclass
class Months:
    payloads: dict[str, dict]          # "MM" -> month file content
    left_out: dict[str, dict[str, int]]  # "MM" -> {status: count}


def collect(store: DevotionalStore | None) -> Months:
    payloads: dict[str, list[dict]] = {}
    left_out: dict[str, dict[str, int]] = {}
    for devotional_id in (store.ids() if store else []):
        devotional = store.load(devotional_id)
        month = devotional["date"][:2]
        if devotional.get("status") in PUBLISHABLE:
            payloads.setdefault(month, []).append(public_devotional(devotional))
        else:
            status = devotional.get("status", "generated")
            left_out.setdefault(month, {}).setdefault(status, 0)
            left_out[month][status] += 1
    validator = month_validator()
    out = {}
    for month in sorted(payloads):
        payload = {"formatVersion": FORMAT_VERSION, "schemaVersion": SCHEMA_VERSION, "month": month,
                   "devotionals": sorted(payloads[month], key=lambda d: d["date"])}
        errors = [f"{e.json_path}: {e.message}" for e in validator.iter_errors(payload)]
        if errors:
            raise ValueError(f"devotionals month {month} fails schema/devotional_month.json: {errors[:5]}")
        out[month] = payload
    return Months(out, left_out)


def month_files(months: Months, prefix: str, gzip_fn) -> tuple[dict[str, bytes], list[dict]]:
    """Site files and manifest entries. gzip_fn = publish.canonical_gzip (byte-deterministic)."""
    files, entries = {}, []
    for month, payload in months.payloads.items():
        data = gzip_fn(payload)
        digest = hashlib.sha256(data).hexdigest()
        path = f"devotionals/v{digest[:12]}/{month}.json.gz"
        files[path] = data
        entries.append({"month": month, "url": prefix + path, "contentVersion": digest[:12],
                        "count": len(payload["devotionals"]),
                        "reviewedCount": sum(1 for d in payload["devotionals"] if d["reviewed"]),
                        "sha256": digest, "bytes": len(data)})
    return files, entries


# ---- Bundling into the app --------------------------------------------------------------------------

def bundled_months(today: date) -> tuple[str, str]:
    """The current and next month (December -> January)."""
    return f"{today.month:02d}", f"{today.month % 12 + 1:02d}"


@dataclass
class BundleResult:
    copied: list[str]
    missing: list[str]
    removed: list[str]
    bytes: int


def bundle(site: Path, today: date, assets: Path = ASSETS) -> BundleResult:
    """Copies the two months' files from the local release (out/site) into the app's assets, with a manifest."""
    from ..bundle import BUNDLED_SUFFIX
    from ..publish import _site_path, canonical_json

    manifest_path = site / "manifest.json"
    if not manifest_path.exists():
        raise FileNotFoundError(f"No {manifest_path}: run `python -m pipeline publish --local` first")
    manifest = json.loads(manifest_path.read_text())
    entries = {m["month"]: m for m in manifest.get("devotionals", {}).get("months", [])}
    assets.mkdir(parents=True, exist_ok=True)
    copied, missing, subset, total = [], [], [], 0
    for month in bundled_months(today):
        entry = entries.get(month)
        if entry is None:
            missing.append(month)
            continue
        data = (site / _site_path(entry["url"])).read_bytes()
        if hashlib.sha256(data).hexdigest() != entry["sha256"]:
            raise ValueError(f"month {month}: file in {site} doesn't match its manifest SHA-256; rebuild the release")
        target = assets / f"{month}{BUNDLED_SUFFIX}"
        if not target.exists() or target.read_bytes() != data:
            target.write_bytes(data)
        copied.append(month)
        total += len(data)
        subset.append({**entry, "url": f"{month}{BUNDLED_SUFFIX}"})
    removed = []
    for stale in [*assets.glob("*.json.gz"), *assets.glob(f"*{BUNDLED_SUFFIX}")]:
        if stale.name.removesuffix(".json.gz").removesuffix(BUNDLED_SUFFIX) not in copied or stale.name.endswith(".json.gz"):
            stale.unlink()
            removed.append(stale.name)
    (assets / "manifest.json").write_bytes(canonical_json(
        {"formatVersion": manifest["formatVersion"], "release": manifest["release"], "months": subset}))
    return BundleResult(copied, missing, removed, total)
