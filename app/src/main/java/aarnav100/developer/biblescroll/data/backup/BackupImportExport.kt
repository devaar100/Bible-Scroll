package aarnav100.developer.biblescroll.data.backup

import aarnav100.developer.biblescroll.data.user.UserDatabase
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

/** Builds the export file from the user database and the exportable settings. */
class BackupExporter(
    private val store: BackupStore,
    private val dataStore: DataStore<Preferences>,
    private val appVersion: String,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun export(): BackupFile {
        val prefs = dataStore.data.first().asMap().mapKeys { it.key.name }
        val settings = BackupSettings.EXPORTED.keys.mapNotNull { key ->
            when (val value = prefs[key]) {
                is Boolean -> key to JsonPrimitive(value)
                is Int -> key to JsonPrimitive(value)
                is String -> key to JsonPrimitive(value)
                else -> null
            }
        }.toMap()
        return BackupFile(
            format = BACKUP_FORMAT,
            schemaVersion = UserDatabase.VERSION,
            appVersion = appVersion,
            exportedAt = now().toString(),
            tables = store.readAll(),
            settings = settings,
        )
    }

    fun encode(file: BackupFile): String = BackupJson.encodeToString(BackupFile.serializer(), file)
}

sealed interface ImportResult {
    data class Imported(val summary: ImportSummary) : ImportResult
    data class Rejected(val error: ImportError) : ImportResult
}

/**
 * Import (S9): validate the whole file, then merge it into the user database in one transaction (see
 * [mergeBackup]), then fill in settings this phone hasn't set. A rejected file changes nothing; existing
 * data is never removed.
 */
class BackupImporter(
    private val store: BackupStore,
    private val dataStore: DataStore<Preferences>,
    /** Re-arm reminders and redraw widgets once the data has changed. */
    private val afterImport: suspend () -> Unit,
) {
    suspend fun import(text: String): ImportResult {
        val file = when (val v = BackupValidator.validate(text)) {
            is Validated.Rejected -> return ImportResult.Rejected(v.error)
            is Validated.Ok -> v.file
        }
        var summary = ImportSummary()
        store.mergeInTransaction { existing ->
            mergeBackup(existing, file.tables).also { summary = it.summary }.write
        }
        // Settings live in DataStore, outside the database transaction. The file has already been
        // validated, so this only fills gaps; it never overwrites a choice made on this phone.
        val applied = fillSettings(file.settings)
        summary = summary.copy(settingsApplied = applied)
        if (!summary.nothingNew) afterImport()
        return ImportResult.Imported(summary)
    }

    /** Returns how many settings (a widget verse counts as one) were taken from the file. */
    private suspend fun fillSettings(settings: Map<String, JsonPrimitive>): Int {
        val units = BackupSettings.EXPORTED.keys
            .filter { key -> BackupSettings.GROUPS.none { key in it } }
            .map { setOf(it) } + BackupSettings.GROUPS
        var applied = 0
        dataStore.edit { prefs ->
            val present = prefs.asMap().keys.map { it.name }.toSet()
            for (unit in units) {
                if (unit.any { it in present } || !unit.all { it in settings }) continue
                val values = unit.associateWith { key -> settings.getValue(key).settingValue(BackupSettings.EXPORTED.getValue(key)) }
                if (values.values.any { it == null }) continue
                values.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> prefs[booleanPreferencesKey(key)] = value
                        is Int -> prefs[intPreferencesKey(key)] = value
                        is String -> prefs[stringPreferencesKey(key)] = value
                    }
                }
                applied++
            }
        }
        return applied
    }
}
