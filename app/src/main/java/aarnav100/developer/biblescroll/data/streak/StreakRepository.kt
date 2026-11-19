package aarnav100.developer.biblescroll.data.streak

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.domain.streak.StreakActivity
import aarnav100.developer.biblescroll.domain.streak.StreakDay
import aarnav100.developer.biblescroll.domain.streak.StreakRules
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records what makes a day count (H2) and exposes the days. Feed verses are only counted once per verse
 * per day; plan days and devotionals call [record] directly when those screens exist (H8-H17).
 */
interface StreakRepository {
    fun days(): Flow<List<StreakDay>>

    /** The device's local date, re-emitted when it changes (midnight, time-zone or clock change). */
    fun today(): Flow<LocalDate>

    /** A verse was on screen in the feed for [StreakRules.VERSE_DWELL_MILLIS]. */
    suspend fun recordVerseViewed(ref: VerseRef)

    suspend fun record(activity: StreakActivity)
}

@Singleton
class LocalStreakRepository internal constructor(
    private val dao: StreakDao,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
    private val tickMillis: Long = 60_000,
) : StreakRepository {

    @Inject
    constructor(dao: StreakDao) : this(dao, System::currentTimeMillis, ZoneId::systemDefault)

    private val lock = Mutex()
    private var seenDate: LocalDate? = null
    private val seenVerses = HashSet<VerseRef>()

    override fun days() = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun today(): Flow<LocalDate> = flow {
        while (true) {
            emit(StreakRules.localDate(clock(), zone()))
            delay(tickMillis)
        }
    }.distinctUntilChanged()

    override suspend fun recordVerseViewed(ref: VerseRef) {
        val now = clock()
        val zone = zone()
        val date = StreakRules.localDate(now, zone)
        val isNew = lock.withLock {
            if (date != seenDate) {
                seenDate = date
                seenVerses.clear()
            }
            seenVerses.add(ref)
        }
        if (isNew) record(StreakActivity.VerseViewed, now, zone, skipIfCounted = true)
    }

    override suspend fun record(activity: StreakActivity) = record(activity, clock(), zone(), skipIfCounted = false)

    private suspend fun record(activity: StreakActivity, now: Long, zone: ZoneId, skipIfCounted: Boolean) {
        val date = StreakRules.localDate(now, zone)
        dao.update(date.toString()) { existing ->
            val day = existing?.toDomain()
            // Once a day counts, more feed verses change nothing that's shown; skip the write.
            if (skipIfCounted && day?.countedAt != null) null
            else StreakRules.record(day, activity, now, zone).toEntity()
        }
    }
}
