package aarnav100.developer.biblescroll.feature.settings

import aarnav100.developer.biblescroll.R
import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Settings → Storage: downloaded and bundled study books, and "Remove downloaded studies". */
@Composable
fun StorageScreen(onBack: () -> Unit, viewModel: StorageViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirming by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val resources = context.resources
    LaunchedEffect(viewModel) {
        viewModel.removed.collect { count ->
            snackbar.showSnackbar(resources.getQuantityString(R.plurals.storage_removed, count, count))
        }
    }

    Box {
        SettingsPage(title = stringResource(R.string.settings_storage), onBack = onBack) {
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.books.isEmpty() -> Text(
                    stringResource(R.string.storage_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                else -> {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.storage_total, Formatter.formatShortFileSize(context, state.totalBytes)))
                        },
                        supportingContent = { Text(stringResource(R.string.storage_explainer)) },
                        modifier = Modifier.semantics { heading() },
                    )
                    HorizontalDivider()
                    state.books.forEach { book ->
                        ListItem(
                            headlineContent = { Text(book.name) },
                            supportingContent = {
                                Text(
                                    pluralStringResource(
                                        if (book.bundled) R.plurals.storage_book_bundled else R.plurals.storage_book_downloaded,
                                        book.studies, book.studies,
                                    ),
                                )
                            },
                            trailingContent = { Text(Formatter.formatShortFileSize(context, book.bytes)) },
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = { confirming = true },
                enabled = state.downloadedCount > 0 && !state.removing,
                modifier = Modifier.padding(16.dp),
            ) {
                Text(stringResource(R.string.storage_remove))
            }
            if (state.downloadedCount == 0 && state.books.isNotEmpty()) {
                Text(
                    stringResource(R.string.storage_nothing_downloaded),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.storage_remove_title)) },
            text = {
                Text(pluralStringResource(R.plurals.storage_remove_body, state.downloadedCount, state.downloadedCount))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    viewModel.removeDownloaded()
                }) { Text(stringResource(R.string.storage_remove_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.notifications_cancel)) } },
        )
    }
}
