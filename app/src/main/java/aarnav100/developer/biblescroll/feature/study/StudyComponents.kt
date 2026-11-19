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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/*
 * Verse-study building blocks shared by D2 Deep study and B8 Verse study (spec: both use the B8 layout).
 * Plain text only: study prose is rendered as text, never as HTML or markdown.
 */

/** A cross reference ready to show: formatted label, verse text (first verse of a range), TSK keyword. */
@Immutable
data class CrossReferenceUi(val start: VerseRef, val label: String, val text: String?, val note: String?)

/** The whole study, in reading order. */
@Composable
fun StudyBody(
    study: Study,
    crossReferences: List<CrossReferenceUi>,
    onOpenReference: (VerseRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StudyLanguageNote()
        StudyProvenance(reviewed = study.reviewed)
        StudySection(stringResource(R.string.study_meaning), study.meaning)
        StudySection(stringResource(R.string.study_historical_context), study.historicalContext)
        StudySection(stringResource(R.string.study_life_in_bible_times), study.lifeInBibleTimes)
        StudySection(stringResource(R.string.study_theology), study.theology)
        StudySection(stringResource(R.string.study_did_you_know), study.didYouKnow)
        StudySection(stringResource(R.string.study_apply_it), study.applyIt)
        OriginalLanguageSection(study.originalLanguage.language, study.originalLanguage.words)
        if (crossReferences.isNotEmpty()) CrossReferencesSection(crossReferences, onOpenReference)
        SourcesSection(study.citations)
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

/** "Reviewed by a person" or "AI-assisted" (spec: every study carries an AI-assisted label). */
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

@Composable
fun StudySection(title: String, text: String) {
    if (text.isBlank()) return
    Column(Modifier.padding(vertical = 8.dp)) {
        SectionTitle(title)
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

/** Word by word: the original word, how to say it, what it means here, and its Strong's number. */
@Composable
fun OriginalLanguageSection(language: Language, words: List<Word>) {
    if (words.isEmpty()) return
    val title = when (language) {
        Language.HEBREW -> R.string.study_original_hebrew
        Language.ARAMAIC -> R.string.study_original_aramaic
        Language.GREEK -> R.string.study_original_greek
    }
    val rtl = language != Language.GREEK
    Column(Modifier.padding(vertical = 8.dp)) {
        SectionTitle(stringResource(title))
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(vertical = 4.dp)) {
                words.forEachIndexed { i, word ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    WordRow(word, rtl)
                }
            }
        }
    }
}

@Composable
private fun WordRow(word: Word, rtl: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            word.surface,
            style = ScriptureTextStyle.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textDirection = if (rtl) TextDirection.Rtl else TextDirection.Ltr,
            ),
            modifier = Modifier.width(110.dp),
        )
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                word.gloss.ifBlank { "—" }.replace("<", "").replace(">", ""),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                stringResource(R.string.two_parts, word.transliteration.replace(".", ""), word.strongs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CrossReferencesSection(references: List<CrossReferenceUi>, onOpen: (VerseRef) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        SectionTitle(stringResource(R.string.study_cross_references))
        references.forEach { ref ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
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
                "${it.source} (${it.licence})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}
