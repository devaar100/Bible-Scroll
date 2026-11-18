package aarnav100.developer.biblescroll.data.user

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/*
 * User data lives in its own database (user.db), separate from the bundled Bible, and must never
 * be destructively migrated. Rows carry updated_at for future last-write-wins sync (spec §5).
 * Marks are keyed by reference, not translation, so a like follows the verse across translations.
 */

@Entity(tableName = "verse_marks", primaryKeys = ["book_id", "chapter", "verse"])
data class VerseMarkEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    @ColumnInfo(name = "chapter") val chapter: Int,
    @ColumnInfo(name = "verse") val verse: Int,
    /** Clearing a flag keeps the row (flag = false) so the change can sync. */
    @ColumnInfo(name = "liked") val liked: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** Added in v3; the default must match MIGRATION_2_3's ALTER TABLE. */
    @ColumnInfo(name = "bookmarked", defaultValue = "0") val bookmarked: Boolean = false,
    /** Added in v3 and backfilled from updated_at for rows that existed before. */
    @ColumnInfo(name = "created_at", defaultValue = "0") val createdAt: Long = updatedAt,
    /** Added in v4: the verse's deep study was opened (D2/B8). */
    @ColumnInfo(name = "studied", defaultValue = "0") val studied: Boolean = false,
    /** Added in v4: the user saved the verse's study (Library, H4). */
    @ColumnInfo(name = "study_saved", defaultValue = "0") val studySaved: Boolean = false,
    /*
     * Added in v5: when each flag was last turned on, for the Library's "Most recent" sort. Null for rows
     * from before v5 (sorting falls back to updated_at). Kept when a flag is turned off, so Undo can restore it.
     */
    @ColumnInfo(name = "liked_at") val likedAt: Long? = null,
    @ColumnInfo(name = "bookmarked_at") val bookmarkedAt: Long? = null,
    @ColumnInfo(name = "studied_at") val studiedAt: Long? = null,
    @ColumnInfo(name = "study_saved_at") val studySavedAt: Long? = null,
)

/** Library hub counts (H3). */
data class LibraryCounts(
    @ColumnInfo(name = "liked") val liked: Int,
    @ColumnInfo(name = "bookmarked") val bookmarked: Int,
    @ColumnInfo(name = "studied") val studied: Int,
    @ColumnInfo(name = "saved") val saved: Int,
    @ColumnInfo(name = "notes") val notes: Int,
)

/** A personal note on a verse (B6). Keyed by reference, so it shows in every translation. */
@Entity(tableName = "notes", primaryKeys = ["book_id", "chapter", "verse"])
data class NoteEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    @ColumnInfo(name = "chapter") val chapter: Int,
    @ColumnInfo(name = "verse") val verse: Int,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Just the key of a row, for "which verses have X" queries. */
data class VerseKey(
    @ColumnInfo(name = "book_id") val bookId: Int,
    @ColumnInfo(name = "chapter") val chapter: Int,
    @ColumnInfo(name = "verse") val verse: Int,
)

/** One row per book, so each book remembers where you were; the newest row is the resume point. */
@Entity(tableName = "reading_positions")
data class ReadingPositionEntity(
    @PrimaryKey
    @ColumnInfo(name = "book_id") val bookId: Int,
    @ColumnInfo(name = "chapter") val chapter: Int,
    @ColumnInfo(name = "verse") val verse: Int,
    @ColumnInfo(name = "translation_id") val translationId: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** A chapter marked read in the chapter grid (B4). Keyed by reference, so it applies to every translation. */
@Entity(tableName = "chapter_reads", primaryKeys = ["book_id", "chapter"])
data class ChapterReadEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    @ColumnInfo(name = "chapter") val chapter: Int,
    /** Unmarking keeps the row with read = false so the change can sync. */
    @ColumnInfo(name = "read") val read: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
