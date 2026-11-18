package aarnav100.developer.biblescroll.widget

import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The verse the home-screen widget shows. */
data class WidgetVerse(val ref: VerseRef, val translationId: String, val isDefault: Boolean)

/** What the widget draws. */
data class WidgetDisplay(val ref: VerseRef, val text: String, val reference: String, val translationId: String)

/** Redraws every placed widget. Abstracted so the repository is testable without Glance. */
fun interface WidgetRefresher {
    suspend fun refresh()
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class WidgetVerseRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val bible: BibleRepository,
    private val refresher: WidgetRefresher,
) {
    val widgetVerse: Flow<WidgetVerse> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val book = prefs[BOOK]
            val chapter = prefs[CHAPTER]
            val verse = prefs[VERSE]
            val translation = prefs[TRANSLATION]
            if (book != null && chapter != null && verse != null && !translation.isNullOrEmpty()) {
                WidgetVerse(VerseRef(book, chapter, verse), translation, isDefault = false)
            } else {
                DEFAULT
            }
        }
        .distinctUntilChanged()

    /** Text and reference for the widget. Falls back to the default verse if the saved one is gone. */
    val display: Flow<WidgetDisplay> = widgetVerse.flatMapLatest { selection ->
        combine(
            bible.verse(selection.translationId, selection.ref),
            bible.books(),
        ) { verse, books -> verse to books.associate { it.id to it.name } }
            .map { (verse, names) ->
                val shown = verse ?: bible.verse(DEFAULT.translationId, DEFAULT.ref).first()
                val ref = shown?.ref ?: DEFAULT.ref
                WidgetDisplay(
                    ref = ref,
                    text = shown?.text.orEmpty(),
                    reference = "${names[ref.bookId].orEmpty()} ${ref.chapter}:${ref.verse}",
                    translationId = shown?.translationId ?: DEFAULT.translationId,
                )
            }
    }.distinctUntilChanged()

    suspend fun setWidgetVerse(ref: VerseRef, translationId: String) {
        dataStore.edit {
            it[BOOK] = ref.bookId
            it[CHAPTER] = ref.chapter
            it[VERSE] = ref.verse
            it[TRANSLATION] = translationId
        }
        refresher.refresh()
    }

    companion object {
        /** Psalm 119:105, "Your word is a lamp to my feet…", until the user picks one. */
        val DEFAULT = WidgetVerse(VerseRef(19, 119, 105), "WEB", isDefault = true)

        private val BOOK = intPreferencesKey("widget_book")
        private val CHAPTER = intPreferencesKey("widget_chapter")
        private val VERSE = intPreferencesKey("widget_verse")
        private val TRANSLATION = stringPreferencesKey("widget_translation")
    }
}
