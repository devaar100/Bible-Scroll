# tools/

## build_bible_db.py — bundled Bible database

Generates `app/src/main/assets/bible.db`, which Room copies on first launch (`createFromAsset`).

### 1. Download the sources (USFM, from eBible.org)

| Translation | Source page | Direct download |
|---|---|---|
| KJV (`eng-kjv2006`) | https://ebible.org/find/details.php?id=eng-kjv2006 | https://eBible.org/Scriptures/eng-kjv2006_usfm.zip |
| WEB (`engwebp`, Protestant canon) | https://ebible.org/find/details.php?id=engwebp | https://eBible.org/Scriptures/engwebp_usfm.zip |

```sh
mkdir -p ~/bible-src && cd ~/bible-src
curl -fLO https://eBible.org/Scriptures/eng-kjv2006_usfm.zip
curl -fLO https://eBible.org/Scriptures/engwebp_usfm.zip
```

Don't commit the zips. Only the generated `bible.db` goes in the repo.

### 2. Build

```sh
./gradlew assembleDebug            # exports the Room schema the script reads
python3 tools/build_bible_db.py --kjv ~/bible-src/eng-kjv2006_usfm.zip --web ~/bible-src/engwebp_usfm.zip
```

If you change an `@Entity` in `data/bible/`, bump `BibleDatabase.version`, build once, then rerun the script.

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

## Release checklist: study content

Before a release build (`./gradlew bundleRelease`):

1. `cd pipeline && PYTHONPATH=src .venv/bin/python -m pipeline publish --dry-run` and read what would change.
2. `PYTHONPATH=src .venv/bin/python -m pipeline publish`: deploys and verifies.
3. `PYTHONPATH=src .venv/bin/python -m pipeline bundle`: copies Gospels and Psalms studies into
   `app/src/main/assets/studies/`.
4. Commit `app/src/main/assets/studies/` with the release, like `bible.db`.
