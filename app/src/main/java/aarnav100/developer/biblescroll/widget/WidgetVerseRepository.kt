package aarnav100.developer.biblescroll.widget

import aarnav100.developer.biblescroll.data.bible.References
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.votd.LocalClock
import aarnav100.developer.biblescroll.data.votd.VerseOfTheDayRepository
import aarnav100.developer.biblescroll.data.votd.VotdTime
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The verse the home-screen widget shows. */
data class WidgetVerse(val ref: VerseRef, val translationId: String, val isDefault: Boolean)

/** B11: a verse the reader chose, or the Verse of the Day (H1), which changes at local midnight. */
enum class WidgetMode { FIXED, VERSE_OF_THE_DAY }

/** What the widget draws. */
data class WidgetDisplay(
    val ref: VerseRef,
    val text: String,
    val reference: String,
    val translationId: String,
    val isVerseOfTheDay: Boolean = false,
)

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
    private val votd: VerseOfTheDayRepository,
    private val midnight: VotdWidgetScheduler,
    private val homeScreen: HomeScreenWidgets,
    private val clock: LocalClock,
) {
    private val prefs = dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    val widgetVerse: Flow<WidgetVerse> = prefs
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

    /** Unknown stored values read as FIXED. */
    val mode: Flow<WidgetMode> = prefs
        .map { p -> WidgetMode.entries.firstOrNull { it.name == p[MODE] } ?: WidgetMode.FIXED }
        .distinctUntilChanged()

    /** The local date the Verse of the Day widget shows; moved on by [refreshDate]. */
    private val today = MutableStateFlow(clock.now().toLocalDate())

    /** Text and reference for the widget. Falls back to the default verse if the saved one is gone. */
    val display: Flow<WidgetDisplay> = combine(mode, widgetVerse) { mode, selection -> mode to selection }
        .flatMapLatest { (mode, selection) ->
            if (mode == WidgetMode.FIXED) {
                fixedDisplay(selection)
            } else {
                today.flatMapLatest { votd.forDate(it) }.flatMapLatest { verse ->
                    if (verse == null) {
                        fixedDisplay(selection)
                    } else {
                        flowOf(
                            WidgetDisplay(verse.verse.ref, verse.verse.text, verse.reference, verse.verse.translationId, isVerseOfTheDay = true),
                        )
                    }
                }
            }
        }
        .distinctUntilChanged()

    private fun fixedDisplay(selection: WidgetVerse): Flow<WidgetDisplay> = combine(
        bible.verse(selection.translationId, selection.ref),
        bible.books(),
    ) { verse, books -> verse to books.associate { it.id to it.name } }
        .map { (verse, names) ->
            val shown = verse ?: bible.verse(DEFAULT.translationId, DEFAULT.ref).first()
            val ref = shown?.ref ?: DEFAULT.ref
            WidgetDisplay(
                ref = ref,
                text = shown?.text.orEmpty(),
                reference = References.verse(names[ref.bookId].orEmpty(), ref),
                translationId = shown?.translationId ?: DEFAULT.translationId,
            )
        }

    /** B11 "A fixed verse". */
    suspend fun setWidgetVerse(ref: VerseRef, translationId: String) {
        dataStore.edit {
            it[BOOK] = ref.bookId
            it[CHAPTER] = ref.chapter
            it[VERSE] = ref.verse
            it[TRANSLATION] = translationId
            it[MODE] = WidgetMode.FIXED.name
        }
        refresher.refresh()
        syncMidnightUpdate()
    }

    /** B11 "Verse of the Day". Keeps any fixed verse, so switching back restores it. */
    suspend fun setVerseOfTheDay() {
        dataStore.edit { it[MODE] = WidgetMode.VERSE_OF_THE_DAY.name }
        refreshDate()
        refresher.refresh()
        syncMidnightUpdate()
    }

    /** Re-reads today's local date (zone included). Called when a widget session starts and at midnight. */
    fun refreshDate() {
        today.value = clock.now().toLocalDate()
    }

    /** Midnight alarm, boot, clock or zone change: show the right day's verse and book the next redraw. */
    suspend fun onDayOrClockChanged() {
        refreshDate()
        if (mode.first() == WidgetMode.VERSE_OF_THE_DAY && homeScreen.hasWidget()) refresher.refresh()
        syncMidnightUpdate()
    }

    /**
     * A redraw just after the next local midnight while a placed widget shows the Verse of the Day; otherwise no
     * alarm. Re-run when a widget is added or removed (VerseWidgetReceiver), at app start and on clock changes.
     */
    suspend fun syncMidnightUpdate() {
        if (mode.first() == WidgetMode.VERSE_OF_THE_DAY && homeScreen.hasWidget()) {
            midnight.schedule(VotdTime.nextLocalMidnight(clock.now()).plusMinutes(MIDNIGHT_SLACK_MINUTES))
        } else {
            midnight.cancel()
        }
    }

    companion object {
        /** Psalm 119:105, "Your word is a lamp to my feet…", until the user picks one. */
        val DEFAULT = WidgetVerse(VerseRef(19, 119, 105), "WEB", isDefault = true)

        /** "Just after" midnight, so the redraw reads the new date even if the clock is a moment behind. */
        const val MIDNIGHT_SLACK_MINUTES = 1L

        private val BOOK = intPreferencesKey("widget_book")
        private val CHAPTER = intPreferencesKey("widget_chapter")
        private val VERSE = intPreferencesKey("widget_verse")
        private val TRANSLATION = stringPreferencesKey("widget_translation")
        private val MODE = stringPreferencesKey("widget_mode")
    }
}
