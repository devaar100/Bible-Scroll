# Project: free Android Bible-scrolling app

Full spec: docs/spec.md. Background: docs/context.md. Read the relevant spec section before building a screen.

## Hard rules
- 100% free: no billing library, no entitlement checks, no "premium" flags, no ads, no tracking SDKs.
- Original name, copy, colours and art.

## Stack
Kotlin, Jetpack Compose, Material 3, minSdk 26. MVVM + unidirectional data flow, Hilt, Navigation Compose.
Room (bundled Bible DB via createFromAsset + separate user DB), DataStore for settings, Coil, WorkManager.

## Conventions
- One feature per session. Keep changes small; app must build and run after every task.
- Room reads off the main thread, exposed as Flow. ViewModels expose StateFlow<UiState>.
- Fonts: Literata for Scripture/headlines, Inter for UI. Words of Jesus in red (tuned per reading background); likes use the user's accent (default Crimson).
- Use version catalog (gradle/libs.versions.toml) for all dependencies.
- After changes, run ./gradlew assembleDebug and fix errors before saying you're done.