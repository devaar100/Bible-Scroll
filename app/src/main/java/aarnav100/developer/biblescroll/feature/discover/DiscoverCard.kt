package aarnav100.developer.biblescroll.feature.discover

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.core.content.model.Language
import aarnav100.developer.biblescroll.feature.bible.redLetterText
import aarnav100.developer.biblescroll.ui.theme.AppShapes
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import aarnav100.developer.biblescroll.ui.theme.Inter
import aarnav100.developer.biblescroll.ui.theme.Literata
import aarnav100.developer.biblescroll.ui.theme.Spacing
import aarnav100.developer.biblescroll.ui.theme.scriptureSp
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.outlined.CloudOff
import java.util.Locale

/** What the card's gestures and buttons do; the screen wires them to the shared feed actions. */
class DiscoverCardActions(
    /** Tap on the card: the deep study (D2). */
    val onOpen: (DiscoverCardUi) -> Unit,
    /** Tap on the card with Hide AI studies on: the verse in the Bible feed. */
    val onReadInContext: (DiscoverCardUi) -> Unit,
    /** Heart button, or double-tap on the card. */
    val onLike: (DiscoverCardUi) -> Unit,
    val onNote: (DiscoverCardUi) -> Unit,
    val onBookmark: (DiscoverCardUi) -> Unit,
    /** Long-press on the card, or Copy in the share sheet. */
    val onCopy: (DiscoverCardUi) -> Unit,
    /** Share button: opens the share sheet (image, text, copy). */
    val onShare: (DiscoverCardUi) -> Unit,
    /** A related-verse pill: opens that verse in the Bible feed. */
    val onRelated: (VerseRef) -> Unit,
)

/**
 * One D1 card (docs/spec.md D1, docs/ui-direction.md). A tonal card in the app's neutrals: no border, shadow or colour
 * panels; the only colours are the words of Jesus and a liked heart. Gestures match the Bible page: tap opens the study
 * (with Hide AI studies on: the verse in context), double-tap likes, long-press copies. Four icons: like, note (dot when
 * a note exists, as on the Bible page), bookmark, share. Up to two related-verse pills above them.
 *
 * Layout: the verse sits right under the reference (with a large hanging quotation mark), so it's in the same place on
 * every card; the study text is pinned to the bottom at fixed sizes (2 lines, then "…"). Only the verse shrinks to fit
 * the space left, so the card never scrolls, whatever the font scale. While the study's book downloads, placeholders hold
 * its space. [ui] null = still loading.
 */
@Composable
fun DiscoverCard(ui: DiscoverCardUi?, actions: DiscoverCardActions, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(ui)
    val openLabel = stringResource(if (ui?.aiHidden == true) R.string.study_read_in_context else R.string.discover_open_study)
    val copyLabel = stringResource(R.string.discover_copy_cd)
    Surface(
        shape = AppShapes.large,
        color = colors.surfaceContainerLow,
        contentColor = colors.onSurface,
        modifier = modifier
            .pointerInput(actions) {
                detectTapGestures(
                    onTap = { current?.let { if (it.aiHidden) actions.onReadInContext(it) else actions.onOpen(it) } },
                    onDoubleTap = {
                        current?.let {
                            haptics.performHapticFeedback(if (it.liked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                            actions.onLike(it)
                        }
                    },
                    onLongPress = {
                        current?.let {
                            // Light tick: the snackbar carries the confirmation (same as the Bible page).
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            actions.onCopy(it)
                        }
                    },
                )
            }
            .semantics {
                if (ui != null) {
                    onClick(openLabel) { if (ui.aiHidden) actions.onReadInContext(ui) else actions.onOpen(ui); true }
                    customActions = listOf(CustomAccessibilityAction(copyLabel) { actions.onCopy(ui); true })
                }
            },
    ) {
        if (ui == null) return@Surface
        Column(Modifier.fillMaxSize().padding(start = Spacing.card, end = Spacing.card, top = Spacing.card)) {
            CardBody(ui, Modifier.weight(1f))
            RelatedVerses(ui, actions)
            Spacer(Modifier.height(Spacing.xs))
            ActionBar(ui, actions)
        }
    }
}

/** Text that keeps to its fixed box: steps down from [max] to [min], then ends in "…". */
@Composable
private fun FitText(
    text: AnnotatedString,
    style: TextStyle,
    min: TextUnit,
    max: TextUnit,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text,
        modifier = modifier,
        style = style,
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
        autoSize = TextAutoSize.StepBased(minFontSize = min, maxFontSize = max, stepSize = 0.5.sp),
    )
}

@Composable
private fun FitText(text: String, style: TextStyle, min: TextUnit, max: TextUnit, maxLines: Int, modifier: Modifier = Modifier) =
    FitText(AnnotatedString(text), style, min, max, maxLines, modifier)

/** AppText.sectionLabel's shape for FitText (BasicText needs the colour in the style). */
private val labelStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.em)

