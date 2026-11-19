package aarnav100.developer.biblescroll.data.plans

import aarnav100.developer.biblescroll.core.content.AssetSource
import aarnav100.developer.biblescroll.core.content.BookCodes
import aarnav100.developer.biblescroll.core.plans.GeneratedPlan
import aarnav100.developer.biblescroll.core.plans.VerseData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** H14 pace: minutes a day, or a number of days. */
sealed interface Pace {
    val value: Int

    data class MinutesPerDay(override val value: Int) : Pace
    data class Days(override val value: Int) : Pace

    companion object {
        val MINUTE_CHOICES = listOf(5, 10, 15, 20)
        val MINUTES_RANGE = 1..60
        const val MAX_DAYS = 730
    }
}

/**
 * Builds custom plans with the algorithm shared with the pipeline (core/plans), from assets/plans/verses.bin,
 * so a custom "John in 21 days" is the same as the pipeline would make.
 */
class CustomPlanBuilder(private val assets: AssetSource, private val io: CoroutineDispatcher) {
    private val lock = Mutex()
    private var data: VerseData? = null

    suspend fun verseData(): VerseData = lock.withLock {
        data ?: withContext(io) {
            assets.open(VerseData.ASSET)?.use(VerseData::read) ?: error("Missing ${VerseData.ASSET}")
        }.also { data = it }
    }

    /** Days for [pace], kept within 1 and one verse a day (and [Pace.MAX_DAYS]). */
    suspend fun days(bookIds: Collection<Int>, pace: Pace): Int {
        val data = verseData()
        val most = minOf(data.verses(bookIds), Pace.MAX_DAYS)
        return when (pace) {
            is Pace.Days -> pace.value.coerceIn(1, most)
            is Pace.MinutesPerDay -> data.daysForMinutes(bookIds, pace.value).coerceAtMost(most)
        }
    }

    suspend fun generate(bookIds: Collection<Int>, pace: Pace): GeneratedPlan =
        verseData().plan(bookIds, days(bookIds, pace))

    companion object {
        const val CREDITS = "Your own plan, made in Bible Scroll. Daily times are estimated from the World English Bible " +
            "at about 200 words a minute."

        /** Same rule as the pipeline's plans/build.py difficulty(). */
        fun difficulty(days: Int, perDay: Int): String = when {
            perDay >= 15 || days >= 180 -> "hard"
            perDay <= 8 && days <= 31 -> "easy"
            else -> "medium"
        }

        fun toPlanFile(id: String, title: String, shortDescription: String, generated: GeneratedPlan): PlanFile = PlanFile(
            id = id,
            contentVersion = Integer.toHexString((title + generated.days.hashCode()).hashCode()).padStart(8, '0'),
            title = title,
            shortDescription = shortDescription,
            description = shortDescription,
            category = "custom",
            status = "custom",
            origin = "custom",
            credits = CREDITS,
            estimatedMinutesPerDay = generated.minutesPerDay,
            difficulty = difficulty(generated.days.size, generated.minutesPerDay),
            days = generated.days.mapIndexed { i, day ->
                PlanDayFile(i + 1, passages = day.passages.map {
                    PassageFile(BookCodes.code(it.bookId), it.startChapter, it.startVerse, it.endChapter, it.endVerse)
                })
            },
        )
    }
}
