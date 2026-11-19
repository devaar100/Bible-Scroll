package aarnav100.developer.biblescroll.data.plans

import aarnav100.developer.biblescroll.testing.DirectoryAssets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Custom plans use the bundled verses.bin and the shared splitter (core/plans), so they match the pipeline. */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomPlanBuilderTest {
    private val builder = CustomPlanBuilder(DirectoryAssets(File("src/main/assets")), UnconfinedTestDispatcher())
    private val json = Json { ignoreUnknownKeys = true }

    private fun bundled(id: String) = json.decodeFromString<PlanFile>(File("src/main/assets/plans/$id.json").readText())

    /**
     * Where a book is read a chapter a day, a custom plan is the bundled plan. (Otherwise they can differ on
     * purpose: bundled plans snap to chapter ends only, custom plans also to paragraph and sentence ends. Exact
     * Python/Kotlin parity is checked by core/plans' golden vectors.)
     */
    @Test
    fun aChapterADayCustomPlanMatchesTheBundledPipelinePlan() = runTest {
        for ((id, book, days) in listOf(Triple("john-21-days", 43, 21), Triple("romans-16-days", 45, 16), Triple("proverbs-31-days", 20, 31))) {
            val generated = builder.generate(listOf(book), Pace.Days(days))
            val file = CustomPlanBuilder.toPlanFile("custom-x", "t", "s", generated)
            assertEquals(id, bundled(id).days.map { it.passages }, file.days.map { it.passages })
            assertEquals(id, bundled(id).estimatedMinutesPerDay, file.estimatedMinutesPerDay)
        }
    }

    @Test
    fun minutesADayGivesTheRoundedUpNumberOfDays() = runTest {
        val john = builder.verseData().words(listOf(43))
        val days = builder.days(listOf(43), Pace.MinutesPerDay(10))
        assertEquals(kotlin.math.ceil(john / 2000.0).toInt(), days)
        assertTrue(builder.generate(listOf(43), Pace.MinutesPerDay(10)).minutesPerDay <= 11)
    }

    @Test
    fun daysAreCappedAtOneVerseADay() = runTest {
        assertEquals(25, builder.days(listOf(65), Pace.Days(400))) // Jude has 25 verses
        assertEquals(Pace.MAX_DAYS, builder.days(listOf(19), Pace.Days(5000)))
        assertEquals(1, builder.days(listOf(65), Pace.Days(0)))
    }

    @Test
    fun severalBooksAreReadInBibleOrderWithoutCrossingBooksInAPassage() = runTest {
        val plan = builder.generate(listOf(43, 40), Pace.Days(10)) // picked John first, Matthew second
        val passages = plan.days.flatMap { it.passages }
        assertEquals(40, passages.first().bookId)
        assertEquals(43, passages.last().bookId)
        assertTrue(plan.days.all { day -> day.passages.all { it.bookId == 40 || it.bookId == 43 } })
    }

    @Test
    fun planFileHasCodesNumbersAndDifficulty() = runTest {
        val file = CustomPlanBuilder.toPlanFile("custom-a", "Mark in 16 days", "Mark", builder.generate(listOf(41), Pace.Days(16)))
        assertEquals((1..16).toList(), file.days.map { it.dayNumber })
        assertEquals("MRK", file.days.first().passages.first().book)
        assertEquals("custom", file.origin)
        assertEquals("easy", file.difficulty)
        assertEquals("hard", CustomPlanBuilder.difficulty(365, 12))
    }

    @Test
    fun whenChaptersDontFitDaysStayWithinTheLengthRule() = runTest {
        val plan = builder.generate(listOf(1), Pace.Days(50)) // Genesis: 50 chapters, but some are long
        val limit = plan.minutesPerDay * 1.5
        assertTrue(plan.days.all { it.words / 200.0 <= limit })
        assertEquals(50, plan.days.size)
    }
}
