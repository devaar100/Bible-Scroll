package aarnav100.developer.biblescroll.data.settings

import aarnav100.developer.biblescroll.testing.FakePreferencesDataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {

    private val dataStore = FakePreferencesDataStore()
    private val repo = SettingsRepository(dataStore)

    @Test
    fun defaultsMatchTheSpec() = runTest {
        val s = repo.settings.first()
        assertEquals(ThemeMode.System, s.themeMode)
        assertEquals(ReadingBackground.Charcoal, s.readingBackground) // dark-default feed
        assertEquals(AccentPreset.Crimson, s.accent)
        assertFalse(s.dynamicColor)
        assertTrue(s.redLetters)
        assertEquals(ScriptureSize.Default, s.scriptureSize)
    }

    @Test
    fun everySettingRoundTrips() = runTest {
        repo.setThemeMode(ThemeMode.Dark)
        repo.setReadingBackground(ReadingBackground.TrueBlack)
        repo.setAccent(AccentPreset.Teal)
        repo.setDynamicColor(true)
        repo.setRedLetters(false)
        repo.setScriptureSize(ScriptureSize.Largest)

        assertEquals(
            AppSettings(ThemeMode.Dark, ReadingBackground.TrueBlack, AccentPreset.Teal, true, false, ScriptureSize.Largest),
            repo.settings.first(),
        )
        // A new instance on the same store sees the same values (i.e. it's persisted, not cached).
        assertEquals(repo.settings.first(), SettingsRepository(dataStore).settings.first())
    }

    @Test
    fun unknownStoredValuesFallBackToDefaults() = runTest {
        dataStore.edit {
            it[stringPreferencesKey("theme_mode")] = "Neon"
            it[stringPreferencesKey("reading_background")] = ""
            it[stringPreferencesKey("scripture_size")] = "Huge"
        }
        val s = repo.settings.first()
        assertEquals(ThemeMode.System, s.themeMode)
        assertEquals(ReadingBackground.Charcoal, s.readingBackground)
        assertEquals(ScriptureSize.Default, s.scriptureSize)
    }

    @Test
    fun scriptureSizesGrowMonotonically() {
        val multipliers = ScriptureSize.entries.map { it.multiplier }
        assertEquals(1f, multipliers.first())
        assertEquals(multipliers.sorted(), multipliers)
        assertEquals(multipliers.size, multipliers.toSet().size)
    }
}
