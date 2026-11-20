# Network calls

Every host the app contacts. **Any new network call must be added here in the same change** (CLAUDE.md).
HTTPS only: cleartext is blocked by `app/src/main/res/xml/network_security_config.xml`.

Last checked: 2026-11-19 (history.json and ministry images added; Community counter reads no longer read past months; earlier: Community refresh rules and counter reads updated; earlier full pass 2026-11-18: code, OkHttp/Retrofit, Coil, WorkManager jobs, Glance widget, notifications, S7 email intent).

## Hosts

| Host | Ours? | What | Sent | Code |
|---|---|---|---|---|
| `biblescroll-content.web.app` | Yes: our CDN (Firebase Hosting, static files only; no Firebase SDK in the app) | `manifest.json`, per-book study files, devotional month files, `discover.json`, `ministries.json` and `history.json` (Community, both hash-pinned in the manifest), ministry card images placed on the Hosting site | Plain HTTPS GET. No cookies, no identifiers, no custom headers. OkHttp's default `User-Agent: okhttp/<version>` only | `core/content/build.gradle.kts` (`CONTENT_BASE_URL`), `core/content/.../di/ContentModule.kt` (`provideOkHttp`, `provideContentApi`), `ContentApi.kt` |

| `firestore.googleapis.com` | Yes: Cloud Firestore REST in our Firebase project (`FIRESTORE_PROJECT_ID`, default `biblescroll-content`). No Firebase SDK, no Auth, no App Check, no API key | Community vote counter (C1): `documents:commit` with one increment-by-1 transform on `community_votes/{YYYY-MM_ministryId}`; `documents:batchGet` of this month's counters to tell whether voting is open (counts are never shown) | Plain HTTPS POST. The body names the month and ministry id (and, for the round check, the month's doc names). No token, no user or device id, no cookies; OkHttp's default `User-Agent` only. Like any server, Google sees the phone's IP address with each request | `core/content/build.gradle.kts` (`FIRESTORE_PROJECT_ID`), `ContentModule.provideFirestoreApi`, `net/FirestoreApi.kt`, `CommunityCounter.kt`; rules in `backend/firestore.rules` |
| `firebasestorage.googleapis.com` | Yes: Firebase Storage in our project; only paths `/v0/b/biblescroll-content.firebasestorage.app/o/…` or `/v0/b/biblescroll-content.appspot.com/o/…`. No Firebase SDK | Ministry card images (Community C1), from the optional `image` URL in ministries.json | Plain HTTPS GET, OkHttp's default `User-Agent` only. No cookies, no identifiers. Like any server, Google sees the IP address | `core/content/.../MinistryImages.kt` (host allow-list; `isAllowed`), `ContentModule.provideMinistryImages`; publish check `pipeline/src/pipeline/community.py` `image_problems` |

Callers of that host:

- `BookDownloader`: manifest + book study files (on demand, and from `StudyRefreshWorker`).
- `DevotionalDownloader`: manifest + devotional month files.
- `DiscoverRepository`: manifest + `discover.json` (format 2: verse ids and card themes, no AI text), once per Discover visit and on Retry. Since 2026-11-19 this also runs with Hide AI studies on; study books are still never prefetched then.
- `StudyRefreshWorker` (WorkManager, unique periodic, every 7 days, unmetered network + battery not low; scheduled in `BibleScrollApplication`).
- `CommunityContentRepository`: manifest + `ministries.json`. When the Community tab is shown (every resume), only if
  nothing is cached, the last check is over 6 hours old, or the manifest cached by another downloader pins a different
  ministries hash; always on pull to refresh and "Try again". `ministries.json` itself is downloaded only when the
  manifest's hash differs from the cached file's. Firebase Hosting serves `*.json` with `Cache-Control: max-age=300`,
  so a new list reaches phones within about 5 minutes of a check.

- `CommunityHistoryRepository`: manifest + `history.json` (Community → History). At most once per UTC calendar month
  (`HistoryRules.shouldFetch`); while the cached file lacks last month's record, at most once a day until it appears.
  Only when the History screen opens; offline, the cached copy shows and nothing is retried in the background.
- `MinistryImages`: each ministry card's `image`, once per URL (kept in cacheDir), when the Community tab shows that
  card. Only our Hosting site or our Storage bucket (above); any other host is never contacted and the card shows a
  tinted fallback. At most 2 MB per image; non-image responses are refused.

Callers of `firestore.googleapis.com`:

- `CommunityRepository.vote` (one tap, after the confirm dialog).
- `CommunityRepository.read` (`documents:batchGet`, read-only): this month's counters each time the Community tab opens
  and on refresh, only to tell whether the round is set up and open. No counts or percentages are shown anywhere, and
  past months are no longer read (2026-11-19: the History screen shows donations from history.json instead). Request
  body: doc names only. Successful reads are cached in memory for 5 minutes (open month) or 1 hour (closed month);
  pull to refresh, Try again and a refused vote bypass the cache. Each ministry is one billed read: with 5 ministries
  a month, one open costs at most 5 reads, and reopening within 5 minutes costs nothing.
- `CommunityVoteWorker` (WorkManager, one-time unique, network required, exponential backoff): sends votes saved while offline; scheduled after an offline vote and at app start if one is waiting.

## Checked and found no network use

- **Coil**: `coil-compose` only draws local files (ministry images already downloaded by `MinistryImages`); the `coil-network-*` module is not included, so Coil itself cannot fetch URLs.
- **Glance widget** (`widget/VerseWidget.kt`) and **notifications** (`data/reminder/`): local `biblescroll://` deep links only.
- **Community "Website" / "Give directly"**: open the ministry's own https page in a Custom Tab (the person's browser), only on tap. No `warmup()` or `mayLaunchUrl()` prefetch, so nothing loads before the tap. The app sends nothing to those sites itself; the browser does, as for any link.
- **Email intent (S7 Report a problem / Send feedback)**: no network call by the app. It opens the person's own email
  app with `ACTION_SENDTO mailto:` (address = `supportEmail` in `app/build.gradle.kts`), subject and body filled in.
  Nothing is sent automatically, nothing is attached, no logs; the person reviews and sends it, which shows us their
  email address. The body holds their note plus report type, content type and id, translation, UI language, content
  version, prompt hash (when the study carries one), app version, Android version and device model: no user or device
  identifiers. With no email app, the text is shown to copy. Code: `feature/report/` (`ProblemReport`, `ReportSender`).
  The manifest's `<queries>` entry (SENDTO + mailto) only lets the app check that an email app exists.
- **Debug only**: LeakCanary makes no network calls.

## Flags

- None of the hosts above is outside our own Firebase project (Hosting, Firestore, Storage).
- Manifest entries may hold absolute URLs (`resolve()` in `BookDownloader`, `DevotionalDownloader`, `DiscoverRepository`).
  The pipeline only ever writes CDN URLs, but the app does not restrict the host. Every file is SHA-256 checked
  against the manifest, which itself comes from the CDN. Consider pinning to `CONTENT_BASE_URL`'s host.

## Planned

None.
