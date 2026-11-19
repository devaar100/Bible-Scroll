package aarnav100.developer.biblescroll.data.backup

import aarnav100.developer.biblescroll.data.user.USER_DB_MIGRATIONS
import aarnav100.developer.biblescroll.data.user.UserDatabase
import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Backup against real SQLite/Room. Every test uses its own throwaway database file: the app's real user.db
 * is never opened.
 */
@RunWith(AndroidJUnit4::class)
class BackupDbTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val opened = mutableListOf<UserDatabase>()
    private val files = mutableListOf<String>()

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UserDatabase::class.java)

    @After
    fun tearDown() {
        opened.forEach { it.close() }
        files.forEach {
            context.deleteDatabase(it)
            File(context.getDatabasePath(it).path + ".lck").delete() // left by MigrationTestHelper
        }
    }

    private fun memoryDb() = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java).build().also { opened += it }

    private fun fileDb(name: String) = Room.databaseBuilder(context, UserDatabase::class.java, name)
        .addMigrations(*USER_DB_MIGRATIONS)
        .openHelperFactory(NoDeleteOpenHelperFactory(File(context.cacheDir, "rescue-$name")))
        .build().also { opened += it }

    private fun dataStore() = PreferenceDataStoreFactory.create { File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb") }

    private val planJson = """{"id":"p1","contentVersion":"v1","title":"P","shortDescription":"s","description":"d",
        "category":"c","status":"auto_checked","origin":"structural","estimatedMinutesPerDay":5,"difficulty":"easy",
        "days":[{"dayNumber":1,"passages":[{"book":"JHN","startChapter":1,"startVerse":1,"endChapter":1,"endVerse":5}]},
                {"dayNumber":2,"passages":[{"book":"JHN","startChapter":2,"startVerse":1,"endChapter":2,"endVerse":5}]}]}"""

    private fun sample(at: Long) = BackupTables(
        verseMarks = listOf(VerseMarkRow(43, 3, 16, liked = true, updatedAt = at, bookmarked = true, createdAt = at, likedAt = at)),
        readingPositions = listOf(ReadingPositionRow(43, 3, 16, "WEB", at)),
        chapterReads = listOf(ChapterReadRow(43, 3, true, at)),
        notes = listOf(NoteRow(43, 3, 16, "A note ✝ with \"quotes\"\nand two lines", at, at)),
        streakDays = listOf(StreakDayRow("2026-11-17", 10, 1, 0, at, 60, at)),
        planEnrollments = listOf(PlanEnrollmentRow("e1", "p1", "P", "v1", planJson, 2, 5, "2026-11-01", 480, null, at, at)),
        planDayProgress = listOf(PlanDayProgressRow("e1", 1, true, at, at)),
        customPlans = listOf(CustomPlanRow("custom-1", "Mine", "43", "days", 2, 2, 5, planJson, at, at)),
        devotionalProgress = listOf(DevotionalProgressRow("2026-11-17", "d-1117", "v1", at, at)),
    )

    @Test
    fun exportThenImportIntoAnotherDatabaseCopiesEveryTable() = runBlocking {
        val source = RoomBackupStore(memoryDb())
        source.mergeInTransaction { sample(1_000) }
        val text = BackupExporter(source, dataStore(), "test").let { it.encode(it.export()) }

        val target = RoomBackupStore(memoryDb())
        val result = BackupImporter(target, dataStore()) {}.import(text)

        assertTrue(result is ImportResult.Imported)
        assertEquals(source.readAll(), target.readAll())
    }

    @Test
    fun aFailureMidImportRollsBackEveryTable() = runBlocking {
        val db = memoryDb()
        val store = RoomBackupStore(db)
        store.mergeInTransaction { BackupTables(notes = listOf(NoteRow(1, 1, 1, "already here", 5, 5))) }
        val before = store.readAll()
        // devotional_progress is written last, so every other table has been written when this fires.
        db.openHelper.writableDatabase.execSQL(
            "CREATE TEMP TRIGGER fail BEFORE INSERT ON devotional_progress BEGIN SELECT RAISE(ABORT, 'disk full'); END",
        )
        val text = BackupExporter(RoomBackupStore(memoryDb()).apply { mergeInTransaction { sample(2_000) } }, dataStore(), "test")
            .let { it.encode(it.export()) }

        val failure = runCatching { BackupImporter(store, dataStore()) {}.import(text) }.exceptionOrNull()

        assertTrue("expected a SQLite failure, got $failure", failure is SQLiteException)
        assertEquals(before, store.readAll())
    }

    @Test
    fun anOlderDatabaseRestoredFromBackupMigratesAndExports() = runBlocking {
        // A user.db from app version 1 (schema v1), as Auto Backup would restore it.
        val name = "restored-v1-${UUID.randomUUID()}.db".also { files += it }
        helper.createDatabase(name, 1).use { db ->
            db.execSQL("INSERT INTO verse_marks (book_id, chapter, verse, liked, updated_at) VALUES (43, 3, 16, 1, 100)")
            db.execSQL("INSERT INTO reading_positions (book_id, chapter, verse, translation_id, updated_at) VALUES (19, 23, 4, 'WEB', 200)")
        }
        val tables = RoomBackupStore(fileDb(name)).readAll()
        assertEquals(VerseMarkRow(43, 3, 16, liked = true, updatedAt = 100, createdAt = 100), tables.verseMarks.single())
        assertEquals(ReadingPositionRow(19, 23, 4, "WEB", 200), tables.readingPositions.single())
    }

    @Test
    fun aMidVersionDatabaseMigratesAndExports() = runBlocking {
        val name = "restored-v5-${UUID.randomUUID()}.db".also { files += it }
        helper.createDatabase(name, 5).use { db ->
            db.execSQL(
                "INSERT INTO verse_marks (book_id, chapter, verse, liked, updated_at, bookmarked, created_at, studied, study_saved, liked_at) " +
                    "VALUES (43, 3, 16, 1, 100, 1, 50, 0, 0, 100)",
            )
            db.execSQL("INSERT INTO notes (book_id, chapter, verse, text, created_at, updated_at) VALUES (43, 3, 16, 'kept', 50, 100)")
        }
        val store = RoomBackupStore(fileDb(name))
        val text = BackupExporter(store, dataStore(), "test").let { it.encode(it.export()) }
        val target = RoomBackupStore(memoryDb())
        BackupImporter(target, dataStore()) {}.import(text)
        assertEquals("kept", target.readAll().notes.single().text)
        assertTrue(target.readAll().verseMarks.single().bookmarked)
    }

    @Test
    fun aCorruptDatabaseIsNeverDeleted() {
        val name = "corrupt-${UUID.randomUUID()}.db".also { files += it }
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val garbage = ByteArray(8_192) { (it * 31 % 251).toByte() }.also { bytes ->
            "SQLite format 3\u0000".toByteArray().copyInto(bytes) // looks like SQLite, isn't
        }
        file.writeBytes(garbage)

        val db = fileDb(name)
        val opened = runCatching {
            db.openHelper.writableDatabase.query("SELECT count(*) FROM sqlite_master").use { it.moveToFirst() }
        }

        assertTrue("a corrupt file must not open as an empty database", opened.isFailure)
        assertTrue(file.exists())
        assertArrayEquals(garbage, file.readBytes())
        val rescued = File(context.cacheDir, "rescue-$name").walkTopDown().filter { it.name == name }.toList()
        assertFalse("expected a rescue copy", rescued.isEmpty())
        File(context.cacheDir, "rescue-$name").deleteRecursively()
    }
}
