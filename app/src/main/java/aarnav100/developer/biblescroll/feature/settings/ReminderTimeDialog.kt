package aarnav100.developer.biblescroll.feature.settings

import aarnav100.developer.biblescroll.R
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date

/** Reminder time picker, shared by S6 and onboarding (O7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimeDialog(
    initial: LocalTime,
    is24Hour: Boolean,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notifications_time_dialog)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.notifications_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.notifications_cancel)) }
        },
    )
}

/** In the phone's own 12/24-hour style. */
fun formatReminderTime(context: Context, time: LocalTime): String {
    val instant = time.atDate(LocalDate.now()).atZone(ZoneId.systemDefault()).toInstant()
    return DateFormat.getTimeFormat(context).format(Date.from(instant))
}

fun is24HourClock(context: Context): Boolean = DateFormat.is24HourFormat(context)
