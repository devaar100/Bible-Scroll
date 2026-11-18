package aarnav100.developer.biblescroll.feature.bible

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import aarnav100.developer.biblescroll.ui.components.TestTags
import aarnav100.developer.biblescroll.ui.components.automationTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** [heart = liked filter] [translation] ... [bookmark] [Book N ▾] */
@Composable
fun FeedTopBar(
    likedFilterOn: Boolean,
    onToggleLikedFilter: () -> Unit,
    /** Null when there's no verse on screen (e.g. empty liked feed); hides the button. */
    bookmarked: Boolean?,
    onToggleBookmark: () -> Unit,
    selectedTranslationId: String,
    onOpenTranslationPicker: () -> Unit,
    chapterLabel: String,
    onOpenBookPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val like = BibleScrollTheme.extendedColors.like
        CircleButton(
            onClick = onToggleLikedFilter,
            label = stringResource(if (likedFilterOn) R.string.feed_liked_filter_off else R.string.feed_liked_filter_on),
            selected = likedFilterOn,
        ) {
            Icon(
                imageVector = if (likedFilterOn) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                tint = if (likedFilterOn) like else LocalContentColor.current,
                modifier = Modifier.size(20.dp),
            )
        }

        val pillDescription = stringResource(R.string.feed_translation_cd, selectedTranslationId)
        Pill(
            onClick = onOpenTranslationPicker,
            modifier = Modifier.semantics { contentDescription = pillDescription },
        ) {
            Text(selectedTranslationId, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.weight(1f))

        if (bookmarked != null) {
            CircleButton(
                onClick = onToggleBookmark,
                label = stringResource(if (bookmarked) R.string.feed_unbookmark else R.string.feed_bookmark),
                selected = bookmarked,
            ) {
                Icon(
                    imageVector = if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        val chapterDescription = stringResource(R.string.feed_book_chapter_cd, chapterLabel)
        if (chapterLabel.isNotEmpty()) Pill(
            onClick = onOpenBookPicker,
            modifier = Modifier
                .semantics { contentDescription = chapterDescription }
                .automationTag(TestTags.BOOK_PICKER_BUTTON),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(chapterLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun CircleButton(
    onClick: () -> Unit,
    label: String,
    selected: Boolean,
    content: @Composable () -> Unit,
) {
    // 40dp circle to look at, 48dp to touch (and for TalkBack's focus box).
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clickable(
                interactionSource = null,
                indication = ripple(bounded = false, radius = 20.dp),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = label
                this.selected = selected
            },
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { content() }
        }
    }
}

@Composable
private fun Pill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) { content() }
    }
}
