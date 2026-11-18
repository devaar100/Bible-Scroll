package aarnav100.developer.biblescroll.ui.components

import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/** Temporary empty tab content until each feature is built. */
@Composable
fun PlaceholderScreen(title: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderScreenPreview() {
    BibleScrollTheme {
        Surface { PlaceholderScreen(title = "Bible") }
    }
}
