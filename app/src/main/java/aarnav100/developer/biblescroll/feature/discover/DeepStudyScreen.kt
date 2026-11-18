package aarnav100.developer.biblescroll.feature.discover

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.core.content.StudyState
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.feature.study.StudyBody
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * D2 Deep study: full-screen B8 layout with close X and a studied check. Bottom bar: Save, Read in context.
 * [onLeave] closes the page; for "Read in context" it runs before the link is posted, so the Bible tab opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepStudyScreen(onLeave: () -> Unit, viewModel: DeepStudyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reference = stringResource(R.string.feed_reference_format, state.bookName, state.ref.chapter, state.ref.verse)
    val readInContext = { target: VerseRef ->
        onLeave()
        viewModel.readInContext(target)
    }
    val available = state.study is StudyState.Available && !state.hidden

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
        bottomBar = {
            if (available) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(onClick = viewModel::toggleSaved, modifier = Modifier.weight(1f)) {
                            Icon(if (state.marks.saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, contentDescription = null)
                            Spacer(Modifier.padding(start = 8.dp))
                            Text(stringResource(if (state.marks.saved) R.string.study_saved else R.string.study_save))
                        }
                        Button(onClick = { readInContext(state.ref) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                            Spacer(Modifier.padding(start = 8.dp))
                            Text(stringResource(R.string.study_read_in_context))
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            StudyVerseHeader(reference, state.verseText)
            Spacer(Modifier.height(12.dp))
            when {
                state.hidden -> StudyMessage(
                    stringResource(R.string.study_hidden_title), stringResource(R.string.study_hidden_body),
                    action = stringResource(R.string.study_hidden_show), onAction = viewModel::showStudies,
                )
                else -> when (val s = state.study) {
                    is StudyState.Available -> StudyBody(s.study, state.crossReferences, onOpenReference = readInContext)
                    is StudyState.Loading -> StudyLoading(s)
                    is StudyState.NotDownloaded -> when (s.reason) {
                        StudyState.NotDownloaded.Reason.OFFLINE -> StudyMessage(
                            stringResource(R.string.study_offline_title),
                            stringResource(R.string.study_offline_body, state.bookName),
                        )
                        StudyState.NotDownloaded.Reason.DOWNLOAD_FAILED -> StudyMessage(
                            stringResource(R.string.study_failed_title), stringResource(R.string.study_failed_body),
                            action = stringResource(R.string.study_retry), onAction = viewModel::retry,
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
