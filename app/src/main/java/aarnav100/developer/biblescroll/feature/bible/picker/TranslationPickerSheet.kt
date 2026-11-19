package aarnav100.developer.biblescroll.feature.bible.picker

import aarnav100.developer.biblescroll.ui.components.SheetDragHandle
import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.bible.model.Translation
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** B5: every bundled translation with its description and licence line. Switching keeps the verse. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationPickerSheet(
    translations: List<Translation>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hideThen = rememberHideThen(sheetState, onDismiss)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragHandle = { SheetDragHandle() }) {
        Text(
            text = stringResource(R.string.translations_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .semantics { heading() },
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(translations, key = { it.id }) { translation ->
                val selected = translation.id == selectedId
                TranslationRow(
                    translation = translation,
                    selected = selected,
                    onClick = {
                        if (selected) hideThen {} else hideThen { onSelect(translation.id) }
                    },
                )
            }
        }
    }
}

@Composable
private fun TranslationRow(translation: Translation, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.surfaceContainerHighest else colors.surfaceContainerLow,
        border = if (selected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(translation.id, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = " · ${translation.name}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                translationDescription(translation.id)?.let { res ->
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(res), style = MaterialTheme.typography.bodyMedium)
                }
                if (!translation.hasRedLetters) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.translations_no_red_letters),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = translation.licence,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                OfflineBadge()
            }
            if (selected) {
                Spacer(Modifier.width(12.dp))
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.translations_selected),
                    tint = colors.primary,
                )
            }
        }
    }
}

/** Every translation is bundled for now; downloadable ones will show a download state instead. */
@Composable
private fun OfflineBadge() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Icon(Icons.Outlined.DownloadDone, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.translations_offline), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** One-line description in the UI language (also used by the onboarding Bible step). */
@StringRes
internal fun translationDescription(id: String): Int? = when (id) {
    "WEB" -> R.string.translation_desc_WEB
    "KJV" -> R.string.translation_desc_KJV
    "BSB" -> R.string.translation_desc_BSB
    "RV1909" -> R.string.translation_desc_RV1909
    else -> null
}
