package aarnav100.developer.biblescroll.data.user

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/** The user's own data. Schema changes need a real migration (exported schemas in app/schemas). */
@Database(
    entities = [VerseMarkEntity::class, ReadingPositionEntity::class, ChapterReadEntity::class, NoteEntity::class],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // adds chapter_reads
        // 2 -> 3 is manual: MIGRATION_2_3 backfills created_at.
        AutoMigration(from = 3, to = 4), // adds verse_marks.studied and study_saved (default 0)
        AutoMigration(from = 4, to = 5), // adds verse_marks.liked_at/bookmarked_at/studied_at/study_saved_at (null)
    ],
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        const val FILE_NAME = "user.db"
    }
}
