package aarnav100.developer.biblescroll.testing

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.streak.StreakDao
import aarnav100.developer.biblescroll.data.streak.StreakDayEntity
import aarnav100.developer.biblescroll.data.streak.StreakRepository
import aarnav100.developer.biblescroll.domain.streak.StreakActivity
import aarnav100.developer.biblescroll.domain.streak.StreakDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

class FakeStreakDao : StreakDao {
    val rows = MutableStateFlow<Map<String, StreakDayEntity>>(emptyMap())
    var writes = 0

    override fun observeAll() = rows.map { m -> m.values.sortedBy { it.date } }
    override suspend fun get(date: String) = rows.value[date]
    override suspend fun upsert(day: StreakDayEntity) {
        writes++
        rows.update { it + (day.date to day) }
    }
}

/** In-memory repository for ViewModel tests. */
class FakeStreakRepository(today: LocalDate = LocalDate.of(2026, 11, 18)) : StreakRepository {
    val days = MutableStateFlow<List<StreakDay>>(emptyList())
    val today = MutableStateFlow(today)
    val versesViewed = mutableListOf<VerseRef>()
    val activities = mutableListOf<StreakActivity>()

    override fun days() = days
    override fun today() = today
    override suspend fun recordVerseViewed(ref: VerseRef) {
        versesViewed += ref
    }
    override suspend fun record(activity: StreakActivity) {
        activities += activity
    }
}
