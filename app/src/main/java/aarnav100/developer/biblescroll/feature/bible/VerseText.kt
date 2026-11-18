package aarnav100.developer.biblescroll.feature.bible

import aarnav100.developer.biblescroll.data.bible.model.Verse
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/**
 * Plain text for the clipboard and share sheet: `"<text>" — John 3:16 (WEB)`.
 *
 * Words of Jesus live in [Verse.wordsOfJesus] as ranges, never as markup inside the text, so using
 * the bare text is what strips the red letters. Whitespace is collapsed so a verse that spans
 * source lines copies as one line.
 */
fun verseClipText(text: String, reference: String, translationId: String): String =
    "\"${text.trim().replace(WHITESPACE, " ")}\" — $reference ($translationId)"

fun Verse.clipText(reference: String): String = verseClipText(text, reference, translationId)

private val WHITESPACE = Regex("\\s+")

/** Verse text with the Words of Jesus in [jesusColor]. Out-of-range offsets are clamped, not thrown. */
fun Verse.redLetterText(jesusColor: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    val style = SpanStyle(color = jesusColor)
    wordsOfJesus.forEach { range ->
        val start = range.first.coerceIn(0, text.length)
        val end = (range.last + 1).coerceIn(start, text.length)
        if (end > start) addStyle(style, start, end)
    }
}
