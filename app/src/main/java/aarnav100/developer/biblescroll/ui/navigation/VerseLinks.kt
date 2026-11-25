package aarnav100.developer.biblescroll.ui.navigation

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-app verse links: `biblescroll://verse/<book>/<chapter>/<verse>`. Only sent as explicit intents
 * to MainActivity (e.g. from the widget), so no exported intent filter is needed.
 */
object VerseLinks {
    private const val PREFIX = "biblescroll://verse/"
    private val PATTERN = Regex("""^biblescroll://verse/(\d{1,2})/(\d{1,3})/(\d{1,3})/?$""")

    fun build(ref: VerseRef): String = "$PREFIX${ref.bookId}/${ref.chapter}/${ref.verse}"

    /** Null for anything that isn't a well-formed verse link with book 1-66 and positive numbers. */
    fun parse(link: String?): VerseRef? {
        val match = PATTERN.matchEntire(link ?: return null) ?: return null
        val (book, chapter, verse) = match.destructured.toList().map { it.toInt() }
        if (book !in 1..66 || chapter < 1 || verse < 1) return null
        return VerseRef(book, chapter, verse)
    }
}

/**
 * A verse link waiting to be opened. MainActivity posts it; the app switches to the Bible tab and the
 * feed consumes it once it's on screen.
 */
@Singleton
class PendingVerseLink @Inject constructor() {
    private val _ref = MutableStateFlow<VerseRef?>(null)
    val ref: StateFlow<VerseRef?> = _ref.asStateFlow()

    /**
     * Whether the pending link came from inside the app (a pill, "Read in context", a library row), so Back on the
     * Bible tab returns to where it was tapped. False for links from outside (the widget), where Back exits as usual.
     */
    @Volatile
    var fromApp: Boolean = true
        private set

    fun post(ref: VerseRef, fromApp: Boolean = true) {
        this.fromApp = fromApp
        _ref.value = ref
    }

    /** Clears [ref] only if it's still the pending one, so a newer link isn't lost. */
    fun consume(ref: VerseRef) {
        _ref.compareAndSet(ref, null)
    }
}
