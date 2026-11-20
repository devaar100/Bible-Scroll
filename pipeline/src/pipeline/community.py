"""
Community spotlight ministries (app C1/C2): community/ministries.json, published as /ministries.json and pinned by
sha256 in manifest.community. backend/scripts/seed-month.mjs reads the same file to create the month's Firestore
vote counters. Entries with "test": true are for debug builds only (the app hides them in release).
"""
from __future__ import annotations

import json
from pathlib import Path
from urllib.parse import urlsplit

from jsonschema import Draft202012Validator

from .paths import PIPELINE

MINISTRIES_FILE = PIPELINE / "community" / "ministries.json"
SCHEMA = PIPELINE / "schema" / "ministries.json"
HISTORY_FILE = PIPELINE / "community" / "history.json"
HISTORY_SCHEMA = PIPELINE / "schema" / "history.json"

# Where ministry images may live: our Firebase Hosting site, or our project's Firebase Storage bucket. The app
# refuses any other host too (core/content MinistryImages), so a new host needs docs/network-calls.md first.
FIREBASE_PROJECT = "biblescroll-content"
IMAGE_HOSTING_HOSTS = frozenset({f"{FIREBASE_PROJECT}.web.app", f"{FIREBASE_PROJECT}.firebaseapp.com"})
IMAGE_STORAGE_HOST = "firebasestorage.googleapis.com"
IMAGE_STORAGE_BUCKETS = frozenset({f"{FIREBASE_PROJECT}.firebasestorage.app", f"{FIREBASE_PROJECT}.appspot.com"})


def load_ministries(path: Path = MINISTRIES_FILE) -> dict | None:
    """The validated file, or None if there isn't one. Raises ValueError listing every problem."""
    if not path.exists():
        return None
    data = json.loads(path.read_text())
    validator = Draft202012Validator(json.loads(SCHEMA.read_text()))
    problems = [f"{'/'.join(map(str, e.absolute_path)) or '(root)'}: {e.message}" for e in validator.iter_errors(data)]
    if not problems:
        seen: set[str] = set()
        for m in data["ministries"]:
            if m["id"] in seen:
                problems.append(f"{m['id']}: id used twice (ids must be unique; they name Firestore documents)")
            seen.add(m["id"])
            if m.get("test") and not m["id"].startswith("test-"):
                problems.append(f"{m['id']}: test entries must have ids starting with test- (keeps them apart in Firestore)")
            problems.extend(link_problems(m))
            problems.extend(image_problems(m))
    if problems:
        raise ValueError(f"{path.name}:\n  " + "\n  ".join(problems))
    return data


# Public suffixes with two labels that ministry sites are likely to use. The full Public Suffix List would need a
# new dependency; a host under a multi-label suffix that isn't listed here is compared on its last two labels,
# which is stricter (e.g. "a.example.co.xx" vs "b.example.co.xx" would differ), never looser.
TWO_LABEL_SUFFIXES = frozenset({
    "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "ltd.uk", "plc.uk",
    "com.au", "org.au", "net.au", "asn.au", "co.nz", "org.nz", "co.za", "org.za", "co.in", "org.in",
    "com.br", "org.br", "com.mx", "org.mx", "com.ar", "org.ar", "co.ke", "or.ke", "co.jp", "or.jp",
    "co.kr", "or.kr", "com.sg", "org.sg", "com.ph", "org.ph", "com.ng", "org.ng", "co.ug", "or.ug",
})


# Registrable domains that belong to the same organisation, so a give_url on one may go with a website on another.
# Add a set only after confirming both domains are the organisation's own (record the evidence in
# docs/ministry-criteria.md).
ALTERNATE_DOMAINS: tuple[frozenset[str], ...] = (
    frozenset({"obolodisanpietro.va", "peterspence.va"}),  # Peter's Pence (Holy See): Italian and English names
)


def same_organisation(a: str, b: str) -> bool:
    return a == b or any(a in group and b in group for group in ALTERNATE_DOMAINS)


def registrable_domain(host: str) -> str:
    """'www.give.example.org' -> 'example.org'; 'shop.example.org.uk' -> 'example.org.uk'. Lowercase, no trailing dot."""
    labels = host.lower().rstrip(".").split(".")
    take = 3 if len(labels) >= 3 and ".".join(labels[-2:]) in TWO_LABEL_SUFFIXES else 2
    return ".".join(labels[-take:])


