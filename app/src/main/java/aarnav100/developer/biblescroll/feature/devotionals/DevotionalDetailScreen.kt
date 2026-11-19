package aarnav100.developer.biblescroll.feature.devotionals

import aarnav100.developer.biblescroll.ui.text.localizedList
import aarnav100.developer.biblescroll.data.bible.References
import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.core.content.DevotionalState
import aarnav100.developer.biblescroll.feature.bible.clipText
import aarnav100.developer.biblescroll.feature.share.CardContent
import aarnav100.developer.biblescroll.feature.share.ShareImageSheet
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** H15: cover, title, theme, verse references, Start (or Read again), and share the verse as an image. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevotionalDetailScreen(
    onBack: () -> Unit,
    onStart: (date: String) -> Unit,
    viewModel: DevotionalDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var sharing by rememberSaveable { mutableStateOf(false) }
    val devotional = (state.content as? DevotionalState.Available)?.devotional
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(state.date.longLabel()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back)) }
                },
                actions = {
                    if (state.visible == true && state.shareVerse != null) {
                        IconButton(onClick = { sharing = true }) {
                            Icon(Icons.Outlined.Image, contentDescription = stringResource(R.string.devo_share_verse))
                        }
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            when {
                state.visible == false -> AiDevotionalsNotice(onShowAnyway = viewModel::showAnyway)
                devotional == null -> DevotionalStateMessage(state.content, onRetry = viewModel::retry)
                else -> {
                    DevotionalCover(
                        style = devotional.coverStyle, title = devotional.title, theme = devotional.theme,
                        eyebrow = stringResource(R.string.devo_series_day, devotional.series.title, devotional.series.day, devotional.series.length),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 5f),
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(devotional.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    Text(devotional.theme, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text(localizedList(state.passages), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { onStart(state.date.toString()) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (state.read) R.string.devo_read_again else R.string.devo_start))
                    }
                    if (state.read) {
                        Text(stringResource(R.string.devo_read_already), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (!devotional.reviewed) {
                        Text(stringResource(R.string.devo_ai_label), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }
        }
    }
    val verse = state.shareVerse
    if (sharing && verse != null) {
        val reference = References.verse(state.bookNames[verse.ref.bookId].orEmpty(), verse.ref)
        ShareImageSheet(
            content = CardContent(verse.text, reference, verse.translationId, stringResource(R.string.app_name)),
            shareText = verse.clipText(reference),
            onDismiss = { sharing = false },
        )
    }
}
