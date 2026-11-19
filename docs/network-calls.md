# Network calls

Every host the app contacts. **Any new network call must be added here in the same change** (CLAUDE.md).
HTTPS only: cleartext is blocked by `app/src/main/res/xml/network_security_config.xml`.

Last checked: 2026-11-18 (code, OkHttp/Retrofit, Coil, WorkManager jobs, Glance widget, notifications).

## Hosts

| Host | Ours? | What | Sent | Code |
|---|---|---|---|---|
| `biblescroll-content.web.app` | Yes: our CDN (Firebase Hosting, static files only; no Firebase SDK in the app) | `manifest.json`, per-book study files, devotional month files, `discover.json` | Plain HTTPS GET. No cookies, no identifiers, no custom headers. OkHttp's default `User-Agent: okhttp/<version>` only | `core/content/build.gradle.kts` (`CONTENT_BASE_URL`), `core/content/.../di/ContentModule.kt` (`provideOkHttp`, `provideContentApi`), `ContentApi.kt` |

Callers of that host:

- `BookDownloader`: manifest + book study files (on demand, and from `StudyRefreshWorker`).
- `DevotionalDownloader`: manifest + devotional month files.
- `DiscoverRepository`: manifest + `discover.json`.
- `StudyRefreshWorker` (WorkManager, unique periodic, every 7 days, unmetered network + battery not low; scheduled in `BibleScrollApplication`).

## Checked and found no network use

- **Coil**: `coil-compose` is a dependency but no code loads images through it, and the `coil-network-*` module is not included, so Coil cannot fetch URLs.
- **Glance widget** (`widget/VerseWidget.kt`) and **notifications** (`data/reminder/`): local `biblescroll://` deep links only.
- **No in-app external links** (`LocalUriHandler`, `ACTION_VIEW`, `mailto:`) today.
- **Debug only**: LeakCanary makes no network calls.

## Flags

- None of the hosts above is outside our CDN.
- Manifest entries may hold absolute URLs (`resolve()` in `BookDownloader`, `DevotionalDownloader`, `DiscoverRepository`).
  The pipeline only ever writes CDN URLs, but the app does not restrict the host. Every file is SHA-256 checked
  against the manifest, which itself comes from the CDN. Consider pinning to `CONTENT_BASE_URL`'s host.

## Planned

| Host | When | What | Sent |
|---|---|---|---|
| Cloud Firestore (exact host TBD) | Session 33 | Community vote: anonymous add-one counter for the chosen ministry | The increment only. No user/device IDs, no auth, no App Check. Record the final host and code path here when built. |
