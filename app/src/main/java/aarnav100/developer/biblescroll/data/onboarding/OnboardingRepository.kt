package aarnav100.developer.biblescroll.data.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Whether O2-O8 has been finished. Once true it is never cleared, so onboarding never shows again. */
@Singleton
class OnboardingRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val complete: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[COMPLETE] ?: false }
        .distinctUntilChanged()

    suspend fun markComplete() {
        dataStore.edit { it[COMPLETE] = true }
    }

    private companion object {
        val COMPLETE = booleanPreferencesKey("onboarding_complete")
    }
}
