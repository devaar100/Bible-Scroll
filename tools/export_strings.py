#!/usr/bin/env python3
"""
Export the app's English strings for translators, with a note per string saying where it appears.

    python3 tools/export_strings.py            # writes docs/translation/strings-en.xliff and strings-en.csv

Reads app/src/main/res/values/{strings,books}.xml. "Where" comes from the code: every file that uses
R.string.x / R.plurals.x / R.array.x (or @string/x in XML) is mapped to the screen it belongs to (SCREENS below).
The XML comment above a string, if any, becomes the translator note. translatable="false" strings are skipped.
Strings no code uses are listed as UNUSED on stderr (and still exported, marked as such).
"""
from __future__ import annotations

import csv
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res/values"
SOURCES = [ROOT / "app/src/main/java", ROOT / "app/src/main/res", ROOT / "app/src/main/AndroidManifest.xml"]
OUT = ROOT / "docs/translation"

# Path fragment (first match wins) -> where it appears, using the spec's screen ids.
SCREENS: list[tuple[str, str]] = [
    ("feature/bible/FeedTopBar", "B1 Bible feed: top bar"),
    ("feature/bible/VersePage", "B1 Bible feed: verse page"),
    ("feature/bible/VerseText", "B1 Bible feed: verse text"),
    ("feature/bible/VerseNote", "B6 Verse notes sheet"),
    ("feature/bible/VerseActions", "B7 Verse actions"),
    ("feature/bible/BibleScreen", "B1 Bible feed"),
    ("feature/bible/BibleFeed", "B1 Bible feed"),
    ("feature/bible/picker/BookPicker", "B3/B4 Book picker and chapter grid"),
    ("feature/bible/picker/TranslationPicker", "B5 Translation picker"),
    ("feature/bible/sheets/Compare", "B9 Compare versions"),
    ("feature/bible/sheets/VerseActionsSheet", "B7 Verse actions sheet"),
    ("feature/bible/sheets/WidgetVerseDialog", "B11 Set as widget verse dialog"),
    ("feature/bible/sheets/", "B7-B11 Verse sheets"),
    ("feature/share/", "B10 Share verse as image"),
    ("feature/discover/DeepStudy", "D2 Deep study page"),
    ("feature/discover/", "D1 Discover tab"),
    ("feature/study/", "B8/D2 Verse study"),
    ("feature/home/RestoreNotice", "H1 Home: card shown once after restoring a backup"),
    ("feature/home/", "H1 Home tab"),
    ("feature/streak/", "H2 Streak chip and sheet"),
    ("feature/library/LibraryList", "H4-H7 Library lists (saved studies, studied, liked & bookmarked, notes)"),
    ("feature/library/", "H3 Library"),
    ("feature/plans/PlansBrowser", "H8 Reading plans browser"),
    ("feature/plans/PlanDetail", "H9 Plan detail / H10 Start plan dialog"),
    ("feature/plans/PlanReader", "H11 Plan reader"),
    ("feature/plans/PlanDays", "H12 Plan days & notes"),
    ("feature/plans/CreatePlan", "H13/H14 Create your own plan"),
    ("feature/plans/PlanComplete", "Plan finished screen"),
    ("feature/plans/PlanTitles", "Names of plans the reader builds (H14)"),
    ("feature/plans/PassageLabels", "Plan day headings (H9, H11, H12)"),
    ("feature/plans/", "Reading plans"),
    ("feature/devotionals/DevotionalReader", "H16 Devotional reader"),
    ("feature/devotionals/DevotionalDetail", "H15 Devotional detail"),
    ("feature/devotionals/DevotionalArchive", "H17 Devotional archive"),
    ("feature/devotionals/DevotionalHomeCards", "H1 Home: devotional cards"),
    ("feature/devotionals/", "Devotionals (H15-H17)"),
    ("feature/onboarding/FeatureTour", "O3 Onboarding feature tour"),
    ("feature/onboarding/", "O1-O8 Onboarding"),
    ("feature/settings/DataBackup", "S9 Settings → Data & backup"),
    ("feature/settings/LanguageRow", "S2 Settings → General → Language"),
    ("feature/settings/Notifications", "S6 Settings → Notifications"),
    ("feature/settings/ReminderTimeDialog", "S6 Reminder time picker"),
    ("feature/settings/Storage", "Settings → Storage"),
    ("feature/settings/", "S1-S4 Settings"),
    ("feature/community/", "C1 Community tab"),
    ("ui/DataErrorScreen", "Error screen shown instead of the app when saved data can't be opened"),
    ("ui/BibleScrollAppUi", "Bottom tab bar"),
    ("ui/navigation/TopLevelDestination", "Bottom tab bar"),
    ("ui/", "App-wide"),
    ("widget/", "Home-screen widget"),
    ("data/reminder/", "Notifications (daily verse and plan reminders)"),
    ("data/bible/References", "Verse references, everywhere a verse is named"),
    ("data/bible/BookNames", "Bible book names, everywhere a book is named"),
    ("data/backup/", "S9 Data & backup"),
    ("AndroidManifest.xml", "App name in the launcher and system settings"),
    ("res/xml/verse_widget_info", "Widget picker"),
]


