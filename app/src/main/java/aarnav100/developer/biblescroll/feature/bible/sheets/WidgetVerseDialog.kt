package aarnav100.developer.biblescroll.feature.bible.sheets

import aarnav100.developer.biblescroll.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * B11. With a widget already placed: confirm and set. Without one: offer to pin it if the launcher
 * supports that, otherwise explain how to add it by hand.
 */
@Composable
fun WidgetVerseDialog(
    state: VerseSheet.WidgetConfirm,
    reference: String,
    onConfirm: (pin: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val offerPin = !state.widgetPlaced && state.canPin
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.actions_widget_verse)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.widget_dialog_body, reference))
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
            TextButton(onClick = { onConfirm(offerPin) }) {
                Text(stringResource(if (offerPin) R.string.widget_add_to_home else R.string.widget_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.widget_cancel)) }
        },
    )
}
