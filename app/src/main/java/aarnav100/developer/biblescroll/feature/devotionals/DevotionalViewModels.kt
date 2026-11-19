package aarnav100.developer.biblescroll.feature.devotionals

import aarnav100.developer.biblescroll.data.bible.References
import aarnav100.developer.biblescroll.core.content.BookCodes
import aarnav100.developer.biblescroll.core.content.DevotionalMonthState
import aarnav100.developer.biblescroll.core.content.DevotionalState
import aarnav100.developer.biblescroll.core.content.model.Devotional
import aarnav100.developer.biblescroll.core.content.model.DevotionalPassage
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.passageVerses
import aarnav100.developer.biblescroll.data.bible.resolveTranslation
import aarnav100.developer.biblescroll.data.devotionals.DevotionalCalendar
import aarnav100.developer.biblescroll.data.devotionals.DevotionalsRepository
import aarnav100.developer.biblescroll.data.plans.PassageFile
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.feature.plans.label
import aarnav100.developer.biblescroll.ui.navigation.DevotionalReaderRoute
import aarnav100.developer.biblescroll.ui.navigation.DevotionalRoute
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** "John 3:16-17" for each passage. */
internal fun List<DevotionalPassage>.labels(bookNames: Map<Int, String>) =
    map { PassageFile(it.book, it.startChapter, it.startVerse, it.endChapter, it.endVerse).label(bookNames) }

/** Every verse of every passage, in the reader's translation. */
internal suspend fun BibleRepository.devotionalVerses(translationId: String, devotional: Devotional): List<Verse> =
    devotional.verses.flatMap { p ->
        val book = BookCodes.id(p.book) ?: return@flatMap emptyList()
        passageVerses(translationId, book, p.startChapter, p.startVerse, p.endChapter, p.endVerse)
    }

// ---- H15 Devotional detail -------------------------------------------------------------------------

