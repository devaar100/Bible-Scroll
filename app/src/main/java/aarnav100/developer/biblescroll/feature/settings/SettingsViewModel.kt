package aarnav100.developer.biblescroll.feature.settings

import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.settings.AccentPreset
import aarnav100.developer.biblescroll.data.settings.AppSettings
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.data.settings.ReadingBackground
import aarnav100.developer.biblescroll.data.settings.ScriptureSize
import aarnav100.developer.biblescroll.data.settings.ScrubberMode
import aarnav100.developer.biblescroll.data.settings.SettingsRepository
import aarnav100.developer.biblescroll.data.settings.ThemeMode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Shared by S1-S4. Every setter writes straight to DataStore; the theme recomposes from there. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    bible: BibleRepository,
    readerPreferences: ReaderPreferences,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** A real red-letter verse for the S3/S4 previews, in the reader's translation. */
    val previewVerse: StateFlow<Verse?> = readerPreferences.translationId
        .flatMapLatest { bible.verse(it, PREVIEW_REF) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setThemeMode(value: ThemeMode) = launch { repository.setThemeMode(value) }
    fun setReadingBackground(value: ReadingBackground) = launch { repository.setReadingBackground(value) }
    fun setAccent(value: AccentPreset) = launch { repository.setAccent(value) }
    fun setDynamicColor(value: Boolean) = launch { repository.setDynamicColor(value) }
    fun setRedLetters(value: Boolean) = launch { repository.setRedLetters(value) }
    fun setScriptureSize(value: ScriptureSize) = launch { repository.setScriptureSize(value) }
    fun setHideAiStudies(value: Boolean) = launch { repository.setHideAiStudies(value) }
    fun setVerseScrubber(value: ScrubberMode) = launch { repository.setVerseScrubber(value) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        /** John 14:6: short, and nearly all Words of Jesus. */
        val PREVIEW_REF = VerseRef(43, 14, 6)
    }
}
