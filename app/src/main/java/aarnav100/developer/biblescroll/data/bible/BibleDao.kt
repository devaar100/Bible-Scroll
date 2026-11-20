package aarnav100.developer.biblescroll.data.bible

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

/** Read-only access to the bundled Bible. Room runs Flow queries off the main thread. */
@Dao
interface BibleDao {

    @Query("SELECT * FROM translations ORDER BY sort_order")
    fun observeTranslations(): Flow<List<TranslationEntity>>

    /**
     * translation_info isn't a Room table (tools/build_bible_db.py adds it beside Room's), so it's read raw.
     * Use [TRANSLATION_INFO_QUERY]. Throws SQLiteException on a database without the table.
     */
    @RawQuery
    suspend fun translationInfo(query: SupportSQLiteQuery): List<TranslationInfoRow>

    @Query("SELECT * FROM books ORDER BY id")
    fun observeBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM chapters WHERE translation_id = :translationId AND book_id = :bookId ORDER BY chapter")
    fun observeChapters(translationId: String, bookId: Int): Flow<List<ChapterEntity>>

    @Query(
        """
        SELECT * FROM verses
        WHERE translation_id = :translationId AND book_id = :bookId AND chapter = :chapter
        ORDER BY verse
        """
    )
    fun observeChapterVerses(translationId: String, bookId: Int, chapter: Int): Flow<List<VerseEntity>>

    @Query(
        """
        SELECT * FROM verses
        WHERE translation_id = :translationId AND book_id = :bookId AND chapter = :chapter AND verse = :verse
        """
    )
    fun observeVerse(translationId: String, bookId: Int, chapter: Int, verse: Int): Flow<VerseEntity?>

    @Query(
        """
        SELECT * FROM verses
        WHERE translation_id = :translationId AND ordinal >= :fromOrdinal
        ORDER BY ordinal
        LIMIT :count
        """
    )
    fun observeVersesFrom(translationId: String, fromOrdinal: Int, count: Int): Flow<List<VerseEntity>>

    @Query(
        """
        SELECT v.* FROM verses v
        JOIN translations t ON t.id = v.translation_id
        WHERE v.book_id = :bookId AND v.chapter = :chapter AND v.verse = :verse
        ORDER BY t.sort_order
        """
    )
    fun observeVerseInAllTranslations(bookId: Int, chapter: Int, verse: Int): Flow<List<VerseEntity>>

    /**
     * Verses whose packed key (book * 1_000_000 + chapter * 1_000 + verse) is in [keys], in Bible
     * order. Keys absent from this translation are simply missing from the result. Callers must keep
     * [keys] under SQLite's 999-variable limit.
     */
    @Query(
        """
        SELECT * FROM verses
        WHERE translation_id = :translationId
          AND (book_id * 1000000 + chapter * 1000 + verse) IN (:keys)
        ORDER BY ordinal
        """
    )
    suspend fun versesByKeys(translationId: String, keys: List<Long>): List<VerseEntity>

    /** Every chapter's first ordinal, row count and last verse number, in feed order (B1 scrubber). */
    @Query(
        """
        SELECT book_id AS bookId, chapter, MIN(ordinal) AS firstOrdinal, COUNT(*) AS verseCount, MAX(verse) AS lastVerse
        FROM verses
        WHERE translation_id = :translationId
        GROUP BY book_id, chapter
        ORDER BY firstOrdinal
        """
    )
    suspend fun chapterSpans(translationId: String): List<ChapterSpanRow>

    @Query("SELECT COUNT(*) FROM verses WHERE translation_id = :translationId")
    suspend fun countVerses(translationId: String): Int

    /** Ordinal of the given verse, or of the next existing one if it is absent in this translation. */
    @Query(
        """
        SELECT ordinal FROM verses
        WHERE translation_id = :translationId
          AND (book_id > :bookId OR (book_id = :bookId AND (chapter > :chapter OR (chapter = :chapter AND verse >= :verse))))
        ORDER BY ordinal
        LIMIT 1
        """
    )
    suspend fun ordinalAtOrAfter(translationId: String, bookId: Int, chapter: Int, verse: Int): Int?
}

val TRANSLATION_INFO_QUERY = SimpleSQLiteQuery("SELECT id, has_red_letters FROM translation_info")

data class TranslationInfoRow(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "has_red_letters") val hasRedLetters: Boolean,
)

data class ChapterSpanRow(
    @ColumnInfo(name = "bookId") val bookId: Int,
    @ColumnInfo(name = "chapter") val chapter: Int,
    @ColumnInfo(name = "firstOrdinal") val firstOrdinal: Int,
    @ColumnInfo(name = "verseCount") val verseCount: Int,
    @ColumnInfo(name = "lastVerse") val lastVerse: Int,
)
