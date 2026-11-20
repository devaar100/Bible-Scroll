package aarnav100.developer.biblescroll.data.bible

import android.database.sqlite.SQLiteException
import aarnav100.developer.biblescroll.data.bible.model.Book
import aarnav100.developer.biblescroll.data.bible.model.Chapter
import aarnav100.developer.biblescroll.data.bible.model.Testament
import aarnav100.developer.biblescroll.data.bible.model.Translation
import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface BibleRepository {
    fun translations(): Flow<List<Translation>>
    fun books(): Flow<List<Book>>
    fun chapters(translationId: String, bookId: Int): Flow<List<Chapter>>
    fun chapterVerses(translationId: String, bookId: Int, chapter: Int): Flow<List<Verse>>
    fun verse(translationId: String, ref: VerseRef): Flow<Verse?>

    /** Up to [count] consecutive verses starting at [fromOrdinal], crossing chapter/book boundaries. */
    fun versesFrom(translationId: String, fromOrdinal: Int, count: Int): Flow<List<Verse>>

    /** The same reference in every bundled translation (Compare Versions). */
    fun verseInAllTranslations(ref: VerseRef): Flow<List<Verse>>

    suspend fun verseCount(translationId: String): Int

    /** Where each chapter sits in the feed (B1 scrubber). One query per translation. */
    suspend fun chapterSpans(translationId: String): ChapterSpans

    /** The given verses in [translationId], in Bible order; refs this translation lacks are skipped. */
    suspend fun versesByRefs(translationId: String, refs: Collection<VerseRef>): List<Verse>

    /** Ordinal of [ref], or of the next verse that exists in this translation; null past the end. */
    suspend fun ordinalAtOrAfter(translationId: String, ref: VerseRef): Int?
}

@Singleton
class OfflineBibleRepository @Inject constructor(
    private val dao: BibleDao,
    private val bookNames: BookNames = BookNames.Stored,
) : BibleRepository {

    override fun translations() = dao.observeTranslations().map { list ->
        val info = translationInfo()
        list.map { it.toModel(hasRedLetters = info[it.id] ?: true) }
    }

    /** Read-only asset data: read once per repository. Empty if the table is missing (old asset, test DB). */
    @Volatile private var info: Map<String, Boolean>? = null

    private suspend fun translationInfo(): Map<String, Boolean> = info ?: try {
        dao.translationInfo(TRANSLATION_INFO_QUERY).associate { it.id to it.hasRedLetters }
    } catch (e: SQLiteException) {
        emptyMap()
    }.also { info = it }

    /** In the app's language; emits again when it changes, so names in open screens follow. */
    override fun books() = combine(dao.observeBooks(), bookNames.books) { list, names ->
        list.map { entity ->
            val model = entity.toModel()
            names?.get(entity.id)?.let { model.copy(name = it.name, abbreviation = it.abbreviation, searchTerms = it.searchTerms) }
                ?: model
        }
    }

    override fun chapters(translationId: String, bookId: Int) =
        dao.observeChapters(translationId, bookId).map { list -> list.map { it.toModel() } }

    override fun chapterVerses(translationId: String, bookId: Int, chapter: Int) =
        dao.observeChapterVerses(translationId, bookId, chapter).map { list -> list.map { it.toModel() } }

    override fun verse(translationId: String, ref: VerseRef) =
        dao.observeVerse(translationId, ref.bookId, ref.chapter, ref.verse).map { it?.toModel() }

    override fun versesFrom(translationId: String, fromOrdinal: Int, count: Int) =
        dao.observeVersesFrom(translationId, fromOrdinal, count).map { list -> list.map { it.toModel() } }

    override fun verseInAllTranslations(ref: VerseRef) =
        dao.observeVerseInAllTranslations(ref.bookId, ref.chapter, ref.verse)
            .map { list -> list.map { it.toModel() } }

    override suspend fun verseCount(translationId: String) = dao.countVerses(translationId)

    override suspend fun chapterSpans(translationId: String) = ChapterSpans.of(translationId, dao.chapterSpans(translationId))

    override suspend fun versesByRefs(translationId: String, refs: Collection<VerseRef>): List<Verse> =
        refs.map { it.packedKey() }
            .chunked(MAX_KEYS_PER_QUERY)
            .flatMap { keys -> dao.versesByKeys(translationId, keys) }
            .sortedBy { it.ordinal }
            .map { it.toModel() }

    override suspend fun ordinalAtOrAfter(translationId: String, ref: VerseRef) =
        dao.ordinalAtOrAfter(translationId, ref.bookId, ref.chapter, ref.verse)
}

/** Under SQLite's 999 bound-variable limit (API < 31), leaving room for translationId. */
private const val MAX_KEYS_PER_QUERY = 900

private fun VerseRef.packedKey(): Long = bookId * 1_000_000L + chapter * 1_000L + verse

private fun TranslationEntity.toModel(hasRedLetters: Boolean) = Translation(id, name, languageCode, licence, hasRedLetters)

private fun BookEntity.toModel() = Book(
    id = id,
    code = code,
    name = name,
    testament = if (testament == "OT") Testament.OLD else Testament.NEW,
    chapterCount = chapterCount,
)

private fun ChapterEntity.toModel() = Chapter(bookId, chapter, verseCount, superscription)

private fun VerseEntity.toModel() = Verse(
    translationId = translationId,
    ref = VerseRef(bookId, chapter, verse),
    ordinal = ordinal,
    text = text,
    wordsOfJesus = parseRanges(jesusRanges),
)

/** "12-48;60-95" (end exclusive) -> [12..47, 60..94]. Malformed parts are skipped. */
internal fun parseRanges(encoded: String?): List<IntRange> {
    if (encoded.isNullOrEmpty()) return emptyList()
    return encoded.split(';').mapNotNull { part ->
        val start = part.substringBefore('-').toIntOrNull()
        val end = part.substringAfter('-', "").toIntOrNull()
        if (start != null && end != null && end > start) start until end else null
    }
}
