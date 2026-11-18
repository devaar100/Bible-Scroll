package aarnav100.developer.biblescroll.feature.library

import aarnav100.developer.biblescroll.data.library.LibraryRepository
import aarnav100.developer.biblescroll.data.settings.SettingsRepository
import aarnav100.developer.biblescroll.data.user.LibraryCounts
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class LibraryUiState(
    val counts: LibraryCounts? = null,
    /** Hide AI studies is on: the Studies group isn't shown. */
    val studiesHidden: Boolean = false,
) {
    /** Header total: items in the lists that are shown. Liked + bookmarked counts each mark once per flag. */
    val total: Int get() = counts?.let {
        it.liked + it.bookmarked + it.notes + if (studiesHidden) 0 else it.saved + it.studied
    } ?: 0
}

/** H3 Library hub. */
@HiltViewModel
class LibraryViewModel @Inject constructor(library: LibraryRepository, settings: SettingsRepository) : ViewModel() {
    val uiState: StateFlow<LibraryUiState> = combine(library.counts(), settings.settings) { counts, s ->
        LibraryUiState(counts, s.hideAiStudies)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())
}
