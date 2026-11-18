package aarnav100.developer.biblescroll

import aarnav100.developer.biblescroll.data.onboarding.OnboardingRepository
import aarnav100.developer.biblescroll.data.settings.AppSettings
import aarnav100.developer.biblescroll.data.settings.SettingsRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    onboardingRepository: OnboardingRepository,
) : ViewModel() {
    /** Null until the first DataStore read; the splash screen stays up until then. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Null until read (splash stays up), so neither onboarding nor the feed flashes on the wrong launch. */
    val onboardingComplete: StateFlow<Boolean?> = onboardingRepository.complete
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
