package aarnav100.developer.biblescroll.feature.discover

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.ui.components.SheetDragHandle
import aarnav100.developer.biblescroll.ui.theme.AppText
import aarnav100.developer.biblescroll.ui.theme.Spacing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/** D1 share button: share the card as an image, share the verse as text (as on the Bible page), or copy it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverShareSheet(
    reference: String,
    onShareImage: () -> Unit,
    onShareText: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = { SheetDragHandle() }) {
        DiscoverShareContent(reference, onShareImage, onShareText, onCopy)
    }
}

/** The sheet's content, stateless (PseudoLocaleScreenshotTest renders it). */
@Composable
internal fun DiscoverShareContent(reference: String, onShareImage: () -> Unit, onShareText: () -> Unit, onCopy: () -> Unit) {
    Column(Modifier.padding(bottom = Spacing.l).navigationBarsPadding()) {
        Text(
            reference,
            style = AppText.itemTitle,
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.s).semantics { heading() },
        )
        ShareRow(Icons.Outlined.Image, stringResource(R.string.actions_share_image), onShareImage)
        ShareRow(Icons.Outlined.Share, stringResource(R.string.discover_share_text), onShareText)
        ShareRow(Icons.Outlined.ContentCopy, stringResource(R.string.actions_copy), onCopy)
    }
}

@Composable
private fun ShareRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = Spacing.s),
    )
}
