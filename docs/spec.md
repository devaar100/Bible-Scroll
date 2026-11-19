# PROJECT CONTEXT: Free Android Bible-scrolling app (inspired by "Scroll The Bible" on iOS)

## 1. Goal
Build a native Android app that turns the doomscrolling habit into Bible reading: one verse per full screen, swipe up for the next. Inspired by the iOS app "Scroll The Bible", but:
- 100% FREE: no paywalls, trials, upsells, usage caps, "premium" features, ads or tracking.
- Original name, logo, copy, colours and illustrations (nothing copied from the reference app).
- Android-only for now.
- I'm vibe coding with Claude Code (Android Studio plugin). I know basic Java Android, SwiftUI and React Native.

## 2. Reference app: Scroll The Bible (iOS)
- Developer: Gabriel Jacobson / JCBSN LLC. Launched Dec 2025. iPhone only, no Android version.
- 4.8 stars, ~21K ratings, #1–2 in Books (US). Third-party revenue estimates range from ~$750K to ~$2M/month.
- Monetisation: freemium subscription (prices A/B tested: e.g. $39.99/yr or $29.99/yr, $4.99–$9.99/mo, 7-day trial).
- Free tier: verse feed, translations, notes, likes, compare, widget, snapshot, plans, Discover deep studies.
- Paid: on-demand AI verse studies (OpenAI), plus paywalls on first devotional / first plan session (appear soft or frequency-capped), and Community voting.
- Translations: KJV bundled; others (NLT, AMP, NASB, NKJV, NIV, CSB, etc.) via API.Bible/YouVersion. English + Spanish UI.

### Reference app screen map (61 screens; I recorded and analysed all of them)
- Onboarding (light theme, serif headlines, black pill "Continue", progress bar): splash → stat hook → 4 animated feature demos (feed, plans, Discover, search) → goal quiz (peace / understanding / routine) → personalised response with verse → timing quiz (morning / break / bed / whenever) → habit plan with mock notification → social proof → 4-step paywall (hero, features, trial reminder, timeline + price) + plans sheet. Faint delayed close X.
- Main app (dark theme, iOS "Liquid Glass" floating tab bar): tabs = Community, Discover, Home, The Bible (default).
- Bible feed: top bar [dice=random verse | heart=liked filter] [KJV pill] ... [bookmark] [Genesis 1 ▾]; left-edge verse scrubber; logo tile "Study Verse" above verse; right rail like/note/share; tap verse = copy. Sheets: notes (auto-saved), book picker (search, OT/NT, progress rings), chapter grid (mark read), translation picker, Study Verse action sheet (Explain Easier, Original Language, Deeper Study, Related Verses [paywalled]; Compare Versions, Set as Widget Verse, Snapshot Verse, Auto-scroll), compare versions, widget alert, snapshot image + share.
- Home: greeting, streak flame, library, settings; cards for Verse of the Day, Verse Search (wheel picker + Study This Verse), Today's Reading, Today's Devotional. Streak sheet, library (saved studies, studied, saved verses, notes), reading plans browser (carousels by category + Build Your Own Plan), plan detail, start-plan alert, plan reader (immersive feed with Mark read), create plan (pick book → minutes/day → plan summary), devotional detail + story-style devotional reader (cover, verse slides, Context/Reflect, Apply It), tomorrow's devotional on a countdown.
- Settings: General (theme, font size, Words of Jesus in red, widget guide, app language, notifications), chat-style Contact & Support tree, Legal, Account, Subscription, passwordless email-code sign-in, log out.
- Discover: vertical feed of verse study cards → full deep-study page (meaning, historical context, original language, life in Bible times, theology, did you know, cross refs, apply it).
- Community: total given to ministries, monthly "vote who we give to" (subscribers only), past donations, hiring card.

## 3. User feedback on the reference app (from reviews)
Problems: paywall is the #1 complaint (paying for Scripture, trapped on trial screen, confusion about what's free); cancellation/refund/support failures; some users dislike AI in Bible study; AI studies have no citations; want reading position saved per chapter/book; not enough for experienced readers; App Store privacy label declares tracking for third-party advertising.
Requests: community features (friends, shared verses, prayer), colour customisation beyond light/dark.
What users love: bite-size verses help people who feel overwhelmed (incl. dyslexia/ADHD), chapter tracking, original Greek/Hebrew study, a real doomscroll replacement.

## 4. Our Android spec (full doc exists; summary here)
Spec doc: https://claude.ai/artifact/XidRbbCZRyHQnPLG2WTLRj (export as Markdown to docs/spec.md in the project)

