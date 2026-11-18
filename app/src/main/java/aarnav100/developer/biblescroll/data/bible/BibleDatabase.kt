package aarnav100.developer.biblescroll.data.bible

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Bundled, pre-populated Scripture database (copied from assets/bible.db on first open).
 * Kept separate from the user database so it can be replaced wholesale on app updates.
 *
 * To ship new text: bump [version], build once (exports the schema), rerun
 * tools/build_bible_db.py. The destructive fallback in DatabaseModule then re-copies the asset.
 */
@Database(
    entities = [
        TranslationEntity::class,
        BookEntity::class,
        ChapterEntity::class,
        VerseEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class BibleDatabase : RoomDatabase() {
    abstract fun bibleDao(): BibleDao

    companion object {
        const val ASSET_PATH = "bible.db"
        const val FILE_NAME = "bible.db"
    }
}
