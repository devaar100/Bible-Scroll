package aarnav100.developer.biblescroll.feature.library

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.library.LibraryList
import aarnav100.developer.biblescroll.data.library.LibrarySort
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/** H4 Saved studies, H5 Studied, H6 Liked & bookmarked, H7 Notes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryListScreen(
    onBack: () -> Unit,
    onOpenStudy: (VerseRef) -> Unit,
    viewModel: LibraryListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removedMessage = stringResource(state.list.removedMessage)
    val undoLabel = stringResource(R.string.library_undo)

    fun remove(ref: VerseRef) {
        scope.launch {
            val removal = viewModel.remove(ref) ?: return@launch
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(removedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(removal)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(state.screen.title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.screen == LibraryScreen.LIKED_AND_BOOKMARKED) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarkFilter.entries.forEach { f ->
                        FilterChip(
                            selected = state.filter == f,
                            onClick = { viewModel.setFilter(f) },
                            label = { Text(stringResource(if (f == MarkFilter.LIKED) R.string.library_liked else R.string.library_bookmarked)) },
                        )
                    }
                }
            }
            if (state.screen == LibraryScreen.NOTES) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text(stringResource(R.string.library_search_notes)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.library_clear_search))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                LibrarySort.entries.forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = state.sort == s,
                        onClick = { viewModel.setSort(s) },
                        shape = SegmentedButtonDefaults.itemShape(i, LibrarySort.entries.size),
                    ) { Text(stringResource(if (s == LibrarySort.BIBLE) R.string.library_sort_bible else R.string.library_sort_recent)) }
                }
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.rows.isEmpty() -> EmptyState(
                    if (state.screen == LibraryScreen.NOTES && state.query.isNotBlank()) {
                        stringResource(R.string.library_no_matches, state.query.trim())
                    } else {
                        stringResource(state.list.emptyHint)
                    },
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.rows, key = { "${state.list}:${it.ref.bookId}.${it.ref.chapter}.${it.ref.verse}" }) { row ->
                        SwipeRow(
                            row = row,
                            removeLabel = stringResource(R.string.library_remove),
                            onOpen = {
                                when (val target = viewModel.open(row.ref)) {
                                    is LibraryOpen.Study -> onOpenStudy(target.ref)
                                    is LibraryOpen.Verse -> Unit // the Bible tab opens on the verse
                                }
                            },
                            onRemove = { remove(row.ref) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(row: LibraryRow, removeLabel: String, onOpen: () -> Unit, onRemove: () -> Unit, modifier: Modifier) {
    val dismiss = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismiss,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onRemove() },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        ListItem(
            headlineContent = { Text(row.reference) },
            supportingContent = {
                Text(
                    row.note ?: row.verseText.orEmpty(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            modifier = Modifier
                .clickable(onClick = onOpen)
                // Swiping isn't discoverable with TalkBack; offer the same as an action.
                .semantics { customActions = listOf(CustomAccessibilityAction(removeLabel) { onRemove(); true }) },
        )
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val LibraryScreen.title: Int get() = when (this) {
    LibraryScreen.SAVED_STUDIES -> R.string.library_saved_studies
    LibraryScreen.STUDIED -> R.string.library_studied
    LibraryScreen.LIKED_AND_BOOKMARKED -> R.string.library_liked_bookmarked
    LibraryScreen.NOTES -> R.string.library_notes
}

private val LibraryList.emptyHint: Int get() = when (this) {
    LibraryList.SAVED_STUDIES -> R.string.library_empty_saved
    LibraryList.STUDIED -> R.string.library_empty_studied
    LibraryList.LIKED -> R.string.library_empty_liked
    LibraryList.BOOKMARKED -> R.string.library_empty_bookmarked
    LibraryList.NOTES -> R.string.library_empty_notes
}

private val LibraryList.removedMessage: Int get() = when (this) {
    LibraryList.SAVED_STUDIES -> R.string.library_removed_saved
    LibraryList.STUDIED -> R.string.library_removed_studied
    LibraryList.LIKED -> R.string.library_removed_liked
    LibraryList.BOOKMARKED -> R.string.library_removed_bookmarked
    LibraryList.NOTES -> R.string.library_removed_note
}
