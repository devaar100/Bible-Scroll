# tools/

## build_bible_db.py — bundled Bible database

Generates `app/src/main/assets/bible.db`, which Room copies on first launch (`createFromAsset`).

### 1. Download the sources (USFM, from eBible.org)

| Translation | Source page | Direct download |
|---|---|---|
| KJV (`eng-kjv2006`) | https://ebible.org/find/details.php?id=eng-kjv2006 | https://eBible.org/Scriptures/eng-kjv2006_usfm.zip |
| WEB (`engwebp`, Protestant canon) | https://ebible.org/find/details.php?id=engwebp | https://eBible.org/Scriptures/engwebp_usfm.zip |
| BSB (`engbsb`) | https://ebible.org/find/details.php?id=engbsb | https://eBible.org/Scriptures/engbsb_usfm.zip |
| RV1909 (`spaRV1909`) | https://ebible.org/find/details.php?id=spaRV1909 | https://eBible.org/Scriptures/spaRV1909_usfm.zip |

```sh
mkdir -p ~/bible-src && cd ~/bible-src
for id in eng-kjv2006 engwebp engbsb spaRV1909; do curl -fLO https://eBible.org/Scriptures/${id}_usfm.zip; done
shasum -a 256 *_usfm.zip   # compare with tools/bible_db_report.md → Sources
```

The committed bible.db was built from the files whose SHA-256s are in `tools/bible_db_report.md`. eBible.org updates
these files in place; if a hash differs, the rebuilt text may differ too (the report and `pipeline/tests/test_bible_db.py`
show what changed). Same files in, byte-identical bible.db out.

Don't commit the zips. Only the generated `bible.db` goes in the repo.

### 2. Build

```sh
./gradlew assembleDebug            # exports the Room schema the script reads
python3 tools/build_bible_db.py --kjv ~/bible-src/eng-kjv2006_usfm.zip --web ~/bible-src/engwebp_usfm.zip \
    --bsb ~/bible-src/engbsb_usfm.zip --rv1909 ~/bible-src/spaRV1909_usfm.zip
cd pipeline && .venv/bin/python -m pytest -q tests/test_bible_db.py   # counts, spot checks, RV1909 mapping
```

It writes `app/src/main/assets/bible.db` (24.4 MiB with four translations; about 9 MB deflated in the AAB) and
`tools/bible_db_report.md` (every verse missing, extra, moved or merged, per translation).

**App side:** a new `bible.db` reaches existing installs only after `BibleDatabase.VERSION` is bumped (the destructive
fallback re-copies the asset). Per-translation metadata the Room entities don't have (attribution, `has_red_letters`,
English/Spanish descriptions, source URL and SHA-256) is in the `translation_info` table, which Room ignores
(the app reads `has_red_letters` with a raw query, BibleDao.translationInfo).

To bump the version without new sources: raise `BibleDatabase.VERSION`, run `./gradlew assembleDebug` (exports
the new schema JSON), then rerun the script, or, if no `@Entity` changed (same identity hash), just
`sqlite3 app/src/main/assets/bible.db "PRAGMA user_version = N"`. Version 2 was done that way (Session 31b).

If you change an `@Entity` in `data/bible/`, bump `BibleDatabase.VERSION`, build once, then rerun the script.

### Licence status (checked 2026-11-17 on the eBible.org pages above)

- **WEB**: Public domain, not copyrighted. "World English Bible" is a trademark of eBible.org,
  so if the text is *changed* it may not be called the WEB. The script only removes markup,
  footnotes and Strong's numbers and leaves the words alone. Do not edit verse text.
- **KJV**: Public domain everywhere except the UK. There, Crown letters patent give Cambridge
  University Press (and the Crown's other patentees) exclusive printing rights. eBible.org states the patent
  "has no effect outside of the UK". Get legal advice before distributing in the UK (Play Store
  country targeting), as the spec requires.

### What the script does to the text

- Keeps the words of Jesus (`\wj`) as offset ranges in `verses.jesus_ranges` (for red letters).
- Keeps psalm titles (`\d`) in `chapters.superscription`. Drops section headings and footnotes.
- Removes KJV `¶` marks. Keeps KJV supplied words (`\add`, usually italic) as plain text.
- Skips verses that have no text in a translation (WEB Luke 17:36, Acts 8:37, 15:34, 24:7,
  Romans 16:25 exist there only as footnotes). WEB puts Romans 16:25–27 at 14:24–26.
- Drops `\qa` acrostic headings (BSB) and empty `\d` titles (BSB Zechariah 12).
- RV1909: remaps the Spanish chapter divisions to KJV keys (`RV1909_SHIFTS`, `RV1909_MERGES`), splits psalm titles out
  of verse 1 where KJV has a title, removes Psalm 119's letter headings. Fails loudly if any rule doesn't match.
- Licence status of BSB and RV1909: `pipeline/LICENCES.md` → "Bible translations bundled in bible.db".

## Release checklist: study content

Before a release build (`./gradlew bundleRelease`):

1. `cd pipeline && PYTHONPATH=src .venv/bin/python -m pipeline publish --dry-run` and read what would change.
2. `PYTHONPATH=src .venv/bin/python -m pipeline publish`: deploys and verifies.
3. `PYTHONPATH=src .venv/bin/python -m pipeline bundle`: copies Gospels and Psalms studies into
   `app/src/main/assets/studies/`.
4. Commit `app/src/main/assets/studies/` with the release, like `bible.db`.
