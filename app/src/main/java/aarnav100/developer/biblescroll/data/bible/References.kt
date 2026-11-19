package aarnav100.developer.biblescroll.data.bible

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import android.content.res.Resources
import java.util.Locale

/**
 * How verse references are written in a language: "John 3:16", "John 3:16–18", "Genesis 1:1–2:3",
 * "Leviticus 27:34 – Numbers 1:1", "Psalm 23". Patterns are string resources (ref_*), so each language sets
 * its own order and punctuation; numbers are formatted for [locale].
 */
data class ReferencePatterns(
    val locale: Locale,
    val verse: String,
    val verseRange: String,
    val chapterRange: String,
    val bookRange: String,
    val chapter: String,
) {
    companion object {
        /** Same as res/values/strings.xml; the default before resources load, and in JVM tests. */
        val ENGLISH = ReferencePatterns(
            Locale.ENGLISH, "%1\$s %2\$d:%3\$d", "%1\$s %2\$d:%3\$d–%4\$d", "%1\$s %2\$d:%3\$d–%4\$d:%5\$d",
            "%1\$s – %2\$s", "%1\$s %2\$d",
        )

        fun from(res: Resources) = ReferencePatterns(
            locale = res.configuration.locales[0] ?: Locale.getDefault(),
            verse = res.getString(R.string.ref_verse),
            verseRange = res.getString(R.string.ref_verse_range),
            chapterRange = res.getString(R.string.ref_chapter_range),
            bookRange = res.getString(R.string.ref_book_range),
            chapter = res.getString(R.string.ref_chapter),
        )
    }
}

/**
 * Formats references in the app's language. [patterns] is process-wide, like Locale.getDefault(): AppLanguage
 * replaces it at start and on every language change, so ViewModels and the widget needn't each inject a formatter.
 * Screens recompute their text when BibleRepository.books() re-emits localized names after the change.
 */
object References {
    @Volatile
    var patterns: ReferencePatterns = ReferencePatterns.ENGLISH

    fun verse(book: String, ref: VerseRef): String = verse(book, ref.chapter, ref.verse)

    fun verse(book: String, chapter: Int, verse: Int): String =
        patterns.run { String.format(locale, this.verse, book, chapter, verse) }

    fun chapter(book: String, chapter: Int): String = patterns.run { String.format(locale, this.chapter, book, chapter) }

    /** A passage from [start] to [end] (inclusive). [endBook] is only used when the books differ. */
    fun range(book: String, start: VerseRef, end: VerseRef, endBook: String = book): String = patterns.run {
        when {
            start == end -> verse(book, start)
            start.bookId != end.bookId -> String.format(locale, bookRange, verse(book, start), verse(endBook, end))
            start.chapter != end.chapter ->
                String.format(locale, chapterRange, book, start.chapter, start.verse, end.chapter, end.verse)
            else -> String.format(locale, verseRange, book, start.chapter, start.verse, end.verse)
        }
    }
}