@Composable
private fun CardBody(ui: DiscoverCardUi, modifier: Modifier) {
    val locale = Locale.getDefault()
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth()) {
        // Theme label and translation, both as plain section labels.
        Row(Modifier.fillMaxWidth().height(HEADER_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
            FitText(
                ui.theme.uppercase(locale), labelStyle.copy(color = muted), min = 9.sp, max = 12.sp, maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.s))
            FitText(ui.translationId.uppercase(locale), labelStyle.copy(color = muted), min = 9.sp, max = 12.sp, maxLines = 1)
        }
        Spacer(Modifier.height(Spacing.m))
        FitText(
            ui.reference,
            TextStyle(fontFamily = Literata, fontWeight = FontWeight.SemiBold, lineHeight = 1.2.em, color = ink),
            min = 18.sp, max = 28.sp, maxLines = 1,
            modifier = Modifier.fillMaxWidth().height(REFERENCE_HEIGHT).semantics { heading() },
        )
        // A large, faint opening quotation mark: the card's one ornament, in the accent at low strength (decorative).
        // The glyph sits high in its line box: lay it out at full size (unbounded) and let the box clip the empty bottom.
        Box(Modifier.fillMaxWidth().height(QUOTE_HEIGHT).clipToBounds().clearAndSetSemantics { }) {
            Text(
                "\u201C",
                style = TextStyle(fontFamily = Literata, fontSize = 80.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.wrapContentHeight(Alignment.Top, unbounded = true).offset(x = (-4).dp, y = (-6).dp),
            )
        }

        // The verse, top-aligned under the reference; it takes the space the bottom blocks leave. Words of Jesus in red.
        val size = DiscoverCardRules.verseSize(ui.verse?.text.orEmpty())
        val jesus = BibleScrollTheme.extendedColors.wordsOfJesus
        val verseText = remember(ui.verse, jesus) { ui.verse?.redLetterText(jesus)?.withoutOpeningQuote() ?: AnnotatedString("") }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopStart) {
            FitText(
                verseText,
                TextStyle(fontFamily = Literata, fontStyle = FontStyle.Italic, lineHeight = size.lineHeight.em, color = ink),
                min = 12.sp, max = scriptureSp(size.fontSp.sp), maxLines = DiscoverCardRules.VERSE_MAX_LINES,
            )
        }

        when (ui.studySlot) {
            StudySlot.DOWNLOADING -> { StudyPlaceholder(); return@Column }
            StudySlot.OFFLINE -> { StudyGhost(stringResource(R.string.discover_study_offline)); return@Column }
            StudySlot.NOT_DOWNLOADED -> { StudyGhost(stringResource(R.string.discover_study_tap)); return@Column }
            StudySlot.SHOWN -> Unit
        }
        val sections = DiscoverCardRules.sections(ui.hook, ui.fact, ui.aiHidden, hasStudyData = ui.dataLine != null)
        sections.hook?.let { hook ->
            Spacer(Modifier.height(Spacing.l))
            SectionLabel(stringResource(R.string.discover_meaning_label))
            Spacer(Modifier.height(Spacing.xs))
            Text(hook, style = MeaningStyle, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (sections.showDataLine) ui.dataLine?.let { line ->
            Spacer(Modifier.height(Spacing.l))
            Text(
                stringResource(
                    R.string.two_parts,
                    stringResource(R.string.discover_data_language, stringResource(line.language.nameRes)),
                    pluralStringResource(R.plurals.discover_data_cross_refs, line.crossReferences, line.crossReferences),
                ),
                style = FactStyle, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        sections.fact?.let { fact ->
            Spacer(Modifier.height(Spacing.m))
            FactInset {
                Text(fact, style = FactStyle, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The verse already has the hanging mark above it, so drop the text's own opening quote (keeps red-letter spans). */
private fun AnnotatedString.withoutOpeningQuote(): AnnotatedString {
    val start = text.indexOfFirst { !it.isWhitespace() }
    return if (start >= 0 && text[start] in "\u201C\"") subSequence(start + 1, length) else this
}

/** Fixed sizes for the study text: every card looks the same; longer text ends in "…" (the full study is a tap away). */
private val MeaningStyle = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp)
private val FactStyle = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp)

@Composable
private fun SectionLabel(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text.uppercase(Locale.getDefault()),
        style = labelStyle.copy(fontSize = 12.sp),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** "Did you know?": a slightly darker neutral inset with a lightbulb; the label takes the accent. */
@Composable
private fun FactInset(content: @Composable () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AppShapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            SectionLabel(stringResource(R.string.study_did_you_know), color = accent)
        }
        Spacer(Modifier.height(Spacing.xs))
        content()
    }
}

/** Holds the study's space while its book downloads, so the card doesn't jump when the text arrives. */
@Composable
private fun StudyPlaceholder() {
    val loading = stringResource(R.string.discover_study_loading)
    val bar = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = loading }) {
        Spacer(Modifier.height(Spacing.l))
        SectionLabel(stringResource(R.string.discover_meaning_label))
        Spacer(Modifier.height(Spacing.s))
        PlaceholderBar(0.92f, bar)
        Spacer(Modifier.height(Spacing.s))
        PlaceholderBar(0.64f, bar)
        Spacer(Modifier.height(Spacing.m))
        FactInset {
            Column {
                PlaceholderBar(0.88f, MaterialTheme.colorScheme.surfaceContainer)
                Spacer(Modifier.height(Spacing.s))
                PlaceholderBar(0.55f, MaterialTheme.colorScheme.surfaceContainer)
            }
        }
    }
}

/**
 * The study isn't on the phone and isn't downloading: a faded outline of the study's sections (so the card keeps its
 * shape) and one line saying when it'll be there. The outline is decorative; TalkBack reads only the caption.
 */
@Composable
private fun StudyGhost(caption: String) {
    val bar = MaterialTheme.colorScheme.surfaceContainerHigh
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().alpha(0.45f).clearAndSetSemantics { }) {
            Spacer(Modifier.height(Spacing.l))
            SectionLabel(stringResource(R.string.discover_meaning_label))
            Spacer(Modifier.height(Spacing.s))
            PlaceholderBar(0.9f, bar)
            Spacer(Modifier.height(Spacing.m))
            Column(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawRoundRect(
                            color = line,
                            cornerRadius = CornerRadius(20.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                        )
                    }
                    .padding(horizontal = Spacing.l, vertical = Spacing.m),
            ) {
                SectionLabel(stringResource(R.string.study_did_you_know))
                Spacer(Modifier.height(Spacing.s))
                PlaceholderBar(0.75f, bar)
            }
        }
        Spacer(Modifier.height(Spacing.m))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlaceholderBar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth(fraction).height(10.dp).clip(CircleShape).background(color))
}

/** Up to [DiscoverViewModel.RELATED_MAX] cross references (source data, not AI) as hairline-outlined pills on one line. */
@Composable
private fun RelatedVerses(ui: DiscoverCardUi, actions: DiscoverCardActions) {
    if (ui.related.isEmpty() || ui.studyPending) return
    Spacer(Modifier.height(Spacing.m))
    Row(
        Modifier.fillMaxWidth().height(RELATED_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ui.related.forEach { verse ->
            val cd = stringResource(R.string.discover_open_in_context_cd, verse.label)
            // Hairline outline, no fill: reads as a link, not another box like "Did you know?".
            Surface(
                onClick = { actions.onRelated(verse.start) },
                shape = CircleShape,
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f, fill = false).fillMaxSize().semantics { contentDescription = cd },
            ) {
                Box(Modifier.padding(horizontal = Spacing.m), contentAlignment = Alignment.Center) {
                    FitText(
                        // A step below the 13-14 sp study text, so the pills read as small links.
                        verse.label, TextStyle(fontFamily = Literata, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant),
                        min = 9.sp, max = 11.5.sp, maxLines = 1,
                    )
                }
            }
        }
    }
}

private val Language.nameRes: Int
    get() = when (this) {
        Language.HEBREW -> R.string.discover_lang_hebrew
        Language.ARAMAIC -> R.string.discover_lang_aramaic
        Language.GREEK -> R.string.discover_lang_greek
    }

/** Like, note, bookmark, share: the Bible page's icons and states, spread across a fixed-height row. */
@Composable
private fun ActionBar(ui: DiscoverCardUi, actions: DiscoverCardActions) {
    val like = BibleScrollTheme.extendedColors.like
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().height(ACTION_BAR_HEIGHT),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(
            icon = if (ui.liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            label = stringResource(if (ui.liked) R.string.feed_unlike else R.string.feed_like),
            tint = if (ui.liked) like else muted,
            state = stringResource(if (ui.liked) R.string.discover_state_liked else R.string.discover_state_not_liked),
        ) { actions.onLike(ui) }
        ActionButton(
            // Same symbol, states and dot as the Bible page's note button (VersePage).
            icon = if (ui.hasNote) Icons.AutoMirrored.Filled.Notes else Icons.Outlined.EditNote,
            label = stringResource(if (ui.hasNote) R.string.feed_note_edit else R.string.feed_note_add),
            tint = if (ui.hasNote) MaterialTheme.colorScheme.primary else muted,
            dot = if (ui.hasNote) like else null,
        ) { actions.onNote(ui) }
        ActionButton(
            icon = if (ui.bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            label = stringResource(if (ui.bookmarked) R.string.feed_unbookmark else R.string.feed_bookmark),
            tint = if (ui.bookmarked) MaterialTheme.colorScheme.primary else muted,
        ) { actions.onBookmark(ui) }
        ActionButton(Icons.Outlined.Share, stringResource(R.string.feed_share), muted) { actions.onShare(ui) }
    }
}

private val HEADER_HEIGHT = 20.dp
private val REFERENCE_HEIGHT = 36.dp
private val QUOTE_HEIGHT = 44.dp
private val RELATED_HEIGHT = 30.dp
private val ACTION_BAR_HEIGHT = 56.dp

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    state: String? = null,
    dot: Color? = null,
    onClick: () -> Unit,
) {
    // IconButton is a 48dp touch target and takes the tap, so the card's own gestures don't fire.
    IconButton(onClick = onClick, modifier = if (state != null) Modifier.semantics { stateDescription = state } else Modifier) {
        Box {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
            if (dot != null) {
                Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp).size(7.dp).clip(CircleShape).background(dot))
            }
        }
    }
}
