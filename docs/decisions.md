# Decisions

Product and architecture decisions, newest first. Each one has a reason and a "revisit if" trigger.

## 2026-11-22: Study pipeline simplified

### 25. Only code checks block a study; the AI reviewer is advisory; prompts are frozen
Blocking checks are code only (pipeline/src/pipeline/checks.py BLOCKING): references correct and allowed, length
limits, no verse numbers or positions, no leaked instructions or placeholders, and quoted original words present in
the verse (plus schema, a generation failure and an open reader report). A study that fails one gets at most one
retry, then status `failed`. Everything else, the Haiku reviewer (factual risk, one-sided theology, tone,
contextMismatch) and the non-blocking code checks (banned phrases, stock traditions lines, a listed word's raw form
such as "he'ōs"), is advisory: kept in `checks.advisory`, listed in reviewer_report.md per wave, never blocking and
never retried. Amended 2026-11-22 (your call): a transliteration written the raw STEPBible way inside one word, with
syllable dots ("be.re.Shit") or a stress capital ("bereShit"), blocks too (checker 12).
Amended again 2026-11-22 (your call): "no verse numbers or positions" now means numbers only ("verse 5", "vv. 3-4",
"v. 5", "verse three"); position wording such as "the verses before it" or "the next verse" passes (checker 13).
"Did you know?" is optional: the writer leaves it empty unless it is confident the fact is about this verse's own
words; an empty one passes and the app hides it. The writer prompt (prompts/study.md) and the reviewer rubric
(rubric.py prompts and schema, .claude/agents/study-reviewer.md) are frozen as of this decision
(pipeline/tests/test_frozen.py); no more rubric changes or test rounds.
**Why:** your call (2026-11-22). Tuning the reviewer took several rounds without converging (decision 21's bar, then
the contextMismatch fixes), and in the 10-verse API run the reviewer's flags were mostly judgment calls; the code
checks catch the mistakes a reader would notice. Supersedes decision 21.
**Revisit if:** reviewer reports show a pattern of real errors a reader would notice that no code check catches.
Changing a frozen prompt needs a new decision and an update to pipeline/tests/test_frozen.py.

## 2026-11-19: Study readiness bar

### 21. Only reviewer ERRORs block; up to 6 WARNs per 20 studies are allowed
**Superseded by decision 25 (2026-11-22).**
In the Claude Code test rounds (pipeline/CHANGELOG-prompts.md), a round is ready when, among other criteria, the
reviewer reports ERROR = 0 and at most 6 WARNs per 20 studies (was: ERROR = 0 and WARN <= 4). These are always ERROR,
never WARN (.claude/agents/study-reviewer.md): subject and object reversed, a phrase attached to the wrong person or
clause, a reference that is wrong, incomplete or outside the listed passages, leaked pipeline or instruction wording or
a placeholder, and a "Did you know?" that isn't about this verse's own words. A study with an ERROR is regenerated once.
**Why:** your call (2026-11-19). The WARN criterion failed three rounds in a row while round 3 had no rule breaks, leaks,
wrong references or off-verse facts; the remaining WARNs were judgment calls spread across sections, not a pattern a
prompt change could remove.
**Revisit if:** a WARN turns out to be a real error a reader would notice, or WARNs cluster in one section or category.
Don't retune the number per run.

## 2026-11-19: Community donation vote and history

