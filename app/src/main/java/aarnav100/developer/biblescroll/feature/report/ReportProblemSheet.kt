package aarnav100.developer.biblescroll.feature.report

import aarnav100.developer.biblescroll.R
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Report a problem (S7). Opened from Help & feedback, studies (D2, and B8 through StudyBody), the devotional reader
 * and the plan reader. Nothing is sent from the app: it opens the person's email app with the report filled in, or,
 * with no email app, shows the address and text to copy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportProblemSheet(context: ReportContext, onDismiss: () -> Unit) {
    val android = LocalContext.current
    val sender = remember(android) { ReportSender(AndroidEmailApps(android)) }
    var type by rememberSaveable { mutableStateOf<ReportType?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var fallback by remember { mutableStateOf<ReportEmail?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val shown = fallback
        if (shown == null) {
            ReportProblemContent(
                selected = type,
                onSelect = { type = it },
                note = note,
                onNoteChange = { note = it.take(ProblemReport.MAX_NOTE_CHARS) },
                onSend = {
                    val chosen = type ?: return@ReportProblemContent
                    val email = ProblemReport.build(SupportAddress, chosen, note, context, currentDeviceInfo(android))
                    when (val result = sender.send(email)) {
                        SendResult.Opened -> onDismiss()
                        is SendResult.NoEmailApp -> fallback = result.email
                    }
                },
                onCancel = onDismiss,
            )
        } else {
            NoEmailAppContent(email = shown, onClose = onDismiss)
        }
    }
}

/** The form. Stateless, so PseudoLocaleScreenshotTest can render it. */
@Composable
fun ReportProblemContent(
    selected: ReportType?,
    onSelect: (ReportType) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    // Scrolls at the largest font sizes; imePadding keeps the note and buttons above the keyboard.
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.report_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.report_question), style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup()) {
            ReportType.entries.forEach { type ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = type == selected, onClick = { onSelect(type) }, role = Role.RadioButton)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The row is the click target and carries the semantics.
                    RadioButton(selected = type == selected, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(type.label), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            label = { Text(stringResource(R.string.report_note_label)) },
            supportingText = {
                Text(stringResource(R.string.report_note_counter, note.length, ProblemReport.MAX_NOTE_CHARS))
            },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.report_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.report_cancel)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSend, enabled = selected != null) { Text(stringResource(R.string.report_send)) }
        }
    }
}

/** No email app: the address and the full text, selectable, with a Copy button. */
@Composable
fun NoEmailAppContent(email: ReportEmail, onClose: () -> Unit) {
    val android = LocalContext.current
    var copied by remember(email) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.report_no_email_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        NoEmailAppBody(email)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.report_close)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { copyReport(android, email); copied = true }) {
                Text(
                    stringResource(if (copied) R.string.report_copied else R.string.report_copy),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** For "Send feedback" (no sheet behind it): the same fallback as a dialog. */
@Composable
fun NoEmailAppDialog(email: ReportEmail, onDismiss: () -> Unit) {
    val android = LocalContext.current
    var copied by remember(email) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_no_email_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { NoEmailAppBody(email) } },
        confirmButton = {
            TextButton(onClick = { copyReport(android, email); copied = true }) {
                Text(stringResource(if (copied) R.string.report_copied else R.string.report_copy))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.report_close)) } },
    )
}

@Composable
private fun NoEmailAppBody(email: ReportEmail) {
    Text(stringResource(R.string.report_no_email_body, email.address))
    Text(stringResource(R.string.report_address_label), style = MaterialTheme.typography.labelLarge)
    SelectionContainer { Text(email.address, style = MaterialTheme.typography.bodyLarge) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
        SelectionContainer {
            Text(
                "${email.subject}\n\n${email.body}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

private fun copyReport(context: Context, email: ReportEmail) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(email.subject, email.copyText))
}
