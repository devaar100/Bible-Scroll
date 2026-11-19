# Network calls

Every host the app contacts. **Any new network call must be added here in the same change** (CLAUDE.md).
HTTPS only: cleartext is blocked by `app/src/main/res/xml/network_security_config.xml`.

Last checked: 2026-11-18 (code, OkHttp/Retrofit, Coil, WorkManager jobs, Glance widget, notifications, S7 email intent).

## Hosts

| Host | Ours? | What | Sent | Code |
|---|---|---|---|---|
| `biblescroll-content.web.app` | Yes: our CDN (Firebase Hosting, static files only; no Firebase SDK in the app) | `manifest.json`, per-book study files, devotional month files, `discover.json`, `ministries.json` (Community, hash-pinned in the manifest) | Plain HTTPS GET. No cookies, no identifiers, no custom headers. OkHttp's default `User-Agent: okhttp/<version>` only | `core/content/build.gradle.kts` (`CONTENT_BASE_URL`), `core/content/.../di/ContentModule.kt` (`provideOkHttp`, `provideContentApi`), `ContentApi.kt` |

| `firestore.googleapis.com` | Yes: Cloud Firestore REST in our Firebase project (`FIRESTORE_PROJECT_ID`, default `biblescroll-content`). No Firebase SDK, no Auth, no App Check, no API key | Community vote counter (C1/C2): `documents:commit` with one increment-by-1 transform on `community_votes/{YYYY-MM_ministryId}`; `documents:batchGet` to read totals | Plain HTTPS POST. The body names the month and ministry id (and, for totals, the month's doc names). No token, no user or device id, no cookies; OkHttp's default `User-Agent` only. Like any server, Google sees the phone's IP address with each request | `core/content/build.gradle.kts` (`FIRESTORE_PROJECT_ID`), `ContentModule.provideFirestoreApi`, `net/FirestoreApi.kt`, `CommunityCounter.kt`; rules in `backend/firestore.rules` |

Callers of that host:

- `BookDownloader`: manifest + book study files (on demand, and from `StudyRefreshWorker`).
- `DevotionalDownloader`: manifest + devotional month files.
- `DiscoverRepository`: manifest + `discover.json`.
- `StudyRefreshWorker` (WorkManager, unique periodic, every 7 days, unmetered network + battery not low; scheduled in `BibleScrollApplication`).
- `CommunityContentRepository`: manifest + `ministries.json` (opening the Community tab, "Try again").

Callers of `firestore.googleapis.com`:

- `CommunityRepository.vote` (one tap, after the confirm dialog), `CommunityRepository.totals` (Community tab: past months, and this month only after voting).
- `CommunityVoteWorker` (WorkManager, one-time unique, network required, exponential backoff): sends votes saved while offline; scheduled after an offline vote and at app start if one is waiting.

## Checked and found no network use

- **Coil**: `coil-compose` is a dependency but no code loads images through it, and the `coil-network-*` module is not included, so Coil cannot fetch URLs.
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

- None of the hosts above is outside our CDN.
- Manifest entries may hold absolute URLs (`resolve()` in `BookDownloader`, `DevotionalDownloader`, `DiscoverRepository`).
  The pipeline only ever writes CDN URLs, but the app does not restrict the host. Every file is SHA-256 checked
  against the manifest, which itself comes from the CDN. Consider pinning to `CONTENT_BASE_URL`'s host.

## Planned

None.
