package aarnav100.developer.biblescroll.feature.home

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.votd.VotdVerse
import aarnav100.developer.biblescroll.feature.bible.redLetterText
import aarnav100.developer.biblescroll.feature.bible.spokenSentence
import aarnav100.developer.biblescroll.feature.study.withoutOpeningQuote
import aarnav100.developer.biblescroll.ui.theme.AppShapes
import aarnav100.developer.biblescroll.ui.theme.AppText
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import aarnav100.developer.biblescroll.ui.theme.Literata
import aarnav100.developer.biblescroll.ui.theme.ScriptureTextStyle
import aarnav100.developer.biblescroll.ui.theme.Spacing
import aarnav100.developer.biblescroll.ui.theme.scriptureSp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * H1 Verse of the Day (docs/ui-direction.md, Home): the Discover card's look (label, faint accent quote mark, verse in
 * Literata italic with red letters, reference), then "Read in context →" and three icons: Study, Share as image, and ⋯
 * (set as widget verse, copy). TalkBack reads it as one item ("Verse of the Day: <verse> <reference>, <translation>")
 * with a custom action for every action.
 */
@Composable
fun VerseOfTheDayCard(
    votd: VotdVerse,
    onReadInContext: () -> Unit,
    onShareImage: () -> Unit,
    onSetWidget: () -> Unit,
    onCopy: () -> Unit,
    onStudy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jesusColor = BibleScrollTheme.extendedColors.wordsOfJesus
    val text = remember(votd.verse, jesusColor) { votd.verse.redLetterText(jesusColor).withoutOpeningQuote() }
    val title = stringResource(R.string.votd_title)
    val readLabel = stringResource(R.string.votd_read_in_context)
    val shareLabel = stringResource(R.string.actions_share_image)
    val widgetLabel = stringResource(R.string.actions_widget_verse)
    val copyLabel = stringResource(R.string.feed_copy)
    val studyLabel = stringResource(R.string.actions_study)
    val fallback = if (votd.fellBack) {
        stringResource(R.string.votd_fallback, votd.requestedTranslationId, votd.verse.translationId)
    } else {
        null
    }
    val spokenVerse = stringResource(R.string.feed_verse_cd, spokenSentence(votd.verse.text), votd.reference, votd.translationName)
    val spoken = stringResource(R.string.votd_cd, title, listOfNotNull(spokenVerse, fallback).joinToString(" "))
    var menu by rememberSaveable { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(shape = AppShapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier
                .clearAndSetSemantics {
                    contentDescription = spoken
                    customActions = listOf(
                        CustomAccessibilityAction(readLabel) { onReadInContext(); true },
                        CustomAccessibilityAction(studyLabel) { onStudy(); true },
                        CustomAccessibilityAction(shareLabel) { onShareImage(); true },
                        CustomAccessibilityAction(widgetLabel) { onSetWidget(); true },
                        CustomAccessibilityAction(copyLabel) { onCopy(); true },
                    )
                }
                .padding(start = Spacing.card, end = Spacing.s, top = Spacing.card, bottom = Spacing.xs),
        ) {
            Text(title.uppercase(Locale.getDefault()), style = AppText.sectionLabel, color = muted)
            // The faint quote mark (accent at low strength), as on Discover and the study page.
            Box(Modifier.fillMaxWidth().height(40.dp).clipToBounds()) {
                Text(
                    "“",
                    style = TextStyle(fontFamily = Literata, fontSize = 72.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.wrapContentHeight(Alignment.Top, unbounded = true).offset(x = (-4).dp, y = (-4).dp),
                )
            }
            Text(
                text,
                style = ScriptureTextStyle.copy(
                    fontStyle = FontStyle.Italic,
                    fontSize = scriptureSp(20.sp),
                    lineHeight = 1.45.em,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.padding(end = Spacing.m),
            )
            Spacer(Modifier.height(Spacing.m))
            Text(
                stringResource(R.string.two_parts, votd.reference, votd.verse.translationId),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Literata, fontWeight = FontWeight.SemiBold),
            )
            if (fallback != null) {
                Text(fallback, style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.height(Spacing.xs))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // The link takes whatever the icons leave (weighted children measure last), then the icons sit at the end.
                Box(Modifier.weight(1f)) {
                    TextButton(onClick = onReadInContext, contentPadding = PaddingValues(start = 0.dp, end = Spacing.s)) {
                        Text(readLabel, style = AppText.button)
                        Spacer(Modifier.width(Spacing.xs))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
                CardIcon(Icons.Outlined.School, studyLabel, onStudy)
                CardIcon(Icons.Outlined.Share, shareLabel, onShareImage)
                Box {
                    CardIcon(Icons.Outlined.MoreHoriz, stringResource(R.string.home_votd_more)) { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(widgetLabel) },
                            leadingIcon = { Icon(Icons.Outlined.Widgets, contentDescription = null) },
                            onClick = { menu = false; onSetWidget() },
                        )
                        DropdownMenuItem(
                            text = { Text(copyLabel) },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                            onClick = { menu = false; onCopy() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    // IconButton keeps a 48 dp touch target.
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
