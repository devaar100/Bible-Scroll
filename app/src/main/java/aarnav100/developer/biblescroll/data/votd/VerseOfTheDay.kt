package aarnav100.developer.biblescroll.data.votd

import aarnav100.developer.biblescroll.core.content.AssetSource
import aarnav100.developer.biblescroll.core.content.BookCodes
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** One row of assets/votd.csv. */
data class VotdEntry(val monthDay: MonthDay, val ref: VerseRef, val id: String, val section: String)

/**
 * The Verse of the Day list (spec H1): one verse per month-day, the same for everyone on that date in every year.
 * 02-29 has its own entry, shown only in leap years.
 */
class VotdList(val entries: List<VotdEntry>) {
    private val byMonthDay = entries.associateBy { it.monthDay }

    /** The entry for [date]'s month and day, or null if the list has none for it. */
    fun entryFor(date: LocalDate): VotdEntry? = byMonthDay[MonthDay.from(date)]

    companion object {
        const val ASSET_PATH = "votd.csv"
        private const val HEADER = "date,id,section"
        private val MONTH_DAY = DateTimeFormatter.ofPattern("MM-dd")

        /**
         * Parses `date,id,section` rows (date MM-DD, id like JHN.3.16). Fails loudly on a malformed row, so a bad
         * list is caught by VotdListTest instead of silently skipping a day. Duplicates and gaps are left for the
         * tests to report.
         */
        fun parse(csv: String): VotdList {
            val lines = csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            require(lines.firstOrNull() == HEADER) { "votd.csv: expected header \"$HEADER\", got \"${lines.firstOrNull()}\"" }
            val entries = lines.drop(1).mapIndexed { index, line ->
                val lineNo = index + 2
                val parts = line.split(',', limit = 3)
                require(parts.size == 3) { "votd.csv line $lineNo: expected 3 fields: $line" }
                val (date, id, section) = parts.map { it.trim() }
                val monthDay = try {
                    MonthDay.parse("--$date")
                } catch (e: DateTimeParseException) {
                    throw IllegalArgumentException("votd.csv line $lineNo: bad date \"$date\"", e)
                }
                require(MONTH_DAY.format(monthDay) == date) { "votd.csv line $lineNo: date must be MM-DD: \"$date\"" }
                val key = BookCodes.parse(id) ?: throw IllegalArgumentException("votd.csv line $lineNo: bad verse id \"$id\"")
                VotdEntry(monthDay, VerseRef(key.bookId, key.chapter, key.verse), id, section)
            }
            return VotdList(entries)
        }
    }
}

/** Reads assets/votd.csv once and keeps it in memory. A list that won't parse is logged and treated as absent. */
class VotdSource(
    private val assets: AssetSource,
    private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var loaded = false
    private var cached: VotdList? = null

    suspend fun list(): VotdList? = mutex.withLock {
        if (!loaded) {
            cached = withContext(io) {
                try {
                    assets.open(VotdList.ASSET_PATH)?.use { VotdList.parse(it.readBytes().decodeToString()) }
                } catch (e: IllegalArgumentException) {
                    Log.e("VerseOfTheDay", "votd.csv is invalid; Verse of the Day is hidden", e)
                    null
                }
            }
            loaded = true
        }
        cached
    }
}

/** The current local time. Reads the default zone on every call, so a time-zone change shows on the next read. */
fun interface LocalClock {
    fun now(): ZonedDateTime

    companion object {
        val System = LocalClock { ZonedDateTime.now() }
    }
}

object VotdTime {
    /**
     * The start of the next local day after [now], in [now]'s zone. Uses atStartOfDay, so in a zone whose clocks
     * skip midnight for daylight saving it's the first moment that does exist (e.g. 01:00).
     */
    fun nextLocalMidnight(now: ZonedDateTime): ZonedDateTime = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
}
