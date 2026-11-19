package aarnav100.developer.biblescroll.data.backup

import aarnav100.developer.biblescroll.data.user.UserDatabase
import aarnav100.developer.biblescroll.testing.FakeBackupStore
import aarnav100.developer.biblescroll.testing.FakePreferencesDataStore
import aarnav100.developer.biblescroll.testing.everyTable
import aarnav100.developer.biblescroll.testing.note
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BackupImportExportTest {

    private val sourceSettings = FakePreferencesDataStore()
    private val source = FakeBackupStore(everyTable())
    private val exporter = BackupExporter(source, sourceSettings, "1.0 (1)") { Instant.parse("2026-11-18T14:05:00Z") }

    private val target = FakeBackupStore()
    private val targetSettings = FakePreferencesDataStore()
    private var afterImportCalls = 0
    private val importer = BackupImporter(target, targetSettings) { afterImportCalls++ }

    private suspend fun exported(): String {
        sourceSettings.edit {
            it[stringPreferencesKey("theme_mode")] = "Dark"
            it[intPreferencesKey("reminder_minute_of_day")] = 1_290
            it[booleanPreferencesKey("onboarding_complete")] = true // device state: never exported
            it[stringPreferencesKey("translation_id")] = "KJV"
        }
        return exporter.encode(exporter.export())
    }

    // ---- Export ----

    @Test
    fun exportHasHeaderEveryTableAndOnlyExportableSettings() = runTest {
        val json = BackupJson.parseToJsonElement(exported()).jsonObject
        assertEquals(BACKUP_FORMAT, (json["format"] as JsonPrimitive).content)
        assertEquals(UserDatabase.VERSION.toString(), (json["schemaVersion"] as JsonPrimitive).content)
        assertEquals("1.0 (1)", (json["appVersion"] as JsonPrimitive).content)
        assertEquals("2026-11-18T14:05:00Z", (json["exportedAt"] as JsonPrimitive).content)
        val tables = json["tables"] as JsonObject
        assertEquals(
            setOf("verse_marks", "reading_positions", "chapter_reads", "notes", "streak_days", "plan_enrollments",
                "plan_day_progress", "custom_plans", "devotional_progress"),
            tables.keys,
        )
        assertEquals(setOf("theme_mode", "reminder_minute_of_day", "translation_id"), (json["settings"] as JsonObject).keys)
    }

    // ---- Round trip ----

    @Test
    fun exportThenImportOnAnEmptyPhoneRestoresEverything() = runTest {
        val result = importer.import(exported())
        assertTrue(result is ImportResult.Imported)
        assertEquals(source.tables, target.tables)
        val prefs = targetSettings.data.first()
        assertEquals("Dark", prefs[stringPreferencesKey("theme_mode")])
        assertEquals(1_290, prefs[intPreferencesKey("reminder_minute_of_day")])
        assertEquals(null, prefs[booleanPreferencesKey("onboarding_complete")])
        assertEquals(3, (result as ImportResult.Imported).summary.settingsApplied)
        assertEquals(1, afterImportCalls)
    }

    @Test
    fun importKeepsThisPhonesSettingsAndOnlyFillsGaps() = runTest {
        targetSettings.edit { it[stringPreferencesKey("theme_mode")] = "Light" }
        importer.import(exported())
        val prefs = targetSettings.data.first()
        assertEquals("Light", prefs[stringPreferencesKey("theme_mode")])
        assertEquals("KJV", prefs[stringPreferencesKey("translation_id")])
    }

    @Test
    fun widgetVerseIsTakenWholeOrNotAtAll() = runTest {
        sourceSettings.edit {
            it[intPreferencesKey("widget_book")] = 43; it[intPreferencesKey("widget_chapter")] = 3
            it[intPreferencesKey("widget_verse")] = 16; it[stringPreferencesKey("widget_translation")] = "WEB"
        }
        targetSettings.edit { it[intPreferencesKey("widget_book")] = 19 } // this phone already has a widget verse
        importer.import(exported())
        val prefs = targetSettings.data.first()
        assertEquals(19, prefs[intPreferencesKey("widget_book")])
        assertEquals(null, prefs[intPreferencesKey("widget_verse")])
    }

    @Test
    fun importingTheSameFileTwiceReportsNothingNew() = runTest {
        val file = exported()
        importer.import(file)
        val second = importer.import(file) as ImportResult.Imported
        assertTrue(second.summary.nothingNew)
        assertEquals(1, afterImportCalls)
    }

    // ---- Bad files: rejected before anything is touched ----

    private suspend fun assertRejected(text: String, expected: (ImportError) -> Boolean) {
        target.tables = everyTable(at = 9_000).copy(notes = listOf(note(1, 1, 1, "keep me", at = 9_000)))
        val before = target.tables
        val result = importer.import(text)
        assertTrue("expected rejection, got $result", result is ImportResult.Rejected && expected(result.error))
        assertEquals(before, target.tables)
        assertEquals(0, target.transactions)
        assertTrue(targetSettings.data.first().asMap().isEmpty())
        assertEquals(0, afterImportCalls)
    }

    private suspend fun exportedWith(edit: (String) -> String) = edit(exported())

    @Test
    fun rejectsNonJson() = runTest { assertRejected("not json {") { it == ImportError.NotABackup } }

    @Test
    fun rejectsTruncatedFile() = runTest {
        val file = exported()
        assertRejected(file.take(file.length / 2)) { it == ImportError.NotABackup }
    }

    @Test
    fun rejectsOtherJson() = runTest { assertRejected("""{"hello":"world"}""") { it == ImportError.NotABackup } }

    @Test
    fun rejectsFileFromANewerApp() = runTest {
        assertRejected(exportedWith { it.replace("\"schemaVersion\": ${UserDatabase.VERSION}", "\"schemaVersion\": 99") }) {
            it == ImportError.TooNew(99)
        }
    }

    @Test
    fun rejectsMissingRequiredField() = runTest {
        assertRejected(exportedWith { it.replace("\"text\":", "\"txet\":") }) { it is ImportError.Damaged }
    }

    @Test
    fun rejectsWrongType() = runTest {
        assertRejected(exportedWith { it.replace("\"liked\": true", "\"liked\": \"yes\"") }) { it is ImportError.Damaged }
    }

    @Test
    fun rejectsOutOfRangeVerse() = runTest {
        assertRejected(exportedWith { it.replace("\"book_id\": 43", "\"book_id\": 99") }) { it is ImportError.Damaged }
    }

    @Test
    fun rejectsBadDate() = runTest {
        assertRejected(exportedWith { it.replace("\"2026-11-17\"", "\"17/11/2026\"") }) { it is ImportError.Damaged }
    }

    @Test
    fun rejectsDuplicateRows() = runTest {
        val file = BackupFile(BACKUP_FORMAT, UserDatabase.VERSION, "1.0", "2026-11-18T14:05:00Z",
            BackupTables(notes = listOf(note(1, 1, 1, "a", 1), note(1, 1, 1, "b", 2))))
        assertRejected(BackupJson.encodeToString(BackupFile.serializer(), file)) { it is ImportError.Damaged }
    }

    @Test
    fun rejectsPlanDayForAPlanThatIsntInTheFile() = runTest {
        val tables = everyTable()
        val file = BackupFile(BACKUP_FORMAT, UserDatabase.VERSION, "1.0", "2026-11-18T14:05:00Z",
            tables.copy(planEnrollments = emptyList()))
        assertRejected(BackupJson.encodeToString(BackupFile.serializer(), file)) { it is ImportError.Damaged }
    }

    @Test
    fun rejectsUnreadablePlan() = runTest {
        val tables = everyTable()
        val file = BackupFile(BACKUP_FORMAT, UserDatabase.VERSION, "1.0", "2026-11-18T14:05:00Z",
            tables.copy(customPlans = tables.customPlans.map { it.copy(planJson = "{}") }))
        assertRejected(BackupJson.encodeToString(BackupFile.serializer(), file)) { it is ImportError.Damaged }
    }

    @Test
    fun rejectsSettingOfTheWrongType() = runTest {
        assertRejected(exportedWith { it.replace("\"reminder_minute_of_day\": 1290", "\"reminder_minute_of_day\": \"late\"") }) {
            it is ImportError.Damaged
        }
    }

    @Test
    fun aWriteThatFailsChangesNothing() = runTest {
        val before = target.tables
        target.failWrites = true
        val failure = runCatching { importer.import(exported()) }.exceptionOrNull()
        assertTrue(failure is java.io.IOException)
        assertEquals(before, target.tables)
        assertTrue(targetSettings.data.first().asMap().isEmpty()) // settings come after the transaction
        assertEquals(0, afterImportCalls)
    }

    @Test
    fun fileFromAnOlderSchemaWithFewerTablesAndColumnsImports() = runTest {
        // What a v3 export would look like: no streak/plan/devotional tables, no v4/v5 mark columns.
        val v3 = """
            {"format":"$BACKUP_FORMAT","schemaVersion":3,"appVersion":"0.3","exportedAt":"2026-01-01T00:00:00Z",
             "tables":{"verse_marks":[{"book_id":43,"chapter":3,"verse":16,"liked":true,"updated_at":5,"bookmarked":true,"created_at":5}],
                       "notes":[{"book_id":43,"chapter":3,"verse":16,"text":"old","created_at":5,"updated_at":5}]}}
        """.trimIndent()
        val result = importer.import(v3) as ImportResult.Imported
        assertEquals(1, result.summary.likesAdded)
        assertEquals(1, result.summary.bookmarksAdded)
        assertFalse(target.tables.verseMarks.single().studied)
    }
}
