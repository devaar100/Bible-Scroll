package aarnav100.developer.biblescroll.core.content.db

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Downloaded and bundled study content. A cache: everything here can be re-imported from the bundle or
 * downloaded again, so it lives apart from the user DB, is excluded from backups, and may be rebuilt on a
 * schema change.
 */
@Database(
    entities = [StudyEntity::class, BookEntity::class, DevotionalEntity::class, DevotionalMonthEntity::class],
    version = 2,
    exportSchema = true,
    // A real migration, so downloaded study books aren't thrown away when devotionals arrive.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class ContentDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao
    abstract fun devotionalDao(): DevotionalDao

    companion object {
        const val FILE_NAME = "content.db"
    }
}

/** One verse's study, stored as its published JSON (decoded only when shown). */
@Entity(tableName = "studies", primaryKeys = ["book_id", "chapter", "verse"])
data class StudyEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    /** The study's own contentVersion. */
    @ColumnInfo(name = "content_version") val contentVersion: String,
    val json: String,
)

@Entity(tableName = "books", primaryKeys = ["book_id"])
data class BookEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    /** USFM code, e.g. "JHN". */
    val code: String,
    /** The book file's contentVersion (sha256 prefix) this content came from. */
    @ColumnInfo(name = "content_version") val contentVersion: String,
    val sha256: String,
    /** BUNDLED (from app assets) or DOWNLOADED (from the CDN, incl. refreshes of bundled books). */
    val source: String,
    @ColumnInfo(name = "verse_count") val verseCount: Int,
    @ColumnInfo(name = "reviewed_count") val reviewedCount: Int,
    /** Bytes of study JSON stored on the device, for Settings → Storage. */
    @ColumnInfo(name = "stored_bytes") val storedBytes: Long,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
)

@Dao
interface ContentDao {
    @Query("SELECT json FROM studies WHERE book_id = :bookId AND chapter = :chapter AND verse = :verse")
    fun observeStudyJson(bookId: Int, chapter: Int, verse: Int): Flow<String?>

    @Query("SELECT * FROM books WHERE book_id = :bookId")
    fun observeBook(bookId: Int): Flow<BookEntity?>

    @Query("SELECT * FROM books ORDER BY book_id")
    fun observeBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY book_id")
    suspend fun books(): List<BookEntity>

    @Query("DELETE FROM studies WHERE book_id = :bookId")
    suspend fun deleteStudies(bookId: Int)

    @Query("DELETE FROM books WHERE book_id = :bookId")
    suspend fun deleteBook(bookId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudies(studies: List<StudyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBook(book: BookEntity)

    /** Swap a whole book in one transaction: readers see the old book or the new one, never a mix. */
    @Transaction
    suspend fun replaceBook(book: BookEntity, studies: List<StudyEntity>) {
        deleteStudies(book.bookId)
        insertStudies(studies)
        upsertBook(book)
    }

    @Transaction
    suspend fun removeBook(bookId: Int) {
        deleteStudies(bookId)
        deleteBook(bookId)
    }
}

/** One devotional, stored as its published JSON. Keyed by "MM-DD". */
@Entity(tableName = "devotionals", primaryKeys = ["date"])
data class DevotionalEntity(
    val date: String,
    /** "01".."12", the month file it came from. */
    val month: String,
    @ColumnInfo(name = "content_version") val contentVersion: String,
    val json: String,
)

/** A month file on the device. */
@Entity(tableName = "devotional_months", primaryKeys = ["month"])
data class DevotionalMonthEntity(
    val month: String,
    @ColumnInfo(name = "content_version") val contentVersion: String,
    val sha256: String,
    /** BUNDLED or DOWNLOADED, as for books. */
    val source: String,
    val count: Int,
    @ColumnInfo(name = "stored_bytes") val storedBytes: Long,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
)

@Dao
interface DevotionalDao {
    @Query("SELECT json FROM devotionals WHERE date = :date")
    fun observeJson(date: String): Flow<String?>

    @Query("SELECT json FROM devotionals WHERE month = :month ORDER BY date")
    fun observeMonthJson(month: String): Flow<List<String>>

    @Query("SELECT * FROM devotional_months WHERE month = :month")
    fun observeMonth(month: String): Flow<DevotionalMonthEntity?>

    @Query("SELECT * FROM devotional_months ORDER BY month")
    suspend fun months(): List<DevotionalMonthEntity>

    @Query("DELETE FROM devotionals WHERE month = :month")
    suspend fun deleteDevotionals(month: String)

    @Query("DELETE FROM devotional_months WHERE month = :month")
    suspend fun deleteMonth(month: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevotionals(devotionals: List<DevotionalEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMonth(month: DevotionalMonthEntity)

    /** Swap a whole month in one transaction. */
    @Transaction
    suspend fun replaceMonth(month: DevotionalMonthEntity, devotionals: List<DevotionalEntity>) {
        deleteDevotionals(month.month)
        insertDevotionals(devotionals)
        upsertMonth(month)
    }

    @Transaction
    suspend fun removeMonth(month: String) {
        deleteDevotionals(month)
        deleteMonth(month)
    }
}
