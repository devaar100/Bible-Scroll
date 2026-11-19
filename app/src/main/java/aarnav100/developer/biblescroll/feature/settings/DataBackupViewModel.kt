package aarnav100.developer.biblescroll.feature.settings

import aarnav100.developer.biblescroll.data.backup.BackupExporter
import aarnav100.developer.biblescroll.data.backup.BackupImporter
import aarnav100.developer.biblescroll.data.backup.BackupValidator
import aarnav100.developer.biblescroll.data.backup.ImportError
import aarnav100.developer.biblescroll.data.backup.ImportResult
import aarnav100.developer.biblescroll.data.backup.MarkdownLabels
import aarnav100.developer.biblescroll.data.backup.notesMarkdown
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@Immutable
data class DataBackupUiState(
    val busy: Boolean = false,
    /** The finished import, shown in a dialog until dismissed. */
    val importResult: ImportResult? = null,
)

sealed interface DataBackupEvent {
    /** Open the system "create file" picker with this suggested name. */
    data class PickBackupFile(val name: String) : DataBackupEvent
    data class PickReadableFile(val name: String) : DataBackupEvent
    data class Saved(val withReadableCopy: Boolean) : DataBackupEvent
    data object SaveFailed : DataBackupEvent
}

/** S9 Data & backup: Export my data (JSON backup + readable Markdown) and Import. Files go through the system picker only. */
@HiltViewModel
class DataBackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporter: BackupExporter,
    private val importer: BackupImporter,
    private val bible: BibleRepository,
    private val readerPreferences: ReaderPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataBackupUiState())
    val uiState: StateFlow<DataBackupUiState> = _uiState.asStateFlow()

    private val _events = Channel<DataBackupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Built once when Export is tapped, so both files describe the same moment. */
    private var pending: Pair<String, String>? = null

    fun startExport(labels: MarkdownLabels) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val file = exporter.export()
                val translation = readerPreferences.translationId.first()
                val refs = (file.tables.notes.map { VerseRef(it.bookId, it.chapter, it.verse) } +
                    file.tables.verseMarks.filter { it.liked || it.bookmarked }.map { VerseRef(it.bookId, it.chapter, it.verse) }).distinct()
                val texts = bible.versesByRefs(translation, refs).associate { it.ref to it.text }
                val names = bible.books().first().associate { it.id to it.name }
                val date = LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
                val markdown = notesMarkdown(file.tables, texts, names, translation, date, labels)
                pending = exporter.encode(file) to markdown
                _events.send(DataBackupEvent.PickBackupFile("bible-scroll-backup-${LocalDate.now()}.json"))
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                _uiState.update { it.copy(busy = false) }
                _events.send(DataBackupEvent.SaveFailed)
            }
        }
    }

    /** The backup file's destination, or null if the picker was cancelled (then nothing is saved). */
    fun onBackupDestination(uri: Uri?) {
        val (json, _) = pending ?: return
        if (uri == null) return finishExport(null)
        viewModelScope.launch {
            if (write(uri, json)) {
                _events.send(DataBackupEvent.PickReadableFile("bible-scroll-notes-${LocalDate.now()}.md"))
            } else {
                finishExport(DataBackupEvent.SaveFailed)
            }
        }
    }

    /** The readable copy's destination, or null if the reader skipped it (the backup is already saved). */
    fun onReadableDestination(uri: Uri?) {
        val (_, markdown) = pending ?: return
        viewModelScope.launch {
            val withCopy = uri != null && write(uri, markdown)
            finishExport(DataBackupEvent.Saved(withCopy))
        }
    }

    private fun finishExport(event: DataBackupEvent?) {
        pending = null
        _uiState.update { it.copy(busy = false) }
        if (event != null) viewModelScope.launch { _events.send(event) }
    }

    fun import(uri: Uri) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = when (val file = read(uri)) {
                ReadFile.Unreadable -> ImportResult.Rejected(ImportError.Unreadable)
                ReadFile.TooLarge -> ImportResult.Rejected(ImportError.TooLarge)
                is ReadFile.Text -> try {
                    importer.import(file.text)
                } catch (e: Exception) {
                    // The merge runs in one transaction, so a failure here has changed nothing.
                    Log.e(TAG, "Import failed", e)
                    ImportResult.Rejected(ImportError.Damaged(e.message ?: "import"))
                }
            }
            (result as? ImportResult.Rejected)?.let { Log.w(TAG, "Import rejected: ${it.error}") }
            _uiState.update { it.copy(busy = false, importResult = result) }
        }
    }

    fun dismissImportResult() = _uiState.update { it.copy(importResult = null) }

    private suspend fun write(uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't write $uri", e)
            false
        }
    }

    private sealed interface ReadFile {
        data class Text(val text: String) : ReadFile
        data object TooLarge : ReadFile
        data object Unreadable : ReadFile
    }

    /** Reads the picked file, stopping past BackupValidator.MAX_BYTES. */
    private suspend fun read(uri: Uri): ReadFile = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > BackupValidator.MAX_BYTES) return@withContext ReadFile.TooLarge
                }
                ReadFile.Text(out.toString(Charsets.UTF_8.name()))
            } ?: ReadFile.Unreadable
        } catch (e: IOException) {
            Log.e(TAG, "Couldn't read $uri", e)
            ReadFile.Unreadable
        } catch (e: SecurityException) {
            Log.e(TAG, "No access to $uri", e)
            ReadFile.Unreadable
        }
    }

    private companion object {
        const val TAG = "DataBackup"
    }
}
