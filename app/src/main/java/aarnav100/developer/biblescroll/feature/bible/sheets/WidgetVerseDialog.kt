package aarnav100.developer.biblescroll.feature.bible.sheets

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.widget.WidgetMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * B11. The widget shows either this verse ("A fixed verse") or the Verse of the Day (H1). With a widget already
 * placed: choose and set. Without one: offer to pin it if the launcher supports that, otherwise explain how to
 * add it by hand.
 */
@Composable
fun WidgetVerseDialog(
    state: VerseSheet.WidgetConfirm,
    reference: String,
    onConfirm: (pin: Boolean, mode: WidgetMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val offerPin = !state.widgetPlaced && state.canPin
    var mode by rememberSaveable(state.verse.ref) { mutableStateOf(state.initialMode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.actions_widget_verse)) },
        text = {
            // Scrolls at the largest font sizes instead of pushing the buttons off screen.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.widget_dialog_question))
                Column(Modifier.selectableGroup()) {
                    ModeOption(
                        selected = mode == WidgetMode.FIXED,
                        title = stringResource(R.string.widget_mode_fixed),
                        detail = stringResource(R.string.widget_mode_fixed_detail, reference),
                        onSelect = { mode = WidgetMode.FIXED },
                    )
                    ModeOption(
                        selected = mode == WidgetMode.VERSE_OF_THE_DAY,
                        title = stringResource(R.string.votd_title),
                        detail = stringResource(R.string.widget_mode_votd_detail),
                        onSelect = { mode = WidgetMode.VERSE_OF_THE_DAY },
                    )
                }
                if (!state.widgetPlaced) {
                    Text(stringResource(R.string.widget_dialog_not_placed))
                    if (!state.canPin) {
                        Text(
                            stringResource(R.string.widget_dialog_manual, stringResource(R.string.app_name)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(offerPin, mode) }) {
                Text(stringResource(if (offerPin) R.string.widget_add_to_home else R.string.widget_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.widget_cancel)) }
        },
    )
}

@Composable
private fun ModeOption(selected: Boolean, title: String, detail: String, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row is the click target and carries the semantics; the button is decoration.
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
