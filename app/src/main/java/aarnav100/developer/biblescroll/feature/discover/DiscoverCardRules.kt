package aarnav100.developer.biblescroll.feature.discover

/** D1 card text rules, kept free of Compose so they can be unit tested. */
object DiscoverCardRules {

    /** Verse type size (sp) and line height (multiple of the size), chosen by character count. */
    data class VerseSize(val fontSp: Float, val lineHeight: Float)

    const val SHORT_MAX = 110
    const val MEDIUM_MAX = 190

    /** The verse slot shows at most this many lines (auto-sized to fit, then ellipsis); the full verse is one tap away. */
    const val VERSE_MAX_LINES = 8

    fun verseSize(text: String): VerseSize {
        val length = text.trim().length
        return when {
            length <= SHORT_MAX -> VerseSize(24f, 1.4f)
            length <= MEDIUM_MAX -> VerseSize(21f, 1.4f)
            else -> VerseSize(19f, 1.4f)
        }
    }

    /** What the card shows under the reference row. Nothing empty is ever laid out (no blank gaps). */
    data class Sections(val hook: String?, val fact: String?, val showDataLine: Boolean)

    /**
     * [hook] and [fact] come from the AI-written study; with Hide AI studies on ([aiHidden]) neither shows and the
     * data line (original language, cross references: from source datasets, not AI) takes the hook's place when the
     * study is on the phone.
     */
    fun sections(hook: String?, fact: String?, aiHidden: Boolean, hasStudyData: Boolean): Sections =
        if (aiHidden) {
            Sections(hook = null, fact = null, showDataLine = hasStudyData)
        } else {
            Sections(hook = hook?.trim()?.takeIf { it.isNotEmpty() }, fact = fact?.trim()?.takeIf { it.isNotEmpty() }, showDataLine = false)
        }
}
