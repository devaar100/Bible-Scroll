package aarnav100.developer.biblescroll.feature.plans

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.plans.MyPlan
import aarnav100.developer.biblescroll.data.plans.PlanCategory
import aarnav100.developer.biblescroll.data.plans.PlanImporter
import aarnav100.developer.biblescroll.data.plans.PlanSummary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

/** H8: My plans, Build your own plan, then a carousel per category. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansBrowserScreen(
    onBack: () -> Unit,
    onOpenPlan: (String) -> Unit,
    onBuildYourOwn: () -> Unit,
    viewModel: PlansBrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plans_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        when (val s = state) {
            PlansBrowserUiState.Loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is PlansBrowserUiState.Ready -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                // Stable first item: LazyColumn keeps the first visible item's key in place, so without this a
                // "My plans" row added on return from a plan would be inserted above the screen, out of sight.
                item(key = "top") { Spacer(Modifier.height(0.dp)) }
                if (s.myPlans.isNotEmpty()) {
                    item(key = "my-plans-title") { SectionTitle(stringResource(R.string.plans_my_plans)) }
                    item(key = "my-plans") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Same anchor trick as the column: a newly started plan appears at the start, in view.
                            item(key = "start") { Spacer(Modifier.width(0.dp)) }
                            items(s.myPlans, key = { it.enrollmentId }) { MyPlanCard(it, onClick = { onOpenPlan(it.planId) }) }
                        }
                    }
                }
                item(key = "build") { BuildYourOwnCard(onBuildYourOwn) }
                s.categories.forEach { category -> categoryRow(category, onOpenPlan) }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.categoryRow(category: PlanCategory, onOpenPlan: (String) -> Unit) {
    item(key = "title-${category.id}") {
        SectionTitle(if (category.id == PlanImporter.START_HERE) stringResource(R.string.plans_start_here) else category.title)
    }
    item(key = "row-${category.id}") {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(category.plans, key = { it.id }) { PlanCard(it, onClick = { onOpenPlan(it.id) }) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 10.dp).semantics { heading() },
    )
}

@Composable
private fun PlanCard(plan: PlanSummary, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.width(220.dp).height(168.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(plan.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(
                plan.shortDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(planMeta(plan.days, plan.minutesPerDay), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MyPlanCard(plan: MyPlan, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.width(240.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(plan.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            PlanProgressBar(plan)
            Spacer(Modifier.height(8.dp))
            Text(myPlanStatus(plan), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun myPlanStatus(plan: MyPlan, today: LocalDate = LocalDate.now()): String = when {
    plan.completed -> stringResource(R.string.plans_finished)
    plan.startDate.isAfter(today) -> stringResource(R.string.plans_starts_tomorrow)
    else -> pluralStringResource(R.plurals.plans_progress, plan.totalDays, plan.daysDone, plan.totalDays)
}

@Composable
internal fun planMeta(days: Int, minutes: Int): String =
    stringResource(
        R.string.two_parts,
        pluralStringResource(R.plurals.plans_days, days, days),
        pluralStringResource(R.plurals.plans_minutes_a_day, minutes, minutes),
    )

@Composable
private fun BuildYourOwnCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.EditCalendar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.plans_build_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.plans_build_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** Visible on the secondary container: the default track colour is too close to it. */
@Composable
internal fun PlanProgressBar(plan: MyPlan) {
    LinearProgressIndicator(
        progress = { plan.daysDone / plan.totalDays.toFloat() },
        trackColor = MaterialTheme.colorScheme.surface,
        drawStopIndicator = {},
        modifier = Modifier.fillMaxWidth(),
    )
}
