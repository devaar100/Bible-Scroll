package aarnav100.developer.biblescroll.feature.discover

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.core.content.model.Language
import aarnav100.developer.biblescroll.feature.bible.redLetterText
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import aarnav100.developer.biblescroll.ui.theme.Inter
import aarnav100.developer.biblescroll.ui.theme.Literata
import aarnav100.developer.biblescroll.ui.theme.scriptureSp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
)

/**
 * One D1 card (docs/spec.md D1): theme label, verse, rule, reference row, hook, "Did you know?", action row. Tapping
 * the body opens the study (with Hide AI studies on: the verse in context). [ui] null = still loading.
 */
@Composable
fun DiscoverCard(ui: DiscoverCardUi?, actions: DiscoverCardActions, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palette = ui?.let { discoverPalette(it.theme, dark) }
    val shape = RoundedCornerShape(28.dp)
    Surface(
        shape = shape,
        color = palette?.card ?: MaterialTheme.colorScheme.surfaceContainerLow,
        border = palette?.let { BorderStroke(1.dp, it.border) },
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        if (ui == null || palette == null) return@Surface
        val openLabel = if (ui.aiHidden) {
            stringResource(R.string.discover_open_in_context_cd, ui.reference)
        } else {
            stringResource(R.string.discover_open_study_cd, ui.reference)
        }
        val open = { if (ui.aiHidden) actions.onReadInContext(ui) else actions.onOpen(ui) }
        Box(Modifier.fillMaxSize()) {
            DiscoverCardArt(ui.theme, palette.accent, Modifier.matchParentSize())
            Column(Modifier.fillMaxSize()) {
                // The body: one clickable surface, role Button. Its text (verse, reference, hook) stays readable to
                // TalkBack, and the click is announced as "Open study for Jeremiah 17:8".
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .clickable(onClickLabel = openLabel, role = Role.Button, onClick = open)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
                ) {
                    CardBody(ui, palette, actions)
                }
                HorizontalDivider(Modifier.padding(horizontal = 24.dp), thickness = 1.dp, color = palette.border)
                ActionRow(ui, palette, actions)
            }
        }
    }
}

@Composable
private fun CardBody(ui: DiscoverCardUi, palette: DiscoverPalette, actions: DiscoverCardActions) {
    val locale = Locale.getDefault()
    val caps = TextStyle(fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.14.em)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(ui.theme.icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(ui.theme.labelRes).uppercase(locale), style = caps, color = palette.accent)
    }
    Spacer(Modifier.height(26.dp))

    val text = ui.verse?.text.orEmpty()
    val size = DiscoverCardRules.verseSize(text)
    val jesus = BibleScrollTheme.extendedColors.wordsOfJesus
    val onSurface = MaterialTheme.colorScheme.onSurface
    // Red letters off makes wordsOfJesus the plain text colour: then the card's own text colour is used.
    val jesusOnCard = if (jesus == onSurface) palette.text else redOnCard(jesus, palette)
    val verseText = remember(ui.verse, jesusOnCard) { ui.verse?.redLetterText(jesusOnCard) ?: AnnotatedString("") }
    Box {
        Text(
            "“",
            style = TextStyle(fontFamily = Literata, fontSize = 110.sp, lineHeight = 110.sp, color = palette.accent.copy(alpha = 0.16f)),
            modifier = Modifier.offset(x = (-6).dp, y = (-38).dp).clearAndSetSemantics { },
        )
        Text(
            verseText,
            style = TextStyle(
                fontFamily = Literata,
                fontWeight = FontWeight.Normal,
                fontSize = scriptureSp(size.fontSp.sp),
                lineHeight = size.lineHeight.em,
                color = palette.text,
            ),
        )
    }
    Spacer(Modifier.height(20.dp))
    Box(Modifier.size(width = 36.dp, height = 2.dp).background(palette.accent.copy(alpha = 0.55f)))
    Spacer(Modifier.height(6.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.two_parts, ui.reference, ui.translationId).uppercase(locale),
            style = caps,
            color = palette.secondary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { actions.onReadInContext(ui) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(
                stringResource(R.string.discover_read_in_context),
                style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = palette.accent,
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
        }
    }

    val sections = DiscoverCardRules.sections(ui.hook, ui.fact, ui.aiHidden, hasStudyData = ui.dataLine != null)
    sections.hook?.let { hook ->
        Spacer(Modifier.height(10.dp))
        Text(
            hook,
            style = TextStyle(fontFamily = Literata, fontStyle = FontStyle.Italic, fontSize = 15.5.sp, lineHeight = 1.5.em, color = palette.text),
        )
    }
    if (sections.showDataLine) ui.dataLine?.let { line ->
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(
                R.string.two_parts,
                stringResource(R.string.discover_data_language, stringResource(line.language.nameRes)),
                pluralStringResource(R.plurals.discover_data_cross_refs, line.crossReferences, line.crossReferences),
            ),
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 1.5.em, color = palette.secondary),
        )
    }
    sections.fact?.let { fact ->
        Spacer(Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(CircleShape).background(palette.pill).padding(vertical = 4.dp, horizontal = 10.dp),
        ) {
            Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = palette.accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.study_did_you_know).uppercase(locale),
                style = TextStyle(fontFamily = Inter, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.12.em),
                color = palette.accent,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(fact, style = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 1.5.em, color = palette.text))
    }
}

/** The app's red letters, but a dark-mode red on dark cards and a light-mode red on light ones. */
private fun redOnCard(jesus: Color, palette: DiscoverPalette): Color {
    val cardIsDark = palette.card.luminance() < 0.5f
    val jesusIsLight = jesus.luminance() > 0.3f
    return when {
        cardIsDark && !jesusIsLight -> Color(0xFFF2A5AE)
        !cardIsDark && jesusIsLight -> Color(0xFFA4132E)
        else -> jesus
    }
}

private val Language.nameRes: Int
    get() = when (this) {
        Language.HEBREW -> R.string.discover_lang_hebrew
        Language.ARAMAIC -> R.string.discover_lang_aramaic
        Language.GREEK -> R.string.discover_lang_greek
    }

@Composable
private fun ActionRow(ui: DiscoverCardUi, palette: DiscoverPalette, actions: DiscoverCardActions) {
    val like = BibleScrollTheme.extendedColors.like
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(
            icon = if (ui.liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            label = stringResource(if (ui.liked) R.string.feed_unlike else R.string.feed_like),
            tint = if (ui.liked) like else palette.secondary,
            state = stringResource(if (ui.liked) R.string.discover_state_liked else R.string.discover_state_not_liked),
        ) { actions.onLike(ui) }
        ActionButton(
            icon = if (ui.hasNote) Icons.Filled.Edit else Icons.Outlined.Edit,
            label = stringResource(if (ui.hasNote) R.string.feed_note_edit else R.string.feed_note_add),
            tint = palette.secondary,
            dot = if (ui.hasNote) palette.accent else null,
        ) { actions.onNote(ui) }
        ActionButton(
            icon = if (ui.bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            label = stringResource(if (ui.bookmarked) R.string.feed_unbookmark else R.string.feed_bookmark),
            tint = if (ui.bookmarked) palette.accent else palette.secondary,
        ) { actions.onBookmark(ui) }
        ActionButton(Icons.Outlined.ContentCopy, stringResource(R.string.discover_copy_cd), palette.secondary) { actions.onCopy(ui) }
        ActionButton(Icons.Outlined.IosShare, stringResource(R.string.actions_share_image), palette.secondary) { actions.onShareImage(ui) }
    }
}

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
