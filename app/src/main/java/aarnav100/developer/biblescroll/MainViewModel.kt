package aarnav100.developer.biblescroll

import aarnav100.developer.biblescroll.data.backup.UserDbHealth
import aarnav100.developer.biblescroll.data.onboarding.OnboardingRepository
import aarnav100.developer.biblescroll.data.settings.AppSettings
import aarnav100.developer.biblescroll.data.settings.SettingsRepository
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whether user.db opened. [checking] is true while a "Try again" runs. */
data class UserDataStatus(val ok: Boolean, val checking: Boolean = false)

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    onboardingRepository: OnboardingRepository,
    private val userDbHealth: UserDbHealth,
) : ViewModel() {
    /** Null until the first DataStore read; the splash screen stays up until then. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Null until read (splash stays up), so neither onboarding nor the feed flashes on the wrong launch. */
    val onboardingComplete: StateFlow<Boolean?> = onboardingRepository.complete
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _userData = MutableStateFlow<UserDataStatus?>(null)

    /** Null until the first check (splash stays up). If not ok, the app shows the data error screen, not an empty app. */
    val userData: StateFlow<UserDataStatus?> = _userData.asStateFlow()

    private val _rawCopySaved = Channel<Boolean>(Channel.BUFFERED)
    val rawCopySaved = _rawCopySaved.receiveAsFlow()

    init {
        viewModelScope.launch { _userData.value = UserDataStatus(userDbHealth.opens()) }
    }

    fun retryUserData() {
        if (_userData.value?.checking == true) return
        _userData.value = UserDataStatus(ok = false, checking = true)
        viewModelScope.launch { _userData.value = UserDataStatus(userDbHealth.opens()) }
    }

    fun saveRawCopy(uri: Uri) {
        viewModelScope.launch { _rawCopySaved.send(userDbHealth.saveRawCopy(uri)) }
    }
}
