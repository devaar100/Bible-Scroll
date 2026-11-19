package aarnav100.developer.biblescroll.feature.study

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.core.content.model.Citation
import aarnav100.developer.biblescroll.core.content.model.Language
import aarnav100.developer.biblescroll.core.content.model.Study
import aarnav100.developer.biblescroll.core.content.model.Word
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.ui.theme.ScriptureTextStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/*
 * Verse-study building blocks shared by D2 Deep study and B8 Verse study (spec: both use the B8 layout).
 * Plain text only: study prose is rendered as text, never as HTML or markdown.
 */

/** A cross reference ready to show: formatted label, verse text (first verse of a range), TSK keyword. */
@Immutable
data class CrossReferenceUi(val start: VerseRef, val label: String, val text: String?, val note: String?)

/**
 * The whole study, in reading order. [showProse] false (Hide AI studies on, B8): only the data sections, which come
 * from source datasets and not from AI (original language, cross references, sources), with a note and
 * [onShowStudies] to turn the setting off.
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showProse) {
            StudyLanguageNote()
            StudyProvenance(reviewed = study.reviewed)
            StudySection(stringResource(R.string.study_meaning), study.meaning)
            StudySection(stringResource(R.string.study_historical_context), study.historicalContext)
            StudySection(stringResource(R.string.study_life_in_bible_times), study.lifeInBibleTimes)
            StudySection(stringResource(R.string.study_theology), study.theology)
            StudySection(stringResource(R.string.study_did_you_know), study.didYouKnow)
            StudySection(stringResource(R.string.study_apply_it), study.applyIt)
        } else {
            HiddenProseNote(onShowStudies)
        }
        OriginalLanguageSection(study.originalLanguage.language, study.originalLanguage.words)
        if (crossReferences.isNotEmpty()) CrossReferencesSection(crossReferences, onOpenReference)
        SourcesSection(study.citations)
        TextButton(onClick = onReport, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
            Icon(Icons.Outlined.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.report_title))
        }
    }
}

@Composable
private fun HiddenProseNote(onShowStudies: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
            Text(stringResource(R.string.study_hidden_data_note), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onShowStudies, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(R.string.study_hidden_show))
            }
        }
    }
}

/** The verse itself, above the study. */
@Composable
fun StudyVerseHeader(reference: String, verseText: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            verseText ?: "",
            style = ScriptureTextStyle.copy(color = MaterialTheme.colorScheme.onSurface, lineHeight = 1.45.em),
        )
        Spacer(Modifier.height(8.dp))
        Text(reference, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * "AI-assisted, reviewed by a person" only when the published status really is reviewed (Study.reviewed is
 * status == "reviewed" in the pipeline's publish); otherwise "AI-assisted, checked automatically".
 */
@Composable
fun StudyProvenance(reviewed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Icon(
            if (reviewed) Icons.Outlined.Verified else Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(if (reviewed) R.string.study_reviewed else R.string.study_ai_assisted),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A prose section: written with AI help, and labelled so under its heading. */
@Composable
fun StudySection(title: String, text: String) {
    if (text.isBlank()) return
    Column(Modifier.padding(vertical = 8.dp)) {
        SectionTitle(title)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.study_section_ai), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(bottom = 6.dp)
            .semantics { heading() },
    )
}

/**
 * Word by word, as chips in reading order (right to left for Hebrew and Aramaic): the original word and what it
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
    Column(Modifier.padding(vertical = 8.dp)) {
        SectionTitle(stringResource(title))
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                words.forEachIndexed { i, word ->
                    // Chip contents keep the app's direction; only the order of chips follows the original language.
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
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier
            .widthIn(min = 48.dp)
            .heightIn(min = 48.dp)
            // TalkBack: "<pronunciation>: <meaning>", double-tap to open "Word details".
            .semantics {
                contentDescription = spoken
                onClick(label = details) { onClick(); true }
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

@Composable
fun CrossReferencesSection(references: List<CrossReferenceUi>, onOpen: (VerseRef) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        SectionTitle(stringResource(R.string.study_cross_references))
        references.forEach { ref ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    // "Go to verse": TalkBack reads the reference and verse, then "double-tap to go to verse".
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.study_open_reference)) { onOpen(ref.start) }
                    .padding(vertical = 8.dp),
            ) {
                Text(ref.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                ref.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3) }
                ref.note?.let {
                    Text(stringResource(R.string.study_reference_note, it), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Spec: citations come from data, with the attribution each source requires. */
@Composable
fun SourcesSection(citations: List<Citation>) {
    if (citations.isEmpty()) return
    Column(Modifier.padding(vertical = 8.dp)) {
        SectionTitle(stringResource(R.string.study_sources))
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
