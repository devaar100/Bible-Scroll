# Decisions

Product and architecture decisions, newest first. Each one has a reason and a "revisit if" trigger.

## 2026-11-18: Verse of the Day (H1)

### 14. One curated verse per local month-day, same for everyone
`assets/votd.csv` holds 366 rows; the phone's local date picks the row, so the verse changes at local midnight and matches for everyone on that date, every year. 02-29 has its own entry (leap years only). The text uses the reader's translation, falling back to WEB with a label.
**Why:** predictable and shareable ("today's verse"), fully offline, no server or per-user state; the fallback keeps the card working for any future translation that omits a verse.
**Revisit if:** a translation bundled later (e.g. RV1909) lacks many list verses (VotdAssetTest will fail), or people want a different verse from the reminder or widget.

### 15. Widget Verse of the Day updates with an inexact, non-waking alarm
B11 chooses "A fixed verse" or "Verse of the Day". The midnight redraw uses `setAndAllowWhileIdle(RTC)` 1 minute after local midnight, only while a widget is placed and in that mode; it's re-armed by the reminder reschedule receiver (boot, time set, time-zone change, app update), at app start, and when a widget is added or removed.
**Why:** no SCHEDULE_EXACT_ALARM/USE_EXACT_ALARM permission, and no waking the phone just to redraw a widget: if it's asleep, the redraw runs as soon as the screen turns on.
**Revisit if:** users report the widget showing yesterday's verse after waking the phone.

## 2026-11-18: Localized content (Session 31a)

### 12. Plans and devotionals carry their own translations; studies stay English
Each plan/devotional file has `i18n.<lang>`; the app picks the UI language and falls back to English per field. Started plans read the translation from their own saved copy, so a plan started in English follows a language switch. Studies are English-only for now with a visible note.
**Why:** keeps one file per item (one hash, one URL), older apps ignore the new field, and nothing needs a second content release per language.
**Revisit if:** a language needs its own Scripture-dependent content (then studies need per-language generation, not translation).

### 13. Translate with Opus 5.5, check drift with Sonnet 5.5
A different model reviews meaning so errors aren't shared; free checks (numbers, reference numbers, Scripture quotes, lengths) run first. Blocking only on major issues (your rule, 2026-11-18): changed meaning, added/dropped claim, changed reference/name/number, mistranslated theological term. One retry with the problems fed back, then that item falls back to English. Minor issues (tone, nuance, quotation, style) are logged with counts. Model and prompt hashes are in each translation's meta. Plans: 29 translated, 0 blocking, 2 minor notes (John, Mark: quotation marks dropped).
**Revisit if:** the drift checker misses problems a reviewer finds (raise to Opus, or require review for everything), or flags too much to be useful.

## 2026-11-18: Spanish preparation

### 9. Per-app language without AppCompat
Android 13+: the system LocaleManager (system screen from S2, backed up by Android). Android 8–12: our own SharedPreferences (`app_language.xml`, in the backup rules) applied in MainActivity.attachBaseContext and to the Application's resources; moved to the system once after an upgrade to 13.
**Why:** AppCompatDelegate.setApplicationLocales only applies below 13 inside an AppCompatActivity; MainActivity is a ComponentActivity with a framework theme, and switching would add AppCompat and a theme migration for this alone.
**Revisit if:** we move to AppCompatActivity for another reason (then use AppCompat's autoStoreLocales and drop AppLanguage's < 13 path).

### 10. Spanish ships in release
`values-es/strings.xml` and `values-es/books.xml` ship, and `es` is in the release locales_config and `app_languages`. Spanish was offered in debug builds only until the translation was reviewed by a native speaker (done 2026-11-18).
**Revisit when:** strings change: run `tools/export_strings.py` and get new or changed Spanish strings translated before release (English shows until then).

### 11. Verse references are formatted from string patterns, held process-wide
`ref_verse`, `ref_verse_range`, `ref_chapter_range`, `ref_book_range`, `ref_chapter` per language; `References.patterns` is refreshed by AppLanguage on every language change, like Locale.getDefault(). Spanish uses the same "Juan 3:16" form (Latin American Reina-Valera usage), not Spain's "Jn 3,16".
**Why:** one place for every reference (12 hand-built ones before) without injecting a formatter into each ViewModel.
**Revisit if:** a screen needs two languages at once, or Spain-style references are wanted (add values-es-rES).

