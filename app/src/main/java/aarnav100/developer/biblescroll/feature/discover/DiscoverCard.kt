package aarnav100.developer.biblescroll.feature.discover

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.core.content.model.Language
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.feature.bible.redLetterText
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import aarnav100.developer.biblescroll.ui.theme.Gold
import aarnav100.developer.biblescroll.ui.theme.GoldContainer
import aarnav100.developer.biblescroll.ui.theme.GoldDarkContainer
import aarnav100.developer.biblescroll.ui.theme.GoldLight
import aarnav100.developer.biblescroll.ui.theme.Inter
import aarnav100.developer.biblescroll.ui.theme.Literata
import aarnav100.developer.biblescroll.ui.theme.scriptureSp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
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
import java.util.Locale

/** What the card's buttons do; the screen wires them to the shared feed actions. */
class DiscoverCardActions(
    val onOpen: (DiscoverCardUi) -> Unit,
    val onReadInContext: (DiscoverCardUi) -> Unit,
    val onLike: (DiscoverCardUi) -> Unit,
    val onNote: (DiscoverCardUi) -> Unit,
    val onBookmark: (DiscoverCardUi) -> Unit,
    val onCopy: (DiscoverCardUi) -> Unit,
    val onShareImage: (DiscoverCardUi) -> Unit,
    /** A related-verse pill: opens that verse in the Bible feed. */
    val onRelated: (VerseRef) -> Unit,
)

/**
 * The card's monochrome palette: black, white and grays only, picked from the app's light or dark background. The one
 * colour on the card is the "Did you know?" panel (the app's Gold tokens); Words of Jesus stay red and likes keep the
 * like colour (project conventions).
 */
private data class Mono(
    val card: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    val fill: Color,
    val factBg: Color,
    val factInk: Color,
)

@Composable
private fun mono(): Mono {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) {
        Mono(
            card = Color(0xFF141414), ink = Color(0xFFF2F2F2), muted = Color(0xFFB0B0B0), faint = Color(0xFF8A8A8A),
            line = Color(0xFF2E2E2E), fill = Color(0xFF222222), factBg = GoldDarkContainer.copy(alpha = 0.55f), factInk = GoldLight,
        )
    } else {
        Mono(
            card = Color.White, ink = Color(0xFF111111), muted = Color(0xFF555555), faint = Color(0xFF6E6E6E),
            line = Color(0xFFE3E3E3), fill = Color(0xFFF2F2F2), factBg = GoldContainer.copy(alpha = 0.45f), factInk = Gold,
        )
    }
}

/**
 * One D1 card (docs/spec.md D1). Every block has a fixed height and its text shrinks to fit (auto-size, then "…"), so
 * the card never scrolls, whatever the font scale; only the verse slot takes the space that's left. The body isn't
 * clickable: the "Read in context" button opens the study (with Hide AI studies on: the verse in context).
 * [ui] null = still loading.
 */
