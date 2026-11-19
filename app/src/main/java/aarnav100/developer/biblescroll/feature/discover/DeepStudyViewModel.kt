package aarnav100.developer.biblescroll.feature.discover

import aarnav100.developer.biblescroll.core.content.StudyRepository
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.data.settings.SettingsRepository
import aarnav100.developer.biblescroll.data.user.UserDataRepository
import aarnav100.developer.biblescroll.feature.study.StudySession
import aarnav100.developer.biblescroll.feature.study.StudyUiState
import aarnav100.developer.biblescroll.ui.navigation.PendingVerseLink
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** D2 and B8 share one state; the name stays for D2's screen. */
typealias DeepStudyUiState = StudyUiState

/** D2 Deep study for one verse (route args: bookId, chapter, verse; see DeepStudyRoute). Logic lives in [StudySession]. */
@HiltViewModel
class DeepStudyViewModel @Inject constructor(
    savedState: SavedStateHandle,
    studies: StudyRepository,
    userData: UserDataRepository,
    settings: SettingsRepository,
    bible: BibleRepository,
    readerPreferences: ReaderPreferences,
    private val pendingVerseLink: PendingVerseLink,
) : ViewModel() {

    val ref = VerseRef(
        checkNotNull(savedState.get<Int>("bookId")),
        checkNotNull(savedState.get<Int>("chapter")),
        checkNotNull(savedState.get<Int>("verse")),
    )

    /** With Hide AI studies on, D2 shows only an explanation and never starts a download. */
    private val session = StudySession(ref, viewModelScope, studies, userData, settings, bible, readerPreferences, loadWhenHidden = false)

    val uiState: StateFlow<DeepStudyUiState> = session.uiState

    fun toggleSaved() = session.toggleSaved()
    fun toggleStudied() = session.toggleStudied()
    fun retry() = session.retry()
    fun showStudies() = session.showStudies()

    /** "Read in context": the Bible tab opens on this verse (same path as a widget tap). */
    fun readInContext(target: VerseRef = ref) = pendingVerseLink.post(target)
}
