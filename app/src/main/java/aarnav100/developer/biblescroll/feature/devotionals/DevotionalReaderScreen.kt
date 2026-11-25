package aarnav100.developer.biblescroll.feature.devotionals

import aarnav100.developer.biblescroll.feature.report.ContentType
import aarnav100.developer.biblescroll.feature.report.ReportContext
import aarnav100.developer.biblescroll.feature.report.ReportProblemSheet
import androidx.compose.material.icons.outlined.Flag
import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.core.content.DevotionalState
import aarnav100.developer.biblescroll.core.content.model.Devotional
import aarnav100.developer.biblescroll.data.devotionals.DevotionalCalendar
import aarnav100.developer.biblescroll.feature.bible.clipText
import aarnav100.developer.biblescroll.feature.share.CardContent
import aarnav100.developer.biblescroll.feature.share.ShareImageSheet
import aarnav100.developer.biblescroll.ui.theme.Literata
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * H16: story-style pages at the reader's own pace. Tap the right side for the next page, the left third for
 * the previous one (swiping works too). No timer, nothing advances by itself, long-press does nothing special.
 */
@Composable
fun DevotionalReaderScreen(onClose: () -> Unit, viewModel: DevotionalReaderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val devotional = (state.content as? DevotionalState.Available)?.devotional
    when {
        devotional != null && state.pages.isNotEmpty() -> StoryPager(state, devotional, onClose, viewModel::finish)
        else -> Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.align(Alignment.Center).padding(24.dp)) {
                    if (devotional == null) DevotionalStateMessage(state.content, onRetry = {})
                }
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.devo_close))
                }
            }
        }
    }
}

@Composable
private fun StoryPager(state: DevotionalReaderUiState, devotional: Devotional, onClose: () -> Unit, onFinish: () -> Unit) {
    val cover = CoverStyle.of(devotional.coverStyle)
    val pages = state.pages
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    var sharing by rememberSaveable { mutableStateOf<Int?>(null) }
    var reporting by rememberSaveable { mutableStateOf(false) }
    val next = { if (pager.currentPage < pages.lastIndex) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
    val previous = { if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }
    val nextLabel = stringResource(R.string.devo_next_page)
    val previousLabel = stringResource(R.string.devo_previous_page)
    val position = stringResource(R.string.devo_page_of, pager.currentPage + 1, pages.size)

    Box(
        Modifier
            .fillMaxSize()
            .background(cover.brush)
            .semantics {
                stateDescription = position
                customActions = listOf(
                    CustomAccessibilityAction(nextLabel) { next(); true },
                    CustomAccessibilityAction(previousLabel) { previous(); true },
                )
            },
    ) {
        HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { index ->
            Box(
                Modifier
                    .fillMaxSize()
                    // Taps on buttons are consumed by the buttons; everywhere else: left third back, rest forward.
                    .pointerInput(index) {
                        detectTapGestures(onTap = { offset -> if (offset.x < size.width / 3f) previous() else next() })
                    }
                    .padding(top = 72.dp, start = 28.dp, end = 28.dp, bottom = 28.dp),
            ) {
                when (val page = pages[index]) {
                    StoryPage.Cover -> DevotionalCover(
                        style = devotional.coverStyle, title = devotional.title, theme = devotional.theme,
                        eyebrow = stringResource(R.string.devo_series_day, devotional.series.title, devotional.series.day, devotional.series.length),
                        modifier = Modifier.fillMaxSize(),
                    )
                    is StoryPage.VersePage -> Column(Modifier.align(Alignment.Center)) {
                        Text(page.verse.text, color = cover.ink, fontFamily = Literata, fontSize = 26.sp, lineHeight = 38.sp)
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.two_parts, page.reference, state.translationId), color = cover.accent, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { sharing = index }) {
                                Icon(Icons.Outlined.Image, contentDescription = stringResource(R.string.devo_share_verse), tint = cover.ink)
                            }
                        }
                    }
                    is StoryPage.Section -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                        Text(
                            stringResource(page.kind.title()).uppercase(),
                            color = cover.accent, fontSize = 13.sp, letterSpacing = 2.sp,
                            modifier = Modifier.semantics { heading() },
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(page.text, color = cover.ink, fontFamily = Literata, fontSize = 21.sp, lineHeight = 32.sp)
                        if (index == pages.lastIndex) {
                            Spacer(Modifier.height(32.dp))
                            FinishBlock(state.finished, cover, onFinish, onClose)
                        }
                    }
                }
            }
        }
        StoryProgress(pages.size, pager.currentPage, cover, Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 16.dp, end = 16.dp))
        Row(Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 4.dp)) {
            IconButton(onClick = { reporting = true }) {
                Icon(Icons.Outlined.Flag, contentDescription = stringResource(R.string.report_title), tint = cover.ink)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.devo_close), tint = cover.ink)
            }
        }
    }
    if (reporting) {
        // On a verse page, name the verse too: a wrong-verse report then says which one.
        val versePage = pages.getOrNull(pager.currentPage) as? StoryPage.VersePage
        ReportProblemSheet(
            ReportContext(
                contentType = ContentType.DEVOTIONAL,
                contentId = devotional.id,
                reference = versePage?.let { stringResource(R.string.two_parts, devotional.title, it.reference) } ?: devotional.title,
                translationId = state.translationId,
                contentVersion = devotional.contentVersion,
            ),
            onDismiss = { reporting = false },
        )
    }
    val shared = sharing?.let { pages.getOrNull(it) as? StoryPage.VersePage }
    if (shared != null) {
        ShareImageSheet(
            content = CardContent(shared.verse.text, shared.reference, shared.verse.translationId, stringResource(R.string.app_name)),
            shareText = shared.verse.clipText(shared.reference),
            onDismiss = { sharing = null },
        )
    }
}

