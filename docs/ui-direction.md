# UI direction: Community, Discover, Home (agreed 2026-11-24)

Goal: minimal, modern and calm. Scripture comes first and the controls stay quiet. This is our own look: Paper/Charcoal neutrals and one accent (default Amber). It is not a copy of any other app.

## Sources
- Material 3 Expressive: shape and containment set the hierarchy, spring motion and shape morphing give feedback, type emphasis and one strong accent highlight what matters.
- Calm reading and devotional apps: one focal card per screen, serif Scripture, generous space, an action row of outline icons, and tinted inset cards for "extra" content (context, facts, apply-it).

## Principles
1. **One hero per screen.** Each screen has one thing that is clearly first: the verse (Discover), today's reading (Home), this month's vote (Community). Everything else is smaller and quieter.
2. **Tonal cards, no chrome.** Cards are `surfaceContainerLow` on the background. No borders or shadows. Corners are 28 dp for hero cards, 20 dp for inset cards and 12 dp for chips. Screen gutters are 20 dp, on an 8 dp grid.
3. **Type does the work.** Literata for Scripture, references and screen headlines, in large sizes. Inter for UI. Section labels are small uppercase Inter (labelSmall, +0.14 em tracking) with an optional 14 dp icon. The tracking is set in the style; there are no "ALL CAPS" strings.
4. **Neutrals plus one accent (agreed 2026-11-24).** Neutrals carry the screen. The user's accent (default Amber since 2026-11-24; Crimson blended with the red words of Jesus) goes only on the main button, text links, "on" states (saved, bookmarked, studied, note added, "Your vote"), the selected tab, callout labels ("Did you know?", "Apply it") and the faint quote-mark ornament. It never goes on card backgrounds, panels or body text. The words of Jesus keep their own red, and the like heart is always red (Crimson): a red heart is the convention people expect. No extra tints (theme colours were tried on Discover and turned down).
5. **One primary action per card.** At most one filled button per card. Secondary actions are text buttons or outline icons, and rarer actions go into a bottom sheet. Bottom sheets replace alert dialogs for anything with more than one sentence.
6. **Expressive but gentle motion.** Spring presses (scale 0.96), the selected state morphs its shape (pill → check), and content cross-fades between states. Nothing loops and nothing bounces in the reading view.
7. **Accessible by default.** 48 dp touch targets, TalkBack labels on every icon, and text that wraps instead of being cut off when the font is scaled. en-XA screenshots at 320 dp.

## Per screen

### Community (C1/C2)
**Built 2026-11-24 (iteration 4, mocked up in HTML and approved first).** Neutrals plus the accent (principle 4, applied the same day): no green or per-ministry colours.
- Header: one eyebrow line, "DECEMBER SPOTLIGHT · CLOSES IN 12 DAYS", with the countdown as plain text and no pill. Then a Literata headline, one short purpose line, and an underlined accent "How it works" link that opens a bottom sheet with the full rules.
- Ministry cards: tonal, 28 dp corners, no border or shadow. A 176 dp photo, or with no photo a grey panel with the name's initial in Literata (History uses the same). The category is a pill over the image. Below it: the name and a 3-line description. Tapping the name or description expands the whole description, and tapping again collapses it. This only works when the text is cut, and there are no More/Less buttons, since the "…" is the hint. TalkBack gets "Show full description" / "Show less" as the click label. Then **Vote** (accent filled) with **Website** and **Give directly** as ink text links.
- Vote → confirm bottom sheet: image or initial, "Vote for X?", the anonymous/final line, **Confirm vote** (accent) and Cancel.
- After voting: the headline becomes plain text with a check ("Thank you for voting") and the purpose line explains what happens next, with no card. The chosen card has no outline; its only marker is an accent "✓ Your vote" pill on the image. Every Vote button is hidden, and descriptions and links stay.
- "View history" is a row at the bottom. History cards have no border or shadow and use the initial panel.
- Rejected along the way: green thank-you card, accent outline on the chosen card, countdown pill, About sheet for the links, More/Less buttons.

