# Project status audit

Audit date: 2026-11-18 (read-only; the only file changed is this one). Sources: CLAUDE.md, docs/spec.md,
docs/decisions.md, docs/network-calls.md, pipeline/CLAUDE.md, the code, the build and test runs below, and a
read-only GET of the live CDN manifest.

**Headline:** the app builds and every unit test passes, and Phases 1-3 plus Sessions 29 and 31a are
implemented. But **almost all work since "phase 2 done" is uncommitted** (Phase 3, Session 29, Spanish, 31a),
**no study, Discover card or devotional content has reached users** (the CDN manifest is empty and nothing is
bundled), and B8, S5, S7, S8, C1 and C2 are missing.

---

## 1. Build health

| Check | Command | Result |
|---|---|---|
| Debug build | `./gradlew assembleDebug` | **PASS** (tasks were up to date, so the log doesn't show compiler warnings). `app-debug.apk` 33.2 MB |
| Release bundle | `./gradlew bundleRelease` | **PASS**. `app-release.aab` 13.4 MB. R8 on, resource shrinking on, `proguard.map` and `baseline.prof` are inside `BUNDLE-METADATA/`. **The bundle is unsigned**: `app/build.gradle.kts` has no release `signingConfig`, so it can't be uploaded as is (fine if you sign in Android Studio or use Play App Signing with an upload key) |
| Unit tests | `./gradlew testDebugUnitTest --rerun --continue` | **PASS: 360 tests, 0 failed, 0 skipped** (`:app` 320, `:core:content` 35, `:core:plans` 5) |
| Pipeline tests | `pipeline/.venv/bin/python -m pytest` | **326 passed, 1 failed**: `tests/test_studies_output.py::test_prose_empty_and_status_generated`. The test expects every study to have empty prose and status `generated`. That's no longer true because the 10-verse pilot wrote prose. The test is out of date; nothing is broken |
| Instrumented tests | not run | 10 files in `app/src/androidTest` (migration, backup DB, DAO, pseudo-locale screenshots, accessibility). Not run, per CLAUDE.md's warning about phones with real data; see §9 |

Bundle contents: `bible.db` 12.8 MB, 29 plan JSONs + `index.json` + `verses.bin`, and OkHttp's `PublicSuffixDatabase.list`.
There are **no `assets/studies/` and no `assets/devotionals/`** (see §4 and §6).

## 2. Git

- Branch: `main`. **5 commits in total** (you asked for the last 60, and these are all of them):

| Date (CST) | Hash | Summary |
|---|---|---|
| 2026-11-18 16:55 | 784a907 | pipeline: snapshot code after the 10-verse rerun (prompt 62631d6c8e7e, checker 3) |
| 2026-11-18 16:16 | f4f8491 | pipeline: study prompt rules 8-14 and pipeline wording check list (hash 62631d6c8e7e) |
| 2026-11-18 16:03 | 282ac41 | pipeline: snapshot pilot study prompt and checks (hash 844b242d7ceb) |
| 2026-11-17 23:30 | 0ca8a13 | phase 2 done |
| 2026-11-17 16:28 | 65cf966 | initial commit |

- Working tree (`git status --porcelain -uall`): **2,136 entries: 620 modified, 1,508 untracked, 8 deleted.**
  - Most of these are build output: **2,860 files under `core/content/build/` and `baselineprofile/build/` are tracked in git**.
    The root `.gitignore` has `/build`, which only ignores the root build folder, not each module's. The 8 "deleted" files are
    stale `core/content/build/tmp/...` class stashes.
  - Not counting build output: **89 modified and 135 untracked source/doc files**. That's all of Phase 3, Session 29 and the
    Spanish/31a work. Nothing guards them except this laptop.

**Modified (not build output, 89):** CLAUDE.md, build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml, docs/spec.md,
app/build.gradle.kts, AndroidManifest.xml, res/values/strings.xml, res/xml/backup_rules.xml, res/xml/data_extraction_rules.xml,
all 30 `app/src/main/assets/plans/*.json`, BibleScrollApplication, MainActivity, MainViewModel, data/bible/{BibleRepository,
model/BibleModels}, data/library/LibraryRepository, data/reminder/{ReminderNotifier, ReminderReceivers, ReminderScheduler},
data/settings/SettingsRepository, data/user/{UserDatabase, UserEntities}, di/{BibleDataModule, UserDataModule},
feature/bible/{BibleFeedViewModel, BibleScreen, picker/BookPickerSheet, picker/BookPickerViewModel},
feature/discover/{DeepStudyScreen, DiscoverCard, DiscoverViewModel}, feature/home/HomeScreen,
feature/library/{LibraryListScreen, LibraryListViewModel}, feature/onboarding/OnboardingSteps, feature/settings/SettingsScreens,
feature/share/VerseCard, feature/study/{StudyComponents, StudyText}, ui/BibleScrollAppUi, ui/navigation/AppNavHost, ui/theme/Theme,
widget/{VerseWidget, WidgetVerseRepository}, core/content/{BookCodes, db/ContentDatabase, di/ContentModule, model/StudyModels,
work/StudyRefreshWorker}, 5 unit tests + UserDatabaseMigrationTest, pipeline/prompts/{devotional.md, drift.md},
pipeline/src/pipeline/devotionals/checks.py, pipeline/tests/test_devotionals.py.

**Untracked (not build output, 135):** `.gitignore`, 12 `.idea/*` files, Room schemas (UserDatabase 6-9, PlanCatalogDatabase 1,
ContentDatabase 2), `assets/plans/verses.bin`, all of `data/backup/*` (9 files) + `BibleScrollBackupAgent`, `data/plans/*`
(9), `data/streak/*` (3), `data/devotionals/*` (3), `domain/streak/*` (2), `data/bible/{BookNames, PassageVerses, References}`,
`data/settings/{AppLanguage, ContentLanguage}`, `di/{BackupModule, PlansModule}`, `feature/plans/*` (12), `feature/devotionals/*` (7),
`feature/streak/*` (2), `feature/settings/{DataBackupScreen, DataBackupViewModel, LanguageRow}`, `feature/home/RestoreNoticeCard`,
`feature/study/StudyLanguageNote`, `ui/{DataErrorScreen, navigation/PlanLinks, text/LocalizedText}`, `res/values-es/*`,
`res/values/{arrays, books}.xml`, `res/xml/{locales_config, network_security_config}.xml`, the whole `:core:plans` module,
6 core/content devotional files + test, 3 androidTest + 26 unit test files (incl. ExportCoversSchemaTest, LocalizationTest),
`docs/{decisions.md, network-calls.md, translation/*}`, `tools/export_strings.py`.

Note: `.gitignore` itself is untracked, so the committed pipeline directories only stay ignored on this machine.

## 3. Screens (spec §4, 49 screens + extras)

Legend: **Done** = built, reachable and tested. **Partial** = built but missing something the spec requires, or has no real content yet.
**Stub** = placeholder. **Not started** = no code.
Paths: `app:` = `app/src/main/java/aarnav100/developer/biblescroll/`, `test:` = `app/src/test/.../biblescroll/`.

### Onboarding
| ID | Status | Evidence |
|---|---|---|
| O1 Splash | Done | `app:MainActivity.kt:46` `installSplashScreen()` |
| O2 Welcome | Done | `app:feature/onboarding/OnboardingSteps.kt` `WelcomeStep`; PseudoLocaleScreenshotTest `welcome` |
| O3 Feature tour | Done | `FeatureTour.kt` `FeatureTourStep`; screenshot `featureTour` |
| O4 Goal quiz | Done | `GoalStep`; `test:feature/onboarding/OnboardingViewModelTest` |
| O5 Goal response | Done | `GoalResponseStep` |
| O6 Timing quiz | Done | `TimingStep` |
| O7 Your plan | Done | `PlanStep` + `NotificationPreview` (editable time) |
| O8 Notification permission | Done | `PermissionStep` |

### Bible tab
| ID | Status | Evidence |
|---|---|---|
| B1 Feed | Done | `feature/bible/BibleScreen.kt`, `VersePage.kt`; BibleFeedViewModelTest, VersePageAccessibilityTest |
| B2 Liked filter | Done | `FeedTopBar.kt` (strings `feed_liked_filter_*`) |
| B3 Book picker | Done | `picker/BookPickerSheet.kt`; BookSearchTest |
| B4 Chapter grid + Continue | Done | same sheet (`onContinue`), per-book `reading_positions` |
| B5 Translation picker | Done | `picker/TranslationPickerSheet.kt`. Only WEB and KJV to pick from (§4) |
| B6 Verse notes | Done | `VerseNoteSheet.kt`; VerseNoteViewModelTest |
| B7 Verse actions | **Partial** | `sheets/VerseActionsSheet.kt`: Copy, Compare, Share image, Widget verse. **No study entry**: the comment at line 28 still says "Study actions (B8) are deliberately absent until Phase 2" |
| B8 Verse study | **Not started (in the feed)** | The study layout exists (`feature/study/StudyComponents.kt` `StudyBody`) and is used by D2, but nothing in B1/B7 opens it. `DeepStudyRoute` is reachable only from Discover and the Library lists |
| B9 Compare | Done | `sheets/CompareSheet.kt`; CompareRowsTest, BibleCompareQueryTest |
| B10 Share as image | Done | `feature/share/*`; CardTextFitTest |
| B11 Widget verse dialog | Done | `sheets/WidgetVerseDialog.kt`, `requestPin` in VerseSheetsViewModel:114; WidgetVerseRepositoryTest |

### Discover
| ID | Status | Evidence |
|---|---|---|
| D1 Study card feed | **Partial (no content)** | `feature/discover/DiscoverScreen.kt`; DiscoverViewModelTest. The live `discover.json` has 0 cards, so users see an empty feed |
| D2 Deep study | **Partial (no content)** | `DeepStudyScreen.kt`; DeepStudyViewModelTest. Code is done; there are 0 published studies |

### Home
| ID | Status | Evidence |
|---|---|---|
| H1 Home | **Partial** | `feature/home/HomeScreen.kt`. Has the streak chip, Settings gear, restore card, devotional cards, Library card and Plans card. **No Verse of the Day (TBD in spec), no Verse Search card, no Today's Reading card.** Stale comment at line 45: "H1 is Phase 3. For now…" |
| H2 Streak | Done | `feature/streak/StreakSheet.kt`; StreakViewModelTest, StreakCalculatorTest, StreakRulesTest (grace day) |
| H3 Library | Done | `feature/library/LibraryScreen.kt`; LibraryRepositoryTest |
| H4 Saved studies | Done (code) | `LibraryListScreen` `SAVED_STUDIES`; LibraryListViewModelTest. Stays empty in practice until B8 exists |
| H5 Studied | Done (code) | `STUDIED`. Same caveat |
| H6 Liked & bookmarked | Done | `LIKED_AND_BOOKMARKED` |
| H7 Notes (+ tip after 5) | Done | `NOTES` |
| H8 Plans browser | Done | `feature/plans/PlansBrowserScreen.kt`; 29 plans in 5 categories (spec: 25+) |
| H9 Plan detail | Done | `PlanDetailScreen.kt`; PlanViewModelsTest |
| H10 Start plan dialog | Done | `PlanDetailScreen.kt:311` `StartPlanDialog` |
| H11 Plan reader | Done | `PlanReaderScreen.kt`; PlanReaderAndDaysTest |
| H12 Days & notes | Done | `PlanDaysScreen.kt` |
| H13 Create plan: book | Done | `CreatePlanScreen.kt`; CreatePlanViewModelTest, CustomPlanBuilderTest |
| H14 Create plan: pace | Done | same screen; `:core:plans` PlanSplitter + SplittingVectorsTest |
| H15 Devotional detail | **Partial (no content)** | `feature/devotionals/DevotionalDetailScreen.kt`; DevotionalViewModelsTest. 0 devotionals bundled or published |
| H16 Devotional reader | **Partial (no content)** | `DevotionalReaderScreen.kt`. Same |
| H17 Devotional archive | **Partial (no content)** | `DevotionalArchiveScreen.kt`. Same |
| Extra: plan complete | Done | `PlanCompleteScreen.kt` (not in spec) |

### Settings
| ID | Status | Evidence |
|---|---|---|
| S1 Settings | **Partial** | `SettingsScreens.kt:50`: General, Notifications, Storage, Data & backup. No Widget guide, Help & feedback or Legal rows. The comment says "Only Phase 1 sections that exist are listed" |
| S2 General | Done | `GeneralSettingsScreen`: Appearance, Font size, Language (`LanguageRow.kt`), Words of Jesus in red, Hide AI studies; SettingsViewModelTest |
| S3 Appearance | Done | `AppearanceScreen`; ContrastTest, ReadableOnTest |
| S4 Font size | Done | `FontSizeScreen` |
| S5 Widget guide | **Not started** | No screen or route. Pinning only happens from B11 |
| S6 Notifications | Done | `NotificationsScreen.kt`; NotificationsViewModelTest |
| S7 Help & feedback | **Not started** | No screen, route or strings. The spec's "Report a problem" on studies depends on it |
| S8 Legal | **Not started** | No screen, route or strings. The spec puts the privacy statement and the CC BY attributions here, and Play needs a privacy policy |
| S9 Data & backup | Done | `DataBackupScreen.kt`, `data/backup/*`, `ui/DataErrorScreen.kt`, `feature/home/RestoreNoticeCard.kt`; BackupImportExportTest, BackupMergeTest, ExportCoversSchemaTest, RestoreAndReadableCopyTest, BackupDbTest (instrumented) |
| Extra: Storage | Done | `StorageScreen.kt` (downloaded/bundled study books, "Remove downloaded studies"; not in spec) |

### Community
| ID | Status | Evidence |
|---|---|---|
| C1 Monthly vote | **Stub** | `feature/community/CommunityScreen.kt:11` renders `PlaceholderScreen(title = "Community")`, a title-only placeholder, as a top-level tab |
| C2 Past results | **Not started** | none |

Placeholders / "coming soon": the only placeholder in use is `PlaceholderScreen` in CommunityScreen. No "coming soon" text
anywhere in the code or strings.

## 4. Features

| Feature | Status | Evidence / notes |
|---|---|---|
| Room: user.db | Done, v9 | `data/user/UserDatabase.kt` VERSION = 9; tables `verse_marks, reading_positions, chapter_reads, notes, streak_days, plan_enrollments, plan_day_progress, custom_plans, devotional_progress`. AutoMigrations 1→2, 3→4 … 8→9, manual `MIGRATION_2_3`; schemas 1-9 exported. Every table has an updated-at column. Only `custom_plans` has a tombstone (`deleted`); notes don't (known gap, decisions.md #7). No `CommunityVote` yet (Session 33) |
| Room: other DBs | Done | bible.db v1 (createFromAsset), plans catalogue v1 (rebuilt from assets), content.db v2 (`:core:content`, AutoMigration 1→2) |
| Bundled translations | **Partial** | `bible.db` has **WEB and KJV only**. The spec also bundles **BSB** (only a description string exists, `TranslationPickerSheet.kt:151`) and **RV1909** (Session 30a/31b, not started) |
| Downloaded translations | **Not started** | No download code for ASV/RV1909 |
| Content layer: studies | Code done, content none | `:core:content` (BookDownloader, BundledContent, StudyRepository, weekly StudyRefreshWorker); 35 tests. `assets/studies/` is missing (spec: Gospels + Psalms bundled). CDN has 0 books |
| Content layer: devotionals | Code done, content none | DevotionalDownloader/Importer/BundledDevotionals. `assets/devotionals/` is missing. Pipeline has 30 drafts (January), none checked |
| Content layer: plans | Done | 29 plans in assets, all `auto_checked`, with Spanish `i18n` (29/29 auto_checked); PlanLocalizationTest |
| Widget | Done | `widget/VerseWidget.kt` (Glance), `HomeScreenWidgets.kt`, B11 |
| Reminders | Done | `data/reminder/*` (AlarmManager, boot/timezone receivers, ReminderVerses); NextTriggerTest, ReminderRepositoryTest, ReminderVersesTest. "Reminder verses in the chosen translation" is Session 31b |
| Share image | Done | `feature/share/*` (MediaStore; WRITE_EXTERNAL_STORAGE only up to API 28) |
| Backup / export / import | Done | Session 29. Include-only rules cover user.db (+WAL), settings DataStore, `app_language.xml` |
| Per-app language | Done | `data/settings/AppLanguage.kt`, `LanguageRow.kt`, `locales_config.xml` (en, es) |
| Spanish strings | **100% present** | `values-es/strings.xml`: 481 of 481 translatable strings/plurals/arrays; `books.xml` 3/3. 15 are the same as English, which is expected (formats like `ref_verse`, `two_parts`, "General", "Sepia", app name) |
| Themes / font size | Done | S3 (Light/Dark/System, 4 backgrounds, 6 accents + Material You, WCAG checks), S4 (4 sizes) |
| Baseline Profile | **Done but stale** | `baselineprofile/` module; `app/src/release/generated/baselineProfiles/*.txt` generated **2026-11-17 21:04**, before the Phase 3 screens. Journeys cover onboarding, feed fling, book picker and compare only, and they click English text ("Skip", "Continue"), so they break on a Spanish device. The profile is in the bundle |
| Hide AI studies | Done | read by Discover, DeepStudy, Library and Devotionals ViewModels |

## 5. Hard-rule check

| Rule | Result |
|---|---|
| Billing library | **None.** Release runtime classpath (1,317 lines) has no billing/play-services/gms artefact |
| Premium / entitlement flags | **None** in code (`premium|entitle|billing|subscription|paywall|isPro|unlock`: no hits outside baseline-profile class names from Compose's `SingleSubscriptionSnapshotFlowManager`) |
| Crashlytics / Firebase SDK / analytics | **None.** No `google-services.json`, no Firebase or analytics dependency. "Firebase" only appears in comments describing the CDN host (Firebase Hosting, static files) |
| Accounts / sign-in | **None.** The only "account" string is `onb_promise_detail` ("No ads, no account.") |
| Network calls not in docs/network-calls.md | **None found.** The only host in code is `CONTENT_BASE_URL = https://biblescroll-content.web.app/` (`core/content/build.gradle.kts:19`), which is listed. OkHttp/Retrofit are the only HTTP libraries; no coil-network. Open flag (already in network-calls.md): manifest URLs aren't pinned to that host |
| Cleartext traffic | **Blocked.** `network_security_config.xml` `cleartextTrafficPermitted="false"`, merged manifest `usesCleartextTraffic="false"` |
| AD_ID permission | **Absent** from the merged release manifest. Permissions there: POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, INTERNET, ACCESS_NETWORK_STATE, WAKE_LOCK, FOREGROUND_SERVICE (from WorkManager), DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION, plus WRITE_EXTERNAL_STORAGE capped at maxSdk 28 in the source manifest |
| Hard-coded UI strings | **None found** in a grep of `Text(`/`contentDescription`/`title`/`label`. The only literals are animation labels and a preview. LocalizationTest passes |
| Secrets | **None found** in tracked or untracked files (scanned for Anthropic/Google key shapes, private keys, `api_key=` literals; build/out/data/.venv excluded). No `pipeline/.env`. `local.properties` and `pipeline/{data,out,state,backups}` are gitignored. Note: `pipeline/state/batches*.json` holds Anthropic batch IDs (not secrets) |

## 6. Content pipeline

- **Studies by status** (counted from all 31,102 `pipeline/out/studies/*.json`; `out/progress.md` is stale from 2026-11-17 21:55
  and shows all zero):

| Book | not written | auto_checked | flagged | reviewed |
|---|---|---|---|---|
| Psalms | 2,458 | 1 | 2 | 0 |
| Matthew, Luke, John, Jeremiah, Ecclesiastes, Song of Solomon, Ephesians | n − 1 each | 1 each | 0 | 0 |
| The other 58 books | all | 0 | 0 | 0 |
| **Total** | **31,092** | **8** | **2** | **0** |

- **Waves run:** only the **10-verse pilot** (the first 10 lines of `discover.txt`). Pilot 1: prompt `844b242d7ceb`, 2 rounds
  (`state/batches-pilot-20261118.json`; 9 written, 1 cut off at max_tokens, 2 retried). Rerun: prompt **`62631d6c8e7e`**
  (system hash `93cf0f10d0ce`), Haiku 5.5, effort medium, max_tokens 16,000, 10/10 succeeded, cost about $0.012
  (`state/batches.json`). No Discover wave (377 ids in `discover.txt`), Gospels/Psalms wave or full run yet.
- **Prompt hash in use:** `62631d6c8e7e` (all 10 written studies). Checker version 3 (commit 784a907).
- **Plans:** 29 built, all `auto_checked`, all with Spanish `auto_checked`; bundled in app assets (30 JSONs incl. index + verses.bin).
- **Devotionals:** calendar has 366 rows. **30 drafted** (`out/devotionals/devotional-01-01…01-31`, **01-03 missing**), all status
  `generated` (none checked, reviewed or translated). The spec wants 365. Devotional translation is blocked on RV1909 (Session 30a).
- **Published to the CDN** (live `https://biblescroll-content.web.app/manifest.json`, release `c8a40bd6e082`): **0 books, Discover
  count 0** (41-byte `discover.json`). Matches `out/publish_state.json`. No devotional months.
- **Bundled in app assets:** plans only. **No studies, no devotionals.**
- Schema note: study files are internal `schemaVersion` 3 (`studies.py`), while publish writes 2 (`publish.py:38`), which matches the app's
  `ContentVersions.MAX_SCHEMA_VERSION = 2`. That's consistent, but pipeline/README's changelog says "3: current published shape",
  which disagrees with `publish.py`. Confirm before the first real publish.
- Versification report is present (`out/versification_report.md`); `out/test_*` and `discover_test10` are model-comparison runs.

## 7. Session plan vs repo

The repo records only Sessions 29-34 (CLAUDE.md) and phases (spec §4). **Your numbering for Sessions 1-28, 20b and "Step 0" appears
nowhere in the repo**, so the rows below map them by phase, not by session. Only one code comment mentions a lower session number
("Session 7 stubs", `BibleScreen.kt:216`, and those stubs have since been built).

| Plan item | Repo shows | Agreement |
|---|---|---|
| Phase 1, Sessions 1-11 (O1-O8, B1-B7, B9-B11, translations, notes/likes/bookmarks, themes, font size, reminders, widget) | All built and tested. Exceptions: **BSB not bundled**; S1 lists only what exists. Committed in 65cf966/0ca8a13 | Mostly matches. Can't verify per session |
| Phase 2, Sessions 12-20b (pipeline + CDN, B8, D1-D2, H3-H7) | Pipeline, CDN client, D1/D2, H3-H7 built; commit "phase 2 done". **B8 from the feed is not built**, and **no content is published** (CDN empty) | **Disagrees**: "phase 2 done" doesn't hold for B8 or content |
| Phase 3, Sessions 21-28 (H1, H2, H8-H14, H15-H17) | H2, H8-H14 done; H15-H17 code done, no content; H1 partial (no VOTD/search/today's reading). **All uncommitted** | Partial. Not in git at all |
| "Step 0" | Not defined anywhere. The nearest match is today's pipeline pilot commits (282ac41, f4f8491, 784a907: study prompt pilot + rerun) | **Unknown**: please define it |
| Session 29: S9 Data & backup | Done (code + tests), matches `[x]`. Instrumented BackupDbTest not run here. **Uncommitted** | Matches (uncommitted) |
| Session 30a: RV1909 in pipeline | Not started (no RV1909 in `pipeline/src`, LICENCES.md or the versification report) | Matches `[ ]` |
| Session 31a: localized plans/devotionals | Done for plans (29 es translations, PlanLocalizationTest, StudyLanguageNote). Devotional translation can't run yet (needs RV1909, and there are no checked devotionals). **Uncommitted** | Matches `[x]` for plans; devotionals part is blocked |
| Session 31b: bundle RV1909, Spanish onboarding offer, reminder verses in translation | Not started | Matches `[ ]` |
| Session 31c: wrap-up | Not started: no `qa` variant, no `docs/data-safety.md`. APK size measured here (AAB 13.4 MB). Secrets scan done here, clean | Matches `[ ]` |
| Verse of the Day | Not started (spec TBD) | Matches |
| Session 32 / 34 | "TBD" in CLAUDE.md | n/a |
| Session 33: C1/C2 Community vote | C1 placeholder tab, C2 none, no CommunityVote table, no Firestore | Matches `[ ]` |
| Spec says Spanish UI "[done]" (Phase 4) | Done: 100% strings, per-app language, references, book names | Matches |

## 8. What's left, in order

| # | Item | Size | Why this order |
|---|---|---|---|
| 1 | **Commit the work.** First add `build/` (any depth) to `.gitignore` and `git rm -r --cached core/content/build baselineprofile/build`, then commit Phase 3, Session 29, Spanish and 31a in a few logical commits (and `.gitignore` itself). Consider a private remote | Small | 135 new source files and 89 changes live only on this laptop |
| 2 | Fix the out-of-date pipeline test `test_prose_empty_and_status_generated` and refresh `out/progress.md` (`pipeline progress`) | Small | Pipeline suite should be green before scaling generation |
| 3 | B8 Verse study from B7 (+ "Save study", marks `studied`, honours Hide AI studies) | Medium | Core Phase 2 screen; H4/H5 depend on it |
| 4 | S8 Legal (privacy statement from spec, CC BY / source attributions, translation licences) and S7 Help & feedback (incl. "Report a problem" from studies; no billing branches). Add any `mailto:`/link to docs/network-calls.md | Medium | Needed for Play (privacy policy) and required by CC BY sources before any study ships |
| 5 | Study content, first wave: Discover list (377) + Gospels/Psalms; check, review priority verses, `publish --dry-run`, publish, `bundle` into `assets/studies/` | Large (mostly pipeline run time + human review + API cost) | Gives D1, D2 and B8 their content |
| 6 | Devotionals: draft the remaining 336 (incl. the missing 01-03), check, edit/review, publish months, `devotionals bundle` current + next month | Large | H15-H17 are empty without it |
| 7 | Session 30a: RV1909 in the pipeline (eBible source, LICENCES.md, versification report) | Medium | Unblocks 31b and devotional translation |
| 8 | Session 31b: bundle RV1909, Spanish onboarding offer, reminder verses in chosen translation | Medium | |
| 9 | Bundle BSB (spec §4 bundled list) | Medium | Spec gap in Phase 1 scope |
| 10 | S5 Widget guide + S1 rows | Small | |
| 11 | H1 completion: decide Verse of the Day in spec, then VOTD card, Verse Search, Today's Reading | Medium | Spec must be decided first |
| 12 | Regenerate the Baseline Profile with Phase 3 journeys (plans, devotionals, Home); make journeys use resource ids/test tags, not English text | Small | Do after the UI settles |
| 13 | Session 31c wrap-up: run instrumented tests on a spare device/emulator, `qa` variant end-to-end, `docs/data-safety.md`, release signing config / upload key | Medium | Release readiness |
| 14 | Session 33: C1/C2 Community (CommunityVote table + BackupFormat/merge/ExportCoversSchemaTest, Firestore counter, network-calls.md) | Large | Last; spec lists it as an open question ("at launch or later") |
| 15 | Small hardening: pin manifest URLs to the CDN host; notes tombstone (decisions.md #7); clean up stale comments (VerseActionsSheet:28, HomeScreen:45, SettingsScreens:48, BibleScreen:216); fix the pipeline README schema changelog | Small | Anytime |
| 16 | Downloadable ASV | Large | Spec lists it; lowest user value |

## 9. Couldn't determine

- **Your session numbering for 1-28, 20b and Step 0**: not recorded in the repo or git history (5 commits). I mapped by phase only.
- **The full spec**: docs/spec.md says the full doc is a claude.ai artifact and that this file is a summary. I audited against the
  summary. **docs/context.md, which CLAUDE.md references, doesn't exist.**
- **Instrumented tests** (migration, backup DB, pseudo-locale screenshots, accessibility): not run. CLAUDE.md warns against running them on a
  phone with real data, and I didn't check for an emulator. Their pass/fail state is unknown.
- **Runtime behaviour on a device** (onboarding flow, reminders firing, widget pinning, restore fix-ups): not exercised. Statuses come from
  code plus unit tests.
- **Human review status of the 10 pilot studies**: none are `reviewed`; whether you've read them outside the review tool is unknown.
- **Whether Firebase Hosting has other sites or files** beyond `manifest.json`/`discover.json`: only those two URLs were fetched.
- **Spanish translation quality**: coverage is measurable (100%). Quality isn't; decisions.md says a native speaker reviewed it on 2026-11-18.
- **Legal status of translations** (spec: "verify all statuses legally"): only the licence text in `bible.db` was read.
