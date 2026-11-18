package aarnav100.developer.biblescroll.data.user

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface UserDataRepository {
    fun likedVerses(): Flow<Set<VerseRef>>
    suspend fun setLiked(ref: VerseRef, liked: Boolean)

    fun bookmarkedVerses(): Flow<Set<VerseRef>>
    suspend fun setBookmarked(ref: VerseRef, bookmarked: Boolean)

    /** D2/B8: whether the verse's study was opened, and whether it's saved. */
    fun studyMarks(ref: VerseRef): Flow<StudyMarks>
    suspend fun setStudied(ref: VerseRef, studied: Boolean)
    suspend fun setStudySaved(ref: VerseRef, saved: Boolean)

    /** Verses that have a note, for the rail's note dot. */
    fun notedVerses(): Flow<Set<VerseRef>>
    suspend fun note(ref: VerseRef): String?

    /** Saves [text] as the note on [ref]; blank text deletes the note. */
    suspend fun saveNote(ref: VerseRef, text: String)
    suspend fun deleteNote(ref: VerseRef)

    /** Most recently read verse across all books, or null on first launch. */
    suspend fun lastReadingPosition(): VerseRef?

    /** Where the reader last was in [bookId], or null if they haven't read it yet. */
    suspend fun readingPosition(bookId: Int): VerseRef?
    fun observeReadingPosition(bookId: Int): Flow<VerseRef?>
    suspend fun saveReadingPosition(translationId: String, ref: VerseRef)

    /** Chapters marked read, as bookId -> chapter numbers. */
    fun readChapters(): Flow<Map<Int, Set<Int>>>
    suspend fun setChaptersRead(bookId: Int, chapters: Collection<Int>, read: Boolean)
}

@Singleton
class LocalUserDataRepository internal constructor(
    private val dao: UserDao,
    private val clock: () -> Long,
) : UserDataRepository {

    @Inject
    constructor(dao: UserDao) : this(dao, System::currentTimeMillis)

    override fun likedVerses() = dao.observeLikedKeys().map { it.toRefSet() }

    override suspend fun setLiked(ref: VerseRef, liked: Boolean) {
        val now = clock()
        dao.updateMark(ref.toKey(), now) { it.copy(liked = liked, likedAt = if (liked && !it.liked) now else it.likedAt) }
    }

    override fun bookmarkedVerses() = dao.observeBookmarkedKeys().map { it.toRefSet() }

    override fun studyMarks(ref: VerseRef) = dao.observeMark(ref.bookId, ref.chapter, ref.verse)
        .map { StudyMarks(studied = it?.studied == true, saved = it?.studySaved == true) }
        .distinctUntilChanged()

    override suspend fun setStudied(ref: VerseRef, studied: Boolean) {
        val now = clock()
        dao.updateMark(ref.toKey(), now) {
            it.copy(studied = studied, studiedAt = if (studied && !it.studied) now else it.studiedAt)
        }
    }

    override suspend fun setStudySaved(ref: VerseRef, saved: Boolean) {
        val now = clock()
        dao.updateMark(ref.toKey(), now) {
            it.copy(studySaved = saved, studySavedAt = if (saved && !it.studySaved) now else it.studySavedAt)
        }
    }

    override suspend fun setBookmarked(ref: VerseRef, bookmarked: Boolean) {
        val now = clock()
        dao.updateMark(ref.toKey(), now) {
            it.copy(bookmarked = bookmarked, bookmarkedAt = if (bookmarked && !it.bookmarked) now else it.bookmarkedAt)
        }
    }

    override fun notedVerses() = dao.observeNoteKeys().map { it.toRefSet() }

    override suspend fun note(ref: VerseRef) = dao.getNote(ref.bookId, ref.chapter, ref.verse)?.text

    override suspend fun saveNote(ref: VerseRef, text: String) {
        // Blank means "no note": delete rather than keep an empty row around.
        if (text.isBlank()) deleteNote(ref) else dao.saveNote(ref.toKey(), text, clock())
    }

    override suspend fun deleteNote(ref: VerseRef) = dao.deleteNote(ref.bookId, ref.chapter, ref.verse)

    override suspend fun lastReadingPosition() = dao.latestPosition()?.toRef()

    override suspend fun readingPosition(bookId: Int) = dao.positionForBook(bookId)?.toRef()

    override fun observeReadingPosition(bookId: Int) = dao.observePositionForBook(bookId).map { it?.toRef() }

    override suspend fun saveReadingPosition(translationId: String, ref: VerseRef) =
        dao.upsertPosition(
            ReadingPositionEntity(ref.bookId, ref.chapter, ref.verse, translationId, clock()),
        )

    override fun readChapters() = dao.observeReadChapters().map { rows ->
        rows.groupBy({ it.bookId }, { it.chapter }).mapValues { (_, chapters) -> chapters.toSet() }
    }

    override suspend fun setChaptersRead(bookId: Int, chapters: Collection<Int>, read: Boolean) {
        if (chapters.isEmpty()) return
        val now = clock()
        dao.upsertChapterReads(chapters.map { ChapterReadEntity(bookId, it, read, now) })
    }
}

private fun ReadingPositionEntity.toRef() = VerseRef(bookId, chapter, verse)

private fun VerseRef.toKey() = VerseKey(bookId, chapter, verse)

private fun List<VerseKey>.toRefSet(): Set<VerseRef> = mapTo(HashSet(size)) { VerseRef(it.bookId, it.chapter, it.verse) }

data class StudyMarks(val studied: Boolean = false, val saved: Boolean = false)