def screen_for(path: Path) -> str:
    rel = path.as_posix()
    for fragment, screen in SCREENS:
        if fragment in rel:
            return screen
    return str(path.relative_to(ROOT))


def read_entries(path: Path) -> list[dict]:
    """Strings, plurals and string-arrays with the comment right above each."""
    text = path.read_text(encoding="utf-8")
    entries = []
    pending_comment = None
    for m in re.finditer(r"<!--(.*?)-->|<(string|plurals|string-array)\s+name=\"([^\"]+)\"([^>]*)>(.*?)</\2>", text, re.S):
        if m.group(1) is not None:
            pending_comment = " ".join(m.group(1).split())
            continue
        kind, name, attrs, body = m.group(2), m.group(3), m.group(4), m.group(5)
        if 'translatable="false"' in attrs:
            pending_comment = None
            continue
        if kind == "string":
            english = {"": "".join(ET.fromstring(f"<x>{body}</x>").itertext())}
        elif kind == "plurals":
            english = {q: "".join(ET.fromstring(f"<x>{b}</x>").itertext())
                       for q, b in re.findall(r'<item quantity="(\w+)">(.*?)</item>', body, re.S)}
        else:
            english = {str(i): "".join(ET.fromstring(f"<x>{b}</x>").itertext())
                       for i, b in enumerate(re.findall(r"<item>(.*?)</item>", body, re.S))}
        entries.append({"kind": kind, "name": name, "english": english, "comment": pending_comment, "file": path.name})
        pending_comment = None
    return entries


def usages() -> dict[tuple[str, str], set[str]]:
    found: dict[tuple[str, str], set[str]] = {}
    pattern = re.compile(r"R\.(string|plurals|array)\.(\w+)|@(string|plurals|array)/(\w+)")
    files = [p for s in SOURCES for p in ([s] if s.is_file() else s.rglob("*")) if p.suffix in (".kt", ".xml")]
    for path in files:
        if "/values" in path.as_posix():
            continue
        for m in pattern.finditer(path.read_text(encoding="utf-8")):
            kind, name = (m.group(1), m.group(2)) if m.group(1) else (m.group(3), m.group(4))
            kind = {"array": "string-array"}.get(kind, kind)
            found.setdefault((kind, name), set()).add(screen_for(path))
    return found


def main() -> None:
    entries = read_entries(RES / "strings.xml") + read_entries(RES / "books.xml")
    used = usages()
    OUT.mkdir(parents=True, exist_ok=True)
    unused = []
    rows = []
    for e in entries:
        where = sorted(used.get((e["kind"], e["name"]), set()))
        if not where:
            unused.append(e["name"])
        for key, english in e["english"].items():
            unit = e["name"] + (f"[{key}]" if key else "")
            placeholders = re.findall(r"%\d+\$[sdf]", english)
            rows.append({
                "id": unit, "kind": e["kind"], "english": english,
                "where": "; ".join(where) or "UNUSED (not referenced in code)",
                "note": e["comment"] or "",
                "placeholders": " ".join(placeholders),
            })

    with (OUT / "strings-en.csv").open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=["id", "kind", "english", "where", "note", "placeholders"])
        writer.writeheader()
        writer.writerows(rows)

    units = []
    for r in rows:
        notes = [f'<note from="where">{escape(r["where"])}</note>']
        if r["note"]:
            notes.append(f'<note from="developer">{escape(r["note"])}</note>')
        if r["placeholders"]:
            notes.append(f'<note from="placeholders">Keep {escape(r["placeholders"])} (you may reorder them).</note>')
        units.append(
            f'      <trans-unit id="{escape(r["id"])}" resname="{escape(r["id"])}">\n'
            f'        <source>{escape(r["english"])}</source>\n'
            + "".join(f"        {n}\n" for n in notes)
            + "      </trans-unit>"
        )
    xliff = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<xliff version="1.2" xmlns="urn:oasis:names:tc:xliff:document:1.2">\n'
        '  <file original="app/src/main/res/values/strings.xml" source-language="en" target-language="es" datatype="plaintext">\n'
        "    <body>\n" + "\n".join(units) + "\n    </body>\n  </file>\n</xliff>\n"
    )
    (OUT / "strings-en.xliff").write_text(xliff, encoding="utf-8")
    print(f"Wrote {len(rows)} units ({len(entries)} resources) to {OUT.relative_to(ROOT)}/")
    if unused:
        print(f"UNUSED ({len(unused)}): " + ", ".join(unused), file=sys.stderr)


if __name__ == "__main__":
    main()
