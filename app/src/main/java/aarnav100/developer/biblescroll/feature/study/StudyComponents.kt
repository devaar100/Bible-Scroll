package aarnav100.developer.biblescroll.feature.study

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.core.content.model.Citation
import aarnav100.developer.biblescroll.core.content.model.Language
import aarnav100.developer.biblescroll.core.content.model.Study
import aarnav100.developer.biblescroll.core.content.model.Word
import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.feature.bible.redLetterText
import aarnav100.developer.biblescroll.ui.theme.AppShapes
import aarnav100.developer.biblescroll.ui.theme.AppText
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import aarnav100.developer.biblescroll.ui.theme.Literata
import aarnav100.developer.biblescroll.ui.theme.ScriptureTextStyle
import aarnav100.developer.biblescroll.ui.theme.Spacing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.util.Locale

/*
 * The shared D2/B8 study layout (docs/ui-direction.md, "Deep study"). Neutral sections with small labels; "Did you
 * know?" and "Apply it" as gray callouts; the accent only on what's interactive. Plain text only: study prose is
 * rendered as text, never as HTML or markdown.
 */

/** A cross reference ready to show: formatted label, verse text (first verse of a range), TSK keyword. */
@Immutable
data class CrossReferenceUi(val start: VerseRef, val label: String, val text: String?, val note: String?)

/**
 * The whole study, in reading order, ending with where it came from (AI-assisted line) and "Report a problem".
 * [showProse] false (Hide AI studies on, B8): only the data sections, which come from source datasets and not from AI
 * (original language, cross references, sources), with a note and [onShowStudies] to turn the setting off.
 */
@Composable
fun StudyBody(
    study: Study,
    crossReferences: List<CrossReferenceUi>,
    onOpenReference: (VerseRef) -> Unit,
    /** "Report a problem" (S7). Shown wherever a study is (D2, B8). */
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
    showProse: Boolean = true,
    onShowStudies: () -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        if (showProse) {
            StudyLanguageNote()
            StudyLead(stringResource(R.string.study_meaning), study.meaning)
            StudySection(stringResource(R.string.study_historical_context), study.historicalContext)
            StudyCallout(Icons.Outlined.Lightbulb, stringResource(R.string.study_did_you_know), study.didYouKnow)
            StudySection(stringResource(R.string.study_life_in_bible_times), study.lifeInBibleTimes)
            StudySection(stringResource(R.string.study_theology), study.theology)
            StudyCallout(Icons.Outlined.SelfImprovement, stringResource(R.string.study_apply_it), study.applyIt)
        } else {
            HiddenProseNote(onShowStudies)
        }
        OriginalLanguageSection(study.originalLanguage.language, study.originalLanguage.words)
        if (crossReferences.isNotEmpty()) CrossReferencesSection(crossReferences, onOpenReference)
        SourcesSection(study.citations)
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Spacing.m))
            if (showProse) StudyProvenance(reviewed = study.reviewed)
            TextButton(
                onClick = onReport,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = Spacing.xs),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            ) {
                Icon(Icons.Outlined.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.s))
                Text(stringResource(R.string.report_title), style = AppText.button)
            }
        }
    }
}

@Composable
private fun HiddenProseNote(onShowStudies: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = AppShapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.l)) {
            Text(stringResource(R.string.study_hidden_data_note), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onShowStudies, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(R.string.study_hidden_show), style = AppText.button)
            }
        }
    }
}

/**
 * The verse above the study, in the Discover card's style: translation label, reference, a faint hanging quotation
 * mark and the verse in Literata italic with the words of Jesus in red. Falls back to [verseText] (no red letters)
 * when [verse] isn't loaded.
 */
@Composable
fun StudyVerseHeader(
    reference: String,
    verse: Verse?,
    modifier: Modifier = Modifier,
    verseText: String? = verse?.text,
    translationId: String = verse?.translationId.orEmpty(),
    /** The Discover card's theme ("Generosity"), at the left of the label row like on the card; null elsewhere. */
    theme: String? = null,
) {
    val jesus = BibleScrollTheme.extendedColors.wordsOfJesus
    val text = remember(verse, verseText, jesus) {
        (verse?.redLetterText(jesus) ?: AnnotatedString(verseText.orEmpty())).withoutOpeningQuote()
    }
    Column(modifier) {
        if (translationId.isNotEmpty() || !theme.isNullOrBlank()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    theme.orEmpty().uppercase(Locale.getDefault()),
                    style = AppText.sectionLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = Spacing.s),
                )
                Text(
                    translationId.uppercase(Locale.getDefault()),
                    style = AppText.sectionLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.s))
        }
        Text(reference, style = AppText.screenTitle, modifier = Modifier.semantics { heading() })
        // The faint quote mark: the accent at low strength, as on the Discover card.
        Box(Modifier.fillMaxWidth().height(40.dp).clipToBounds().clearAndSetSemantics { }) {
            Text(
                "“",
                style = TextStyle(fontFamily = Literata, fontSize = 76.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.wrapContentHeight(Alignment.Top, unbounded = true).offset(x = (-4).dp, y = (-6).dp),
            )
        }
        Text(
            text,
            style = ScriptureTextStyle.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontStyle = FontStyle.Italic,
                fontSize = 21.sp,
                lineHeight = 1.45.em,
            ),
        )
    }
}

/** The header already has the hanging mark, so drop the text's own opening quote (keeps red-letter spans). */
internal fun AnnotatedString.withoutOpeningQuote(): AnnotatedString {
    val start = text.indexOfFirst { !it.isWhitespace() }
    return if (start >= 0 && text[start] in "“\"") subSequence(start + 1, length) else this
}

