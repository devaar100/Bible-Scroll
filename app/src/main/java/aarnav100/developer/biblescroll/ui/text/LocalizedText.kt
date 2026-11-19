package aarnav100.developer.biblescroll.ui.text

import android.icu.text.ListFormatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/** "A, B and C" in English, "A, B y C" in Spanish: the language's own list style, not a hard-coded ", ". */
fun formatList(items: List<String>, locale: Locale): String = ListFormatter.getInstance(locale).format(items)

/** [formatList] in the screen's language. */
@Composable
fun localizedList(items: List<String>): String = formatList(items, LocalConfiguration.current.locales[0])
