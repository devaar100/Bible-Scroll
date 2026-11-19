package aarnav100.developer.biblescroll.testing

import aarnav100.developer.biblescroll.core.content.AssetSource
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.data.votd.LocalClock
import aarnav100.developer.biblescroll.data.votd.VerseOfTheDayRepository
import aarnav100.developer.biblescroll.data.votd.VotdSource
import aarnav100.developer.biblescroll.widget.HomeScreenWidgets
import aarnav100.developer.biblescroll.widget.VotdWidgetScheduler
import aarnav100.developer.biblescroll.widget.WidgetRefresher
import aarnav100.developer.biblescroll.widget.WidgetVerseRepository
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A small Verse of the Day list over verses FakeBibleRepository has. 2028 is a leap year. John 3:16 is missing
 * from the fake "SHORT" translation, so 03-16 exercises the WEB fallback.
 */
val FAKE_VOTD_CSV = """
    date,id,section
    01-01,GEN.1.1,Torah and history
    02-28,GEN.1.2,Torah and history
    02-29,GEN.1.3,Torah and history
    03-01,PSA.119.105,Psalms
    03-16,JHN.3.16,Gospels and Acts
    03-17,JHN.3.17,Gospels and Acts
    12-31,JHN.14.6,Gospels and Acts
""".trimIndent()

fun votdSource(csv: String = FAKE_VOTD_CSV, io: CoroutineDispatcher = Dispatchers.Unconfined) =
    VotdSource(AssetSource { path -> if (path == "votd.csv") csv.byteInputStream() else null }, io)

/** A clock tests move by hand; [zone] changes simulate a time-zone change. */
class FakeLocalClock(var now: ZonedDateTime) : LocalClock {
    override fun now(): ZonedDateTime = now

    fun moveTo(zone: ZoneId) {
        now = now.withZoneSameInstant(zone)
    }
}

class FakeVotdWidgetScheduler : VotdWidgetScheduler {
    var scheduledAt: ZonedDateTime? = null
    var cancels = 0

    override fun schedule(at: ZonedDateTime) {
        scheduledAt = at
    }

    override fun cancel() {
        scheduledAt = null
        cancels++
    }
}

fun votdRepository(
    dataStore: DataStore<Preferences> = FakePreferencesDataStore(),
    bible: BibleRepository = FakeBibleRepository(),
    csv: String = FAKE_VOTD_CSV,
) = VerseOfTheDayRepository(votdSource(csv), bible, ReaderPreferences(dataStore))

fun widgetVerseRepository(
    dataStore: DataStore<Preferences> = FakePreferencesDataStore(),
    bible: BibleRepository = FakeBibleRepository(),
    refresher: WidgetRefresher = FakeWidgetRefresher(),
    clock: LocalClock = FakeLocalClock(ZonedDateTime.of(2028, 3, 16, 9, 0, 0, 0, ZoneId.of("America/Chicago"))),
    scheduler: VotdWidgetScheduler = FakeVotdWidgetScheduler(),
    homeScreen: HomeScreenWidgets = FakeHomeScreenWidgets(placed = true),
) = WidgetVerseRepository(dataStore, bible, refresher, votdRepository(dataStore, bible), scheduler, homeScreen, clock)
