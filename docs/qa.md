# QA builds and release checks

## The `qa` build type

Release code (R8, resource shrinking, no StrictMode, no LeakCanary, no debug study import) signed with the debug key,
package `aarnav100.developer.biblescroll.qa`, version suffix `-qa`. It installs beside the store or debug app with
its own data, so it's safe on a phone with real data. Differences from release: debug signing, Community also lists
`test:` ministries (`BuildConfig.QA`), and it isn't gated on `supportEmail`.

```sh
./gradlew assembleQa
adb install -r app/build/outputs/apk/qa/app-qa.apk
adb shell am start -n aarnav100.developer.biblescroll.qa/aarnav100.developer.biblescroll.MainActivity
```

R8 problems (missing keep rules for Room, Hilt, kotlinx.serialization, Retrofit) only show up in a minified
build, so run the checklist below on `qa`, not debug.

## End-to-end checklist (no account, fresh install)

1. **Onboarding.** English UI: O2 → tour → goal → timing → plan → permission. Spanish UI (Settings → language):
   the Bible step appears after O2 with RV1909 preselected; finish and confirm the feed opens in RV1909 at the goal verse.
2. **Feed and reader.** Swipe, like, bookmark, note; switch translation (BSB and RV1909 show the "no red letters"
   note); Compare sheet; share as image.
3. **Home.** Verse of the Day, Today's Reading, Verse Search; enroll in a plan and finish a day.
4. **Discover and study.** Open a study (downloads, hash-checked), "Study this verse" from B7, Report a problem
   opens the email app.
5. **Reminder.** Set it a minute ahead; notification shows a verse in the chosen translation; tap opens the feed.
   Reboot and confirm it's still scheduled.
6. **Widget.** Add it, switch to Verse of the Day mode, tap opens the feed.
7. **Community.** Vote **only on a `test-` ministry** (real counters are shared). Airplane mode vote: it shows
   "waiting to send" and is sent when back online.
8. **Data & backup.** Export, uninstall, reinstall, import: counts match. `adb shell bmgr backupnow
   aarnav100.developer.biblescroll.qa`, then uninstall and `adb install` again to check Auto Backup restore
   (onboarding skipped, Welcome back card).
9. **Upgrade path.** Install the previous qa build, add data, then `adb install -r` the new one: data kept, and
   with a bumped `BibleDatabase.VERSION` the new translations appear.

## Runs

- 2026-11-18, SM-M146B (Android 14), qa build: Spanish onboarding (Bible step, tour, permission), feed in RV1909,
  translation picker (red-letter notes). No crashes, no R8 errors in logcat. Found and fixed: book names in English
  on a Spanish UI (ResourceBookNames; ResourceBookNamesTest). Steps 2-9 not run yet.

## Instrumented tests

Per CLAUDE.md, never `connectedAndroidTest` on a phone with real data (Gradle uninstalls the app afterwards).

```sh
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w aarnav100.developer.biblescroll.test/androidx.test.runner.AndroidJUnitRunner
# one class: add  -e class aarnav100.developer.biblescroll.data.backup.BackupDbTest
```

Session 29 (S9) classes: BackupDbTest, UserDatabaseMigrationTest. Bible asset: TranslationLicenceTest,
ReminderVersesDbTest. Last full run: 2026-11-18, 63/63 on SM-M146B.

## Size (measured 2026-11-18, qa build, same code as release)

| Artifact | Size |
|---|---|
| AAB (`bundleQa`) | 18.3 MB |
| Universal APK (`assembleQa`) | 13.9 MB |
| of which `assets/bible.db` (4 translations) | 25.5 MB raw, 9.0 MB compressed |
| of which `classes.dex` | 5.0 MB raw, 2.5 MB compressed |
| of which fonts (Literata, Inter) | 1.8 MB raw, 1.0 MB compressed |

Play's per-device download will be a little under the universal APK (it drops unused ABIs and densities). On the
phone, Room copies bible.db out of the APK on first run, so installed size is about 13.9 MB + 25.5 MB ≈ 40 MB
before downloaded studies. bible.db is two-thirds of the APK. If size becomes a concern, the options are shipping
RV1909 as a download for Spanish phones only or storing verses compressed. Neither is needed now.

Studies (2026-11-24, decision 27): content.db stores each study compressed, about 3.4x smaller than its JSON. The
bundled Gospels and Psalms (`pipeline bundle`, 7.5 MB of .json.gzip in the APK) take about 9.6 MB in content.db
instead of 33 MB, so a new install is about 21.5 MB from Play and about 57 MB on the phone after first launch.
