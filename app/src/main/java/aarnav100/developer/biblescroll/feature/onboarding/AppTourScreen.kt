package aarnav100.developer.biblescroll.feature.onboarding

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The onboarding tour's verses in the reader's current translation, for the replay from Settings. */
@HiltViewModel
class AppTourViewModel @Inject constructor(bible: BibleRepository, readerPreferences: ReaderPreferences) : ViewModel() {
    val tour: StateFlow<TourVerses> = tourVerses(bible, readerPreferences.translationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TourVerses())
}

/** Settings > "Take the app tour": the same O3 pages as onboarding, ending in "Done". Changes no settings. */
@Composable
fun AppTourScreen(onDone: () -> Unit, viewModel: AppTourViewModel = hiltViewModel()) {
    val tour by viewModel.tour.collectAsStateWithLifecycle()
    FeatureTourStep(
        tour = tour,
        onBack = onDone,
        onSkip = onDone,
        onContinue = onDone,
        lastLabel = stringResource(R.string.onb_tour_done),
        showSkip = false,
    )
}
