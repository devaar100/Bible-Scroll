package aarnav100.developer.biblescroll.feature.devotionals

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.core.content.DevotionalState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Home: today's devotional, a "Tomorrow" countdown card, and the archive link. */
@Composable
fun DevotionalHomeCards(
    onOpen: (date: String) -> Unit,
    onOpenArchive: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeDevotionalViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.devo_today_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (state.visible == true) TextButton(onClick = onOpenArchive) { Text(stringResource(R.string.devo_archive_link)) }
        }
        when {
            state.visible == false -> AiDevotionalsNotice(onShowAnyway = viewModel::showAnyway)
            state.visible == null -> Unit
            else -> {
                val today = state.today
                val devotional = (state.todayState as? DevotionalState.Available)?.devotional
                if (devotional != null && today != null) {
                    Surface(onClick = { onOpen(today.toString()) }, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CoverThumb(devotional.coverStyle, Modifier.size(96.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(devotional.title, style = MaterialTheme.typography.titleMedium)
                                Text(devotional.theme, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                if (state.readToday) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.devo_read_today), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    DevotionalStateMessage(state.todayState, onRetry = {})
                }
                Spacer(Modifier.height(12.dp))
                TomorrowCard(state.tomorrow?.theme)
            }
        }
    }
}

/** Counts down to local midnight, when tomorrow's devotional becomes today's. */
@Composable
private fun TomorrowCard(theme: String?) {
    val left = rememberCountdown()
    val label = stringResource(R.string.devo_tomorrow_in, left)
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.devo_tomorrow), style = MaterialTheme.typography.titleSmall)
                theme?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            // Read once by TalkBack (not every second): the label is static semantics, refreshed on focus.
            Text(left, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics { contentDescription = label })
        }
    }
}
