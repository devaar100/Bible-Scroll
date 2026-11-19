package aarnav100.developer.biblescroll.feature.home

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.feature.devotionals.DevotionalHomeCards
import aarnav100.developer.biblescroll.feature.library.LibraryViewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import aarnav100.developer.biblescroll.feature.streak.StreakChip
import aarnav100.developer.biblescroll.feature.streak.StreakSheet
import aarnav100.developer.biblescroll.feature.streak.StreakViewModel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** H1 is Phase 3. For now Home is a header (streak chip, Settings gear), the Library (H3) and Reading plans (H8). */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenDevotional: (date: String) -> Unit,
    onOpenDevotionalArchive: () -> Unit,
    modifier: Modifier = Modifier,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    streakViewModel: StreakViewModel = hiltViewModel(),
) {
    val library by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val streak by streakViewModel.uiState.collectAsStateWithLifecycle()
    var showStreak by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.tab_home),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            StreakChip(streak, onClick = {
                streakViewModel.resetMonth()
                showStreak = true
            })
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        }
        RestoreNoticeCard()
        DevotionalHomeCards(onOpen = onOpenDevotional, onOpenArchive = onOpenDevotionalArchive)
        HomeEntry(
            icon = Icons.Outlined.CollectionsBookmark,
            title = stringResource(R.string.library_title),
            subtitle = pluralStringResource(R.plurals.library_total, library.total, library.total),
            onClick = onOpenLibrary,
        )
        HomeEntry(
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            title = stringResource(R.string.plans_title),
            subtitle = stringResource(R.string.plans_home_body),
            onClick = onOpenPlans,
        )
    }
    if (showStreak) {
        StreakSheet(
            state = streak,
            onPreviousMonth = streakViewModel::showPreviousMonth,
            onNextMonth = streakViewModel::showNextMonth,
            onGraceDayChange = streakViewModel::setGraceDay,
            onDismiss = { showStreak = false },
        )
    }
}

@Composable
private fun HomeEntry(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
