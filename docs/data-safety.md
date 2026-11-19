# Play Console: Data safety answers

Draft answers for Play Console → App content → Data safety, derived from docs/privacy-inventory.md and
docs/network-calls.md on 2026-11-18. **Not legal advice**: item 1 of the inventory's "For legal review" list covers
these answers. Update this file whenever the inventory changes in a way that affects a question below.

Play's terms: data is **collected** when the app transmits it off the device (including to a service provider
acting for us, here Google as host of Firebase Hosting and Cloud Firestore). It is **shared** when it goes to a
third party. Data handled only on the device, or sent by the person through another app they chose (email, share
sheet, browser), is neither.

## Data collection and security

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** (conservative; see "Alternative" below) | Only because of the Community vote |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | HTTPS only; cleartext blocked by network_security_config.xml |
| Do you provide a way for users to request that their data is deleted? | **No** | No account and nothing stored per person on a server. The vote counter keeps only a count that can't be traced to anyone. Everything else is on the phone and is deleted by uninstalling or clearing data. Use the free-text explanation if Play offers one |
| Account creation | **My app does not allow users to create an account** | No accounts, no sign-in |

## Data types

Declare only this row. Everything else is **not collected**.

| Data type | Collected | Shared | Processed ephemerally | Required or optional | Purposes |
|---|---|---|---|---|---|
| App activity → **Other actions** (Community vote: which ministry for which month) | Yes | No (Google processes it as our service provider) | No (it becomes part of a stored count) | Optional (voting is a choice) | App functionality |

**Alternative (for legal review):** the vote carries no identifier and only increments a total, so it may count as
anonymous data that needs no declaration. If counsel agrees, answer "No" to the first question and remove the row.
Declaring it is the safe choice; under-declaring is a policy violation, over-declaring isn't.

## Not declared, and why

| What | Reason |
|---|---|
| Content downloads (manifest, studies, devotionals, discover.json, ministries.json) | No user data is sent: plain GETs with no identifiers or cookies. The IP address is part of any network connection and isn't used for location, identification or anything else |
| Notes, likes, bookmarks, plans, reading history, settings, notification time | Stay on the device. Never transmitted by the app |
| Export my data | The person writes a file through the system file picker. The app transmits nothing |
| Report a problem / Send feedback | The app opens the person's own email app (ACTION_SENDTO) with editable text. Nothing is sent unless they press send in that app. Flagged for legal review: we do receive their email address if they send it |
| Share as text/image, Copy | User-initiated, through the Android share sheet or clipboard to an app they pick |
| Ministry Website / Give directly | Opens the ministry's page in the person's browser on tap. The app sends nothing |
| Android Auto Backup / device transfer | Android's own service, stored in the person's Google account. Not the app transmitting data to us |
| Crash and ANR data | No crash-reporting SDK. Android vitals is collected by Google Play, not by the app |
| Location, contacts, photos, files, device IDs, advertising ID, health, financial info, messages, audio, calendar | None accessed. No AD_ID permission (checked in the merged release and qa manifests) |

## Other App content forms (same section of Play Console)

| Form | Answer |
|---|---|
| Privacy policy | URL of web/privacy/ once hosted (see web/README.md). Must match the in-app S8 Privacy text |
| Ads | **No, my app does not contain ads** |
| App access | **All functionality is available without special access** (no login) |
| Target audience and content | To decide: see inventory legal item 7 (children). Not designed for children under 13 unless counsel says otherwise |
| Government / financial / health apps | No |

## Recheck before each release

- `./gradlew :app:processReleaseMainManifest` and confirm the merged manifest still has no AD_ID or new permissions.
- docs/network-calls.md lists no new host; if it does, revisit "Data types".
- The store listing line "We don't track you. No account, no ads, no analytics. Your data stays on your phone." still
  matches these answers.
