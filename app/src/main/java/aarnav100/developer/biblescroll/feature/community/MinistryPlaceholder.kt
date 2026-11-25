package aarnav100.developer.biblescroll.feature.community

import aarnav100.developer.biblescroll.ui.theme.Literata
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import java.util.Locale

/**
 * The first letter or digit of a ministry's name, upper-cased ("River Bible Translation" → "R"), or "" when there's
 * none. Whole code points, so a letter outside the BMP isn't split.
 */
internal fun ministryInitial(name: String, locale: Locale = Locale.getDefault()): String {
    val cp = name.codePoints().filter { Character.isLetterOrDigit(it) }.findFirst()
    return if (cp.isPresent) String(Character.toChars(cp.asInt)).uppercase(locale) else ""
}

/**
 * Stands in for a ministry with no photo (Community card, confirm sheet, History): a neutral panel with the name's
 * initial in Literata. No per-ministry colours (docs/ui-direction.md: Community uses neutrals only). Decorative.
 */
@Composable
fun MinistryPlaceholder(name: String, letterSize: TextUnit, modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Text(
            ministryInitial(name),
            style = TextStyle(fontFamily = Literata, fontSize = letterSize),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
