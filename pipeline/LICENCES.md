# Licences of pipeline sources

Checked 2026-11-17 against each source itself: the downloaded files, the repo README, the
publisher's web page or the module's own config. Quotes are verbatim. This is not legal advice. Get
it reviewed before the app ships content built from these sources.

**Status key**
- **OK**: licence confirmed from the source; follow the attribution.
- **FLAG**: licence stated, but the source names upstream material whose terms differ or are unclear.
- **BLOCKED**: the source says permission is needed first. Do not ship that part.

## Summary

| Source | Used for | Licence (as stated by the source) | Status |
|---|---|---|---|
| STEPBible TAHOT | Hebrew words, Strong's, morphology, transliteration | CC BY 4.0 | **FLAG**: morphology from ETCBC |
| STEPBible TAGNT | Greek words, Strong's, morphology, transliteration | CC BY 4.0 | **FLAG**: NA28 spellings; English from BSB (now public domain) |
| STEPBible TBESH: gloss, lemma, morph columns | Hebrew lexicon glosses | CC BY 4.0 | **FLAG** |
| STEPBible TBESH: "Meaning" column (`lexicon.definition` for `H…`) | Hebrew definitions | © Larry Pierce / OnlineBible.net, permission required | **BLOCKED** |
| STEPBible TBESG | Greek lexicon (glosses, definitions) | CC BY 4.0 | OK |
| STEPBible TVTMS | Cross-check only; not shipped | CC BY 4.0 | OK |
| OpenBible.info cross references | Cross references with votes | CC BY 4.0 | OK |
| Treasury of Scripture Knowledge (CrossWire SWORD module `TSK` 1.4) | Cross references | Public Domain | OK |
| App KJV verse list (`app/src/main/assets/bible.db`) | Target versification only | See `tools/README.md` | (already documented) |
| Berean Standard Bible (eBible.org `engbsb`) | Bundled Bible text (BSB) | Public domain (dedicated 30 April 2023) | OK |
| Reina-Valera 1909 (eBible.org `spaRV1909`) | Bundled Bible text (RV1909) | Public domain | OK (see notes) |

## STEPBible data (TAHOT, TAGNT, TBESH, TBESG, TVTMS)

- **URL:** https://github.com/STEPBible/STEPBible-Data (files listed in `src/pipeline/sources.py`)
- **Licence:** Creative Commons Attribution 4.0 International (CC BY 4.0),
  https://creativecommons.org/licenses/by/4.0/
- **Source evidence:** the header of every file says: "Data created by www.STEPBible.org based on work at
  Tyndale House Cambridge (CC BY 4.0)". The repo README is headed "STEPBible Data Repository **CC BY 4.0**".
- **Required attribution:**
  - From the file headers: "Attribute the data to Tyndale House and STEPBible.org, and include a link to
    STEPBible.github.io/STEPBible-Data as the source of this data."
  - From the repo README: "**Credit it** to 'STEP Bible' linked to www.STEPBible.org".
  - Suggested in-app text: *"Hebrew and Greek data: STEP Bible (www.STEPBible.org), based on work at
    Tyndale House Cambridge. CC BY 4.0. Source: STEPBible.github.io/STEPBible-Data. Modified (reformatted,
    re-versified to KJV)."*
- **Conditions in the files:**
  - "You MAY make changes yourself, but you should include a note of changes that can be viewed by those
    who use your new data". `out/versification_report.md` records ours.
  - "Please do not post copies of this data file elsewhere, so that this remains the single up-to-date
    source". So: don't commit or mirror the raw files (they are gitignored), and link to the source.

### FLAG: TAHOT morphology (ETCBC)

TAHOT says: "Morphology is from ETCBC, with Ketiv forms parsed by Tyndale scholars". The ETCBC data
repository (https://github.com/ETCBC/bhsa) gives two different licences:
- Its README says: "This work is licensed under a Attribution-NonCommercial 4.0 International (CC BY-NC
  4.0)".
- Its LICENSE file is the MIT License.