/**
 * "AI-assisted, reviewed by a person" only when the published status really is reviewed (Study.reviewed is
 * status == "reviewed" in the pipeline's publish); otherwise "AI-assisted, checked automatically". At the end of the
 * study, next to "Report a problem".
 */
@Composable
fun StudyProvenance(reviewed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Spacing.xs)) {
        Icon(
            if (reviewed) Icons.Outlined.Verified else Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(Spacing.s))
        Text(
            stringResource(if (reviewed) R.string.study_reviewed else R.string.ai_crafted),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A section label ("MEANING ✦"). [ai]: the prose was written with AI help; the small sparkle says so (TalkBack:
 * "AI-assisted"), which is how every prose section stays labelled.
 */
@Composable
private fun SectionLabel(title: String, ai: Boolean = false, icon: ImageVector? = null, accent: Boolean = false) {
    val aiLabel = stringResource(R.string.study_section_ai)
    // Callout labels ("Did you know?", "Apply it") take the accent; section labels stay muted.
    val muted = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(bottom = Spacing.s)
            .semantics(mergeDescendants = true) { heading() },
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(title.uppercase(Locale.getDefault()), style = AppText.sectionLabel, color = muted)
        if (ai) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Outlined.AutoAwesome, contentDescription = aiLabel, tint = muted, modifier = Modifier.size(13.dp))
        }
    }
}

/** The opening section ("Meaning"), set a little larger in Literata. */
@Composable
private fun StudyLead(title: String, text: String) {
    if (text.isBlank()) return
    Column {
        SectionLabel(title, ai = true)
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Literata, fontSize = 18.sp, lineHeight = 28.sp))
    }
}

/** A prose section: written with AI help, and labelled so beside its heading. */
@Composable
fun StudySection(title: String, text: String) {
    if (text.isBlank()) return
    Column {
        SectionLabel(title, ai = true)
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp))
    }
}

/** "Did you know?" and "Apply it": the same gray inset as the Discover card. */
@Composable
private fun StudyCallout(icon: ImageVector, title: String, text: String) {
    if (text.isBlank()) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = AppShapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.card)) {
            SectionLabel(title, ai = true, icon = icon, accent = true)
            Text(text, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp))
        }
    }
}

/**
 * Word by word, as tiles in reading order (right to left for Hebrew and Aramaic): the original word and what it
 * means here. A tap opens the word's details (lemma, pronunciation, grammar code, Strong's number).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OriginalLanguageSection(language: Language, words: List<Word>) {
    if (words.isEmpty()) return
    val title = when (language) {
        Language.HEBREW -> R.string.study_original_hebrew
        Language.ARAMAIC -> R.string.study_original_aramaic
        Language.GREEK -> R.string.study_original_greek
    }
    val rtl = language != Language.GREEK
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    val outer = LocalLayoutDirection.current
    Column {
        SectionLabel(stringResource(title))
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                words.forEachIndexed { i, word ->
                    // Tile contents keep the app's direction; only the order of tiles follows the original language.
                    CompositionLocalProvider(LocalLayoutDirection provides outer) {
                        WordChip(word, rtl, onClick = { open = i })
                    }
                }
            }
        }
    }
    open?.let { i -> words.getOrNull(i)?.let { WordSheet(it, rtl, onDismiss = { open = null }) } }
}

@Composable
private fun WordChip(word: Word, rtl: Boolean, onClick: () -> Unit) {
    val gloss = cleanGloss(word.gloss)
    val spoken = stringResource(R.string.study_word_cd, cleanTransliteration(word.transliteration), gloss)
    val details = stringResource(R.string.study_word_details)
    Surface(
        onClick = onClick,
        shape = AppShapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .widthIn(min = 48.dp)
            .heightIn(min = 48.dp)
            // TalkBack: "<pronunciation>: <meaning>", double-tap to open "Word details".
            .semantics {
                contentDescription = spoken
                onClick(label = details) { onClick(); true }
            },
    ) {
        Column(Modifier.padding(horizontal = Spacing.m, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                word.surface,
                style = ScriptureTextStyle.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                    lineHeight = MaterialTheme.typography.titleMedium.lineHeight,
                    textDirection = if (rtl) TextDirection.Rtl else TextDirection.Ltr,
                ),
            )
            Text(gloss, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun cleanGloss(gloss: String) = gloss.ifBlank { "—" }.replace("<", "").replace(">", "")
internal fun cleanTransliteration(text: String) = text.replace(".", "")

/** One row per reference: the reference in Literata, the verse in gray, a chevron. A tap goes to the verse. */
@Composable
fun CrossReferencesSection(references: List<CrossReferenceUi>, onOpen: (VerseRef) -> Unit) {
    Column {
        SectionLabel(stringResource(R.string.study_cross_references))
        references.forEachIndexed { i, ref ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    // "Go to verse": TalkBack reads the reference and verse, then "double-tap to go to verse".
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.study_open_reference)) { onOpen(ref.start) }
                    .padding(vertical = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(ref.label, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Literata, fontWeight = FontWeight.SemiBold))
                    ref.text?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                    }
                    ref.note?.let {
                        Text(stringResource(R.string.study_reference_note, it), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.s),
                )
            }
        }
    }
}

/** Spec: citations come from data, with the attribution each source requires. */
@Composable
fun SourcesSection(citations: List<Citation>) {
    if (citations.isEmpty()) return
    Column {
        SectionLabel(stringResource(R.string.study_sources))
        citations.forEach {
            Text(
                stringResource(R.string.study_citation, it.source, it.licence),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}
