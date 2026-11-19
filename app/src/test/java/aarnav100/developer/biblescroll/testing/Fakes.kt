package aarnav100.developer.biblescroll.testing

import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.model.Book
import aarnav100.developer.biblescroll.data.bible.model.Chapter
import aarnav100.developer.biblescroll.data.bible.model.Testament
import aarnav100.developer.biblescroll.data.bible.model.Translation
import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.user.ChapterReadEntity
import aarnav100.developer.biblescroll.data.user.NoteEntity
import aarnav100.developer.biblescroll.data.user.ReadingPositionEntity
import aarnav100.developer.biblescroll.data.user.UserDao
import aarnav100.developer.biblescroll.data.user.VerseKey
import aarnav100.developer.biblescroll.data.user.VerseMarkEntity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** In-memory UserDao. The interface's @Transaction default methods run unchanged on top of it. */
class FakeUserDao : UserDao {
    val marks = MutableStateFlow<Map<VerseKey, VerseMarkEntity>>(emptyMap())
    val notes = MutableStateFlow<Map<VerseKey, NoteEntity>>(emptyMap())
    private val positions = MutableStateFlow<Map<Int, ReadingPositionEntity>>(emptyMap())
    private val chapterReads = MutableStateFlow<Map<Pair<Int, Int>, ChapterReadEntity>>(emptyMap())

    override fun observeLikedKeys() = marks.map { m -> m.values.filter { it.liked }.map { it.key() } }
    override fun observeBookmarkedKeys() = marks.map { m -> m.values.filter { it.bookmarked }.map { it.key() } }
    override suspend fun getMark(bookId: Int, chapter: Int, verse: Int) = marks.value[VerseKey(bookId, chapter, verse)]
    override fun observeMark(bookId: Int, chapter: Int, verse: Int) = marks.map { it[VerseKey(bookId, chapter, verse)] }
    override suspend fun upsertMark(mark: VerseMarkEntity) {
        marks.value = marks.value + (mark.key() to mark)
    }

    override fun observeNoteKeys() = notes.map { it.keys.toList() }

    override fun observeLiked() = marks.map { m -> m.values.filter { it.liked } }
    override fun observeBookmarked() = marks.map { m -> m.values.filter { it.bookmarked } }
    override fun observeStudied() = marks.map { m -> m.values.filter { it.studied } }
    override fun observeSavedStudies() = marks.map { m -> m.values.filter { it.studySaved } }
    override fun observeLibraryCounts() = kotlinx.coroutines.flow.combine(marks, notes) { m, n ->
        aarnav100.developer.biblescroll.data.user.LibraryCounts(
            liked = m.values.count { it.liked }, bookmarked = m.values.count { it.bookmarked },
            studied = m.values.count { it.studied }, saved = m.values.count { it.studySaved }, notes = n.size,
        )
    }
    override fun observeNotes() = notes.map { it.values.toList() }

    /** Mirrors SQLite: LIKE '%x%' ESCAPE '\', case-insensitive for ASCII. */
    override fun searchNotes(escaped: String) = notes.map { n ->
        val literal = Regex("""\\(.)""").replace(escaped) { it.groupValues[1] }
        n.values.filter { it.text.contains(literal, ignoreCase = true) }
    }
    override suspend fun getNote(bookId: Int, chapter: Int, verse: Int) = notes.value[VerseKey(bookId, chapter, verse)]
    override suspend fun upsertNote(note: NoteEntity) {
        notes.value = notes.value + (VerseKey(note.bookId, note.chapter, note.verse) to note)
    }
    override suspend fun deleteNote(bookId: Int, chapter: Int, verse: Int) {
        notes.value = notes.value - VerseKey(bookId, chapter, verse)
    }

    override suspend fun latestPosition() = positions.value.values.maxByOrNull { it.updatedAt }
    override fun observeLatestPosition() = positions.map { m -> m.values.maxByOrNull { it.updatedAt } }
    override suspend fun positionForBook(bookId: Int) = positions.value[bookId]
    override fun observePositionForBook(bookId: Int) = positions.map { it[bookId] }
    override suspend fun upsertPosition(position: ReadingPositionEntity) {
        positions.value = positions.value + (position.bookId to position)
    }