### Discover
- **Always dark (2026-11-24):** the Discover tab, its tab bar and system bars, and a study opened from a Discover card use the dark (Charcoal) theme whatever the app theme. A study opened from elsewhere follows the app theme.
**Card built 2026-11-24 (v5 + option A, mocked up in HTML and approved first).** Neutrals plus the accent, like Community. The user turned down theme colours and motion.
- A tonal card: surfaceContainerLow, 28 dp corners, no border or shadow, 20 dp gutter. The next card still peeks below.
- Theme and translation as plain section labels at either end, with no chip. The reference is in Literata semi-bold (28 sp), then a large faint hanging quotation mark (the one ornament), then the verse in Literata italic (24/21/19 sp by length), top-aligned so it sits in the same place on every card. The words of Jesus stay red.
- "What it means" (Inter 14) and "Did you know?" (Inter 13, gray inset with a lightbulb) are pinned to the bottom at fixed sizes: 2 lines, then "…", never shrinking, so every card matches. While a study downloads, gray placeholder bars hold the space. When it isn't on the phone and can't download (offline), a faded, dashed outline of the sections holds the space with a small cloud-off caption, "Available when you're online" (the user picked this over a quote card or a gray note box). Up to two related-verse pills sit above the icons: hairline outline, no fill, muted text (they read as links, not another box).
- Four icons, the same Material icons and states as the Bible page: like, note (with the Bible page's dot when a note exists), bookmark, share. Share opens a sheet with Share as image, Share text and Copy.
- Gestures match the Bible page: tap opens the study, double-tap likes, long-press copies. There's no hint text; onboarding should teach "tap a card to study it".
- Removed from the card: the black "Read in context" button, the copy icon and the divider line.
- Rejected along the way: a "Study this verse" link, a "Tap to study" hint, putting copy and note only inside the share sheet, dropping bookmark or the pills, theme colours (tinted card, coloured ornament, tinted page), a theme pill with a divider ornament, and motion (heart burst, verse fade-in).

### Deep study (D2) and Verse study sheet (B8)
**Built 2026-11-24 (mocked up in HTML and approved first).** They share StudyBody, so both changed.
- The verse header is in the card's style: translation label, the reference in Literata, the faint accent quote mark, and the verse in Literata italic with red letters.
- D2: Save and Studied are icons in the top bar (accent when on). "Read in context →" is an accent text link under the verse. There's no bottom bar.
- Sections use small spaced labels with a ✦ (TalkBack: "AI-assisted"), so every prose section stays labelled. "Meaning" leads in Literata 18. "Did you know?" and "Apply it" are gray callouts.
- Word tiles are gray (no outline). Cross references are rows: reference in Literata, verse in gray, ›. Sources are small and gray.
- The AI-assisted provenance line and "Report a problem" sit at the bottom, under a divider.

### Home
**Built 2026-11-25 (mockup v2 with stock photos, approved first).**
- Header: the date as a small label, a time-of-day greeting in Literata (screenTitle), then the streak chip and Settings.
- Verse of the Day: the Discover card look (label, faint accent quote mark, italic verse with red letters, reference in Literata). "Read in context →" is an accent link, with Study, Share and ⋯ (widget verse, copy) as gray icons.
- Today's reading: one compact card. On a plan: the plan's photo, "Plan · Day 3 of 30", the passages in Literata, one button (filled only when the day is due) and a thin accent progress bar. Without a plan: one Continue reading / Start reading row. Several plans page sideways with dots.
- Today's devotional: a 200 dp photo card (the photo matches the devotional's cover style), series and title in white on a bottom scrim, a "Read today" tick, and one countdown line underneath.
- Reading plans: a sideways row of photo cards bleeding to the screen edges, ending in an outlined "Build your own plan" card. "See all" opens the browser.
- Find a verse: unchanged (the wheels, Read and "Study this verse").
- Your library: four tappable counts (red heart for liked, accent icons for the rest). "Open" goes to the hub.
- Section headers are a shared component (ui/components/SectionHeader): a spaced label plus an optional accent link.
- Photos: bundled Unsplash stock (HomePhotos.kt; the Unsplash License needs no credit, and none is shown). This is an exception to "original art" that the user chose.

## Order of work
Community (this session) → Discover → Home, each in its own session.