@Composable
fun DiscoverCard(ui: DiscoverCardUi?, actions: DiscoverCardActions, modifier: Modifier = Modifier) {
    val m = mono()
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = m.card,
        contentColor = m.ink,
        border = BorderStroke(1.dp, m.line),
        shadowElevation = 8.dp,
        modifier = modifier,
    ) {
        if (ui == null) return@Surface
        val open = { if (ui.aiHidden) actions.onReadInContext(ui) else actions.onOpen(ui) }
        Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
            CardBody(ui, m, Modifier.weight(1f))
            Spacer(Modifier.height(12.dp))
            RelatedVerses(ui, m, actions)
            ReadInContextButton(m, open)
            Spacer(Modifier.height(8.dp))
            ActionBar(ui, m, actions)
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

@Composable
private fun CardBody(ui: DiscoverCardUi, m: Mono, modifier: Modifier) {
    val locale = Locale.getDefault()
    val caps = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.em)
    Column(modifier.fillMaxWidth()) {
        // Theme label and translation tag.
        Row(Modifier.fillMaxWidth().height(HEADER_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
            FitText(
                ui.theme.uppercase(locale), caps.copy(color = m.muted), min = 9.sp, max = 11.sp, maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.border(0.8.dp, m.line, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                FitText(ui.translationId.uppercase(locale), caps.copy(color = m.muted), min = 8.sp, max = 10.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(10.dp))
        FitText(
            ui.reference,
            TextStyle(fontFamily = Literata, fontWeight = FontWeight.Medium, lineHeight = 1.2.em, color = m.ink),
            min = 16.sp, max = 24.sp, maxLines = 1,
            modifier = Modifier.fillMaxWidth().height(REFERENCE_HEIGHT).semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))

        // The verse beside a thin ink bar: takes the space the fixed blocks leave; Words of Jesus in red.
        val size = DiscoverCardRules.verseSize(ui.verse?.text.orEmpty())
        val jesus = BibleScrollTheme.extendedColors.wordsOfJesus
        val verseText = remember(ui.verse, jesus) { ui.verse?.redLetterText(jesus) ?: AnnotatedString("") }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.width(2.dp).fillMaxHeight().padding(vertical = 4.dp).clip(CircleShape).background(m.ink))
            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                FitText(
                    verseText,
                    TextStyle(fontFamily = Literata, fontStyle = FontStyle.Italic, lineHeight = size.lineHeight.em, color = m.ink),
                    min = 12.sp, max = scriptureSp(size.fontSp.sp), maxLines = DiscoverCardRules.VERSE_MAX_LINES,
                )
            }
        }

        val sections = DiscoverCardRules.sections(ui.hook, ui.fact, ui.aiHidden, hasStudyData = ui.dataLine != null)
        sections.hook?.let { hook ->
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth().height(MEANING_HEIGHT)) {
                FitText(
                    stringResource(R.string.discover_meaning_label).uppercase(locale), caps.copy(color = m.faint),
                    min = 9.sp, max = 11.sp, maxLines = 1, modifier = Modifier.height(16.dp),
                )
                Spacer(Modifier.height(4.dp))
                FitText(
                    hook, TextStyle(fontFamily = Literata, lineHeight = 1.4.em, color = m.ink),
                    min = 11.sp, max = 14.sp, maxLines = 2, modifier = Modifier.weight(1f),
                )
            }
        }
        if (sections.showDataLine) ui.dataLine?.let { line ->
            Spacer(Modifier.height(14.dp))
            FitText(
                stringResource(
                    R.string.two_parts,
                    stringResource(R.string.discover_data_language, stringResource(line.language.nameRes)),
                    pluralStringResource(R.plurals.discover_data_cross_refs, line.crossReferences, line.crossReferences),
                ),
                TextStyle(fontFamily = Inter, lineHeight = 1.4.em, color = m.muted),
                min = 10.sp, max = 12.sp, maxLines = 2,
                modifier = Modifier.fillMaxWidth().height(DATA_LINE_HEIGHT),
            )
        }
        sections.fact?.let { fact ->
            Spacer(Modifier.height(12.dp))
            // The one coloured block on the card.
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(FACT_HEIGHT)
                    .clip(RoundedCornerShape(16.dp))
                    .background(m.factBg)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(Modifier.height(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = m.factInk, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    FitText(
                        stringResource(R.string.study_did_you_know).uppercase(locale), caps.copy(color = m.factInk),
                        min = 9.sp, max = 11.sp, maxLines = 1,
                    )
                }
                Spacer(Modifier.height(4.dp))
                FitText(
                    fact, TextStyle(fontFamily = Inter, lineHeight = 1.4.em, color = m.ink),
                    min = 10.sp, max = 13.sp, maxLines = 2, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Up to [DiscoverViewModel.RELATED_MAX] cross references (source data, not AI) as gray outlined pills on one line. */
@Composable
private fun RelatedVerses(ui: DiscoverCardUi, m: Mono, actions: DiscoverCardActions) {
    if (ui.related.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().height(RELATED_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ui.related.forEach { verse ->
            val cd = stringResource(R.string.discover_open_in_context_cd, verse.label)
            Surface(
                onClick = { actions.onRelated(verse.start) },
                shape = CircleShape,
                color = m.fill,
                contentColor = m.ink,
                modifier = Modifier.weight(1f, fill = false).fillMaxHeight().semantics { contentDescription = cd },
            ) {
                Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    FitText(
                        verse.label, TextStyle(fontFamily = Literata, fontWeight = FontWeight.Medium, color = m.ink),
                        min = 10.sp, max = 12.sp, maxLines = 1,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

/** The only way into the card: a filled ink pill. */
@Composable
private fun ReadInContextButton(m: Mono, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = m.ink,
        contentColor = m.card,
        modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            FitText(
                stringResource(R.string.study_read_in_context),
                TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, color = m.card),
                min = 11.sp, max = 15.sp, maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

private val Language.nameRes: Int
    get() = when (this) {
        Language.HEBREW -> R.string.discover_lang_hebrew
        Language.ARAMAIC -> R.string.discover_lang_aramaic
        Language.GREEK -> R.string.discover_lang_greek
    }

/** The five actions under a hairline, in a fixed-height row. */
@Composable
private fun ActionBar(ui: DiscoverCardUi, m: Mono, actions: DiscoverCardActions) {
    val like = BibleScrollTheme.extendedColors.like
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(m.line))
        Row(
            Modifier.fillMaxWidth().height(ACTION_BAR_HEIGHT),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionButton(
                icon = if (ui.liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                label = stringResource(if (ui.liked) R.string.feed_unlike else R.string.feed_like),
                tint = if (ui.liked) like else m.muted,
                state = stringResource(if (ui.liked) R.string.discover_state_liked else R.string.discover_state_not_liked),
            ) { actions.onLike(ui) }
            ActionButton(
                // Same symbol and states as the Bible page's note button (VersePage), dot in the like colour.
                icon = if (ui.hasNote) Icons.AutoMirrored.Filled.Notes else Icons.Outlined.EditNote,
                label = stringResource(if (ui.hasNote) R.string.feed_note_edit else R.string.feed_note_add),
                tint = if (ui.hasNote) m.ink else m.muted,
                dot = if (ui.hasNote) like else null,
            ) { actions.onNote(ui) }
            ActionButton(
                icon = if (ui.bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                label = stringResource(if (ui.bookmarked) R.string.feed_unbookmark else R.string.feed_bookmark),
                tint = if (ui.bookmarked) m.ink else m.muted,
            ) { actions.onBookmark(ui) }
            ActionButton(Icons.Outlined.ContentCopy, stringResource(R.string.discover_copy_cd), m.muted) { actions.onCopy(ui) }
            ActionButton(Icons.Outlined.IosShare, stringResource(R.string.actions_share_image), m.muted) { actions.onShareImage(ui) }
        }
    }
}

private val HEADER_HEIGHT = 24.dp
private val REFERENCE_HEIGHT = 32.dp
private val MEANING_HEIGHT = 58.dp
private val DATA_LINE_HEIGHT = 36.dp
private val FACT_HEIGHT = 82.dp
private val RELATED_HEIGHT = 32.dp
private val BUTTON_HEIGHT = 46.dp
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
    // IconButton is a 48dp touch target.
    IconButton(onClick = onClick, modifier = if (state != null) Modifier.semantics { stateDescription = state } else Modifier) {
        Box {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
            if (dot != null) {
                Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp).size(7.dp).clip(CircleShape).background(dot))
            }
        }
    }
}