@Immutable
data class DevotionalDetailUiState(
    val date: LocalDate,
    /** Null while settings load; false = Hide AI studies is on and the reader hasn't chosen to show them. */
    val visible: Boolean? = null,
    val content: DevotionalState = DevotionalState.Loading(),
    val passages: List<String> = emptyList(),
    val read: Boolean = false,
    /** The anchor's first verse in the reader's translation, for B10 share. */
    val shareVerse: Verse? = null,
    val bookNames: Map<Int, String> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DevotionalDetailViewModel internal constructor(
    private val date: LocalDate,
    private val devotionals: DevotionalsRepository,
    bible: BibleRepository,
    preferences: ReaderPreferences,
) : ViewModel() {
    @Inject
    constructor(handle: SavedStateHandle, devotionals: DevotionalsRepository, bible: BibleRepository, preferences: ReaderPreferences) :
        this(LocalDate.parse(handle.toRoute<DevotionalRoute>().date), devotionals, bible, preferences)

    private val content = devotionals.devotional(DevotionalCalendar.key(date))

    private val shareVerse: Flow<Verse?> = combine(content, preferences.translationId) { c, t -> (c as? DevotionalState.Available)?.devotional to t }
        .distinctUntilChanged()
        .mapLatest { (d, t) ->
            val anchor = d?.verses?.firstOrNull() ?: return@mapLatest null
            val book = BookCodes.id(anchor.book) ?: return@mapLatest null
            bible.passageVerses(bible.resolveTranslation(t), book, anchor.startChapter, anchor.startVerse, anchor.startChapter, anchor.startVerse).firstOrNull()
        }

    val uiState: StateFlow<DevotionalDetailUiState> = combine(
        devotionals.visible(), content, devotionals.finished(date.year), bible.books(), shareVerse,
    ) { visible, c, finished, books, verse ->
        val names = books.associate { it.id to it.name }
        DevotionalDetailUiState(
            date = date, visible = visible, content = c,
            passages = (c as? DevotionalState.Available)?.devotional?.verses?.labels(names).orEmpty(),
            read = date in finished, shareVerse = verse, bookNames = names,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevotionalDetailUiState(date))

    fun showAnyway() {
        viewModelScope.launch { devotionals.showAnyway() }
    }

    fun retry() = devotionals.content.retry(DevotionalCalendar.key(date).take(2))
}

// ---- H16 Story reader ------------------------------------------------------------------------------

@Immutable
sealed interface StoryPage {
    data object Cover : StoryPage
    data class VersePage(val verse: Verse, val reference: String) : StoryPage
    data class Section(val kind: SectionKind, val text: String) : StoryPage
}

enum class SectionKind { Context, Reflect, ApplyIt, Prayer }

@Immutable
data class DevotionalReaderUiState(
    val date: LocalDate,
    val visible: Boolean? = null,
    val content: DevotionalState = DevotionalState.Loading(),
    /** Cover, each verse, Context, Reflect, Apply it, then Prayer if there is one. Empty until verses load. */
    val pages: List<StoryPage> = emptyList(),
    val finished: Boolean = false,
    val translationId: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DevotionalReaderViewModel internal constructor(
    private val date: LocalDate,
    private val devotionals: DevotionalsRepository,
    private val bible: BibleRepository,
    preferences: ReaderPreferences,
) : ViewModel() {
    @Inject
    constructor(handle: SavedStateHandle, devotionals: DevotionalsRepository, bible: BibleRepository, preferences: ReaderPreferences) :
        this(LocalDate.parse(handle.toRoute<DevotionalReaderRoute>().date), devotionals, bible, preferences)

    private val content = devotionals.devotional(DevotionalCalendar.key(date))
    private val finishing = MutableStateFlow(false)

    private val pages: Flow<Pair<String, List<StoryPage>>> =
        combine(content, preferences.translationId, bible.books()) { c, t, books -> Triple((c as? DevotionalState.Available)?.devotional, t, books) }
            .distinctUntilChanged()
            .mapLatest { (d, preferred, books) ->
                if (d == null) return@mapLatest "" to emptyList()
                val translationId = bible.resolveTranslation(preferred)
                val names = books.associate { it.id to it.name }
                val verses = bible.devotionalVerses(translationId, d).map { v ->
                    StoryPage.VersePage(v, References.verse(names[v.ref.bookId].orEmpty(), v.ref))
                }
                translationId to buildList {
                    add(StoryPage.Cover)
                    addAll(verses)
                    add(StoryPage.Section(SectionKind.Context, d.context))
                    add(StoryPage.Section(SectionKind.Reflect, d.reflect))
                    add(StoryPage.Section(SectionKind.ApplyIt, d.applyIt))
                    d.prayer?.takeIf { it.isNotBlank() }?.let { add(StoryPage.Section(SectionKind.Prayer, it)) }
                }
            }

    val uiState: StateFlow<DevotionalReaderUiState> = combine(
        devotionals.visible(), content, pages, devotionals.finished(date.year),
    ) { visible, c, (translation, pages), finished ->
        DevotionalReaderUiState(date, visible, c, pages, date in finished, translation)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevotionalReaderUiState(date))

    /** The last page's "Finish": progress + streak (first time only). */
    fun finish() {
        val devotional = (uiState.value.content as? DevotionalState.Available)?.devotional ?: return
        if (finishing.value) return
        finishing.value = true
        viewModelScope.launch {
            try {
                devotionals.markFinished(date, devotional)
            } finally {
                finishing.value = false
            }
        }
    }

    fun showAnyway() {
        viewModelScope.launch { devotionals.showAnyway() }
    }
}

// ---- H17 Archive -----------------------------------------------------------------------------------

@Immutable
data class ArchiveDay(val date: LocalDate, val title: String?, val read: Boolean)

@Immutable
data class ArchiveMonth(val month: Int, val state: DevotionalMonthState, val days: List<ArchiveDay>)

@Immutable
data class DevotionalArchiveUiState(val visible: Boolean? = null, val months: List<ArchiveMonth> = emptyList(), val readCount: Int = 0)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DevotionalArchiveViewModel @Inject constructor(private val devotionals: DevotionalsRepository) : ViewModel() {

    val uiState: StateFlow<DevotionalArchiveUiState> = combine(devotionals.visible(), devotionals.today()) { v, t -> v to t }
        .flatMapLatest { (visible, today) ->
            // Only touch content (and so start downloads) once the reader can see devotionals.
            if (!visible) return@flatMapLatest flowOf(DevotionalArchiveUiState(visible = false))
            val months = DevotionalCalendar.archiveMonths(today).map { m ->
                devotionals.month(DevotionalCalendar.monthKey(m)).map { m to it }
            }
            combine(combine(months) { it.toList() }, devotionals.finished(today.year)) { states, finished ->
                DevotionalArchiveUiState(
                    visible = true,
                    months = states.map { (m, state) ->
                        val published = (state as? DevotionalMonthState.Available)?.devotionals.orEmpty()
                        ArchiveMonth(m, state, DevotionalCalendar.pastDays(today.year, m, today).reversed().map { d ->
                            ArchiveDay(d, published[DevotionalCalendar.key(d)]?.title, d in finished)
                        })
                    },
                    readCount = finished.count { !it.isAfter(today) },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevotionalArchiveUiState())

    fun showAnyway() {
        viewModelScope.launch { devotionals.showAnyway() }
    }

    fun retry(month: Int) = devotionals.content.retry(DevotionalCalendar.monthKey(month))
}

// ---- Home cards: today + tomorrow ------------------------------------------------------------------

@Immutable
data class HomeDevotionalUiState(
    val visible: Boolean? = null,
    val today: LocalDate? = null,
    val todayState: DevotionalState = DevotionalState.Loading(),
    val readToday: Boolean = false,
    /** Tomorrow's devotional if it's on the device (theme as a gentle preview), else null. */
    val tomorrow: Devotional? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeDevotionalViewModel @Inject constructor(private val devotionals: DevotionalsRepository) : ViewModel() {
    val uiState: StateFlow<HomeDevotionalUiState> = combine(devotionals.visible(), devotionals.today()) { v, t -> v to t }
        .flatMapLatest { (visible, today) ->
            if (!visible) return@flatMapLatest flowOf(HomeDevotionalUiState(visible = false, today = today))
            combine(
                devotionals.devotional(DevotionalCalendar.key(today)),
                devotionals.devotional(DevotionalCalendar.key(today.plusDays(1))),
                devotionals.finished(today.year),
            ) { todayState, tomorrowState, finished ->
                HomeDevotionalUiState(true, today, todayState, today in finished, (tomorrowState as? DevotionalState.Available)?.devotional)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeDevotionalUiState())

    fun showAnyway() {
        viewModelScope.launch { devotionals.showAnyway() }
    }
}
