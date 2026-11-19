# Translating Bible Scroll

Files for translators (regenerate after any string change: `python3 tools/export_strings.py`):

- `strings-en.xliff`: XLIFF 1.2 for CAT tools (Crowdin, Lokalise, Weblate, POEditor). Target language `es`.
- `strings-en.csv`: the same as a spreadsheet: `id, kind, english, where, note, placeholders`.

Every string has a **where** note: the screen it appears on (spec ids such as "S9 Settings → Data & backup"),
taken from the code that uses it. Some also have a **developer** note with context.

## Rules

- **Placeholders** (`%1$s`, `%2$d`, …): keep every one; you may move them. `%1$s` is text (a name, a reference),
  `%1$d` a number. Write `%%` for a literal percent sign.
- **Plurals**: ids like `import_notes[one]` / `import_notes[other]` are forms of one message. Spanish needs
  `one`, `many` (for large round numbers such as 1 000 000) and `other`; add `many` if your tool asks for it.
- **Lists** ("A, B and C") are built by the app in each language's own style; don't add separators.
- **Verse references** use `ref_*` patterns (book, chapter, verse); change punctuation only if your language
  writes references differently.
- **Book names, abbreviations and search aliases** are in `app/src/main/res/values-es/books.xml` already
  (Reina-Valera tradition). Review them; aliases are separated by `|`.
- Keep text about 30% longer than English at most where you can: the pseudo-locale test checks layouts at that
  length on a 320 dp phone.
- Don't translate: `app_name` ("Bible Scroll"), translation codes (WEB, KJV), Strong's numbers.

## Returning a translation

Spanish (`es`) is done and offered in release. For another language:
1. Put its `strings.xml` in `app/src/main/res/values-xx/` and its book names in `values-xx/books.xml`.
2. Add `<locale android:name="xx" />` to `app/src/main/res/xml/locales_config.xml` and `xx` to `app_languages` in
   `app/src/main/res/values/arrays.xml`.
3. Run `./gradlew test` (LocalizationTest checks the lists and files match) and the pseudo-locale screenshots.

For changed or new English strings, send only those ids back to the translator; until they're translated, Spanish
users see the English text.
