package aarnav100.developer.biblescroll.feature.plans

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.plans.PlanRepository
import aarnav100.developer.biblescroll.ui.navigation.PlanCompleteRoute
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PlanCompleteViewModel @Inject constructor(savedStateHandle: SavedStateHandle, plans: PlanRepository) : ViewModel() {
    /** (title, days) of the finished plan, or null while loading. */
    val plan = plans.enrollment(savedStateHandle.toRoute<PlanCompleteRoute>().enrollmentId)
        .map { it?.let { e -> e.plan.title to e.plan.totalDays } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Shown once, after the last day is marked read. Calm: no confetti, no streak talk. */
@Composable
fun PlanCompleteScreen(onDone: () -> Unit, viewModel: PlanCompleteViewModel = hiltViewModel()) {
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Spa, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(20.dp))
        plan?.let { (title, days) ->
            Text(
                stringResource(R.string.plans_complete_title, title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                pluralStringResource(R.plurals.plans_complete_body, days, days),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onDone, modifier = Modifier.widthIn(min = 220.dp)) { Text(stringResource(R.string.plans_complete_done)) }
    }
}
