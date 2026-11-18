package aarnav100.developer.biblescroll.feature.share

/** Result of fitting text into a box: the size to use, and whether even [CardFit.min] overflowed. */
data class FitResult(val fontSize: Float, val overflows: Boolean)

/**
 * Largest font size in [min]..[max] (on a [step] grid) for which [fits] is true. Assumes [fits] is
 * monotonic: if a size fits, every smaller size fits too. Binary search, so a long verse costs
 * about log2((max - min) / step) measurements rather than one per step.
 *
 * If nothing fits, returns [min] with overflows = true; the caller ellipsizes.
 */
fun fitFontSize(min: Float, max: Float, step: Float, fits: (Float) -> Boolean): FitResult {
    require(min > 0f && max >= min && step > 0f) { "Bad range $min..$max step $step" }
    val steps = ((max - min) / step).toInt()
    fun sizeAt(i: Int) = min + i * step

    if (!fits(sizeAt(0))) return FitResult(min, overflows = true)
    var lo = 0 // known to fit
    var hi = steps // unknown
    if (fits(sizeAt(hi))) return FitResult(sizeAt(hi), overflows = false)
    hi -= 1
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (fits(sizeAt(mid))) lo = mid else hi = mid - 1
    }
    return FitResult(sizeAt(lo), overflows = false)
}

enum class CardFormat(val widthPx: Int, val heightPx: Int) {
    Square(1080, 1080),
    Story(1080, 1920),
}

/**
 * Card geometry in card pixels (the card is always 1080 wide). Both the preview and the full-size
 * export draw from these numbers, scaled, so they lay out identically.
 */
data class CardLayout(
    val padding: Float,
    /** Box the verse must fit in. */
    val textWidth: Float,
    val textMaxHeight: Float,
    val minFont: Float,
    val maxFont: Float,
    /** Line height as a multiple of font size. */
    val lineHeight: Float,
    val referenceFont: Float,
    val brandFont: Float,
) {
    companion object {
        const val FONT_STEP = 2f

        fun of(format: CardFormat): CardLayout = when (format) {
            CardFormat.Square -> CardLayout(
                padding = 104f, textWidth = 1080f - 2 * 104f, textMaxHeight = 640f,
                minFont = 30f, maxFont = 72f, lineHeight = 1.35f, referenceFont = 36f, brandFont = 26f,
            )
            CardFormat.Story -> CardLayout(
                padding = 112f, textWidth = 1080f - 2 * 112f, textMaxHeight = 1240f,
                minFont = 34f, maxFont = 84f, lineHeight = 1.35f, referenceFont = 40f, brandFont = 28f,
            )
        }
    }
}
