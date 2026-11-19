package aarnav100.developer.biblescroll.data.backup

import aarnav100.developer.biblescroll.data.user.UserDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards future sessions: a table or column added to user.db must be added to the export format
 * (data/backup/BackupFormat.kt), and every DataStore key must be marked exported or not.
 * Reads Room's exported schema (app/schemas), so it checks what's really in the database.
 */
class ExportCoversSchemaTest {

    private val schemaDir = File("schemas/${UserDatabase::class.qualifiedName}")

    private fun latestSchema(): Pair<Int, File> {
        val files = schemaDir.listFiles { f -> f.name.matches(Regex("\\d+\\.json")) }.orEmpty()
        assertTrue("No Room schemas found in ${schemaDir.absolutePath}", files.isNotEmpty())
        return files.map { it.nameWithoutExtension.toInt() to it }.maxBy { it.first }
    }

    @Test
    fun schemaVersionIsTheLatestRoomSchema() {
        assertEquals(
            "UserDatabase.VERSION (written to exports as schemaVersion) must match the newest file in app/schemas",
            latestSchema().first, UserDatabase.VERSION,
        )
    }

    @Test
    fun everyTableAndColumnIsExported() {
        val db = Json.parseToJsonElement(latestSchema().second.readText()).jsonObject.getValue("database").jsonObject
        val schema: Map<String, Set<String>> = db.getValue("entities").jsonArray.associate { e ->
            val entity = e.jsonObject
            entity.getValue("tableName").jsonPrimitive.content to
                entity.getValue("fields").jsonArray.map { it.jsonObject.getValue("columnName").jsonPrimitive.content }.toSet()
        }

        val tables = BackupTables.serializer().descriptor
        val exported: Map<String, Set<String>> = (0 until tables.elementsCount).associate { i ->
            val row = tables.getElementDescriptor(i).getElementDescriptor(0) // List<Row> -> Row
            tables.getElementName(i) to (0 until row.elementsCount).map(row::getElementName).toSet()
        }

        val missingTables = schema.keys - exported.keys
        assertTrue("Tables in user.db but not in the export: $missingTables (add them to BackupTables)", missingTables.isEmpty())
        val extraTables = exported.keys - schema.keys
        assertTrue("Export has tables user.db doesn't: $extraTables", extraTables.isEmpty())
        schema.forEach { (table, columns) ->
            val missing = columns - exported.getValue(table)
            assertTrue("Columns of $table not in the export: $missing (add them to its Row class)", missing.isEmpty())
            val extra = exported.getValue(table) - columns
            assertTrue("Export fields of $table that aren't columns: $extra", extra.isEmpty())
        }
    }

    @Test
    fun everyDataStoreKeyIsMarkedExportedOrNot() {
        val keyPattern = Regex("""(?:boolean|int|long|float|double|string|stringSet)PreferencesKey\("([^"]+)"\)""")
        val keys = File("src/main/java").walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { keyPattern.findAll(it.readText()).map { m -> m.groupValues[1] } }
            .toSet()
        assertTrue("Found no DataStore keys: is the source path right?", keys.isNotEmpty())
        val unmarked = keys - BackupSettings.EXPORTED.keys - BackupSettings.NOT_EXPORTED
        assertTrue("DataStore keys not in BackupSettings.EXPORTED or NOT_EXPORTED: $unmarked", unmarked.isEmpty())
        val both = BackupSettings.EXPORTED.keys intersect BackupSettings.NOT_EXPORTED
        assertTrue("Keys marked both exported and not: $both", both.isEmpty())
    }
}
