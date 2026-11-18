package aarnav100.developer.biblescroll.feature.onboarding

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.feature.bible.VersePage
import aarnav100.developer.biblescroll.feature.share.CardContent
import aarnav100.developer.biblescroll.feature.share.CardFormat
import aarnav100.developer.biblescroll.feature.share.CardStyle
import aarnav100.developer.biblescroll.feature.share.VerseCard
import aarnav100.developer.biblescroll.feature.share.rememberVerseFit
import aarnav100.developer.biblescroll.ui.theme.ReadingTheme
import aarnav100.developer.biblescroll.feature.settings.formatReminderTime
import java.time.LocalTime
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class TourPage(@StringRes val title: Int, @StringRes val body: Int) {
    Feed(R.string.onb_tour_feed_title, R.string.onb_tour_feed_body),
    Like(R.string.onb_tour_like_title, R.string.onb_tour_like_body),
    Share(R.string.onb_tour_share_title, R.string.onb_tour_share_body),
    Reminder(R.string.onb_tour_reminder_title, R.string.onb_tour_reminder_body),
}

/**
 * O3. Each page runs a small live demo made from the app's own composables (the feed's VersePage, the
 * share card, the reminder preview), so the tour always matches the real UI. With system animations
 * off, the demos hold still.
 */
@Composable
internal fun FeatureTourStep(
    tour: TourVerses,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
) {
    val pages = TourPage.entries
    // rememberPagerState is saveable, so rotation keeps the page.
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val animate = rememberAnimationsEnabled()
    val last = pager.currentPage == pages.lastIndex

    StepScaffold(
        onBack = onBack,
        scrollable = false,
        actions = {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.onb_skip)) }
        },
        bottom = {
            PageDots(count = pages.size, current = pager.currentPage)
            Spacer(Modifier.height(16.dp))
            PrimaryButton(stringResource(R.string.onb_continue), onClick = {
                if (last) onContinue() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            })
        },
    ) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            val page = pages[index]
            // Only the visible page animates; the others sit still while off screen.
            val active = animate && pager.currentPage == index && !pager.isScrollInProgress
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                PhoneFrame(Modifier.weight(1f)) {
                    when (page) {
                        TourPage.Feed -> FeedDemo(tour.feed, active)
                        TourPage.Like -> LikeDemo(tour.liked, active)
                        TourPage.Share -> ShareDemo(tour.card, active)
                        TourPage.Reminder -> ReminderDemo(tour.reminder, active)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(page.title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(page.body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    minLines = 2,
                )
            }
        }
    }
}

@Composable
private fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    val description = stringResource(R.string.onb_page_indicator, current + 1, count)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 24.dp else 8.dp, label = "dot")
            Box(
                Modifier
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}

/**
 * A phone-shaped window that lays its content out at a real phone size (360 x 720 dp) and scales it
 * down, so the real screens render exactly as they do full size. Decorative: hidden from TalkBack,
 * since the page's headline says the same thing.
 */
@Composable
private fun PhoneFrame(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val height = min(min(maxHeight, maxWidth * 2), 460.dp)
        val width = height / 2
        val scale = width / PHONE_WIDTH
        val shape = RoundedCornerShape(width / 8)
        Box(
            Modifier
                .size(width, height)
                .clip(shape)
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .requiredSize(PHONE_WIDTH, PHONE_WIDTH * 2)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                ReadingTheme {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) { content() }
                }
            }
        }
    }
}

private val PHONE_WIDTH = 360.dp

/** The verse feed, swiping itself up every couple of seconds. */
@Composable
private fun FeedDemo(verses: List<ShownVerse>, active: Boolean) {
    if (verses.isEmpty()) return
    val pager = rememberPagerState { verses.size }
    LaunchedEffect(active, verses.size) {
        if (!active) return@LaunchedEffect
        while (true) {
            delay(2_200)
            val next = (pager.currentPage + 1) % verses.size
            if (next == 0) pager.scrollToPage(0) else pager.animateScrollToPage(next)
        }
    }
    VerticalPager(state = pager, userScrollEnabled = false, modifier = Modifier.fillMaxSize()) { i ->
        DemoVersePage(verses[i], liked = false)
    }
}

/** A verse whose heart fills and empties on a loop, as a double-tap would. */
@Composable
private fun LikeDemo(verse: ShownVerse?, active: Boolean) {
    verse ?: return
    var liked by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            delay(1_400)
            liked = !liked
        }
    }
    DemoVersePage(verse, liked = liked)
}

@Composable
private fun DemoVersePage(verse: ShownVerse, liked: Boolean) {
    VersePage(
        verse = verse.verse,
        reference = verse.reference(),
        translationName = verse.verse.translationId,
        liked = liked,
        bookmarked = false,
        hasNote = false,
        onToggleLike = {},
        onToggleBookmark = {},
        onNote = {},
        onShare = {},
        onCopy = {},
        onActions = {},
    )
}

/** The share card, trying on each background in turn. */
@Composable
private fun ShareDemo(verse: ShownVerse?, active: Boolean) {
    verse ?: return
    val styles = CardStyle.entries
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            delay(1_800)
            index = (index + 1) % styles.size
        }
    }
    val content = CardContent(
        verseText = verse.verse.text,
        reference = verse.reference(),
        translationId = verse.verse.translationId,
        appName = stringResource(R.string.app_name),
    )
    val fit = rememberVerseFit(content.verseText, CardFormat.Story)
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Crossfade(targetState = styles[index], label = "cardStyle") { style ->
            VerseCard(
                content = content,
                format = CardFormat.Story,
                style = style,
                fit = fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(CardFormat.Story.widthPx / CardFormat.Story.heightPx.toFloat())
                    .clip(RoundedCornerShape(24.dp)),
            )
        }
    }
}

/** A quiet lock screen where the daily verse arrives. */
@Composable
private fun ReminderDemo(verse: ShownVerse?, active: Boolean) {
    var shown by remember { mutableStateOf(!active) }
    LaunchedEffect(active) {
        if (!active) {
            shown = true
            return@LaunchedEffect
        }
        while (true) {
            shown = false
            delay(700)
            shown = true
            delay(3_200)
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            formatReminderTime(LocalContext.current, LocalTime.of(8, 0)),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(48.dp))
        AnimatedVisibility(
            visible = shown,
            enter = slideInVertically { -it } + fadeIn(),
            exit = fadeOut(),
        ) {
            NotificationPreview(verse = verse, modifier = Modifier.width(320.dp))
        }
    }
}
