package aarnav100.developer.biblescroll.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReaderPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** Selected translation id; WEB is the modern default (spec §5). */
    val translationId: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[TRANSLATION_ID] ?: DEFAULT_TRANSLATION_ID }
        .distinctUntilChanged()

    suspend fun setTranslationId(id: String) {
        dataStore.edit { it[TRANSLATION_ID] = id }
    }

    private companion object {
        val TRANSLATION_ID = stringPreferencesKey("translation_id")
        const val DEFAULT_TRANSLATION_ID = "WEB"
    }
}