49 screens:
- Onboarding (8): O1 Splash, O2 Welcome, O3 Feature tour (pager), O4 Goal quiz, O5 Goal response, O6 Timing quiz, O7 Your plan (editable reminder time), O8 Notification permission (Android 13+). No account, no social proof, no paywall.
- Bible tab (11): B1 Feed, B2 Liked filter, B3 Book picker, B4 Chapter grid (with "Continue" to the saved per-book position), B5 Translation picker, B6 Verse notes, B7 Verse actions, B8 Verse study (free, from cached content), B9 Compare, B10 Share as image, B11 Widget verse dialog. Double-tap = like; long-press = copy.
- Discover (2): D1 Study card feed, D2 Deep study.
- Home (17): H1 Home, H2 Streak (optional weekly grace day), H3 Library, H4 Saved studies, H5 Studied, H6 Liked & bookmarked, H7 Notes, H8 Plans browser (25+ plans), H9 Plan detail, H10 Start plan dialog, H11 Plan reader, H12 Days & notes, H13 Create plan: book, H14 Create plan: pace, H15 Devotional detail, H16 Devotional reader, H17 Devotional archive.
- Verse of the Day (H1 card): **TBD**. Not built yet; in the reference app it is a Home card. Decide what it shows (a curated verse per date, in the reader's translation) and how it relates to the widget verse and the daily reminder verse before building.
- Settings (9): S1 Settings, S2 General (includes "Hide AI studies" and Language: the system per-app language screen on Android 13+, an in-app dialog on 8–12), S3 Appearance, S4 Font size, S5 Widget guide (requestPinAppWidget), S6 Notifications, S7 Help & feedback (chat tree, no billing branches), S8 Legal, S9 Data & backup.
- S9 Data & backup **[done]**: two sentences ("Your notes, likes, bookmarks, plans, reading progress and settings are stored on this phone. If Google backup is turned on in your phone's settings, Android may also save a copy to your own Google Drive."; never promises it is up to date), then **Export my data** (a JSON backup with schemaVersion, app version and date, then an optional readable Markdown copy of notes, likes and bookmarks; both through the system file picker, no storage permission) and **Import** (validates the whole file first; merges by key keeping the newer updatedAt, in one transaction; never removes anything; fills only settings this phone hasn't set; shows "Added 12 notes, 40 likes"). Auto Backup and device transfer carry only user.db and the settings DataStore. After a restore: onboarding skipped, reminders re-armed, missing translation falls back to WEB, invalid widget verse resets, and Home shows a one-time "Welcome back" card to allow notifications and re-download studies. If user.db won't open, the app shows an error screen with "Save a copy of the data file" and never deletes the file. H7 shows a one-time tip after 5 notes.
- Community (2): C1 Monthly ministry vote: a short list of ministries for this month; anyone can cast one anonymous vote per month (no account, no IDs; the phone remembers it voted in a local CommunityVote row); shows the running tally; "Give directly" links to each ministry's own page; the app handles no money. C2 Past results: previous months' winners and vote totals. Built in Session 33.
- S3 Appearance (replaces "Theme") **[done]**: mode (Light/Dark/System); reading backgrounds Paper, Sepia, Charcoal, True Black; 6 curated accent presets + "Match my wallpaper" (Material You, Android 12+); live preview; WCAG AA contrast checks on every background/accent pair; Words-of-Jesus red tuned per reading background.
- "Hide AI studies" (S2): one switch that hides B8 Verse study, the D1/D2 Discover studies and the H4/H5 study lists, and removes study entry points from B7. Scripture, notes, plans and devotionals stay. Default off; stored on device.
- Removed from reference: all 8 paywall screens, social proof, subscription/restore/promo settings, billing help, refund policy. Rule: no billing library, no entitlement checks, no "premium" flags in code.

Design system: Material 3. Scripture/headlines in Literata, UI in Inter/Roboto Flex. Light (warm off-white) / Dark (charcoal) / System; dark default for feed. Crimson accent (Words of Jesus, likes), orange streaks, green success. Font sizes Default/Large/Larger/Largest. iOS→Android mappings: glass tab bar → NavigationBar; sheets → ModalBottomSheet; wheel picker → 3 snapping LazyColumns; alerts → AlertDialog; segmented → SegmentedButton; share → Sharesheet; photo save → MediaStore (no permission); per-app language API; Glance home-screen widgets; predictive back.

Content & licensing:
- Bundled public-domain translations: KJV, WEB (modern default), BSB; Reina-Valera 1909 to be bundled (decided 2026-11-18; Spanish phones are offered it in onboarding); downloadable ASV, Reina-Valera 1909. Licensed ones (NIV, NLT, ESV, etc.) only after signed licences. Verify all statuses legally.
- Original language: openly licensed tagged Hebrew/Greek (e.g. STEPBible data, CC BY) + Strong's. Cross-refs: Treasury of Scripture Knowledge + OpenBible.info (CC BY). Built offline by `pipeline/` (see pipeline/LICENCES.md for each source's licence and required attribution).
- Citations on every study: each B8/D2 study ends with a Sources list naming the lexicon entries (e.g. TBESH/TBESG with Strong's numbers) and cross-reference datasets (TSK, OpenBible.info) it used, plus the CC BY attributions those sources require. Studies are generated with source IDs attached, so citations come from data, not from the model's memory.
- AI study content generated ONCE for all 31,102 verses, auto-checked, human-reviewed (priority: Discover verses, most-read books), shipped as versioned static files on a CDN per book; Gospels/Psalms bundled. "AI-assisted" label + Report a problem. No live per-tap AI, no chatbot in v1.
- Localized content: plan titles, descriptions, day titles/intros and category names, and devotionals, carry per-language translations (pipeline `translate`: Claude Opus 5.5 translates, Claude Sonnet 5.5 checks meaning drift; only checked, up-to-date translations ship; English fallback per field). Devotional translations quote the target language's Bible text (RV1909), never a model's translation. Studies stay English for now; non-English UIs show "Studies are in English for now".
- 365 devotionals (AI-assisted drafts, human-edited), 25+ JSON reading plans.

Tech stack: Kotlin, Jetpack Compose, Material 3, minSdk 26, MVVM + UDF, Hilt, Navigation Compose, feature modules; Room (pre-populated Bible DB via createFromAsset + user DB), DataStore; Retrofit/OkHttp for CDN content; one anonymous add-one vote counter in Cloud Firestore (Session 33; no auth, no IDs); WorkManager; AlarmManager for reminders (reschedule on boot/timezone change); Glance widgets; Coil; R8 + resource shrinking. No accounts, no sync, no Firebase Auth/App Check/Analytics, no crash-reporting, analytics, ads or tracking SDKs. Every network host is listed in docs/network-calls.md.
Data model: ReadingPosition (one row per book; the feed resumes the latest, B4 "Continue" resumes that book **[done]**), VerseMark (liked/bookmarked/studied), Note, SavedStudy, ChapterRead, PlanEnrollment, PlanDayProgress, DevotionalProgress, StreakDay, Settings (CommunityVote: a local "I voted this month" table, Session 33). All user data is on the device only. User tables keep updatedAt (and tombstones where rows can be deleted) so Import (S9) merges newest-change-wins.
Privacy: no account and no tracking. User data never leaves the phone except through Android's own backup and the user's own export file. No ads, analytics, advertising IDs (no AD_ID permission) or crash-reporting SDKs. Crashes are read from Play Console Android vitals, deobfuscated with the R8 mapping that ships inside the release bundle. Network: static CDN downloads with no identifiers, plus the anonymous vote counter (Session 33). The Play listing and in-app Legal (S8) state this plainly: "We don't track you. No account, no ads, no analytics. Your data stays on your phone."
Smoothness: test in release builds with Baseline Profiles; preload adjacent pages; Room reads off main thread as Flow; stable state; haptics.

Build phases:
1. Core reader: O1–O8, B1–B7, B9–B11, bundled translations, notes/likes/bookmarks, themes, font size, reminders, widget.
2. Study & Discover: content pipeline + CDN, B8, D1–D2, Library H3–H7.
3. Habits: H1 Home, H2 Streak, plans H8–H14, devotionals H15–H17.
4. Data & Community: S9 Data & backup (export/import), C1–C2 anonymous monthly vote, Spanish UI **[done]** (per-app language, Spanish strings reviewed by a native speaker, book names/aliases, reference formats by language; docs/translation/), more translations. Session checklist (29–34) in CLAUDE.md.

## 5. Agreed additions (now part of the spec in section 4)
| # | Addition | Where in §4 | Status |
|---|---|---|---|
| 1 | Citations on every study | Content & licensing | Planned (Phase 2) |
| 2 | "Hide AI studies" setting | Settings S2 | Planned (Phase 2) |
| 3 | Reading position per book | Data model, B4 | **Done** |
| 4 | No-tracking privacy stance | Privacy | Planned (Play listing, S8) |
| 5 | Theme expanded into Appearance | Settings S3 | **Done** |

Later phase idea (not agreed): community features (friends, shared verses, prayer) — needs moderation.

Later: accounts and sync (not planned). v1 has no accounts, sign-in or sync (docs/decisions.md). If they are ever added, they must stay optional (the app keeps working fully signed out), the updatedAt columns and tombstones already support a newest-change-wins merge, and the backend choice, privacy copy, Data safety form and docs/network-calls.md all need revisiting first.

## 6. Open questions
App name/logo/trademark; which licensed translations; who does theological review; Community vote at launch or later; streak grace days.

## 7. Tooling & plan
- Claude Code CLI + Claude Code plugin in Android Studio; run from project root; keep spec at docs/spec.md.
- Model: Opus 5.5 for most work; Fable 5.2 only for hard problems (on Pro it costs usage credits).
- On Claude Pro ($20): realistic to build Phase 1 in ~1 week; full app ~3–6 weeks. Upgrade to Max 5x if limits block progress. Check usage with /usage.
- Work one feature per session, test on a real phone after each feature.

## 8. Next step
Scaffold the Android project and build Phase 1, starting with the B1 verse feed.