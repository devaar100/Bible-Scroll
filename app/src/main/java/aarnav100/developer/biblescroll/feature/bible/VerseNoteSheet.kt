package aarnav100.developer.biblescroll.feature.bible

import aarnav100.developer.biblescroll.ui.components.SheetDragHandle
import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.ui.theme.ScriptureTextStyle
import aarnav100.developer.biblescroll.ui.theme.scriptureSp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** B6: note editor for one verse. Saves as you type (see [VerseNoteViewModel]); no Save button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerseNoteSheet(
    state: VerseNoteUiState.Open,
    reference: String,
    onTextChange: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragHandle = { SheetDragHandle() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .imePadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reference,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.hasSavedNote) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.note_delete))
                    }
                }
            }
            Text(
                text = state.verse.text,
                style = ScriptureTextStyle.copy(fontSize = scriptureSp(17.sp), color = MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 16.dp)
                    .heightIn(max = 160.dp)
                    .verticalScroll(rememberScrollState()),
            )

            val initial = state.initialText
            if (initial == null) {
                Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            } else {
                // Local state keeps typing synchronous; the ViewModel only hears about changes to save them.
                key(state.ref) {
                    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focus.requestFocus() }
                    OutlinedTextField(
                        value = value,
                        onValueChange = { new ->
                            if (new.text != value.text) onTextChange(new.text)
                            value = new
                        },
                        placeholder = { Text(stringResource(R.string.note_hint)) },
                        minLines = 5,
                        maxLines = 12,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                    )
                }
                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    text = stringResource(R.string.note_autosaved),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