    override fun observeReadChapters() = chapterReads.map { m -> m.values.filter { it.read } }
    override suspend fun upsertChapterReads(reads: List<ChapterReadEntity>) {
        chapterReads.value = chapterReads.value + reads.associateBy { it.bookId to it.chapter }
    }

    private fun VerseMarkEntity.key() = VerseKey(bookId, chapter, verse)
}

/**
 * A tiny two-translation Bible: Genesis 1:1-3, Psalm 119:105, John 3:15-17 and John 14:6. "SHORT" lacks John 3:16, to test a
 * mark on a verse that a translation omits (like WEB Acts 8:37).
 */
class FakeBibleRepository : BibleRepository {
    private val refs = listOf(
        VerseRef(1, 1, 1), VerseRef(1, 1, 2), VerseRef(1, 1, 3),
        VerseRef(19, 119, 105),
        VerseRef(43, 3, 15), VerseRef(43, 3, 16), VerseRef(43, 3, 17),
        VerseRef(43, 14, 6),
    )
    private val byTranslation: Map<String, List<Verse>> = mapOf(
        "WEB" to refs.toVerses("WEB"),
        "SHORT" to refs.filterNot { it == VerseRef(43, 3, 16) }.toVerses("SHORT"),
    )

    fun ordinalOf(translationId: String, ref: VerseRef) = byTranslation.getValue(translationId).first { it.ref == ref }.ordinal

    override fun translations() = flowOf(
        listOf(Translation("WEB", "World English Bible", "en", "PD"), Translation("SHORT", "Short", "en", "PD")),
    )

    override fun books() = flowOf(
        listOf(
            Book(1, "GEN", "Genesis", Testament.OLD, 50),
            Book(19, "PSA", "Psalms", Testament.OLD, 150),
            Book(43, "JHN", "John", Testament.NEW, 21),
        ),
    )

    override fun chapters(translationId: String, bookId: Int): Flow<List<Chapter>> = flowOf(emptyList())
    override fun chapterVerses(translationId: String, bookId: Int, chapter: Int) = flowOf(
        byTranslation.getValue(translationId).filter { it.ref.bookId == bookId && it.ref.chapter == chapter },
    )
    override fun verse(translationId: String, ref: VerseRef) = flowOf(byTranslation.getValue(translationId).firstOrNull { it.ref == ref })
    override fun versesFrom(translationId: String, fromOrdinal: Int, count: Int) = flowOf(
        byTranslation.getValue(translationId).filter { it.ordinal >= fromOrdinal }.take(count),
    )
    override fun verseInAllTranslations(ref: VerseRef) = flowOf(byTranslation.values.mapNotNull { v -> v.firstOrNull { it.ref == ref } })
    /** Simulates a slow query so tests can interleave UI callbacks with session recomputation. */
    var verseCountDelayMs = 0L

    override suspend fun verseCount(translationId: String): Int {
        if (verseCountDelayMs > 0) kotlinx.coroutines.delay(verseCountDelayMs)
        return byTranslation.getValue(translationId).size
    }
    override suspend fun versesByRefs(translationId: String, refs: Collection<VerseRef>) =
        byTranslation.getValue(translationId).filter { it.ref in refs }
    override suspend fun ordinalAtOrAfter(translationId: String, ref: VerseRef) =
        byTranslation.getValue(translationId).firstOrNull { it.ref.order() >= ref.order() }?.ordinal

    private fun VerseRef.order() = bookId * 1_000_000L + chapter * 1_000L + verse

    private fun List<VerseRef>.toVerses(translationId: String) = mapIndexed { i, ref ->
        Verse(translationId, ref, ordinal = i + 1, text = "$translationId $ref", wordsOfJesus = emptyList())
    }
}

class FakePreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

/** Records refreshes instead of redrawing real widgets. */
class FakeWidgetRefresher : aarnav100.developer.biblescroll.widget.WidgetRefresher {
    var refreshes = 0
    override suspend fun refresh() {
        refreshes++
    }
}

class FakeHomeScreenWidgets(
    var placed: Boolean = false,
    var pinSupported: Boolean = true,
    var pinAccepted: Boolean = true,
) : aarnav100.developer.biblescroll.widget.HomeScreenWidgets {
    var pinRequests = 0
    override fun hasWidget() = placed
    override fun canPin() = pinSupported
    override fun requestPin(): Boolean {
        pinRequests++
        return pinAccepted
    }
}
