package aarnav100.developer.biblescroll.feature.home

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.plans.PlanSummary
import aarnav100.developer.biblescroll.feature.library.LibraryScreen
import aarnav100.developer.biblescroll.feature.library.LibraryUiState
import aarnav100.developer.biblescroll.ui.components.SectionHeader
import aarnav100.developer.biblescroll.ui.theme.AppShapes
import aarnav100.developer.biblescroll.ui.theme.AppText
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import aarnav100.developer.biblescroll.ui.theme.Literata
import aarnav100.developer.biblescroll.ui.theme.Spacing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp

/**
 * Home "Reading plans": a sideways row of photo cards (the featured plans), ending with "Build your own plan".
 * "See all" opens the plans browser (H8).
 */
@Composable
fun HomePlansRow(
    plans: List<PlanSummary>,
    onOpenPlan: (planId: String) -> Unit,
    onBuildYourOwn: () -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (plans.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.plans_title), link = stringResource(R.string.home_plans_see_all), onLink = onSeeAll)
        // The row bleeds to the screen edges; content starts on the gutter.
        LazyRow(
            modifier = Modifier.bleed(Spacing.gutter),
            contentPadding = PaddingValues(horizontal = Spacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            items(plans, key = { it.id }) { plan -> PlanCard(plan) { onOpenPlan(plan.id) } }
            item(key = "build") { BuildYourOwnCard(onBuildYourOwn) }
        }
    }
}

/** Widens this element by [by] on both sides (the screen's gutter), so a scrolling row reaches the screen edges. */
private fun Modifier.bleed(by: Dp): Modifier = layout { measurable, constraints ->
    val extra = by.roundToPx() * 2
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

@Composable
private fun PlanCard(plan: PlanSummary, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = AppShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.width(148.dp),
    ) {
        Column {
            Image(
                painterResource(HomePhotos.forPlan(plan.id).res),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(92.dp),
            )
            Column(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)) {
                Text(
                    plan.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    minLines = 2,
                )
                Text(
                    pluralStringResource(R.plurals.plans_days, plan.days, plan.days),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BuildYourOwnCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = AppShapes.medium,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.width(120.dp).height(152.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacing.s))
            Text(stringResource(R.string.plans_build_title), style = AppText.button, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Home "Your library": four tappable counts (liked, bookmarks, notes, saved studies) that open the matching list;
 * "Open" opens the Library hub (H3). With Hide AI studies on, the studies count isn't shown.
 */
@Composable
fun HomeLibraryCard(
    state: LibraryUiState,
    onOpenLibrary: () -> Unit,
    onOpenList: (LibraryScreen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val counts = state.counts ?: return
    Column(modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.library_title), link = stringResource(R.string.home_library_open), onLink = onOpenLibrary)
        Surface(shape = AppShapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(vertical = Spacing.s, horizontal = Spacing.xs)) {
                val accent = MaterialTheme.colorScheme.primary
                Stat(Icons.Filled.Favorite, BibleScrollTheme.extendedColors.like, counts.liked, stringResource(R.string.library_liked)) {
                    onOpenList(LibraryScreen.LIKED_AND_BOOKMARKED)
                }
                Stat(Icons.Filled.Bookmark, accent, counts.bookmarked, stringResource(R.string.home_library_bookmarks)) {
                    onOpenList(LibraryScreen.LIKED_AND_BOOKMARKED)
                }
                Stat(Icons.AutoMirrored.Outlined.Notes, accent, counts.notes, stringResource(R.string.library_notes)) {
                    onOpenList(LibraryScreen.NOTES)
                }
                if (!state.studiesHidden) {
                    Stat(Icons.Outlined.School, accent, counts.saved, stringResource(R.string.home_library_studies)) {
                        onOpenList(LibraryScreen.SAVED_STUDIES)
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Stat(icon: ImageVector, tint: Color, count: Int, label: String, onClick: () -> Unit) {
    val spoken = stringResource(R.string.two_parts, label, count.toString())
    Surface(
        onClick = onClick,
        shape = AppShapes.small,
        color = Color.Transparent,
        modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        Column(Modifier.padding(vertical = Spacing.s), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint)
            }
            Text(count.toString(), style = MaterialTheme.typography.titleLarge.copy(fontFamily = Literata, fontWeight = FontWeight.SemiBold))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
