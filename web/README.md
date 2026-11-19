# web/: the privacy policy page (not deployed)

`web/privacy/index.html` (English) and `web/privacy/es/index.html` (Spanish) are **generated** from the app's own
strings by `tools/build_privacy_page.py`, the same text S8 Legal → Privacy shows. Never edit them by hand:

```sh
python3 tools/build_privacy_page.py          # after changing any privacy_* string, PRIVACY_UPDATED or supportEmail
python3 tools/build_privacy_page.py --check  # fails if the pages are out of date (LegalTextTest runs the same check)
```

## Where to host it

**Not** on the content site (`biblescroll-content.web.app`). `pipeline publish` deploys `pipeline/out/site/` as a
complete Firebase Hosting release, and Firebase replaces a site's whole file set on every deploy, so a privacy page
there would disappear at the next content publish.

Use a **second Hosting site in the same Firebase project**. It has its own URL and deploy history, and the content
publish (which only deploys the project's default site from `pipeline/firebase.json`) can't touch it:

```sh
# once
firebase hosting:sites:create biblescroll-legal --project <your-project-id>
# each time the text changes
python3 tools/build_privacy_page.py
cd web && firebase deploy --only hosting --project <your-project-id>
```

The URL is then `https://biblescroll-legal.web.app/privacy/` (Spanish: `/privacy/es/`). That's the one to give
Google Play (store listing → Privacy policy). If `biblescroll-legal` is taken, pick another site id and change it in
`web/firebase.json`. Any static host you control (GitHub Pages, your own domain) works too.

Before deploying: set `supportEmail` in `app/build.gradle.kts`, regenerate, and have the text reviewed
(docs/privacy-inventory.md → "For legal review").
