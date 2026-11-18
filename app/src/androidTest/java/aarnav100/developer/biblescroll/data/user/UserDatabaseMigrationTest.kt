package aarnav100.developer.biblescroll.data.user

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The user DB holds likes and reading progress, so every migration must keep existing rows. */
@RunWith(AndroidJUnit4::class)
class UserDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        UserDatabase::class.java,
    )

    @Test
    fun migrate1To2KeepsDataAndAddsChapterReads() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO verse_marks (book_id, chapter, verse, liked, updated_at) VALUES (43, 3, 16, 1, 100)")
            db.execSQL(
                "INSERT INTO reading_positions (book_id, chapter, verse, translation_id, updated_at) " +
                    "VALUES (19, 23, 4, 'WEB', 200)",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true).use { db ->
            db.query("SELECT COUNT(*) FROM verse_marks WHERE book_id = 43 AND liked = 1").use {
                it.moveToFirst(); assertEquals(1, it.getInt(0))
            }
            db.query("SELECT verse FROM reading_positions WHERE book_id = 19").use {
                it.moveToFirst(); assertEquals(4, it.getInt(0))
            }
            db.execSQL("INSERT INTO chapter_reads (book_id, chapter, read, updated_at) VALUES (1, 1, 1, 300)")
            db.query("SELECT COUNT(*) FROM chapter_reads").use {
                it.moveToFirst(); assertEquals(1, it.getInt(0))
            }
        }
    }

    @Test
    fun migrate2To3KeepsLikesAndAddsBookmarksAndNotes() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL("INSERT INTO verse_marks (book_id, chapter, verse, liked, updated_at) VALUES (43, 3, 16, 1, 100)")
            db.execSQL("INSERT INTO chapter_reads (book_id, chapter, read, updated_at) VALUES (1, 1, 1, 300)")
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, *USER_DB_MIGRATIONS).use { db ->
            db.query("SELECT liked, bookmarked, created_at, updated_at FROM verse_marks WHERE book_id = 43").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
                assertEquals(0, it.getInt(1))
                assertEquals(100L, it.getLong(2)) // backfilled from updated_at
                assertEquals(100L, it.getLong(3))
            }
            db.query("SELECT COUNT(*) FROM chapter_reads").use {
                it.moveToFirst(); assertEquals(1, it.getInt(0))
            }
            db.execSQL(
                "INSERT INTO notes (book_id, chapter, verse, text, created_at, updated_at) VALUES (43, 3, 16, 'hi', 1, 1)",
            )
            db.query("SELECT text FROM notes").use {
                it.moveToFirst(); assertEquals("hi", it.getString(0))
            }
        }
    }

    @Test
    fun migrate1To3() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO verse_marks (book_id, chapter, verse, liked, updated_at) VALUES (43, 3, 16, 1, 100)")
        }
        helper.runMigrationsAndValidate(TEST_DB, 3, true, *USER_DB_MIGRATIONS).use { db ->
            db.query("SELECT COUNT(*) FROM verse_marks WHERE liked = 1").use {
                it.moveToFirst(); assertEquals(1, it.getInt(0))
            }
        }
    }

    @Test
    fun migrate4To5AddsEmptyDatesAndKeepsFlags() {
        helper.createDatabase(TEST_DB, 4).use { db ->
            db.execSQL(
                "INSERT INTO verse_marks (book_id, chapter, verse, liked, updated_at, bookmarked, created_at, studied, study_saved) " +
                    "VALUES (43, 3, 16, 1, 100, 0, 50, 1, 1)",
            )
        }
        helper.runMigrationsAndValidate(TEST_DB, 5, true, *USER_DB_MIGRATIONS).use { db ->
            db.query("SELECT liked, studied, study_saved, liked_at, studied_at, study_saved_at, bookmarked_at FROM verse_marks").use {
                it.moveToFirst()
                assertEquals(listOf(1, 1, 1), (0..2).map { i -> it.getInt(i) })
                assertEquals(listOf(true, true, true, true), (3..6).map { i -> it.isNull(i) }) // sort falls back to updated_at
            }
        }
    }

    @Test
    fun migrate3To4KeepsMarksAndAddsStudyFlags() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO verse_marks (book_id, chapter, verse, liked, updated_at, bookmarked, created_at) " +
                    "VALUES (43, 3, 16, 1, 100, 1, 50)",
            )
            db.execSQL("INSERT INTO notes (book_id, chapter, verse, text, created_at, updated_at) VALUES (43, 3, 16, 'hi', 1, 1)")
        }

        helper.runMigrationsAndValidate(TEST_DB, 4, true, *USER_DB_MIGRATIONS).use { db ->
            db.query("SELECT liked, bookmarked, created_at, studied, study_saved FROM verse_marks WHERE book_id = 43").use {
                it.moveToFirst()
                assertEquals(listOf(1, 1, 50, 0, 0), (0..4).map { i -> it.getInt(i) })
            }
            db.query("SELECT text FROM notes").use { it.moveToFirst(); assertEquals("hi", it.getString(0)) }
        }
    }

    @Test
    fun migrate1To5() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO verse_marks (book_id, chapter, verse, liked, updated_at) VALUES (43, 3, 16, 1, 100)")
        }
        helper.runMigrationsAndValidate(TEST_DB, 5, true, *USER_DB_MIGRATIONS).use { db ->
            db.query("SELECT liked, studied, study_saved FROM verse_marks").use {
                it.moveToFirst()
                assertEquals(listOf(1, 0, 0), (0..2).map { i -> it.getInt(i) })
            }
        }
    }

    private companion object {
        const val TEST_DB = "user-migration-test.db"
    }
}
