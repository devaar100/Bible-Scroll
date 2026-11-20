# Privacy inventory

Everything the app handles, derived from the code and docs/network-calls.md on 2026-11-18. The S8 Privacy text and
web/privacy/ are written from this. **Not legal advice**: the "For legal review" list at the end is what a lawyer
should check before release.

Update this file in the same change as anything that adds data, storage, a network call, an intent that leaves the
app, or a third-party library with its own data handling.

## 1. Stays on the device

| What | Where | Code |
|---|---|---|
| Likes, bookmarks, studied verses, saved studies | user.db `verse_marks` | data/user/UserEntities.kt |
| Notes | user.db `notes` | same |
| Reading place per book, chapters read | user.db `reading_positions`, `chapter_reads` | same |
| Streak days | user.db `streak_days` | data/streak/StreakDayEntity.kt |
| Reading plans, day progress, custom plans | user.db `plan_enrollments`, `plan_day_progress`, `custom_plans` | data/plans/PlanEnrollmentEntities.kt, CustomPlans.kt |
| Devotionals finished | user.db `devotional_progress` | data/devotionals/DevotionalProgress.kt |
| Community vote (month, ministry id, when, sent) | user.db `community_votes` | data/community/CommunityVote.kt |
| Settings (theme, background, accent, font size, red letters, Hide AI studies, translation, reminder on/time, widget verse/mode, streak grace day, onboarding done, one-off tips) | DataStore `settings.preferences_pb` | data/settings/*, data/reminder/ReminderSettings.kt, widget/WidgetVerseRepository.kt |
| App language on Android 8–12 | SharedPreferences `app_language.xml` | data/settings/AppLanguage.kt |
| Bible text (WEB, KJV, BSB, RV1909) | bible.db (shipped in the APK) | data/bible/ |
| Plan catalogue | plans.db (rebuilt from APK assets) | data/plans/PlanCatalogDatabase.kt |
| Downloaded studies and devotionals | content.db | core/content/db/ContentDatabase.kt |
| Last manifest / Discover list / ministries list / donation history (history.json + last fetch time) | files/content-cache/ (not in Auto Backup or the export file) | core/content/ContentCache.kt, CommunityHistoryRepository.kt |
| Ministry card images (downloaded copies) | cache/ministry-images/ (never backed up; Android may clear it) | core/content/MinistryImages.kt |
| Download temp files | cache/study-downloads, cache/devotional-downloads | core/content/di/ContentModule.kt |
| Share-as-image PNGs (until shared) | cache/ (FileProvider) | feature/share/VerseImageExporter.kt |
| Scheduled reminder and widget alarms, WorkManager jobs | Android AlarmManager / WorkManager's own database | data/reminder/, widget/VotdWidgetScheduler.kt, data/community/CommunityVoteWorker.kt |
| Restore marker, unreadable user.db rescue copy | no_backup/ | data/backup/RestoreFixups.kt, UserDbSafety.kt |

Debug builds only (never in release): LeakCanary heap dumps; files/debug-studies (manual study import).

## 2. Leaves the device

All network traffic is HTTPS (cleartext blocked by network_security_config.xml). No analytics, ads, crash-reporting
or tracking SDK; no advertising ID permission; no account or sign-in.

| What | To | Contains | Who can see what |
|---|---|---|---|
| Content downloads: manifest.json, study book files, devotional months, discover.json, ministries.json, history.json, ministry images on our Hosting site | `biblescroll-content.web.app` (Firebase Hosting, Google) | Plain GET, no cookies, no identifiers, OkHttp's default User-Agent | Google (Firebase Hosting) sees the IP address, time, requested file and User-Agent |
| Community vote | `firestore.googleapis.com` (Cloud Firestore, Google) | One increment-by-1 on `community_votes/{YYYY-MM_ministryId}`: the month and ministry id. No token, key or device/user id | Google sees the IP address with the month and ministry; the counter itself stores only a count |
| Community round check | `firestore.googleapis.com` | batchGet of this month's counter names (only to tell whether voting is open; counts are never shown) | Google sees the IP address and which month was read |
| Ministry card images in our Storage bucket | `firebasestorage.googleapis.com` (Firebase Storage, Google) | Plain GET of the image URL from ministries.json, no cookies, no identifiers, OkHttp's default User-Agent | Google sees the IP address, time, requested image and User-Agent |
| Report a problem / Send feedback | The person's own email app, then whoever they email (our support address) | Subject and body they can read and edit first: their note, report type, content type and id, translation, UI language, content version, prompt hash, app version, Android version, device model. Nothing attached, no logs | Their email provider; us (their email address and message). Nothing is sent unless they press send |
| Ministry Website / Give directly | The person's browser (Custom Tab) | The page they tapped, loaded by the browser | That website, under its own policy. No prefetch before the tap |
| Share as text / image, Copy | Android share sheet / clipboard | The verse text or image | Whichever app the person picks |
| Export my data | A file the person saves via the system picker | user.db tables + exportable settings (JSON), optional Markdown of notes/likes/bookmarks | Wherever they save it |

## 3. What Android and Google Play do

- **Auto Backup / device transfer** (res/xml/backup_rules.xml, data_extraction_rules.xml; include-only): user.db
  (+WAL), the settings DataStore and app_language.xml. Android may store this in the person's own Google account
  if backup is on in the phone's settings. On Android 12+ cloud backup is allowed only when Android can encrypt it
  end to end (`disableIfNoEncryptionCapabilities`, needs a screen lock); device-to-device transfer has no such
  condition. Everything else (Bible, catalogue, content caches) is left out.
- **Crash and ANR statistics**: the app has no crash-reporting code. Google Play's Android vitals may collect crash
  data from phones whose owners allow usage and diagnostics sharing, and show it to the developer in Play Console.
- **Notifications** are local (daily reminder, plan reminders); no push service.

## 4. Who can see what

- **Us (the developer)**: emails people choose to send; Community vote counts (not who voted); Play Console vitals
  if enabled. We can't see notes, likes, plans, settings or reading history.
- **Google**: as host of the CDN (Firebase Hosting) and the vote counter (Cloud Firestore), IP addresses and
  request details for those calls; Auto Backup data in the person's own Google account (end-to-end encrypted on
  Android 12+); Play vitals data.
- **Email providers and browsers**: what passes through them when the person uses them.

## 5. Deletion

- Uninstalling deletes everything in section 1. Clearing app data does the same.
- A Google backup follows the person's Google account settings; they can delete it there.
- Community votes can't be deleted or attributed: the server stores only counts.
- Report and feedback emails: we hold them in our mailbox; deletion on request (process to be decided).

## 6. Couldn't verify from the code

- How long Google keeps Firebase Hosting and Cloud Firestore request logs (IP addresses) for this project, and
  whether Cloud Logging data-access logs are enabled. Server-side settings, not in the repo.
- Whether Android vitals is enabled for the Play Console account, and exactly which fields Google collects.
- Auto Backup encryption on Android 8–11 (depends on Android version, vendor and screen lock).
- Which Firestore project and region the app finally points at (`FIRESTORE_PROJECT_ID` defaults to
  `biblescroll-content`; not deployed yet).
- The support mailbox: address not set (`supportEmail` placeholder), provider and retention unknown.
- Behaviour of third-party email apps, browsers, keyboards and launchers.

## For legal review

1. The whole Privacy text (S8 and web/privacy/) against GDPR/UK GDPR, CCPA and Play's User Data policy, and the
   Play Data safety form answers it implies (IP addresses processed by Google as host; data "not collected" vs
   "collected" for the vote and the CDN).
2. Whether the developer is a controller of the IP addresses Google processes for Hosting/Firestore, and whether a
   data processing agreement with Google is needed or already covered.
3. "We don't track you" / "no analytics" wording, given Play vitals.
4. The Auto Backup sentence (Android 12+ end-to-end encryption; Android 8–11 not promised).
5. The Community vote: "anonymous" claim, and that votes can't be deleted.
6. Report emails: the notice line ("Your email will show us your address. We may use your report to correct the
   content."), retention and deletion on request.
7. Children: whether the app needs a children's privacy statement (COPPA, Play Families) given its audience.
8. Scripture: KJV Crown letters patent in the UK; the WEB trademark condition ("modified text may not use that
   name"); whether reformatting counts as modification.
9. Study sources: the STEP Bible / OpenBible.info CC BY 4.0 attributions as shown; TAHOT morphology (ETCBC, CC BY-NC
   vs MIT, hidden in the app for now); TAGNT NA28 spellings; the five lemmas taken from STEP's brief lexicons
   (TBESH lemma column unconfirmed); TSK public domain.
10. "How studies are made": that studies are AI-generated, checked automatically, and not individually reviewed by a
    person unless marked; naming Anthropic's Claude.
11. Open-source notices: Apache-2.0 NOTICE files (Licensee doesn't collect them), the protobuf BSD-3-Clause notice,
    OFL notices for Literata and Inter.
12. "Give directly": that the app handles no money and isn't responsible for ministries' sites; ministry vetting.
