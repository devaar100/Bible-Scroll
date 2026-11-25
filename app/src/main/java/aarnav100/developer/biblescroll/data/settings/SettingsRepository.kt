package aarnav100.developer.biblescroll.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

/** S2-S4 settings in the shared settings DataStore. Enums are stored by name; unknown names fall back to defaults. */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val defaults = AppSettings()
            AppSettings(
                themeMode = prefs[THEME_MODE].toEnum(defaults.themeMode),
                readingBackground = prefs[READING_BACKGROUND].toEnum(defaults.readingBackground),
                accent = prefs[ACCENT].toEnum(defaults.accent),
                dynamicColor = prefs[DYNAMIC_COLOR] ?: defaults.dynamicColor,
                redLetters = prefs[RED_LETTERS] ?: defaults.redLetters,
                scriptureSize = prefs[SCRIPTURE_SIZE].toEnum(defaults.scriptureSize),
                hideAiStudies = prefs[HIDE_AI_STUDIES] ?: defaults.hideAiStudies,
                verseScrubber = prefs[VERSE_SCRUBBER].toEnum(defaults.verseScrubber),
            )
        }
        .distinctUntilChanged()

    /** H2: cover one missed day per Monday-Sunday week. Off by default. */
    val streakGraceDay: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[STREAK_GRACE_DAY] ?: false }
        .distinctUntilChanged()

    suspend fun setStreakGraceDay(value: Boolean) = put(STREAK_GRACE_DAY, value)

    suspend fun setThemeMode(value: ThemeMode) = put(THEME_MODE, value.name)
    suspend fun setReadingBackground(value: ReadingBackground) = put(READING_BACKGROUND, value.name)
    suspend fun setAccent(value: AccentPreset) = put(ACCENT, value.name)
    suspend fun setDynamicColor(value: Boolean) = put(DYNAMIC_COLOR, value)
    suspend fun setRedLetters(value: Boolean) = put(RED_LETTERS, value)
    suspend fun setScriptureSize(value: ScriptureSize) = put(SCRIPTURE_SIZE, value.name)
    suspend fun setHideAiStudies(value: Boolean) = put(HIDE_AI_STUDIES, value)
    suspend fun setVerseScrubber(value: ScrubberMode) = put(VERSE_SCRUBBER, value.name)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val READING_BACKGROUND = stringPreferencesKey("reading_background")
        val ACCENT = stringPreferencesKey("accent")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val RED_LETTERS = booleanPreferencesKey("red_letters")
        val SCRIPTURE_SIZE = stringPreferencesKey("scripture_size")
        val HIDE_AI_STUDIES = booleanPreferencesKey("hide_ai_studies")
        val VERSE_SCRUBBER = stringPreferencesKey("verse_scrubber")
        val STREAK_GRACE_DAY = booleanPreferencesKey("streak_grace_day")
    }
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
