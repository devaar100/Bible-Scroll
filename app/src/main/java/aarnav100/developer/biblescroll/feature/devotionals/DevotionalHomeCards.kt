package aarnav100.developer.biblescroll.feature.devotionals

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.core.content.DevotionalState
import aarnav100.developer.biblescroll.feature.home.HomePhotos
import aarnav100.developer.biblescroll.ui.components.SectionHeader
import aarnav100.developer.biblescroll.ui.theme.AppShapes
import aarnav100.developer.biblescroll.ui.theme.AppText
import aarnav100.developer.biblescroll.ui.theme.Spacing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Home: today's devotional as a photo card (the photo matching its cover style, HomePhotos) with the series and title
 * over a dark scrim, a "Read today" tick once read, and one line counting down to tomorrow's. "All days" opens the
 * archive.
 */
@Composable
fun DevotionalHomeCards(
    onOpen: (date: String) -> Unit,
    onOpenArchive: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeDevotionalViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = state.today ?: return
    Column(modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.devo_today_title), link = stringResource(R.string.devo_archive_link), onLink = onOpenArchive)
        val devotional = (state.todayState as? DevotionalState.Available)?.devotional
        if (devotional == null) {
            DevotionalStateMessage(state.todayState, onRetry = {})
            return@Column
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(AppShapes.large)
                .clickable(role = Role.Button) { onOpen(today.toString()) },
        ) {
            Image(
                painterResource(HomePhotos.forDevotional(devotional.coverStyle).res),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            // Bottom scrim so the white title reads on any photo.
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))))
            Column(Modifier.align(Alignment.BottomStart).padding(Spacing.l)) {
                Text(
                    devotional.series.title.uppercase(Locale.getDefault()),
                    style = AppText.sectionLabel,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(devotional.title, style = AppText.cardTitle, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (state.readToday) {
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.m)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.devo_read_today), style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
        }
        TomorrowLine()
    }
}

/** Counts down to local midnight, when tomorrow's devotional becomes today's. */
@Composable
private fun TomorrowLine() {
    val left = rememberCountdown()
    val label = stringResource(R.string.devo_tomorrow_in, left)
    Row(Modifier.padding(top = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        // Read once by TalkBack (not every second): the label is static semantics, refreshed on focus.
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics { contentDescription = label },
        )
    }
}
