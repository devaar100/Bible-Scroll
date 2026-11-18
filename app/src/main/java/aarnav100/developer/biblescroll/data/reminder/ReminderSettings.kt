package aarnav100.developer.biblescroll.data.reminder

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** S6 daily reminder. Off until the user (or onboarding, O7) turns it on. */
data class ReminderSettings(
    val enabled: Boolean = false,
    val time: LocalTime = DEFAULT_TIME,
) {
    companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(8, 0)
    }
}

@Singleton
class ReminderRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<ReminderSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val minuteOfDay = prefs[MINUTE_OF_DAY]?.takeIf { it in 0 until 24 * 60 }
            ReminderSettings(
                enabled = prefs[ENABLED] ?: false,
                time = minuteOfDay?.let { LocalTime.of(it / 60, it % 60) } ?: ReminderSettings.DEFAULT_TIME,
            )
        }
        .distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED] = enabled }
    }

    suspend fun setTime(time: LocalTime) {
        dataStore.edit { it[MINUTE_OF_DAY] = time.hour * 60 + time.minute }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("reminder_enabled")
        val MINUTE_OF_DAY = intPreferencesKey("reminder_minute_of_day")
    }
}