### 23. The monthly vote decides a real donation; a published history shows where it went
The Community vote now has a stated purpose: "Each month, readers vote for one ministry. When the month ends, we
donate to the one with the most votes." No vote counts or percentages are shown anywhere (the old C2 totals are gone;
counters are read only to tell whether voting is open). After the month ends we publish `history.json` on the CDN
(pinned in manifest.history; pipeline `community/history.json`, schema/history.json; publish refuses a month that
isn't over yet or appears twice). The app shows each finished month's ministry and amount exactly as published,
formatted with the record's currency, and never computes, estimates or pre-announces an amount (`amountPlanned` is
ignored). It fetches the file at most once per UTC calendar month (daily while last month's record is missing) and
keeps it in files/content-cache, outside Auto Backup and the export file. Ministry cards gain optional `category`
(comma-separated) and `image` (our Firebase Hosting site or our Firebase Storage bucket only, checked by the pipeline
and again by the app; downloaded with our OkHttp client into cacheDir and drawn by Coil from the file).
**Data safety:** unchanged. history.json and images are public content downloads with no identifiers; nothing new is
collected, stored about the person or sent (docs/data-safety.md answers stand; still no personal data).
**Accepted trade-off:** decision 16's "fine for a spotlight, not for anything with money attached" no longer holds:
votes are one per install, and the counter can be inflated outside the app. Your call (2026-11-19): proceed, since
gaming a small monthly vote is unlikely.
**Why:** the approved design; a public record of each donation keeps the promise checkable without accounts.
**Revisit if:** counts look manipulated before a payout (check the counter's growth, then rate-limit or add App Check),
or a donation needs to be shown before the month ends.

## 2026-11-19: Discover card redesign

### 22. Discover cards carry a required theme label, and stay visible with Hide AI studies on
discover.json moved to format 2: each verse is `{"id", "theme"}`, where theme is a free-text label of 1-40 characters
("Trust and guidance") shown on the card as written. Every card uses the one approved layout (wave art, wave icon, in the
app's own colours); the label is the only thing that changes, so new themes need no app release (2026-11-19: replaced the earlier
three fixed themes with their own colours, at your call). The pipeline refuses to publish a discover.txt line without
a theme or with one over 40 characters (the batch wave still reads ids alone), and the app ignores a format-1 file and
any entry with a blank or over-long theme, so a card is never drawn without one. The list itself is Scripture, so Discover no longer disappears when Hide AI
studies is on: the cards drop the AI-written hook and "Did you know?", show a line from the source data (original
language, cross-reference count) when the study is on the phone, open the verse in context instead of the study, and
nothing is prefetched. The discover.json download now happens with the setting on too (no AI text in it; same host).
**Why:** the approved design shows a theme on each card, you wanted every card to need one, and there will be many
themes; hiding the whole tab took Scripture away from people who only object to AI prose.
**Revisit if:** labels need translating (they show as written, so Spanish UIs see the English label), or people with
the setting on ask for a fully AI-free Discover tab.

## 2026-11-19: Community refresh and empty states

### 18. The Community tab re-checks its list when stale (6 h) or re-pinned, and reads this month's counters on open
The ministry list is cached on disk with its sha256 and last-check time (`content-cache/ministries.meta`). Showing the
tab re-checks the CDN manifest only if nothing is cached, the check is over 6 hours old, or a manifest cached by another
download pins a different hash; `ministries.json` is downloaded only when its hash changed. Pull to refresh and "Try
again" always check. A manifest with no `community` entry clears the cached list (withdrawn lists stop showing). This
month's Firestore counters are read (anonymous `batchGet`) when the tab opens, so the screen can say "not open yet"
(counters missing or window not started) or "closed" instead of failing on Vote; totals still show only after voting.
Each empty case has its own message: offline with nothing cached, list not published, list failed its checks, list
empty, no ministry this month (with the next vote's month when known), round not open/closed, totals unavailable.
Debug builds show a line naming each source and when it was last fetched.
Counter reads are cached in memory per month for 5 minutes (1 hour once the month has closed); refresh and voting
bypass the cache. With 5 ministries and one past month that's at most 10 reads per open and at most ~120 per hour of
continuous reopening, so the 50,000 free reads a day cover thousands of daily readers.
**Why:** previously every visit re-downloaded both files, a withdrawn list stayed visible offline, and one generic
"couldn't be loaded" message covered five different situations, which made the launch hard to check on a phone.
**Revisit if:** the extra counter read per visit shows up in Firestore's free-tier read quota (then cache it for a few
minutes), or a list must reach phones faster than 6 hours without a manual refresh.

### 19. A release publish refuses a ministries file with test entries or no entries
`pipeline publish` (and `--dry-run`) stops if `community/ministries.json` is missing, empty, or holds any `test` entry,
on top of the existing checks (https only; `give_url` on the website's registrable domain, with an allow-list of
confirmed alternates such as Peter's Pence: `obolodisanpietro.va` / `peterspence.va`).
**Why:** test entries are for debug builds and the emulator; a release must never ship them, and an empty list would
publish a Community tab with nothing in it.
**Revisit if:** a release ever needs to go out while the list is deliberately empty (then allow it with an explicit flag).
**Note:** the test placeholders were replaced by the five real entries on 2026-11-19 (docs/ministry-criteria.md).

## 2026-11-19: Firestore database for the Community vote

### 20. One `(default)` database, Native mode, Standard edition, nam5, on the free tier
Created from the CLI on 2026-11-19 (`firebase firestore:databases:create "(default)" --location nam5 --edition
standard`). The project has no billing account (Cloud Billing `billingEnabled: false`), so usage is capped at the free
quota (50,000 reads, 20,000 writes a day) rather than billed. Rules from `backend/firestore.rules` deployed with
`--only firestore:rules` after the emulator tests passed; new rules took about 8 minutes to reach every server.
**Why:** US multi-region keeps the counters available without billing; one database keeps rules and seeding simple.
**Revisit if:** reads approach the free quota (lengthen the counter cache before considering billing).
## 2026-11-19: Verse scrubber redesign

### 24. The scrubber's touch strip runs 0 to 48 dp from the edge, and only the active capsule drags
The track sits 8 dp from the start edge, so a 48 dp strip centred on it would hang 16 dp off-screen; the strip starts at the edge instead, with the track inside it. Only the active capsule (a zone at least 48 dp tall) can be dragged; a tap elsewhere on the track jumps there, and a drag that starts elsewhere does nothing. The system back gesture is excluded only in a 160 dp band that follows the active capsule (Android allows 200 dp per edge).
**Why:** your call (2026-11-19). Grabbing only the active capsule keeps stray edge swipes from scrubbing, and the small exclusion leaves back working along the rest of the edge. Tested on an SM-M146B with gesture and three-button navigation: grabs at the edge were never taken by the system, and back still worked away from the capsule.
**Revisit if:** a device's gesture navigation still takes the grab (then switch to press-and-hold to grab), or people expect dragging anywhere on the track to scrub.

## 2026-11-19: Launch content run

### 17. Every study is generated and reviewed by Claude Haiku 5.5 at effort "medium"
The launch run (waves.yaml: discover, Mark, Matthew, Luke, John, Psalms) uses claude-haiku-5-5 at effort medium for generation (Message Batches, max_tokens 32,000, a max_tokens stop counts as a failed attempt) and for the AI reviewer. No Opus or Sonnet anywhere in it. Code checks reject references outside the verses shown, verse-number/position wording, sections more than 20% over or under their range, pipeline wording, dotted transliterations and Hebrew/Greek words not in the verse data; a flagged verse is regenerated once, then marked `failed` and never published. Model, effort, max_tokens and prompt hashes are in every study's meta. (2026-11-19: max_tokens raised from 16,000 to 32,000 after Claude Code test round 1 showed 2 of 20 Haiku drafts above 16,000 output tokens and 6 more at 14,400–15,300.)
**Why:** your call (2026-11-19). One cheap model keeps the whole run near $12 (estimate for 6,476 verses incl. retries and review) and one cache namespace; the stricter prompt and the code checks carry most of the safety.
**Revisit if:** the flagged or failed share climbs well above the pilot's ~8%, unverified reviewer quotes become common, or reviewers find errors the checks missed (then raise generation or review for that wave only).

## 2026-11-18: Community vote (Session 33)

### 16. An anonymous, add-only counter: "+1 for ministry X in month M"
Each vote is one Firestore REST commit with an increment-by-1 transform on `community_votes/{YYYY-MM_ministryId}`. No account, no Auth, no App Check, no Firebase SDK, no identifier of any kind; the phone remembers its own pick in user.db (`community_votes`). Rules (`backend/firestore.rules`, emulator-tested) allow only `count` to change, only by exactly +1, only between `opensAt` and `closesAt`; clients can't create or delete. Months are UTC; the app shows them in local time and blocks voting outside the window, the server enforces it.
**Accepted trade-offs:**
- One vote per month **per install**, not per person: clearing data or reinstalling allows a new vote, and nothing server-side can tell. Fine for a "spotlight", not for anything with money attached.
- A vote whose request reached the server but whose reply was lost is retried and counted twice. Rare; accepted rather than adding a request id (an identifier).
- Without Auth or App Check, anyone can send +1s outside the app (one per request). The rules stop resets, decrements and big jumps, not repetition.
- Votes are final: rules forbid decrements, so the app has no "change my vote". Import (S9) never replaces a month this phone already voted in.
**Why:** keeps the "no account, no tracking" promise while letting readers pick which ministry is featured; the app handles no money ("Give directly" opens the ministry's own page).
**Revisit if:** counts look manipulated (rate-limit via a Cloud Function or App Check, and update docs/network-calls.md), or the spotlight ever drives money decisions. (It now does: see decision 23.)

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
