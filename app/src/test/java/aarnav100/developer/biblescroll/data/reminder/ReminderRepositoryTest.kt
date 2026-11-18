package aarnav100.developer.biblescroll.data.reminder

import aarnav100.developer.biblescroll.testing.FakePreferencesDataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ReminderRepositoryTest {

    private val store = FakePreferencesDataStore()
    private val repo = ReminderRepository(store)

    @Test
    fun offAt8ByDefault() = runTest {
        val s = repo.settings.first()
        assertFalse(s.enabled)
        assertEquals(LocalTime.of(8, 0), s.time)
    }

    @Test
    fun roundTrips() = runTest {
        repo.setEnabled(true)
        repo.setTime(LocalTime.of(21, 45))

        assertEquals(ReminderSettings(true, LocalTime.of(21, 45)), ReminderRepository(store).settings.first())
    }

    @Test
    fun secondsAreDropped() = runTest {
        repo.setTime(LocalTime.of(6, 5, 59))
        assertEquals(LocalTime.of(6, 5), repo.settings.first().time)
    }

    @Test
    fun outOfRangeStoredTimeFallsBackToDefault() = runTest {
        store.edit { it[intPreferencesKey("reminder_minute_of_day")] = 24 * 60 }
        assertEquals(ReminderSettings.DEFAULT_TIME, repo.settings.first().time)
        assertTrue(repo.settings.first().time == LocalTime.of(8, 0))
    }
}
