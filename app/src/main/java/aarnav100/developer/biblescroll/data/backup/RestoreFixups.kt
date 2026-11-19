package aarnav100.developer.biblescroll.data.backup

import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.widget.WidgetRefresher
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Set by the backup agent after Android restores our data; lives in noBackupFilesDir so it never travels. */
object RestoreMarker {
    private fun file(context: Context) = File(context.noBackupFilesDir, "restored-from-backup")
    fun set(context: Context) {
        try {
            file(context).createNewFile()
        } catch (e: IOException) {
            Log.w("RestoreMarker", "Couldn't write the restore marker", e)
        }
    }
    fun isSet(context: Context) = file(context).exists()
    fun clear(context: Context) = file(context).delete()
}

/**
 * First launch after a restore. Onboarding is already skipped (its flag is restored) and reminders are already
 * re-armed (BibleScrollApplication does that on every start). This fixes what can point at things the new
 * phone doesn't have, and raises the Home "welcome back" notice.
 */
class RestoreRepair(
    private val dataStore: DataStore<Preferences>,
    private val bible: BibleRepository,
    private val widgets: WidgetRefresher,
) {
    suspend fun repair() {
        val translations = bible.translations().first().map { it.id }.toSet()
        val prefs = dataStore.data.first()

        val selected = prefs[TRANSLATION_ID]
        val translationGone = selected != null && selected !in translations

        val widgetTranslation = prefs[WIDGET_TRANSLATION]
        val widgetRef = listOf(prefs[WIDGET_BOOK], prefs[WIDGET_CHAPTER], prefs[WIDGET_VERSE])
        val widgetGone = widgetTranslation != null && (
            widgetTranslation !in translations || widgetRef.any { it == null } ||
                bible.verse(widgetTranslation, VerseRef(widgetRef[0]!!, widgetRef[1]!!, widgetRef[2]!!)).first() == null
            )

        dataStore.edit {
            if (translationGone) it[TRANSLATION_ID] = DEFAULT_TRANSLATION
            if (widgetGone) {
                // Back to the default widget verse (WidgetVerseRepository.DEFAULT).
                it.remove(WIDGET_BOOK); it.remove(WIDGET_CHAPTER); it.remove(WIDGET_VERSE); it.remove(WIDGET_TRANSLATION)
            }
            it[RESTORE_NOTICE] = true
        }
        // Launchers may restore a placed widget; draw it with this phone's data either way.
        widgets.refresh()
    }

    companion object {
        const val DEFAULT_TRANSLATION = "WEB"
        private val TRANSLATION_ID = stringPreferencesKey("translation_id")
        private val WIDGET_BOOK = intPreferencesKey("widget_book")
        private val WIDGET_CHAPTER = intPreferencesKey("widget_chapter")
        private val WIDGET_VERSE = intPreferencesKey("widget_verse")
        private val WIDGET_TRANSLATION = stringPreferencesKey("widget_translation")
        val RESTORE_NOTICE = booleanPreferencesKey("restore_notice")
    }
}

/** Runs [RestoreRepair] once per restore, from BibleScrollApplication. */
@Singleton
class RestoreFixups @Inject constructor(
    @ApplicationContext private val context: Context,
    dataStore: DataStore<Preferences>,
    bible: BibleRepository,
    widgets: WidgetRefresher,
) {
    private val repair = RestoreRepair(dataStore, bible, widgets)

    suspend fun runIfRestored() {
        if (!RestoreMarker.isSet(context)) return
        try {
            repair.repair()
            RestoreMarker.clear(context)
        } catch (e: Exception) {
            // Left set, so the next launch tries again.
            Log.w("RestoreFixups", "Restore fix-ups failed", e)
        }
    }
}

/** The one-time Home notice after a restore, and the Library → Notes export tip. */
@Singleton
class BackupNotices @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private val prefs = dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    val restoreNotice: Flow<Boolean> = prefs.map { it[RestoreRepair.RESTORE_NOTICE] ?: false }
    suspend fun dismissRestoreNotice() = dataStore.edit { it.remove(RestoreRepair.RESTORE_NOTICE) }

    val notesTipDismissed: Flow<Boolean> = prefs.map { it[NOTES_TIP_DISMISSED] ?: false }
    suspend fun dismissNotesTip() = dataStore.edit { it[NOTES_TIP_DISMISSED] = true }

    private companion object {
        val NOTES_TIP_DISMISSED = booleanPreferencesKey("notes_export_tip_dismissed")
    }
}
