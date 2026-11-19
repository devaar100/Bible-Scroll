package aarnav100.developer.biblescroll.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** O2-O8 host. MainActivity shows the app instead once the "complete" flag is saved. */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportDrawn()

    // On O2 this is disabled, so Back leaves the app as usual.
    BackHandler(enabled = state.canGoBack) { viewModel.onBack() }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onPermissionResult(granted)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally { w -> if (forward) w / 4 else -w / 4 } + fadeIn()) togetherWith fadeOut()
            },
            label = "onboardingStep",
        ) { step ->
            when (step) {
                OnboardingStep.Welcome -> WelcomeStep(onGetStarted = viewModel::onGetStarted)
                OnboardingStep.Bible -> BibleStep(
                    options = state.bibleOptions,
                    selectedId = state.bibleChoice,
                    onSelect = viewModel::onBibleSelected,
                    onBack = { viewModel.onBack() },
                    onContinue = viewModel::onBibleContinue,
                )
                OnboardingStep.Tour -> FeatureTourStep(
                    tour = state.tour,
                    onBack = { viewModel.onBack() },
                    onSkip = viewModel::onTourSkip,
                    onContinue = viewModel::onTourContinue,
                )
                OnboardingStep.Goal -> GoalStep(
                    selected = state.goal,
                    onSelect = viewModel::onGoalSelected,
                    onBack = { viewModel.onBack() },
                    onContinue = viewModel::onGoalContinue,
                )
                OnboardingStep.GoalResponse -> GoalResponseStep(
                    goal = state.goal,
                    verse = state.goalVerse,
                    onBack = { viewModel.onBack() },
                    onContinue = viewModel::onGoalResponseContinue,
                )
                OnboardingStep.Timing -> TimingStep(
                    selected = state.timing,
                    onSelect = viewModel::onTimingSelected,
                    onBack = { viewModel.onBack() },
                    onContinue = viewModel::onTimingContinue,
                )
                OnboardingStep.Plan -> PlanStep(
                    state = state,
                    onBack = { viewModel.onBack() },
                    onToggleReminder = viewModel::onReminderToggle,
                    onTimeChange = viewModel::onReminderTimeChange,
                    onContinue = viewModel::onPlanContinue,
                )
                OnboardingStep.Permission -> PermissionStep(
                    time = state.reminderTime,
                    declined = state.permissionDeclined,
                    onBack = { viewModel.onBack() },
                    onAllow = {
                        // The view model only routes here on 13+; the check keeps lint happy.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.onPermissionResult(true)
                        }
                    },
                    onNotNow = viewModel::onPermissionNotNow,
                    onFinish = viewModel::onFinishWithoutReminder,
                )
            }
        }
    }
}
