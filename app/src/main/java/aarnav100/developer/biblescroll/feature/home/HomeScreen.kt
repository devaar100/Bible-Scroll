package aarnav100.developer.biblescroll.feature.home

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.feature.library.LibraryViewModel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** H1 is Phase 3. For now Home is a header (Settings gear) and the Library (H3) entry. */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
) {
    val library by libraryViewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.tab_home),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        }
        Surface(
            onClick = onOpenLibrary,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.library_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        pluralStringResource(R.plurals.library_total, library.total, library.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}