/** A segment per page: done and current filled, the rest faint. */
@Composable
private fun StoryProgress(count: Int, current: Int, cover: CoverStyle, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= current) cover.ink else cover.ink.copy(alpha = 0.25f)),
            )
        }
    }
}

@Composable
private fun FinishBlock(finished: Boolean, cover: CoverStyle, onFinish: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val colors = ButtonDefaults.buttonColors(containerColor = cover.ink, contentColor = cover.top)
        if (!finished) {
            Button(onClick = onFinish, colors = colors, modifier = Modifier.widthIn(min = 200.dp)) { Text(stringResource(R.string.devo_finish)) }
        } else {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = cover.ink, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.devo_finished), color = cover.ink, fontSize = 18.sp, textAlign = TextAlign.Center)
            Countdown(cover)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClose, colors = colors, modifier = Modifier.widthIn(min = 200.dp)) { Text(stringResource(R.string.devo_close)) }
        }
    }
}

@Composable
private fun Countdown(cover: CoverStyle) {
    val left = rememberCountdown()
    Text(stringResource(R.string.devo_next_in, left), color = cover.ink.copy(alpha = 0.85f), fontSize = 15.sp)
}

/** "HH:MM:SS" to the next local midnight, ticking every second while shown. */
@Composable
fun rememberCountdown(): String {
    var text by remember { mutableStateOf(DevotionalCalendar.format(DevotionalCalendar.untilMidnight(ZonedDateTime.now()))) }
    LaunchedEffect(Unit) {
        while (true) {
            text = DevotionalCalendar.format(DevotionalCalendar.untilMidnight(ZonedDateTime.now()))
            delay(1_000L - System.currentTimeMillis() % 1_000L)
        }
    }
    return text
}

private fun SectionKind.title(): Int = when (this) {
    SectionKind.Context -> R.string.devo_section_context
    SectionKind.Reflect -> R.string.devo_section_reflect
    SectionKind.ApplyIt -> R.string.devo_section_apply
    SectionKind.Prayer -> R.string.devo_section_prayer
}
