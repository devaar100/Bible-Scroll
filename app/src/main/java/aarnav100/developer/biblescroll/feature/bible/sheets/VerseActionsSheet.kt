package aarnav100.developer.biblescroll.feature.bible.sheets

import aarnav100.developer.biblescroll.ui.components.SheetDragHandle
import aarnav100.developer.biblescroll.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * B7: actions for one verse. Study actions (B8) are deliberately absent until Phase 2.
 * Swipe down / back calls [onDismiss] and the feed stays where it was.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerseActionsSheet(
    reference: String,
    onCopy: () -> Unit,
    onCompare: () -> Unit,
    onShareAsImage: () -> Unit,
    onSetWidgetVerse: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = { SheetDragHandle() }) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Text(
                text = reference,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ActionRow(Icons.Outlined.ContentCopy, stringResource(R.string.actions_copy), onCopy)
            ActionRow(Icons.AutoMirrored.Outlined.CompareArrows, stringResource(R.string.actions_compare), onCompare)
            ActionRow(Icons.Outlined.Image, stringResource(R.string.actions_share_image), onShareAsImage)
            ActionRow(Icons.Outlined.Widgets, stringResource(R.string.actions_widget_verse), onSetWidgetVerse)
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
    )
}
