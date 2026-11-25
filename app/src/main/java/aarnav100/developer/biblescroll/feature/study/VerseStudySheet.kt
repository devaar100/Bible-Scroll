package aarnav100.developer.biblescroll.feature.study

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.core.content.StudyState
import aarnav100.developer.biblescroll.data.bible.References
import aarnav100.developer.biblescroll.feature.report.ContentType
import aarnav100.developer.biblescroll.feature.report.ReportContext
import aarnav100.developer.biblescroll.feature.report.ReportProblemSheet
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * B8 Verse study: a full-height sheet over the feed. Swipe down, the close button, back and predictive back all
 * dismiss it and leave the feed on the same verse. Content is the shared study layout (StudyStateContent/StudyBody).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerseStudySheet(viewModel: VerseStudyViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val open = state ?: return
    var reporting by rememberSaveable(open.ref) { mutableStateOf(false) }
    val reference = References.verse(open.bookName, open.ref)

    ModalBottomSheet(
        onDismissRequest = viewModel::close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        VerseStudyContent(
            state = open,
            reference = reference,
            onClose = viewModel::close,
            onToggleSaved = viewModel::toggleSaved,
            onRetry = { viewModel.retry() },
            onShowStudies = { viewModel.showStudies() },
            onGoToVerse = viewModel::goToVerse,
            onReport = { reporting = true },
        )
    }
    val study = (open.study as? StudyState.Available)?.study
    if (reporting && study != null) {
        ReportProblemSheet(
            ReportContext(
                contentType = ContentType.STUDY,
                contentId = study.id,
                reference = reference,
                translationId = open.translationId,
                contentVersion = study.contentVersion,
                promptHash = study.promptVersion,
            ),
            onDismiss = { reporting = false },
        )
    }
}

/** Stateless B8 content (PseudoLocaleScreenshotTest renders it). */
@Composable
fun VerseStudyContent(
    state: StudyUiState,
    reference: String,
    onClose: () -> Unit,
    onToggleSaved: () -> Unit,
    onRetry: () -> Unit,
    onShowStudies: () -> Unit,
    onGoToVerse: (aarnav100.developer.biblescroll.data.bible.model.VerseRef) -> Unit,
    onReport: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.actions_study),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.study_close)) }
        }
        StudyVerseHeader(reference, state.verse, verseText = state.verseText, translationId = state.translationId)
        if (state.study is StudyState.Available) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onToggleSaved) {
                Icon(if (state.marks.saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (state.marks.saved) R.string.study_study_saved else R.string.study_save_study))
            }
        }
        Spacer(Modifier.height(8.dp))
        StudyStateContent(
            state,
            dataOnlyWhenHidden = true,
            onRetry = onRetry,
            onShowStudies = onShowStudies,
            onOpenReference = onGoToVerse,
            onReport = onReport,
        )
    }
}
