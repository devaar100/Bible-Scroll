package aarnav100.developer.biblescroll.feature.bible.sheets

import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.widget.HomeScreenWidgets
import aarnav100.developer.biblescroll.widget.WidgetVerseRepository
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which per-verse sheet or dialog is open. Opening or closing one never touches the feed's pager. */
sealed interface VerseSheet {
    data object None : VerseSheet

    /** B7 action list. */
    @Immutable
    data class Actions(val verse: Verse) : VerseSheet

    /** B9. [rows] is null while loading. */
    @Immutable
    data class Compare(val verse: Verse, val rows: List<CompareRow>?) : VerseSheet

    /** B10. */
    @Immutable
    data class ShareImage(val verse: Verse) : VerseSheet

    /** B11 confirm dialog. Placement/pin support are read when the dialog opens. */
    @Immutable
    data class WidgetConfirm(val verse: Verse, val widgetPlaced: Boolean, val canPin: Boolean) : VerseSheet
}

/** One-off results for the screen's snackbar. */
enum class VerseSheetEvent { WidgetVerseSet }

private sealed interface Request {
    data object None : Request
    data class Actions(val verse: Verse) : Request
    data class Compare(val verse: Verse) : Request
    data class ShareImage(val verse: Verse) : Request
    data class WidgetConfirm(val verse: Verse, val placed: Boolean, val canPin: Boolean) : Request
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VerseSheetsViewModel @Inject constructor(
    private val bible: BibleRepository,
    private val widgetVerses: WidgetVerseRepository,
    private val homeScreen: HomeScreenWidgets,
) : ViewModel() {

    private val request = MutableStateFlow<Request>(Request.None)

    private val _events = Channel<VerseSheetEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val sheet: StateFlow<VerseSheet> = request
        .flatMapLatest { r ->
            when (r) {
                Request.None -> flowOf(VerseSheet.None)
                is Request.Actions -> flowOf(VerseSheet.Actions(r.verse))
                is Request.ShareImage -> flowOf(VerseSheet.ShareImage(r.verse))
                is Request.WidgetConfirm -> flowOf(VerseSheet.WidgetConfirm(r.verse, r.placed, r.canPin))
                is Request.Compare -> combine(
                    bible.translations(),
                    bible.verseInAllTranslations(r.verse.ref),
                ) { translations, verses ->
                    compareRows(translations, verses, currentTranslationId = r.verse.translationId)
                }
                    .map<List<CompareRow>, VerseSheet> { VerseSheet.Compare(r.verse, it) }
                    .onStart { emit(VerseSheet.Compare(r.verse, rows = null)) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VerseSheet.None)

    fun openActions(verse: Verse) {
        request.value = Request.Actions(verse)
    }

    /** From B7: swaps the action sheet for the compare sheet. */
    fun openCompare(verse: Verse) {
        request.value = Request.Compare(verse)
    }

    fun openShareImage(verse: Verse) {
        request.value = Request.ShareImage(verse)
    }

    fun openWidgetConfirm(verse: Verse) {
        request.value = Request.WidgetConfirm(verse, homeScreen.hasWidget(), homeScreen.canPin())
    }

    /** B11 confirm. [pin] asks the launcher to add the widget (only offered when none is placed). */
    fun confirmWidgetVerse(pin: Boolean) {
        val open = request.value as? Request.WidgetConfirm ?: return
        request.value = Request.None
        viewModelScope.launch {
            widgetVerses.setWidgetVerse(open.verse.ref, open.verse.translationId)
            // The launcher shows its own confirmation when pinning, so ours would be noise.
            if (pin && homeScreen.requestPin()) return@launch
            _events.send(VerseSheetEvent.WidgetVerseSet)
        }
    }

    /** Swipe down, back, or an action that finishes the flow. */
    fun close() {
        request.value = Request.None
    }
}
