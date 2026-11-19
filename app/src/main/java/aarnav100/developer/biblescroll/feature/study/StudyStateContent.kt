package aarnav100.developer.biblescroll.feature.study

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.core.content.StudyState
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Everything below the verse in D2 and B8: the study, or a calm explained state (downloading with progress,
 * not available offline, download failed, no study yet, app update needed). Never an error screen or a crash.
 *
 * [dataOnlyWhenHidden]: with Hide AI studies on, B8 shows the study's data sections (true); D2 shows only an
 * explanation (false).
 */
@Composable
fun StudyStateContent(
    state: StudyUiState,
    dataOnlyWhenHidden: Boolean,
    onRetry: () -> Unit,
    onShowStudies: () -> Unit,
    onOpenReference: (VerseRef) -> Unit,
    onReport: () -> Unit,
) {
    if (state.hidden && !dataOnlyWhenHidden) {
        StudyMessage(
            stringResource(R.string.study_hidden_title), stringResource(R.string.study_hidden_body),
            action = stringResource(R.string.study_hidden_show), onAction = onShowStudies,
        )
        return
    }
    when (val s = state.study) {
        is StudyState.Available -> StudyBody(
            s.study, state.crossReferences, onOpenReference = onOpenReference, onReport = onReport,
            showProse = !state.hidden, onShowStudies = onShowStudies,
        )
        is StudyState.Loading -> StudyLoading(s)
        is StudyState.NotDownloaded -> when (s.reason) {
            StudyState.NotDownloaded.Reason.OFFLINE -> StudyMessage(
                stringResource(R.string.study_offline_title),
                stringResource(R.string.study_offline_body, state.bookName),
                action = stringResource(R.string.study_retry), onAction = onRetry,
            )
            StudyState.NotDownloaded.Reason.DOWNLOAD_FAILED -> StudyMessage(
                stringResource(R.string.study_failed_title), stringResource(R.string.study_failed_body),
                action = stringResource(R.string.study_retry), onAction = onRetry,
            )
        }
        is StudyState.Unavailable -> when (s.reason) {
            StudyState.Unavailable.Reason.NO_STUDY -> StudyMessage(
                stringResource(R.string.study_none_title), stringResource(R.string.study_none_body),
            )
            StudyState.Unavailable.Reason.APP_UPDATE_REQUIRED -> StudyMessage(
                stringResource(R.string.study_update_title), stringResource(R.string.study_update_body),
            )
        }
    }
}

@Composable
private fun StudyLoading(state: StudyState.Loading) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val fraction = state.progress?.fraction
        if (state.progress == null) {
            CircularProgressIndicator()
        } else {
            Text(stringResource(R.string.study_downloading), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

/** A calm, explained state (offline, hidden, nothing yet) with an optional action. Never an error screen. */
@Composable
fun StudyMessage(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}
