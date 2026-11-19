package aarnav100.developer.biblescroll.data.bible

import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import kotlinx.coroutines.flow.first

/**
 * Every verse of a passage (one book, KJV numbering) in [translationId], in order. Verses that translation
 * lacks are skipped. Shared by the plan reader (H11) and the devotional reader (H16).
 */
suspend fun BibleRepository.passageVerses(
    translationId: String,
    bookId: Int,
    startChapter: Int,
    startVerse: Int,
    endChapter: Int,
    endVerse: Int,
): List<Verse> {
    val start = ordinalAtOrAfter(translationId, VerseRef(bookId, startChapter, startVerse)) ?: return emptyList()
    val afterEnd = ordinalAtOrAfter(translationId, VerseRef(bookId, endChapter, endVerse + 1)) ?: (verseCount(translationId) + 1)
    val count = afterEnd - start
    if (count <= 0) return emptyList()
    val first = startChapter to startVerse
    val last = endChapter to endVerse
    // Guard against the run spilling into the next chapter or book.
    return versesFrom(translationId, start, count).first().filter {
        val at = it.ref.chapter to it.ref.verse
        it.ref.bookId == bookId && compareValuesBy(at, first, { p -> p.first }, { p -> p.second }) >= 0 &&
            compareValuesBy(at, last, { p -> p.first }, { p -> p.second }) <= 0
    }
}

/** The reader's preferred translation if it's installed, else the first one. */
suspend fun BibleRepository.resolveTranslation(preferred: String): String {
    val all = translations().first()
    return all.firstOrNull { it.id == preferred }?.id ?: all.first().id
}
