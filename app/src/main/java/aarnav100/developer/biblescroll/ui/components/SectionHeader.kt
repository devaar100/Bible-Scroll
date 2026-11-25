package aarnav100.developer.biblescroll.ui.components

import aarnav100.developer.biblescroll.ui.theme.AppText
import aarnav100.developer.biblescroll.ui.theme.Spacing
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * A section's small spaced label ("TODAY'S DEVOTIONAL") with an optional accent link at the other end ("All days").
 * 48 dp tall so the link keeps its touch target.
 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, link: String? = null, onLink: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(Locale.getDefault()),
            style = AppText.sectionLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = Spacing.s).semantics { heading() },
        )
        if (link != null) {
            TextButton(onClick = onLink, contentPadding = PaddingValues(horizontal = Spacing.s)) {
                Text(link, style = AppText.button)
            }
        }
    }
}
