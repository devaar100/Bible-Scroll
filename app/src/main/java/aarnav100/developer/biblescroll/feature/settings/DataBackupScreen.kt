package aarnav100.developer.biblescroll.feature.settings

import aarnav100.developer.biblescroll.ui.text.formatList
import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.backup.ImportError
import aarnav100.developer.biblescroll.data.backup.ImportResult
import aarnav100.developer.biblescroll.data.backup.ImportSummary
import aarnav100.developer.biblescroll.data.backup.MarkdownLabels
import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** S9 Data & backup: what's stored where, Export my data, Import. */
@Composable
fun DataBackupScreen(onBack: () -> Unit, viewModel: DataBackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalContext.current.resources
    val snackbar = remember { SnackbarHostState() }

    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        viewModel.onBackupDestination(it)
    }
    val pickReadable = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) {
        viewModel.onReadableDestination(it)
    }
    // Some file managers label .json files as text or binary, so accept those too; the file is validated anyway.
    val pickImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DataBackupEvent.PickBackupFile -> pickBackup.launch(event.name)
                // Straight after the backup file: the suggested name ("bible-scroll-notes-….md") says what it is,
                // and cancelling skips it.
                is DataBackupEvent.PickReadableFile -> pickReadable.launch(event.name)
                is DataBackupEvent.Saved -> snackbar.showSnackbar(
                    resources.getString(if (event.withReadableCopy) R.string.data_backup_saved_both else R.string.data_backup_saved),
                )
                DataBackupEvent.SaveFailed -> snackbar.showSnackbar(resources.getString(R.string.data_backup_save_failed))
            }
        }
    }

    Box {
        DataBackupContent(
            busy = state.busy,
            onBack = onBack,
            onExport = { viewModel.startExport(markdownLabels(resources)) },
            onImport = { pickImport.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    state.importResult?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::dismissImportResult,
            title = {
                Text(
                    stringResource(
                        if (result is ImportResult.Imported) R.string.data_backup_import_done else R.string.data_backup_import_failed,
                    ),
                )
            },
            text = {
                Text(
                    when (result) {
                        is ImportResult.Imported -> importSummaryText(resources, result.summary)
                        is ImportResult.Rejected -> stringResource(result.error.message)
                    },
                )
            },
            confirmButton = { TextButton(onClick = viewModel::dismissImportResult) { Text(stringResource(R.string.data_backup_ok)) } },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

private fun markdownLabels(r: Resources) = MarkdownLabels(
    title = r.getString(R.string.md_title),
    exportedLine = r.getString(R.string.md_exported),
    notes = r.getString(R.string.md_notes),
    likes = r.getString(R.string.md_likes),
    bookmarks = r.getString(R.string.md_bookmarks),
    none = r.getString(R.string.md_none),
)

private val ImportError.message: Int get() = when (this) {
    ImportError.NotABackup -> R.string.data_backup_error_not_backup
    is ImportError.TooNew -> R.string.data_backup_error_too_new
    ImportError.TooLarge -> R.string.data_backup_error_too_large
    ImportError.Unreadable -> R.string.data_backup_error_unreadable
    is ImportError.Damaged -> R.string.data_backup_error_damaged
}

/** "Added 12 notes, 40 likes. Updated 3 notes." */
internal fun importSummaryText(r: Resources, s: ImportSummary): String {
    if (s.nothingNew) return r.getString(R.string.data_backup_nothing_new)
    fun q(id: Int, n: Int) = if (n > 0) r.getQuantityString(id, n, n) else null
    val added = listOfNotNull(
        q(R.plurals.import_notes, s.notesAdded),
        q(R.plurals.import_likes, s.likesAdded),
        q(R.plurals.import_bookmarks, s.bookmarksAdded),
        q(R.plurals.import_saved_studies, s.savedStudiesAdded),
        q(R.plurals.import_chapters_read, s.chaptersReadAdded),
        q(R.plurals.import_plans, s.plansAdded),
        q(R.plurals.import_plan_days, s.planDaysAdded),
        q(R.plurals.import_devotionals, s.devotionalsAdded),
        q(R.plurals.import_streak_days, s.streakDaysAdded),
        q(R.plurals.import_settings, s.settingsApplied),
    )
    val sentences = buildList {
        if (added.isNotEmpty()) add(r.getString(R.string.data_backup_added, formatList(added, r.configuration.locales[0])))
        q(R.plurals.import_notes_updated, s.notesUpdated)?.let(::add)
        if (added.isEmpty() && s.notesUpdated == 0) add(r.getQuantityString(R.plurals.import_other_changes, s.rowsWritten, s.rowsWritten))
    }
    return sentences.joinToString(" ")
}

/** The page itself, without the ViewModel or file pickers (also rendered by the pseudo-locale screenshot test). */
@Composable
internal fun DataBackupContent(busy: Boolean, onBack: () -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    SettingsPage(title = stringResource(R.string.settings_data_backup), onBack = onBack) {
        Text(
            stringResource(R.string.data_backup_stored),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            stringResource(R.string.data_backup_google),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onExport,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) { Text(stringResource(R.string.data_backup_export)) }
        Hint(stringResource(R.string.data_backup_export_detail))
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onImport,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) { Text(stringResource(R.string.data_backup_import)) }
        Hint(stringResource(R.string.data_backup_import_detail))
        if (busy) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(32.dp))
            }
        }
    }
}
