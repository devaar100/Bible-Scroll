package aarnav100.developer.biblescroll.feature.settings

import aarnav100.developer.biblescroll.data.reminder.NotificationAccess
import aarnav100.developer.biblescroll.data.reminder.ReminderRepository
import aarnav100.developer.biblescroll.data.reminder.ReminderScheduler
import aarnav100.developer.biblescroll.data.reminder.ReminderSettings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class NotificationsUiState(
    val enabled: Boolean = false,
    val time: LocalTime = ReminderSettings.DEFAULT_TIME,
    /** Show the "notifications are off" card with a link to system settings. */
    val blocked: Boolean = false,
)

enum class NotificationsEvent { RequestPermission }

/** S6. Turning the reminder on only sticks once notifications can actually be shown. */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val access: NotificationAccess,
) : ViewModel() {

    /** The user tried to turn reminders on and it couldn't be done (denied, or blocked in settings). */
    private val denied = MutableStateFlow(false)
    /** Re-read on resume: the user may have changed it in system settings meanwhile. */
    private val canPost = MutableStateFlow(access.canPost())

    private val _events = Channel<NotificationsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState: StateFlow<NotificationsUiState> = combine(repository.settings, denied, canPost) { s, d, can ->
        NotificationsUiState(enabled = s.enabled, time = s.time, blocked = d || (s.enabled && !can))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUiState())

    fun onToggle(on: Boolean) {
        when {
            !on -> setEnabled(false)
            access.canPost() -> setEnabled(true)
            // Asking can only help on 13+ before a permanent denial; the screen launches the request.
            access.needsRuntimePermission() -> viewModelScope.launch { _events.send(NotificationsEvent.RequestPermission) }
            // Pre-13 or permission granted but the app's notifications are switched off.
            else -> denied.value = true
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted && access.canPost()) setEnabled(true) else denied.value = true
    }

    fun onTimeChange(time: LocalTime) {
        viewModelScope.launch {
            repository.setTime(time)
            scheduler.syncFromStore()
        }
    }

    /** Back from system settings: clear the card if notifications are now allowed. */
    fun onResume() {
        canPost.value = access.canPost()
        if (canPost.value) denied.value = false
    }

    private fun setEnabled(enabled: Boolean) {
        if (enabled) denied.value = false
        viewModelScope.launch {
            repository.setEnabled(enabled)
            scheduler.syncFromStore()
        }
    }
}
