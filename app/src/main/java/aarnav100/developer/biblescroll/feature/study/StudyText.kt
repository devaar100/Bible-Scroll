package aarnav100.developer.biblescroll.feature.study

import aarnav100.developer.biblescroll.core.content.BookCodes
import aarnav100.developer.biblescroll.core.content.model.Study
import aarnav100.developer.biblescroll.data.bible.References
import aarnav100.developer.biblescroll.data.bible.model.VerseRef

/** D1 card teaser: the study's first sentence, cut at a word boundary to about [maxChars]. */
fun studyTeaser(study: Study, maxChars: Int = 140): String {
    val text = study.meaning.trim().replace(Regex("\\s+"), " ")
    val firstSentence = Regex("^.+?[.!?](?=\\s|$)").find(text)?.value ?: text
    if (firstSentence.length <= maxChars) return firstSentence
    val cut = firstSentence.take(maxChars + 1).substringBeforeLast(' ').trimEnd(',', ';', ':', ' ')
    return "$cut…"
}

/** A cross reference ("ROM.8.28" or "JHN.1.1-JHN.1.3") as app refs; null if malformed. */
data class RefRange(val start: VerseRef, val end: VerseRef)

fun parseRefRange(ref: String): RefRange? {
    val parts = ref.split("-")
    if (parts.size !in 1..2) return null
    val start = BookCodes.parse(parts[0]) ?: return null
    val end = if (parts.size == 2) BookCodes.parse(parts[1]) ?: return null else start
    return RefRange(VerseRef(start.bookId, start.chapter, start.verse), VerseRef(end.bookId, end.chapter, end.verse))
}

/** "Romans 8:28", "Romans 8:28–30", "Genesis 1:1–2:3", "Leviticus 27:34 – Numbers 1:1", in the app's language. */
fun formatRefRange(range: RefRange, bookNames: Map<Int, String>): String {
    val (a, b) = range
    return References.range(bookNames[a.bookId].orEmpty(), a, b, bookNames[b.bookId].orEmpty())
}
