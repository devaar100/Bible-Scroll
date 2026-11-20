package aarnav100.developer.biblescroll.feature.bible

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import aarnav100.developer.biblescroll.ui.theme.ScriptureTextStyle
import aarnav100.developer.biblescroll.ui.theme.scriptureSp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import aarnav100.developer.biblescroll.ui.components.scrubber.ScrubberVisibility
import androidx.compose.ui.unit.Dp

/**
 * One full-screen verse: text, reference, right rail. Double-tap toggles like, long-press copies.
 *
 * TalkBack: the verse and its reference read as one item ("text. John 3:16, World English Bible"),
 * and since double-tap means "activate" there, every gesture and rail button is also a custom action
 * on that item (Like, Bookmark, Add note, Copy, Share, Verse actions).
 */
@Composable
fun VersePage(
    verse: Verse,
    reference: String,
    /** Spoken after the reference, e.g. "World English Bible". */
    translationName: String,
    liked: Boolean,
    bookmarked: Boolean,
    hasNote: Boolean,
    onToggleLike: () -> Unit,
    onToggleBookmark: () -> Unit,
    onNote: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onActions: () -> Unit,
    modifier: Modifier = Modifier,
    /** Start padding of the text; wider when the verse scrubber is on, so the strip never covers it. */
    textStartInset: Dp = ScrubberVisibility.DEFAULT_TEXT_START_DP.dp,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val burst = remember(verse.ref) { Animatable(0f) }
    val jesusColor = BibleScrollTheme.extendedColors.wordsOfJesus
    val text = remember(verse, jesusColor) { verse.redLetterText(jesusColor) }

    val likeLabel = stringResource(if (liked) R.string.feed_unlike else R.string.feed_like)
    val bookmarkLabel = stringResource(if (bookmarked) R.string.feed_unbookmark else R.string.feed_bookmark)
    val noteLabel = stringResource(if (hasNote) R.string.feed_note_edit else R.string.feed_note_add)
    val copyLabel = stringResource(R.string.feed_copy)
    val shareLabel = stringResource(R.string.feed_share)
    val actionsLabel = stringResource(R.string.feed_verse_actions)
    val spoken = stringResource(R.string.feed_verse_cd, spokenSentence(verse.text), reference, translationName)
    // pointerInput only restarts per verse, so read the latest values through these.
    val isLiked by rememberUpdatedState(liked)
    val toggleLike by rememberUpdatedState(onToggleLike)
    val copy by rememberUpdatedState(onCopy)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(verse.ref) {
                detectTapGestures(
                    onDoubleTap = {
                        val liking = !isLiked
                        haptics.performHapticFeedback(
                            if (liking) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                        )
                        toggleLike()
                        // The burst celebrates a like; unliking just empties the rail heart.
                        if (liking) {
                            scope.launch {
                                burst.snapTo(0f)
                                burst.animateTo(1f, tween(durationMillis = 650))
                            }
                        }
                    },
                    onLongPress = {
                        // Light tick: the snackbar carries the confirmation.
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        copy()
                    },
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = textStartInset, end = 84.dp, top = 72.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            ActionsPill(onClick = onActions)
            Spacer(Modifier.height(16.dp))
            // One TalkBack item for verse + reference, carrying every page action.
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .clearAndSetSemantics {
                        contentDescription = spoken
                        customActions = listOf(
                            CustomAccessibilityAction(likeLabel) { toggleLike(); true },
                            CustomAccessibilityAction(bookmarkLabel) { onToggleBookmark(); true },
                            CustomAccessibilityAction(noteLabel) { onNote(); true },
                            CustomAccessibilityAction(copyLabel) { copy(); true },
                            CustomAccessibilityAction(shareLabel) { onShare(); true },
                            CustomAccessibilityAction(actionsLabel) { onActions(); true },
                        )
                    },
            ) {
            BasicText(
                text = text,
                // Long verses (e.g. Esther 8:9) shrink to fit instead of clipping.
                autoSize = TextAutoSize.StepBased(
                    minFontSize = scriptureSp(14.sp),
                    maxFontSize = scriptureSp(26.sp),
                    stepSize = 1.sp,
                ),
                style = ScriptureTextStyle.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 1.45.em,
                ),
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = reference,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }
        }

        RightRail(
            liked = liked,
            hasNote = hasNote,
            onToggleLike = {
                haptics.performHapticFeedback(if (liked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                onToggleLike()
            },
            onNote = onNote,
            onShare = onShare,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
        )

        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = BibleScrollTheme.extendedColors.like,
            modifier = Modifier
                .align(Alignment.Center)
                .size(120.dp)
                .graphicsLayer {
                    val p = burst.value
                    alpha = if (p <= 0f || p >= 1f) 0f else 1f - p * p
                    scaleX = 0.7f + 0.5f * p
                    scaleY = scaleX
                },
        )
    }
}

/** Verse text ending in punctuation, so TalkBack pauses before the reference without reading "..". */
internal fun spokenSentence(text: String): String {
    val trimmed = text.trim()
    return if (trimmed.lastOrNull()?.isLetterOrDigit() == true) "$trimmed." else trimmed
}

@Composable
private fun RightRail(
    liked: Boolean,
    hasNote: Boolean,
    onToggleLike: () -> Unit,
    onNote: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RailButton(
            icon = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            label = stringResource(if (liked) R.string.feed_unlike else R.string.feed_like),
            tint = if (liked) BibleScrollTheme.extendedColors.like else MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onToggleLike,
        )
        BadgedBox(
            badge = {
                if (hasNote) {
                    Badge(
                        containerColor = BibleScrollTheme.extendedColors.like,
                        modifier = Modifier.offset(x = (-10).dp, y = 10.dp),
                    )
                }
            },
        ) {
            RailButton(
                icon = if (hasNote) Icons.AutoMirrored.Filled.Notes else Icons.Outlined.EditNote,
                label = stringResource(if (hasNote) R.string.feed_note_edit else R.string.feed_note),
                tint = if (hasNote) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onNote,
            )
        }
        RailButton(Icons.Outlined.Share, stringResource(R.string.feed_share), onClick = onShare)
    }
}

@Composable
private fun RailButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(30.dp))
    }
}

/** B7 entry point, above the verse. */
@Composable
private fun ActionsPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Icon(Icons.Outlined.MoreHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.feed_verse_actions), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun VersePagePreview() {
    val text = "Jesus said to him, “I am the way, the truth, and the life. No one comes to the Father, except through me."
    BibleScrollTheme {
        Surface {
            VersePage(
                verse = Verse("WEB", VerseRef(43, 14, 6), 26_652, text, listOf(19 until text.length)),
                reference = "John 14:6",
                translationName = "World English Bible",
                liked = true,
                bookmarked = false,
                hasNote = true,
                onToggleLike = {}, onToggleBookmark = {}, onNote = {}, onShare = {}, onCopy = {}, onActions = {},
            )
        }
    }
}
