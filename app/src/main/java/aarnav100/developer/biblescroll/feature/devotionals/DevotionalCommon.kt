package aarnav100.developer.biblescroll.feature.devotionals

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.core.content.DevotionalState
import aarnav100.developer.biblescroll.core.content.StudyState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Loading / offline / failed / not published / update needed, worded like the study screens. */
@Composable
fun DevotionalStateMessage(state: DevotionalState, onRetry: () -> Unit, modifier: Modifier = Modifier, forMonth: Boolean = false) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state) {
            is DevotionalState.Loading -> {
                val fraction = state.progress?.fraction
                if (fraction != null) LinearProgressIndicator(progress = { fraction }) else CircularProgressIndicator()
                Text(stringResource(R.string.devo_loading), style = MaterialTheme.typography.bodyMedium)
            }
            is DevotionalState.NotDownloaded -> {
                val offline = state.reason == StudyState.NotDownloaded.Reason.OFFLINE
                Text(stringResource(if (offline) R.string.devo_offline else R.string.devo_failed), textAlign = TextAlign.Center)
                if (!offline) OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.devo_retry)) }
            }
            is DevotionalState.Unavailable -> Text(
                stringResource(
                    when {
                        state.reason == DevotionalState.Unavailable.Reason.APP_UPDATE_REQUIRED -> R.string.devo_update_required
                        forMonth -> R.string.devo_month_not_published
                        else -> R.string.devo_not_published
                    },
                ),
                textAlign = TextAlign.Center,
            )
            is DevotionalState.Available -> Unit
        }
    }
}

internal fun LocalDate.longLabel(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
