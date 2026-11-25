package aarnav100.developer.biblescroll.feature.discover

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import aarnav100.developer.biblescroll.feature.report.ContentType
import aarnav100.developer.biblescroll.feature.report.ReportContext
import aarnav100.developer.biblescroll.feature.report.ReportProblemSheet
import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.core.content.StudyState
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.feature.study.StudyStateContent
import aarnav100.developer.biblescroll.feature.study.StudyVerseHeader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import aarnav100.developer.biblescroll.ui.theme.Spacing
import aarnav100.developer.biblescroll.ui.theme.AppText
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * D2 Deep study: full-screen B8 layout with close X and a studied check. Bottom bar: Save, Read in context.
 * [onLeave] closes the page; for "Read in context" it runs before the link is posted, so the Bible tab opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepStudyScreen(onLeave: () -> Unit, theme: String? = null, viewModel: DeepStudyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reference = stringResource(R.string.ref_verse, state.bookName, state.ref.chapter, state.ref.verse)
    // The study stays open under the jump to the Bible tab, so Back there returns here (BibleScrollAppUi.returnTo).
    val readInContext = { target: VerseRef -> viewModel.readInContext(target) }
    val available = state.study is StudyState.Available && !state.hidden
    var reporting by rememberSaveable { mutableStateOf(false) }
    (state.study as? StudyState.Available)?.study?.takeIf { reporting }?.let { study ->
        ReportProblemSheet(
            ReportContext(
                contentType = ContentType.STUDY,
                contentId = study.id,
                reference = reference,
                translationId = state.translationId,
                contentVersion = study.contentVersion,
                promptHash = study.promptVersion,
            ),
            onDismiss = { reporting = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(reference) },
                navigationIcon = {
                    IconButton(onClick = onLeave) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.study_close))
                    }
                },
                actions = {
                    if (available) {
                        // Save study (H4): an icon in the bar; the accent when on.
                        IconToggleButton(checked = state.marks.saved, onCheckedChange = { viewModel.toggleSaved() }) {
                            Icon(
                                if (state.marks.saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = stringResource(if (state.marks.saved) R.string.study_saved else R.string.study_save),
                                tint = if (state.marks.saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val studiedLabel = stringResource(if (state.marks.studied) R.string.study_studied else R.string.study_not_studied)
                        IconToggleButton(
                            checked = state.marks.studied,
                            onCheckedChange = { viewModel.toggleStudied() },
                            modifier = Modifier.semantics { stateDescription = studiedLabel },
                        ) {
                            Icon(
                                if (state.marks.studied) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                                contentDescription = stringResource(R.string.study_mark_studied),
                                tint = if (state.marks.studied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.gutter)
                .padding(top = Spacing.s, bottom = Spacing.xxl),
        ) {
            StudyVerseHeader(reference, state.verse, verseText = state.verseText, translationId = state.translationId, theme = theme)
            if (available) {
                // Read in context: a quiet accent link under the verse (it replaced the filled bottom button).
                TextButton(
                    onClick = { readInContext(state.ref) },
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    modifier = Modifier.padding(top = Spacing.s).heightIn(min = 48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.s))
                    Text(stringResource(R.string.study_read_in_context), style = AppText.button)
                    Spacer(Modifier.width(Spacing.xs))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(Spacing.l))
            StudyStateContent(
                state, dataOnlyWhenHidden = false, onRetry = viewModel::retry, onShowStudies = viewModel::showStudies,
                onOpenReference = readInContext, onReport = { reporting = true },
            )
        }
    }
}
