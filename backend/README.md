# backend/: Community vote counter (Cloud Firestore)

The app's Community tab (C1/C2) keeps one counter per ministry per month in Cloud Firestore:
`community_votes/{YYYY-MM_ministryId}` = `{ count, opensAt, closesAt }`. The app talks to it over the Firestore REST
API with no Firebase SDK, no Auth, no App Check and no identifiers (docs/decisions.md #16, docs/network-calls.md).

| File | What |
|---|---|
| `firestore.rules` | Anyone reads. Clients can't create or delete. An update may change only `count`, by exactly +1, while `opensAt <= request.time < closesAt`. |
| `test/rules.test.mjs` | Emulator tests with raw REST commits (increment transforms), as the app sends them. |
| `scripts/seed-month.mjs` | Creates a month's counters (count 0, UTC window) from `pipeline/community/ministries.json`. Never overwrites an existing doc. |
| `test/seed.test.mjs` | The seed script against the emulator. |

No npm dependencies. Node 20+.

## Test

The Firestore emulator needs **JDK 21+** (firebase-tools refuses older Java). Android Studio's bundled runtime works:

```sh
cd backend
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
npm test   # starts the emulator for the demo project "demo-biblescroll"; touches no real project
```

## Deploy the rules

```sh
cd backend
firebase deploy --only firestore:rules --project <your-project-id>
```

The project needs a Firestore database (Native mode, `(default)`). The app's project id is `FIRESTORE_PROJECT_ID` in
`core/content/build.gradle.kts`.

## Seed next month

Run it yourself before each month starts. It needs a service-account key with Firestore write access, **kept outside
this repo** (`backend/.gitignore` blocks common key names, and the script refuses a key path inside the repo):

```sh
cd backend
node scripts/seed-month.mjs --project <your-project-id> --dry-run            # what it would create
GOOGLE_APPLICATION_CREDENTIALS=~/keys/biblescroll-seed.json \
  node scripts/seed-month.mjs --project <your-project-id> [--month 2026-12]  # default: next UTC month
```

Or with your existing `firebase login` (no key file; the token stays in memory), several months at once. Every
document is printed before it's written, and an existing counter is never touched:

```sh
node scripts/seed-month.mjs --project <your-project-id> --firebase-login --month 2026-11 --month 2026-12 --dry-run
node scripts/seed-month.mjs --project <your-project-id> --firebase-login --month 2026-11 --month 2026-12
node scripts/seed-month.mjs --project <your-project-id> --verify --month 2026-11 --month 2026-12   # read-only, no credentials
```

`--include-test` also creates counters for `test:true` ministries (for debug builds). The script mints a short-lived
OAuth token from the key (scope: Firestore only) and never prints it.

## Notes

- A plain update setting `count` to old+1 passes the rules too (rules see only the result); the app always uses the
  increment transform, which is safe under concurrent votes.
- Without Auth or App Check anyone can send +1s outside the app. See decision #16 for the trade-off.
