package aarnav100.developer.biblescroll.data.bible

import androidx.compose.runtime.Immutable

/** One chapter's place in a translation's feed. Feed page = verse ordinal - 1. */
@Immutable
data class ChapterSpan(
    val bookId: Int,
    val chapter: Int,
    /** Feed page of the chapter's first verse. */
    val firstPage: Int,
    /** Verse rows in this translation (a verse with no text, like WEB Acts 8:37, has no row). */
    val count: Int,
    /** Highest verse number. Differs from [count] only where a translation omits a verse. */
    val lastVerse: Int,
) {
    /** Position of [page] within the chapter, 0-based, clamped to the chapter. */
    fun indexOf(page: Int): Int = (page - firstPage).coerceIn(0, count - 1)

    /** Feed page of the [index]th verse row, clamped to the chapter. */
    fun pageOf(index: Int): Int = firstPage + index.coerceIn(0, count - 1)
}

/**
 * Every chapter of one translation in feed order, for the B1 scrubber: finds the chapter of any page without a
 * database query (one query per translation builds it, ~1,200 rows).
 */
@Immutable
class ChapterSpans(
    val translationId: String,
    private val bookIds: IntArray,
    private val chapters: IntArray,
    private val firstOrdinals: IntArray,
    private val counts: IntArray,
    private val lastVerses: IntArray,
) {
    init {
        val n = firstOrdinals.size
        require(bookIds.size == n && chapters.size == n && counts.size == n && lastVerses.size == n) { "Arrays differ in length" }
        for (i in 1 until n) require(firstOrdinals[i] > firstOrdinals[i - 1]) { "Chapters must be in feed order" }
    }

    val size: Int get() = firstOrdinals.size

    /** The chapter holding feed [page], or null if no chapter does. */
    fun spanAt(page: Int): ChapterSpan? {
        val ordinal = page + 1
        var lo = 0
        var hi = firstOrdinals.size - 1
        var found = -1
        while (lo <= hi) { // last chapter whose first ordinal is <= ordinal
            val mid = (lo + hi) ushr 1
            if (firstOrdinals[mid] <= ordinal) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        if (found < 0 || ordinal >= firstOrdinals[found] + counts[found]) return null
        return ChapterSpan(bookIds[found], chapters[found], firstOrdinals[found] - 1, counts[found], lastVerses[found])
    }

    companion object {
        fun of(translationId: String, rows: List<ChapterSpanRow>) = ChapterSpans(
            translationId,
            IntArray(rows.size) { rows[it].bookId },
            IntArray(rows.size) { rows[it].chapter },
            IntArray(rows.size) { rows[it].firstOrdinal },
            IntArray(rows.size) { rows[it].verseCount },
            IntArray(rows.size) { rows[it].lastVerse },
        )
    }
}