def link_problems(ministry: dict) -> list[str]:
    """website and give_url must be plain https links, and give_url must be on the website's registrable domain."""
    problems, hosts = [], {}
    for field in ("website", "give_url"):
        parts = urlsplit(ministry[field])
        if parts.scheme != "https":
            problems.append(f"{ministry['id']}: {field} isn't https: {ministry[field]}")
        elif not parts.hostname or "." not in parts.hostname or parts.username or parts.password or parts.port:
            problems.append(f"{ministry['id']}: {field} needs a plain host name (no login, no port): {ministry[field]}")
        else:
            hosts[field] = registrable_domain(parts.hostname)
    if len(hosts) == 2 and not same_organisation(hosts["website"], hosts["give_url"]):
        problems.append(f"{ministry['id']}: give_url is on {hosts['give_url']} but website is on {hosts['website']}; "
                        "donations must go to the ministry's own domain (or add a confirmed pair to community.ALTERNATE_DOMAINS)")
    return problems


def image_problems(ministry: dict) -> list[str]:
    """An image must be https on our Firebase Hosting site or in our Firebase Storage bucket (no other hosts)."""
    url = ministry.get("image")
    if url is None:
        return []
    parts = urlsplit(url)
    host = (parts.hostname or "").lower()
    if parts.scheme != "https" or parts.username or parts.password or parts.port:
        return [f"{ministry['id']}: image must be a plain https link: {url}"]
    if host in IMAGE_HOSTING_HOSTS:
        return []
    if host == IMAGE_STORAGE_HOST:
        segments = parts.path.split("/")  # "", "v0", "b", "<bucket>", "o", "<object>"
        if len(segments) >= 6 and segments[1] == "v0" and segments[2] == "b" and segments[3] in IMAGE_STORAGE_BUCKETS and segments[4] == "o":
            return []
        return [f"{ministry['id']}: image is in Firebase Storage but not in our bucket ({', '.join(sorted(IMAGE_STORAGE_BUCKETS))}): {url}"]
    return [f"{ministry['id']}: image host {host or '?'} isn't ours; use Firebase Hosting ({', '.join(sorted(IMAGE_HOSTING_HOSTS))}) "
            f"or Firebase Storage ({IMAGE_STORAGE_HOST}/v0/b/<our bucket>/o/...)"]


def load_history(path: Path = HISTORY_FILE, current_month: str | None = None) -> dict | None:
    """
    community/history.json validated, or None if there isn't one. Every month at most once, and only finished months:
    [current_month] (UTC "YYYY-MM", default now) and later are refused, because the app must never show a month whose
    vote is still open. Raises ValueError listing every problem.
    """
    if not path.exists():
        return None
    from datetime import datetime, timezone
    current = current_month or datetime.now(timezone.utc).strftime("%Y-%m")
    data = json.loads(path.read_text())
    validator = Draft202012Validator(json.loads(HISTORY_SCHEMA.read_text()))
    problems = [f"{'/'.join(map(str, e.absolute_path)) or '(root)'}: {e.message}" for e in validator.iter_errors(data)]
    if not problems:
        seen: set[str] = set()
        for d in data["donations"]:
            if d["month"] in seen:
                problems.append(f"{d['month']}: listed twice (one donation record per month)")
            seen.add(d["month"])
            if d["month"] >= current:
                problems.append(f"{d['month']}: not finished yet (only months before {current} may be published)")
    if problems:
        raise ValueError(f"{path.name}:\n  " + "\n  ".join(problems))
    return data


def release_problems(data: dict | None) -> list[str]:
    """
    Extra rules for a real publish (and its --dry-run), on top of load_ministries(): the file must exist, list at
    least one ministry, and hold no test entries (those are for debug builds and the emulator only).
    """
    if data is None:
        return [f"{MINISTRIES_FILE.name} is missing: a release must publish the ministry list"]
    problems = []
    if not data["ministries"]:
        problems.append("no ministries: a release needs at least one entry")
    problems.extend(f"{m['id']}: test entry in a release publish (remove it, or publish the real list)"
                    for m in data["ministries"] if m.get("test") or m["id"].startswith("test-"))
    return problems


def featured(data: dict, month: str, include_test: bool = False) -> list[dict]:
    """Ministries in the spotlight for a UTC month ("2026-12"), in file order."""
    return [m for m in data["ministries"] if month in m["months_featured"] and (include_test or not m.get("test"))]
