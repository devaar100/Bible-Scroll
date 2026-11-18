package aarnav100.developer.biblescroll.feature.library

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.feature.settings.SettingsPage
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** H3 Library: live counts, grouped as in the spec (Studies; Bible). */
@Composable
fun LibraryScreen(onBack: () -> Unit, onOpen: (LibraryScreen) -> Unit, viewModel: LibraryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val counts = state.counts
    SettingsPage(title = stringResource(R.string.library_title), onBack = onBack) {
        Text(
            pluralStringResource(R.plurals.library_total, state.total, state.total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (!state.studiesHidden) {
            Group(stringResource(R.string.library_group_studies))
            Entry(Icons.Outlined.BookmarkBorder, stringResource(R.string.library_saved_studies), counts?.saved) {
                onOpen(LibraryScreen.SAVED_STUDIES)
            }
            Entry(Icons.Outlined.CheckCircle, stringResource(R.string.library_studied), counts?.studied) {
                onOpen(LibraryScreen.STUDIED)
            }
        }
        Group(stringResource(R.string.library_group_bible))
        Entry(
            Icons.Outlined.FavoriteBorder, stringResource(R.string.library_liked_bookmarked),
            counts?.let { it.liked + it.bookmarked },
        ) { onOpen(LibraryScreen.LIKED_AND_BOOKMARKED) }
        Entry(Icons.AutoMirrored.Outlined.StickyNote2, stringResource(R.string.library_notes), counts?.notes) {
            onOpen(LibraryScreen.NOTES)
        }
    }
}

@Composable
private fun Group(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun Entry(icon: ImageVector, title: String, count: Int?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(count?.toString() ?: "", style = MaterialTheme.typography.bodyLarge)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
