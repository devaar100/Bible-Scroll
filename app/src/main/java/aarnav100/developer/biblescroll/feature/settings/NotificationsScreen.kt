package aarnav100.developer.biblescroll.feature.settings

import aarnav100.developer.biblescroll.R
import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** S6 Notifications: daily reminder switch and time. */
@Composable
fun NotificationsScreen(onBack: () -> Unit, viewModel: NotificationsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onPermissionResult(granted)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                NotificationsEvent.RequestPermission -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    SettingsPage(title = stringResource(R.string.settings_notifications), onBack = onBack) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.notifications_daily)) },
            supportingContent = { Text(stringResource(R.string.notifications_daily_summary)) },
            trailingContent = { Switch(checked = state.enabled, onCheckedChange = null) },
            modifier = Modifier.toggleable(value = state.enabled, role = Role.Switch, onValueChange = viewModel::onToggle),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.notifications_time)) },
            supportingContent = { Text(formatReminderTime(context, state.time)) },
            modifier = Modifier.clickable { pickingTime = true },
        )
        if (state.blocked) {
            BlockedCard(onOpenSettings = { context.openNotificationSettings() })
        }
    }

    if (pickingTime) {
        ReminderTimeDialog(
            initial = state.time,
            is24Hour = DateFormat.is24HourFormat(context),
            onConfirm = {
                viewModel.onTimeChange(it)
                pickingTime = false
            },
            onDismiss = { pickingTime = false },
        )
    }
}

@Composable
private fun BlockedCard(onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(Icons.Outlined.NotificationsOff, contentDescription = null)
            Text(
                stringResource(R.string.notifications_blocked, stringResource(R.string.app_name)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = onOpenSettings, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.notifications_open_settings))
            }
        }
    }
}

private fun Context.openNotificationSettings() {
    startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
