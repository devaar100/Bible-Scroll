package aarnav100.developer.biblescroll.feature.bible.sheets

import aarnav100.developer.biblescroll.ui.components.SheetDragHandle
import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import aarnav100.developer.biblescroll.ui.theme.ScriptureTextStyle
import aarnav100.developer.biblescroll.ui.theme.scriptureSp
import aarnav100.developer.biblescroll.feature.bible.redLetterText
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import aarnav100.developer.biblescroll.ui.components.TestTags
import aarnav100.developer.biblescroll.ui.components.automationTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * B9: the verse in every bundled translation, current one first. Has its own snackbar host because
 * a ModalBottomSheet sits above the screen's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareSheet(
    reference: String,
    rows: List<CompareRow>?,
    onCopy: (CompareRow) -> Unit,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.automationTag(TestTags.COMPARE_SHEET),
        dragHandle = { SheetDragHandle() },
    ) {
        Box {
            Column(Modifier.padding(bottom = 16.dp)) {
                Text(
                    text = stringResource(R.string.compare_title, reference),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .semantics { heading() },
                )
                if (rows == null) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn {
                        items(rows, key = { it.translation.id }) { row ->
                            CompareRowItem(row, onCopy = { onCopy(row) })
                            HorizontalDivider(Modifier.padding(horizontal = 24.dp))
                        }
                    }
                }
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun CompareRowItem(row: CompareRow, onCopy: () -> Unit) {
    val jesusColor = BibleScrollTheme.extendedColors.wordsOfJesus
    Column(Modifier.padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Name + badge take the free width; the copy button keeps the same spot on every row.
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.translation.name,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.isCurrent) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.compare_current),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            if (row.verse != null) {
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.compare_copy_cd, row.translation.id),
                    )
                }
            } else {
                // Keep row height consistent when there's nothing to copy.
                Box(Modifier.size(48.dp))
            }
        }
        val verse = row.verse
        if (verse == null) {
            Text(
                text = stringResource(R.string.compare_missing),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp),
            )
        } else {
            val text = remember(verse, jesusColor) { verse.redLetterText(jesusColor) }
            Text(
                text = text,
                style = ScriptureTextStyle.copy(fontSize = scriptureSp(18.sp), lineHeight = 1.5.em),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 16.dp),
            )
        }
    }
}
