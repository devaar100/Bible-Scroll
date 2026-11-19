# Project: free Android Bible-scrolling app

Full spec: docs/spec.md. Background: docs/context.md. Read the relevant spec section before building a screen.

## Hard rules
- 100% free: no billing library, no entitlement checks, no "premium" flags, no ads, no tracking SDKs.
- Original name, copy, colours and art.

## Privacy & network (decided 2026-11-18; reasons in docs/decisions.md)
- No accounts, no sign-in, no sync. All user data stays on the device. A new phone is covered by Android Auto Backup plus manual export/import (S9 Data & backup, Session 29).
- No Firebase Auth, no App Check, no Firebase Analytics, no Crashlytics, and no other analytics, crash-reporting, ads or tracking SDK. No AD_ID permission in the merged manifest.
- Crashes come from Play Console vitals; keep R8 on so the release bundle carries the mapping file.
- Allowed network calls only: static content downloads from our CDN (no user identifiers), and from Session 33 one anonymous Community vote counter in Cloud Firestore (add-one only, no IDs, no auth).
- Any new network call (host, library, worker or widget) must be added to docs/network-calls.md in the same change. HTTPS only (network_security_config.xml).
- Keep updatedAt columns and tombstones on user tables: the Session 29 import merge depends on them.
- New user.db table or column: bump UserDatabase.VERSION, add it to data/backup/BackupFormat.kt (row class + entity mappers) and its merge key in BackupMerge.kt/BackupValidator.kt. ExportCoversSchemaTest fails until you do. New DataStore key: list it in BackupSettings.EXPORTED or NOT_EXPORTED.
- Backup rules are include-only (res/xml/backup_rules.xml, data_extraction_rules.xml): a new file that holds user data must be added there, or it won't survive a phone change.
- Run instrumented tests with `adb install -r` + `adb shell am instrument`, not `connectedAndroidTest`, on a phone with real data: Gradle uninstalls the app afterwards, which deletes its data.

## Phase 4 checklist (Data & Community)
- [x] Session 29: S9 Data & backup: Auto Backup rules, export/import with newest-change-wins merge on updatedAt, restore fix-ups, no-delete DB safety
- [ ] Session 30a: Reina-Valera 1909 in the pipeline (eBible source, LICENCES.md, versification report: 18 shifted verses, psalm titles in verse 1)
- [x] Session 31a: localized plans/devotionals (pipeline `translate` with drift check; app shows them in the UI language), "studies are in English" note
- [ ] Session 31b: bundle RV1909, onboarding offer on Spanish phones, reminder verses in the chosen translation
- [ ] Session 31c: wrap-up: Session 29 tests, secrets scan, APK size, signed-out end-to-end on a `qa` variant, docs/data-safety.md
- [x] Verse of the Day (H1 card + widget mode; definition in docs/spec.md). Verse Search and Today's Reading cards: not built
- [ ] Session 32: TBD
- [ ] Session 33: C1/C2 Community anonymous monthly vote: local CommunityVote table + Firestore add-one counter (add host to docs/network-calls.md)
- [ ] Session 34: TBD

## Stack
Kotlin, Jetpack Compose, Material 3, minSdk 26. MVVM + unidirectional data flow, Hilt, Navigation Compose.
Room (bundled Bible DB via createFromAsset + separate user DB), DataStore for settings, Coil, WorkManager.

## Conventions
- All user-facing text in res/values/strings.xml: positional args (%1$s), plurals for counts, no "a + b" sentences. Two labels on one line: R.string.two_parts. Lists: formatList/localizedList (ListFormatter). Verse references: References (data/bible/References.kt), never "$name $c:$v". Book names come from BibleRepository.books() (localized via res/values*/books.xml). LocalizationTest guards these.
- After adding or changing strings: `python3 tools/export_strings.py` (docs/translation/). New strings appear in English in Spanish builds until translated.
- New screen with lots of text: add it to PseudoLocaleScreenshotTest (en-XA, 320 dp).
- One feature per session. Keep changes small; app must build and run after every task.
- Room reads off the main thread, exposed as Flow. ViewModels expose StateFlow<UiState>.
- Fonts: Literata for Scripture/headlines, Inter for UI. Words of Jesus in red (tuned per reading background); likes use the user's accent (default Crimson).
- Use version catalog (gradle/libs.versions.toml) for all dependencies.
- After changes, run ./gradlew assembleDebug and fix errors before saying you're done.