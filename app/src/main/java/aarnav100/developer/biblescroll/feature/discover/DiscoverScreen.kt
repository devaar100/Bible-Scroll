package aarnav100.developer.biblescroll.feature.discover

import aarnav100.developer.biblescroll.feature.study.StudyMessage
import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** D1 Discover: a vertical feed of curated verse-study cards. Tap a card for its deep study (D2). */
@Composable
fun DiscoverScreen(
    onOpenStudy: (VerseRef) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val s = state) {
            DiscoverUiState.Loading -> CircularProgressIndicator()
            DiscoverUiState.Hidden -> Centered(Icons.Outlined.VisibilityOff) {
                StudyMessage(
                    stringResource(R.string.discover_hidden_title), stringResource(R.string.discover_hidden_body),
                    action = stringResource(R.string.study_hidden_show), onAction = viewModel::showStudies,
                )
            }
            DiscoverUiState.Offline -> Centered(Icons.Outlined.CloudOff) {
                StudyMessage(
                    stringResource(R.string.discover_offline_title), stringResource(R.string.discover_offline_body),
                    action = stringResource(R.string.study_retry), onAction = viewModel::retry,
                )
            }
            DiscoverUiState.Empty -> Centered(Icons.Outlined.Explore) {
                StudyMessage(stringResource(R.string.discover_empty_title), stringResource(R.string.discover_empty_body))
            }
            is DiscoverUiState.Ready -> key(s.cards) {
                // Created once per card list, starting on the card last shown (kept by the view model).
                val pager = rememberPagerState(initialPage = s.startIndex) { s.cards.size }
                LaunchedEffect(pager) {
                    snapshotFlow { pager.settledPage }.collect(viewModel::onCardShown)
                }
                VerticalPager(state = pager, beyondViewportPageCount = 1, key = { s.cards[it].hashCode() }) { page ->
                    val ref = s.cards[page]
                    val data = remember(ref) { viewModel.card(ref) }
                    DiscoverCard(data, onOpen = { onOpenStudy(ref) })
                }
            }
        }
    }
}

@Composable
private fun Centered(icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}
