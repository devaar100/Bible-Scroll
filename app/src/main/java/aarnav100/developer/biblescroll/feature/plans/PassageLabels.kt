package aarnav100.developer.biblescroll.feature.plans

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.bible.References
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.plans.PassageFile
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** "John 3:16", "John 3:1–21", "Genesis 1:1–2:3", in the app's language. Unknown books fall back to the code. */
fun PassageFile.label(bookNames: Map<Int, String>): String {
    val name = bookId?.let(bookNames::get) ?: book
    val id = bookId ?: 0
    return References.range(name, VerseRef(id, startChapter, startVerse), VerseRef(id, endChapter, endVerse))
}

/** "Day 3" or "Day 3 · The Good Shepherd". */
@Composable
internal fun dayHeading(day: Int, title: String?): String =
    if (title == null) stringResource(R.string.plans_day_number, day) else stringResource(R.string.plans_day_titled, day, title)
