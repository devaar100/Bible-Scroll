package aarnav100.developer.biblescroll.feature.home

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.votd.VotdVerse
import aarnav100.developer.biblescroll.feature.bible.redLetterText
import aarnav100.developer.biblescroll.feature.bible.spokenSentence
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import aarnav100.developer.biblescroll.ui.theme.ScriptureTextStyle
import aarnav100.developer.biblescroll.ui.theme.scriptureSp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * H1 Verse of the Day card. Scripture in Literata at the reader's font size (S4), red letters as in the feed.
 * TalkBack reads it as one item ("Verse of the Day: <verse> <reference>, <translation>") with a custom action
 * per button. The actions wrap onto more lines at large font sizes and in longer languages.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    val text = remember(votd.verse, jesusColor) { votd.verse.redLetterText(jesusColor) }
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

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
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
                // Text sits 12 dp further in, level with the buttons' own inner padding, so icons and text line up.
                .padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text,
                style = ScriptureTextStyle.copy(
                    fontSize = scriptureSp(20.sp),
                    lineHeight = 1.45.em,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.two_parts, votd.reference, votd.verse.translationId),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            if (fallback != null) {
                Text(
                    fallback,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            FlowRow(Modifier.fillMaxWidth()) {
                CardAction(Icons.AutoMirrored.Outlined.MenuBook, readLabel, onReadInContext)
                CardAction(Icons.Outlined.School, studyLabel, onStudy)
                CardAction(Icons.Outlined.Image, shareLabel, onShareImage)
                CardAction(Icons.Outlined.Widgets, widgetLabel, onSetWidget)
                CardAction(Icons.Outlined.ContentCopy, copyLabel, onCopy)
            }
        }
    }
}

@Composable
private fun CardAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    // TextButton keeps a 48 dp touch target.
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
