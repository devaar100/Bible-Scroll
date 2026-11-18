package aarnav100.developer.biblescroll.data.user

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v2 -> v3: bookmarks and created_at on verse_marks, plus the notes table (B1/B6). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `verse_marks` ADD COLUMN `bookmarked` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `verse_marks` ADD COLUMN `created_at` INTEGER NOT NULL DEFAULT 0")
        // Best guess for existing likes: they were created no later than their last update.
        db.execSQL("UPDATE `verse_marks` SET `created_at` = `updated_at`")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notes` (`book_id` INTEGER NOT NULL, `chapter` INTEGER NOT NULL, " +
                "`verse` INTEGER NOT NULL, `text` TEXT NOT NULL, `created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`book_id`, `chapter`, `verse`))",
        )
    }
}

/** Every manual migration, in order. Pass to the builder and to migration tests. */
val USER_DB_MIGRATIONS = arrayOf(MIGRATION_2_3)
