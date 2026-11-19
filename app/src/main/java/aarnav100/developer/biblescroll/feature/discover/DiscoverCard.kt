package aarnav100.developer.biblescroll.feature.discover

import aarnav100.developer.biblescroll.feature.study.StudyLanguageNote
import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.ui.theme.ScriptureTextStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow

/** One D1 card: verse, reference, teaser. The whole card opens the deep study. */
@Composable
fun DiscoverCard(data: Flow<DiscoverCardUi>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val card by data.collectAsStateWithLifecycle(initialValue = null)
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        val c = card ?: return@Surface
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.Center) {
            Text(
                c.verseText.orEmpty(),
                style = ScriptureTextStyle.copy(color = MaterialTheme.colorScheme.onSurface, lineHeight = 1.4.em),
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Text(c.reference, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StudyLanguageNote()
            Spacer(Modifier.height(24.dp))
            Text(
                c.teaser ?: stringResource(R.string.discover_open_to_read),
                style = MaterialTheme.typography.bodyLarge,
                color = if (c.teaser != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.discover_read_study)) }
        }
    }
}
