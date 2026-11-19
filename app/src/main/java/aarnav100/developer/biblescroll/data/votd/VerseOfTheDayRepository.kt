package aarnav100.developer.biblescroll.data.votd

import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.References
import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** The Verse of the Day for one date, ready to show. */
data class VotdVerse(
    val date: LocalDate,
    /** Red-letter ranges included; its translationId is the one actually shown. */
    val verse: Verse,
    /** In the app's language, e.g. "Juan 3:16". */
    val reference: String,
    /** The reader's translation. Differs from verse.translationId when that translation lacks the verse. */
    val requestedTranslationId: String,
    /** Full name of the translation shown, e.g. "World English Bible" (spoken by TalkBack). */
    val translationName: String,
) {
    val fellBack: Boolean get() = verse.translationId != requestedTranslationId
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class VerseOfTheDayRepository @Inject constructor(
    private val source: VotdSource,
    private val bible: BibleRepository,
    private val reader: ReaderPreferences,
) {
    /**
     * [date]'s verse in the reader's translation, or in WEB if that translation lacks it (or has it empty).
     * Null if the list has no entry for the date or neither translation has the verse. Follows translation and
     * language changes.
     */
    fun forDate(date: LocalDate): Flow<VotdVerse?> = flow { emit(source.list()?.entryFor(date)) }
        .flatMapLatest { entry ->
            if (entry == null) return@flatMapLatest flowOf(null)
            reader.translationId.flatMapLatest { translationId ->
                combine(textIn(translationId, entry), bible.books(), bible.translations()) { verse, books, translations ->
                    verse?.let {
                        val name = books.firstOrNull { b -> b.id == entry.ref.bookId }?.name.orEmpty()
                        val translationName = translations.firstOrNull { t -> t.id == it.translationId }?.name ?: it.translationId
                        VotdVerse(date, it, References.verse(name, entry.ref), translationId, translationName)
                    }
                }
            }
        }
        .distinctUntilChanged()

    private fun textIn(translationId: String, entry: VotdEntry): Flow<Verse?> =
        bible.verse(translationId, entry.ref).flatMapLatest { verse ->
            when {
                verse != null && verse.text.isNotBlank() -> flowOf(verse)
                translationId == FALLBACK_TRANSLATION -> flowOf(null)
                else -> bible.verse(FALLBACK_TRANSLATION, entry.ref).map { it?.takeIf { v -> v.text.isNotBlank() } }
            }
        }

    companion object {
        /** Every list entry is checked against it (VotdAssetTest); also the app's default translation. */
        const val FALLBACK_TRANSLATION = "WEB"
    }
}