## 2026-11-18: Backup and export (Session 29)

### 6. Auto Backup is include-only: user.db (+ WAL) and the settings DataStore
Everything else (bible.db, plans.db, content.db, Discover cache, WorkManager DB, -shm files) ships in the app or re-downloads. Cloud backup requires end-to-end encryption on Android 12+ (`disableIfNoEncryptionCapabilities`); device transfer doesn't. A custom BackupAgent (fullBackupOnly) checkpoints the WAL before backup and marks restores.
**Why:** the old exclude-list backed up the 12.8 MB Bible DB on every backup. Measured on a test phone now: ~160 KB. Estimated heavy reader ~2 MB, extreme (20 copied Bible-in-a-year plans) ~8 MB, against the 25 MB limit.
**Revisit if:** a new file holds user data (add it to both rule files), or plan copies push backups near 10 MB.

### 7. Export format = user.db tables as JSON, keyed by column name, plus schemaVersion
One `.json` (format, schemaVersion = UserDatabase.VERSION, appVersion, exportedAt, tables, settings) and an optional readable `.md`. Import is strict, all-or-nothing, newest updatedAt wins per key, ties keep this phone, nothing is removed, settings only fill gaps.
**Why:** readable and diffable, easy to keep in step with Room (ExportCoversSchemaTest), and safe to import twice.
**Known gap:** deleted notes leave no tombstone, so importing an older file brings a deleted note back. Revisit if people report it (add a deleted flag to notes).
**Revisit if:** exports get large enough to need streaming, or a future table can't be merged by key + updatedAt.

### 8. A user.db that won't open is never deleted
SQLite's default corruption handler deletes the file; ours keeps it and copies it to noBackupFilesDir/user-db-rescue/. The app shows an error screen with "Save a copy of the data file" (zip) and "Try again". No destructive migration fallback.
**Why:** losing everything silently is the worst outcome for an app with no account to restore from.
**Revisit if:** we add a repair path (e.g. salvaging readable rows into a new DB).

## 2026-11-18: Privacy and data model for v1

### 1. No accounts, sign-in or sync
All user data (notes, likes, bookmarks, plans, streaks, settings) stays on the device.
**Why:** removes the backend, auth flows, account deletion and data-breach risk; fits "free, no tracking"; the app already worked fully signed out.
**Revisit if:** many users ask for multi-device use (e.g. phone + tablet at the same time), which backup and export can't cover.

### 2. New phone = Android Auto Backup + manual export/import
Auto Backup / device transfer carry user.db and settings; S9 Data & backup adds export/import (Session 29) with a newest-change-wins merge on `updatedAt`.
**Why:** covers the main real need (changing phones) without a server. `updatedAt` columns and tombstones stay for the merge.
**Revisit if:** restores often fail or lose data in reviews/support, or Auto Backup's 25 MB limit gets close.

### 3. No Crashlytics, analytics, crash-reporting or tracking SDK
Crashes are read from Play Console Android vitals; the release bundle carries the R8 mapping so traces are deobfuscated.
**Why:** no third-party SDK collecting data; a simpler Data safety form; Play vitals is enough for a small app. Crashlytics was never live (it needed a google-services.json that was never added).
**Revisit if:** Play vitals can't explain a crash that affects a significant share of users (e.g. missing breadcrumbs or non-fatal errors). Prefer an opt-in, self-hosted option before any SDK.

### 4. Only two kinds of network call
Static content from our CDN (no identifiers) and, from Session 33, one anonymous add-one Community vote counter in Cloud Firestore (no IDs, no auth). Every host is listed in docs/network-calls.md; cleartext is off.
**Why:** a short, checkable list keeps the "we don't track you" promise true and auditable.
**Revisit if:** a feature needs a new host. Add it to docs/network-calls.md and this file first. Also revisit if the vote counter is abused (without auth or App Check it can be inflated).

### 5. Community tab stays, as a monthly anonymous ministry vote
C1 is this month's vote with a running tally and "Give directly" links. C2 is past results. Votes are anonymous; the phone remembers locally that it voted (CommunityVote table, Session 33). The app handles no money.
**Why:** keeps the giving idea from the reference app without accounts, subscriptions or payments.
**Revisit if:** the counts are clearly manipulated, or the monthly list can't be curated reliably.
