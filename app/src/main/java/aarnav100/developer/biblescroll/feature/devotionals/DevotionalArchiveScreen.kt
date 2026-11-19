package aarnav100.developer.biblescroll.feature.devotionals

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.core.content.DevotionalMonthState
import aarnav100.developer.biblescroll.core.content.DevotionalState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** H17: every day so far this year, newest first, grouped by month, with what's been read. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DevotionalArchiveScreen(
    onBack: () -> Unit,
    onOpen: (date: String) -> Unit,
    viewModel: DevotionalArchiveViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.devo_archive_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back)) }
                },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        if (state.visible == false) {
            Box(Modifier.padding(padding).padding(20.dp)) { AiDevotionalsNotice(onShowAnyway = viewModel::showAnyway) }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "count") {
                Text(
                    pluralStringResource(R.plurals.devo_archive_read, state.readCount, state.readCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            state.months.forEach { month ->
                stickyHeader(key = "m-${month.month}") {
                    Text(
                        Month.of(month.month).getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() },
                    )
                }
                when (val s = month.state) {
                    is DevotionalMonthState.Available -> Unit
                    else -> item(key = "state-${month.month}") {
                        DevotionalStateMessage(s.asDayState(month.month), onRetry = { viewModel.retry(month.month) }, forMonth = true)
                    }
                }
                items(month.days, key = { it.date.toString() }) { day ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(day.date.toString()) }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (day.read) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = stringResource(if (day.read) R.string.devo_read else R.string.devo_not_read),
                            tint = if (day.read) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(day.date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())), style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.width(64.dp))
                        Text(
                            day.title ?: stringResource(R.string.devo_archive_missing),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (day.title == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun DevotionalMonthState.asDayState(month: Int): DevotionalState = when (this) {
    is DevotionalMonthState.Loading -> DevotionalState.Loading(progress)
    is DevotionalMonthState.NotDownloaded -> DevotionalState.NotDownloaded("%02d".format(month), reason)
    is DevotionalMonthState.Unavailable -> DevotionalState.Unavailable(reason)
    is DevotionalMonthState.Available -> DevotionalState.Loading()
}
