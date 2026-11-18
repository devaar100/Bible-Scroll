package aarnav100.developer.biblescroll.feature.settings

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.settings.AccentPreset
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.data.settings.ReadingBackground
import aarnav100.developer.biblescroll.data.settings.ScriptureSize
import aarnav100.developer.biblescroll.data.settings.SettingsRepository
import aarnav100.developer.biblescroll.data.settings.ThemeMode
import aarnav100.developer.biblescroll.testing.FakeBibleRepository
import aarnav100.developer.biblescroll.testing.FakePreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val store = FakePreferencesDataStore()
    private val repo = SettingsRepository(store)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun kotlinx.coroutines.test.TestScope.viewModel() =
        SettingsViewModel(repo, FakeBibleRepository(), ReaderPreferences(store)).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.settings.collect {} }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.previewVerse.collect {} }
        }

    @Test
    fun settersPersistAndUpdateState() = runTest {
        val vm = viewModel()

        vm.setThemeMode(ThemeMode.Light)
        vm.setReadingBackground(ReadingBackground.Sepia)
        vm.setAccent(AccentPreset.Sage)
        vm.setScriptureSize(ScriptureSize.Larger)
        vm.setRedLetters(false)

        val s = vm.settings.value
        assertEquals(ThemeMode.Light, s.themeMode)
        assertEquals(ReadingBackground.Sepia, s.readingBackground)
        assertEquals(AccentPreset.Sage, s.accent)
        assertEquals(ScriptureSize.Larger, s.scriptureSize)
        assertFalse(s.redLetters)
        assertEquals(s, repo.settings.first())
    }

    @Test
    fun previewVerseIsJohn14_6InTheReadersTranslation() = runTest {
        val vm = viewModel()
        assertEquals(VerseRef(43, 14, 6), vm.previewVerse.value?.ref)
        assertEquals("WEB", vm.previewVerse.value?.translationId)

        ReaderPreferences(store).setTranslationId("SHORT")
        assertEquals("SHORT", vm.previewVerse.value?.translationId)
    }
}
