package aarnav100.developer.biblescroll.feature.settings

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.widget.HomeScreenWidgets
import aarnav100.developer.biblescroll.widget.WidgetDisplay
import aarnav100.developer.biblescroll.widget.WidgetMode
import aarnav100.developer.biblescroll.widget.WidgetVerseRepository
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class WidgetGuideUiState(
    val mode: WidgetMode = WidgetMode.FIXED,
    val preview: WidgetDisplay? = null,
    val placed: Boolean = false,
    val canPin: Boolean = false,
)

/** S5. Placement is re-read on resume: the person may have just added or removed the widget. */
@HiltViewModel
class WidgetGuideViewModel @Inject constructor(
    widgets: WidgetVerseRepository,
    private val homeScreen: HomeScreenWidgets,
) : ViewModel() {
    private val placement = MutableStateFlow(homeScreen.hasWidget() to homeScreen.canPin())

    val uiState: StateFlow<WidgetGuideUiState> = combine(widgets.mode, widgets.display, placement) { mode, display, (placed, canPin) ->
        WidgetGuideUiState(mode, display, placed, canPin)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WidgetGuideUiState())

    fun refreshPlacement() {
        placement.value = homeScreen.hasWidget() to homeScreen.canPin()
    }

    /** The launcher shows its own "Add to home screen" prompt; false if it refused. */
    fun pin(): Boolean = homeScreen.requestPin()
}

@Composable
fun WidgetGuideScreen(onBack: () -> Unit, viewModel: WidgetGuideViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshPlacement()
        onPauseOrDispose { }
    }
    WidgetGuideContent(state, onBack, onPin = { viewModel.pin() })
}

/** Stateless S5 (PseudoLocaleScreenshotTest renders it). */
@Composable
fun WidgetGuideContent(state: WidgetGuideUiState, onBack: () -> Unit, onPin: () -> Unit) {
    SettingsPage(title = stringResource(R.string.widget_guide_title), onBack = onBack) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.widget_guide_intro), style = MaterialTheme.typography.bodyLarge)
            Mode(stringResource(R.string.widget_mode_fixed), stringResource(R.string.widget_guide_fixed_body))
            Mode(stringResource(R.string.votd_title), stringResource(R.string.widget_guide_votd_body))
            val modeName = stringResource(if (state.mode == WidgetMode.VERSE_OF_THE_DAY) R.string.votd_title else R.string.widget_mode_fixed)
            Text(stringResource(R.string.widget_guide_current, modeName), style = MaterialTheme.typography.titleSmall)
            state.preview?.let { Preview(it) }
            when {
                state.placed -> Text(stringResource(R.string.widget_guide_placed), style = MaterialTheme.typography.bodyMedium)
                state.canPin -> Button(onClick = onPin) { Text(stringResource(R.string.widget_add_to_home)) }
            }
            if (!state.placed) ManualSteps()
        }
    }
}

@Composable
private fun Mode(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Looks like the medium widget (VerseWidget): system serif text, reference and translation. */
@Composable
private fun Preview(display: WidgetDisplay) {
    val label = stringResource(R.string.widget_guide_preview)
    val reference = stringResource(R.string.two_parts, display.reference, display.translationId)
    val spoken = stringResource(R.string.widget_guide_preview_cd, display.text, reference)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = spoken },
        ) {
            Column(Modifier.padding(18.dp)) {
                if (display.isVerseOfTheDay) {
                    Text(stringResource(R.string.votd_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                }
                Text(display.text, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.bodyLarge, maxLines = 6)
                Spacer(Modifier.height(10.dp))
                Text(reference, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ManualSteps() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.widget_guide_manual_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        listOf(
            stringResource(R.string.widget_guide_step1),
            stringResource(R.string.widget_guide_step2),
            stringResource(R.string.widget_guide_step3, stringResource(R.string.app_name)),
        ).forEachIndexed { i, step ->
            Row {
                Text(stringResource(R.string.widget_guide_step_number, i + 1), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}