STEPBible publishes the converted codes as CC BY 4.0. Our app is non-commercial (free, no ads), but CC
BY-NC terms still need checking. **Confirm with STEPBible (STEPBible@Gmail.com) that the TAHOT `Grammar`
column is cleared for CC BY use** before shipping Hebrew morphology. Hebrew words, Strong's numbers and
transliteration are not affected: the text is the public-domain Westminster Leningrad Codex, via
OpenScriptures.

### FLAG: TAGNT Greek text and English glosses

- **Greek text:** "Spelling comes from NA28 when available, then from TR or the Other edition". The
  Nestle-Aland 28 text is copyrighted (Deutsche Bibelgesellschaft). STEPBible publishes the word list as
  CC BY 4.0, and each word records which editions contain it (`word.editions`). Showing single tagged
  words is low risk. **Do not reconstruct and display a continuous "NA28 text"** without checking. If in
  doubt, show only words whose `editions` include `TR`, `Byz`, `SBL`, `WH` or `Treg`.
- **English glosses:** "Based on Berean Study Bible, with permission". The Berean site
  (https://berean.bible/terms.htm) now says: "The Berean Bible and Majority Bible texts are officially
  dedicated to the public domain as of April 30, 2023. All uses are freely permitted." Low risk.

### BLOCKED: TBESH "Meaning" column, and a flag on the rest of TBESH

The TBESH file header says:

> "The Brief lexicon is based on Abridged BDB by Online Bible, and edited to conform with the extended
> Strongs. This is provided for guidence only. Permission should be gained from Online Bible before these
> definitions are applied in any project."

Its field notes add: "Meaning: These are based on the Abridged BDB by Online Bible, © Larry Pierce of
OnlineBible.net <olbsupport@onlinebible.net>."

- **Do not ship `lexicon.definition` for Hebrew (`H…`) entries** unless OnlineBible.net gives written
  permission.
- The short **Gloss** column isn't named in that notice, but it comes from the same lexicon. Treat it as
  **unconfirmed** until the permission question is answered.
- **Safer alternatives for Hebrew glosses:**
  - the per-word gloss inside TAHOT's "Expanded Strong tags" (CC BY 4.0);
  - the public-domain 1906 BDB via OpenScriptures' HebrewLexicon (check its licence first).

TBESG (Greek) says: "The Brief lexicon is based on the Abbott-Smith definitions" (1922; public domain).
It has no permission notice and is covered by STEPBible's CC BY 4.0. **OK.**

## OpenBible.info cross references

- **URL:** https://www.openbible.info/labs/cross-references/ (data:
  https://a.openbible.info/data/cross-references.zip)
- **Licence:** CC BY 4.0. The page says "Unless otherwise indicated, all content is licensed under a
  Creative Commons Attribution License", linking to http://creativecommons.org/licenses/by/4.0/. The data
  file's header line reads `#www.openbible.info CC-BY <date>`.
- **Required attribution:** credit OpenBible.info with a link and the licence. Suggested: *"Cross
  references: OpenBible.info (www.openbible.info/labs/cross-references), CC BY 4.0. Modified (re-versified
  to KJV)."*
- The page also says the data "draws primarily from public-domain sources, especially the Treasury of
  Scripture Knowledge". The vote scores are OpenBible's own.

## Treasury of Scripture Knowledge (CrossWire SWORD module)

- **URL:** https://www.crosswire.org/ftpmirror/pub/sword/packages/rawzip/TSK.zip (module info:
  https://www.crosswire.org/sword/modules/ModInfo.jsp?modName=TSK)
- **Licence:** public domain. The module's own `mods.d/tsk.conf` says `DistributionLicense=Public Domain`,
  `Version=1.4`, and "About=Treasury of Scripture Knowledge: five-hundred thousand scripture references
  and parallel passages by Canne, Browne, Blayney, Scott, and others about 1880".
- **Attribution:** none required. Suggested courtesy: *"Treasury of Scripture Knowledge (c. 1880), public
  domain; digital edition by CrossWire Bible Society."*
- The commercial "TSK Enhanced/Expanded" editions are a different, copyrighted work. Use only this module.

## Bible translations bundled in bible.db (checked 2026-11-18)

Built by `tools/build_bible_db.py`; the report of every verse that doesn't map one-to-one is `tools/bible_db_report.md`.
WEB and KJV are documented in `tools/README.md`.

### Berean Standard Bible (BSB)

- **Source:** https://eBible.org/Scriptures/engbsb_usfm.zip (details: https://ebible.org/find/details.php?id=engbsb).
  eBible.org "English Berean Standard Bible", contributor "BSB Publishing, LLC", page "Last updated 2026-10-30";
  the zip's `copr.htm` says "Public Domain … from source files dated 30 Oct 2026". SHA-256 of the file used:
  `75ca91d5a5c69ca4cbdeabdb03ea1a458db20cf3966abe15d86df086acf7fbc4`.
- **Licence:** public domain. The official site (https://berean.bible/terms.htm) says: "The Berean Bible and Majority
  Bible texts are officially dedicated to the public domain as of April 30, 2023." and "All uses are freely permitted."
  eBible.org's page and the zip both say "Public Domain".
- **Attribution:** not required ("Attribution Notice (appreciated but not required)"). We show: *"The Holy Bible,
  Berean Standard Bible, BSB. Dedicated to the public domain. Source: eBible.org (engbsb)."*
- **Condition (a request, not a licence term):** "For derivative works that vary from the official text, we
  respectfully request that the Berean name is not used." We only remove markup, footnotes, headings and Strong's
  numbers; the words are unchanged, so we use the name. **Do not edit BSB verse text.**
- **Red letters:** the source has no `\wj` markup, so `has_red_letters = 0`.
- **Versification:** KJV numbering; 16 verses (Matthew 17:21, Acts 8:37, …) are omitted by the translation, not lost.

### Reina-Valera 1909 (RV1909)

- **Source:** https://eBible.org/Scriptures/spaRV1909_usfm.zip (details: https://ebible.org/find/details.php?id=spaRV1909).
  "Santa Biblia — Reina Valera 1909", "Translation by: Reina y Valera", page "Last updated 2013-12-13"; the zip's
  `copr.htm` says "Public Domain … Dominio Público … from source files dated 10 Aug 2015". SHA-256:
  `56937c2be9ad278aed91370d6a15a98fe976f275d0cbaa6e26910291d949c7a5`.
- **Licence:** public domain (published 1909; eBible.org and the file both state "Public Domain" / "Dominio
  Público"). There is no official publisher site for the 1909 text to check against; the eBible.org statement is the
  only one available. **FLAG (low):** confirm public-domain status for the target countries with a lawyer, as for KJV.
- **Attribution:** none required. We show: *"Santa Biblia, Reina-Valera 1909. Dominio público. Source: eBible.org
  (spaRV1909)."*
- **Red letters:** no `\wj` markup, so `has_red_letters = 0`.
- **Changes we make (all listed in `tools/bible_db_report.md`):** markup, `\add` brackets and Strong's numbers removed
  (words kept); psalm titles moved from verse 1 to the chapter superscription for the 116 psalms KJV titles; the 22
  Hebrew letter headings in Psalm 119 removed; 15 shifted verse blocks put back under their KJV references; 11 places
  where the source merged 2–9 verses into one kept whole under the first KJV reference (18 KJV references have no RV1909
  verse of their own). Verse text itself is not edited. The source keeps chapter-initial capitals ("JEHOVÁ es mi
  pastor") and some Pauline subscriptions in final verses (e.g. 2 Corinthians 13:14); we keep them as published.

## Before shipping study content: to do

1. Email OnlineBible.net about TBESH definitions and glosses, or switch Hebrew glosses to a confirmed source.
2. Email STEPBible to confirm the ETCBC-derived TAHOT morphology is cleared for CC BY 4.0 use.
3. Add the STEP Bible and OpenBible.info attributions to Settings → Legal (S8) and to each study's Sources
   list (spec: "Citations on every study").
