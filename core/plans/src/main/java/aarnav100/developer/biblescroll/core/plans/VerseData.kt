package aarnav100.developer.biblescroll.core.plans

import java.io.InputStream

/** A run of verses within one book: book id 1-66 and KJV chapter/verse numbers. */
data class Passage(val bookId: Int, val startChapter: Int, val startVerse: Int, val endChapter: Int, val endVerse: Int)

/** One day of a generated plan. */
data class GeneratedDay(val passages: List<Passage>, val words: Int)

data class GeneratedPlan(val days: List<GeneratedDay>, val minutesPerDay: Int)

/**
 * assets/plans/verses.bin, written by the pipeline (plans/verse_data.py): every KJV verse with the same word
 * count and boundary tier the pipeline uses, so plans built here match the pipeline's exactly.
 */
class VerseData private constructor(
    /** chapterVerses[book - 1][chapter - 1] = verse count. */
    private val chapterVerses: List<IntArray>,
    private val words: IntArray,
    private val tiers: IntArray,
) {
    /** Index of each book's first verse in [words]. */
    private val bookStart = IntArray(67).also { starts ->
        var at = 0
        for (b in 1..66) {
            starts[b - 1] = at
            at += chapterVerses[b - 1].sum()
        }
        starts[66] = at
    }

    val verseCount: Int get() = words.size

    fun chapterCount(bookId: Int): Int = chapterVerses[bookId - 1].size

    /** Total words in [bookIds]. */
    fun words(bookIds: Collection<Int>): Long = bookIds.distinct().sumOf { b -> (bookStart[b - 1] until bookStart[b]).sumOf { words[it].toLong() } }

    fun verses(bookIds: Collection<Int>): Int = bookIds.distinct().sumOf { b -> bookStart[b] - bookStart[b - 1] }

    /** Days at [minutes] a day for [bookIds]. */
    fun daysForMinutes(bookIds: Collection<Int>, minutes: Int): Int =
        PlanSplitter.daysForMinutes(words(bookIds), minutes, verses(bookIds))

    /** Splits [bookIds] (read in canonical order) into [days] days. */
    fun plan(bookIds: Collection<Int>, days: Int): GeneratedPlan {
        val books = bookIds.distinct().sorted()
        require(books.isNotEmpty() && books.all { it in 1..66 }) { "Pick at least one book" }
        val indices = books.flatMap { b -> bookStart[b - 1] until bookStart[b] }
        val w = IntArray(indices.size) { words[indices[it]] }
        val t = IntArray(indices.size) { tiers[indices[it]] }
        val cuts = PlanSplitter.planCuts(w, t, days)
        val dayWords = PlanSplitter.dayWords(w, cuts)
        val keys = indices.map { keyOf(it) }
        val bounds = intArrayOf(-1, *cuts, indices.size - 1)
        val generated = (0 until days).map { d ->
            val from = bounds[d] + 1
            val to = bounds[d + 1]
            val passages = mutableListOf<Passage>()
            var runStart = from
            for (i in from..to) {
                val endsRun = i == to || keys[i + 1][0] != keys[i][0]
                if (endsRun) {
                    val a = keys[runStart]
                    val z = keys[i]
                    passages += Passage(a[0], a[1], a[2], z[1], z[2])
                    runStart = i + 1
                }
            }
            GeneratedDay(passages, dayWords[d])
        }
        return GeneratedPlan(generated, PlanSplitter.estimateMinutes(dayWords))
    }

    /** [book, chapter, verse] of the verse at canonical [index]. */
    private fun keyOf(index: Int): IntArray {
        var book = 1
        while (bookStart[book] <= index) book++
        var rest = index - bookStart[book - 1]
        val chapters = chapterVerses[book - 1]
        var chapter = 0
        while (rest >= chapters[chapter]) {
            rest -= chapters[chapter]
            chapter++
        }
        return intArrayOf(book, chapter + 1, rest + 1)
    }

    companion object {
        const val ASSET = "plans/verses.bin"
        private val MAGIC = "BSPV".toByteArray()
        private const val VERSION = 1

        /** Fails loudly on a file it doesn't understand. */
        fun read(input: InputStream): VerseData {
            val bytes = input.readBytes()
            require(bytes.size > 5 && bytes.copyOfRange(0, 4).contentEquals(MAGIC)) { "Not a verses.bin file" }
            require(bytes[4].toInt() == VERSION) { "verses.bin version ${bytes[4]} isn't supported" }
            var at = 5
            fun next(): Int {
                require(at < bytes.size) { "verses.bin is truncated" }
                return bytes[at++].toInt() and 0xFF
            }
            val chapters = (1..66).map { IntArray(next()) { next() } }
            val count = chapters.sumOf { it.sum() }
            require(bytes.size - at == count * 2) { "verses.bin has ${bytes.size - at} bytes of verse data; expected ${count * 2}" }
            val words = IntArray(count)
            val tiers = IntArray(count)
            for (i in 0 until count) {
                words[i] = next()
                tiers[i] = next()
            }
            return VerseData(chapters, words, tiers)
        }
    }
}
