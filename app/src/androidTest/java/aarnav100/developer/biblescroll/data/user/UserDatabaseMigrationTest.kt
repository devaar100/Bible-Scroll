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

    @Test
    fun migrate5To6AddsEmptyStreakDaysAndKeepsMarks() {
        helper.createDatabase(TEST_DB, 5).use { db ->
            db.execSQL("INSERT INTO verse_marks (book_id, chapter, verse, liked, updated_at, bookmarked, created_at, studied, study_saved) VALUES (43, 3, 16, 1, 100, 0, 50, 0, 0)")
        }
        helper.runMigrationsAndValidate(TEST_DB, 6, true, *USER_DB_MIGRATIONS).use { db ->
            db.query("SELECT COUNT(*) FROM streak_days").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            db.query("SELECT liked FROM verse_marks").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            db.execSQL("INSERT INTO streak_days (date, verses_viewed, plan_days_completed, devotionals_finished, counted_at, utc_offset_minutes, updated_at) VALUES ('2026-11-18', 5, 0, 0, 1, 60, 1)")
        }
    }

    @Test
    fun migrate6To7AddsEmptyPlanTablesAndKeepsStreaks() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.execSQL("INSERT INTO streak_days (date, verses_viewed, plan_days_completed, devotionals_finished, counted_at, utc_offset_minutes, updated_at) VALUES ('2026-11-18', 5, 0, 0, 1, 60, 1)")
        }
        helper.runMigrationsAndValidate(TEST_DB, 7, true, *USER_DB_MIGRATIONS).use { db ->
            db.query("SELECT COUNT(*) FROM streak_days").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            db.query("SELECT COUNT(*) FROM plan_enrollments").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            db.query("SELECT COUNT(*) FROM plan_day_progress").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test
    fun migrate7To8AddsCustomPlansAndKeepsEnrollments() {
        helper.createDatabase(TEST_DB, 7).use { db ->
            db.execSQL("INSERT INTO plan_enrollments (id, plan_id, plan_title, plan_content_version, plan_json, total_days, minutes_per_day, start_date, reminder_minute_of_day, completed_at, created_at, updated_at) VALUES ('e1', 'john-21-days', 'John', 'v', '{}', 21, 5, '2026-11-18', NULL, NULL, 1, 1)")
        }
        helper.runMigrationsAndValidate(TEST_DB, 8, true, *USER_DB_MIGRATIONS).use { db ->
            db.query("SELECT COUNT(*) FROM plan_enrollments").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            db.execSQL("INSERT INTO custom_plans (id, title, book_ids, pace_kind, pace_value, total_days, minutes_per_day, plan_json, created_at, updated_at) VALUES ('custom-1', 'Mine', '43', 'days', 21, 21, 5, '{}', 1, 1)")
            db.query("SELECT deleted FROM custom_plans").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test
    fun migrate8To9AddsDevotionalProgress() {
        helper.createDatabase(TEST_DB, 8).close()
        helper.runMigrationsAndValidate(TEST_DB, 9, true, *USER_DB_MIGRATIONS).use { db ->
            db.execSQL("INSERT INTO devotional_progress (read_for, devotional_id, content_version, finished_at, updated_at) VALUES ('2026-11-18', 'devotional-11-18', 'v', 1, 1)")
            db.query("SELECT COUNT(*) FROM devotional_progress").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        }
    }

    @Test
    fun migrate9To10AddsCommunityVotesAndKeepsDevotionals() {
        helper.createDatabase(TEST_DB, 9).use { db ->
            db.execSQL("INSERT INTO devotional_progress (read_for, devotional_id, content_version, finished_at, updated_at) VALUES ('2026-11-18', 'devotional-11-18', 'v', 1, 1)")
        }
        helper.runMigrationsAndValidate(TEST_DB, 10, true, *USER_DB_MIGRATIONS).use { db ->
            db.query("SELECT COUNT(*) FROM devotional_progress").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            db.execSQL("INSERT INTO community_votes (month, ministry_id, voted_at, sent, updated_at) VALUES ('2026-12', 'river', 1, 0, 1)")
            // One vote per month: the month is the primary key.
            db.execSQL("INSERT OR IGNORE INTO community_votes (month, ministry_id, voted_at, sent, updated_at) VALUES ('2026-12', 'harbor', 2, 0, 2)")
            db.query("SELECT ministry_id FROM community_votes").use { it.moveToFirst(); assertEquals("river", it.getString(0)); assertEquals(1, it.count) }
        }
    }

    private companion object {
        const val TEST_DB = "user-migration-test.db"
    }
}
