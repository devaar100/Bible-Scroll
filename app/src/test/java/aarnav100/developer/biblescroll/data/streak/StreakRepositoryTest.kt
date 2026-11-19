package aarnav100.developer.biblescroll.data.streak

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.domain.streak.StreakActivity
import aarnav100.developer.biblescroll.testing.FakeStreakDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class StreakRepositoryTest {

    private val dao = FakeStreakDao()
    private var zone: ZoneId = ZoneId.of("America/New_York")
    private var now: Long = at("2026-11-18T08:00")
    private val repo = LocalStreakRepository(dao, { now }, { zone })

    private fun at(local: String, z: ZoneId = zone) = LocalDateTime.parse(local).atZone(z).toInstant().toEpochMilli()
    private fun verse(n: Int) = VerseRef(43, 3, n)
    private fun row(date: String) = dao.rows.value[date]

    @Test
    fun theSameVerseCountsOncePerDay() = runTest {
        repeat(5) { repo.recordVerseViewed(verse(16)) }
        assertEquals(1, row("2026-11-18")!!.versesViewed)
        assertNull(row("2026-11-18")!!.countedAt)
    }

    @Test
    fun fiveDifferentVersesCountTheDayThenFurtherVersesDontWrite() = runTest {
        (1..5).forEach { repo.recordVerseViewed(verse(it)) }
        assertNotNull(row("2026-11-18")!!.countedAt)
        val writes = dao.writes
        (6..20).forEach { repo.recordVerseViewed(verse(it)) }
        assertEquals(writes, dao.writes)
    }

    @Test
    fun aNewDayStartsAFreshCount() = runTest {
        repo.recordVerseViewed(verse(16))
        now = at("2026-11-19T00:00:01")
        repo.recordVerseViewed(verse(16))
        assertEquals(1, row("2026-11-19")!!.versesViewed)
    }

    @Test
    fun planDaysAndDevotionalsCountImmediately() = runTest {
        repo.record(StreakActivity.PlanDayCompleted)
        assertNotNull(row("2026-11-18")!!.countedAt)
        now = at("2026-11-19T07:00")
        repo.record(StreakActivity.DevotionalFinished)
        assertEquals(1, row("2026-11-19")!!.devotionalsFinished)
    }

    @Test
    fun aZoneChangeUsesTheNewZoneFromThatMomentOn() = runTest {
        // 23:30 in New York is already the 19th in Paris.
        now = at("2026-11-18T23:30")
        repo.recordVerseViewed(verse(1))
        zone = ZoneId.of("Europe/Paris")
        repo.recordVerseViewed(verse(2))
        assertEquals(1, row("2026-11-18")!!.versesViewed)
        assertEquals(1, row("2026-11-19")!!.versesViewed)
    }

    @Test
    fun aClockSetBackwardsRecordsTheDateTheDeviceShows() = runTest {
        repo.record(StreakActivity.PlanDayCompleted)
        now = at("2026-11-10T08:00")
        repo.record(StreakActivity.PlanDayCompleted)
        assertEquals(setOf("2026-11-10", "2026-11-18"), dao.rows.value.keys)
        assertEquals(listOf(LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-18")), repo.days().first().map { it.date })
    }

    @Test
    fun todayTicksOverAtMidnight() = runTest {
        val repoWithTick = LocalStreakRepository(dao, { now }, { zone }, tickMillis = 60_000)
        now = at("2026-11-18T23:59:30")
        val seen = mutableListOf<LocalDate>()
        val job = launch { repoWithTick.today().take(2).toList(seen) }
        advanceTimeBy(1)
        now = at("2026-11-19T00:00:30")
        advanceTimeBy(60_001)
        job.join()
        assertEquals(listOf(LocalDate.parse("2026-11-18"), LocalDate.parse("2026-11-19")), seen)
    }
}
