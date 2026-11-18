package aarnav100.developer.biblescroll.data.user

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT book_id, chapter, verse FROM verse_marks WHERE liked = 1")
    fun observeLikedKeys(): Flow<List<VerseKey>>

    @Query("SELECT book_id, chapter, verse FROM verse_marks WHERE bookmarked = 1")
    fun observeBookmarkedKeys(): Flow<List<VerseKey>>

    @Query("SELECT * FROM verse_marks WHERE book_id = :bookId AND chapter = :chapter AND verse = :verse")
    suspend fun getMark(bookId: Int, chapter: Int, verse: Int): VerseMarkEntity?

    @Query("SELECT * FROM verse_marks WHERE book_id = :bookId AND chapter = :chapter AND verse = :verse")
    fun observeMark(bookId: Int, chapter: Int, verse: Int): Flow<VerseMarkEntity?>

    @Upsert
    suspend fun upsertMark(mark: VerseMarkEntity)

    /**
     * Read-modify-write so changing one flag never clobbers the other. (SQLite on API 26 predates
     * `ON CONFLICT DO UPDATE`, so this can't be a single statement.)
     */
    @Transaction
    suspend fun updateMark(key: VerseKey, now: Long, change: (VerseMarkEntity) -> VerseMarkEntity) {
        val existing = getMark(key.bookId, key.chapter, key.verse)
            ?: VerseMarkEntity(key.bookId, key.chapter, key.verse, liked = false, updatedAt = now, createdAt = now)
        val changed = change(existing)
        if (changed != existing) upsertMark(changed.copy(updatedAt = now))
    }

    @Query("SELECT book_id, chapter, verse FROM notes")
    fun observeNoteKeys(): Flow<List<VerseKey>>

    // ---- Library (H3-H7). Sorting happens in Kotlin; these only select. ----

    @Query("SELECT * FROM verse_marks WHERE liked = 1")
    fun observeLiked(): Flow<List<VerseMarkEntity>>

    @Query("SELECT * FROM verse_marks WHERE bookmarked = 1")
    fun observeBookmarked(): Flow<List<VerseMarkEntity>>

    @Query("SELECT * FROM verse_marks WHERE studied = 1")
    fun observeStudied(): Flow<List<VerseMarkEntity>>

    @Query("SELECT * FROM verse_marks WHERE study_saved = 1")
    fun observeSavedStudies(): Flow<List<VerseMarkEntity>>

    @Query(
        "SELECT COALESCE(SUM(liked), 0) AS liked, COALESCE(SUM(bookmarked), 0) AS bookmarked, " +
            "COALESCE(SUM(studied), 0) AS studied, COALESCE(SUM(study_saved), 0) AS saved, " +
            "(SELECT COUNT(*) FROM notes) AS notes FROM verse_marks",
    )
    fun observeLibraryCounts(): Flow<LibraryCounts>

    @Query("SELECT * FROM notes")
    fun observeNotes(): Flow<List<NoteEntity>>

    /** [escaped] must have \, % and _ escaped with a backslash (see LibraryRepository). */
    @Query("SELECT * FROM notes WHERE text LIKE '%' || :escaped || '%' ESCAPE '\\'")
    fun searchNotes(escaped: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE book_id = :bookId AND chapter = :chapter AND verse = :verse")
    suspend fun getNote(bookId: Int, chapter: Int, verse: Int): NoteEntity?

    @Upsert
    suspend fun upsertNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE book_id = :bookId AND chapter = :chapter AND verse = :verse")
    suspend fun deleteNote(bookId: Int, chapter: Int, verse: Int)

    /** Keeps the original createdAt when editing an existing note. */
    @Transaction
    suspend fun saveNote(key: VerseKey, text: String, now: Long) {
        val existing = getNote(key.bookId, key.chapter, key.verse)
        if (existing?.text == text) return
        upsertNote(NoteEntity(key.bookId, key.chapter, key.verse, text, existing?.createdAt ?: now, now))
    }

    @Query("SELECT * FROM reading_positions ORDER BY updated_at DESC LIMIT 1")
    suspend fun latestPosition(): ReadingPositionEntity?

    @Query("SELECT * FROM reading_positions WHERE book_id = :bookId")
    suspend fun positionForBook(bookId: Int): ReadingPositionEntity?

    @Query("SELECT * FROM reading_positions WHERE book_id = :bookId")
    fun observePositionForBook(bookId: Int): Flow<ReadingPositionEntity?>

    @Upsert
    suspend fun upsertPosition(position: ReadingPositionEntity)

    @Query("SELECT * FROM chapter_reads WHERE read = 1")
    fun observeReadChapters(): Flow<List<ChapterReadEntity>>

    @Upsert
    suspend fun upsertChapterReads(reads: List<ChapterReadEntity>)
}
