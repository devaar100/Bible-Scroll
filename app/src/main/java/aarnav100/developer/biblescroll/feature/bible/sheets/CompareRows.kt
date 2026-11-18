package aarnav100.developer.biblescroll.feature.bible.sheets

import aarnav100.developer.biblescroll.data.bible.model.Translation
import aarnav100.developer.biblescroll.data.bible.model.Verse
import androidx.compose.runtime.Immutable

/** One B9 row. [verse] is null when this translation has no verse at that reference. */
@Immutable
data class CompareRow(
    val translation: Translation,
    val verse: Verse?,
    val isCurrent: Boolean,
)

/**
 * Every bundled translation exactly once, in [translations] order but with [currentTranslationId]
 * first. Translations without the verse (textual variants like Acts 8:37, or different numbering
 * like the Romans doxology) still get a row, with a null verse.
 */
fun compareRows(
    translations: List<Translation>,
    verses: List<Verse>,
    currentTranslationId: String,
): List<CompareRow> {
    val byTranslation = verses.associateBy { it.translationId }
    return translations
        .sortedByDescending { it.id == currentTranslationId } // stable: keeps the rest in order
        .map { CompareRow(it, byTranslation[it.id], isCurrent = it.id == currentTranslationId) }
}
