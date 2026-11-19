package aarnav100.developer.biblescroll.feature.onboarding

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.onboarding.OnboardingRepository
import aarnav100.developer.biblescroll.data.reminder.NotificationAccess
import aarnav100.developer.biblescroll.data.reminder.ReminderRepository
import aarnav100.developer.biblescroll.data.reminder.ReminderScheduler
import aarnav100.developer.biblescroll.data.reminder.ReminderSettings
import aarnav100.developer.biblescroll.data.settings.ContentLanguage
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.data.user.LocalUserDataRepository
import aarnav100.developer.biblescroll.testing.FakeBibleRepository
import aarnav100.developer.biblescroll.testing.FakePreferencesDataStore
import aarnav100.developer.biblescroll.testing.FakeUserDao
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private class FakeAccess(var canPost: Boolean = true, var needsPermission: Boolean = false) : NotificationAccess {
        override fun canPost() = canPost
        override fun needsRuntimePermission() = needsPermission
    }

    private class RecordingScheduler(private val repo: ReminderRepository) : ReminderScheduler {
        val synced = mutableListOf<ReminderSettings>()
        override fun sync(settings: ReminderSettings) {
            synced += settings
        }
        override suspend fun syncFromStore() = sync(repo.settings.first())
        override suspend fun syncPlanReminders() = Unit
    }

    private val dataStore = FakePreferencesDataStore()
    private var bible = FakeBibleRepository()
    private val userData = LocalUserDataRepository(FakeUserDao()) { 1L }
    private val reminders = ReminderRepository(dataStore)
    private val scheduler = RecordingScheduler(reminders)
    private val onboarding = OnboardingRepository(dataStore)
    private val access = FakeAccess()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ContentLanguage.set("en")
    }

    private fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()) = OnboardingViewModel(
        handle, bible, ReaderPreferences(dataStore), userData, reminders, scheduler, access, onboarding,
    ).also { vm -> backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} } }

    private val OnboardingViewModel.step get() = uiState.value.step

    @Test
    fun fullPathAndBackRetracesSteps() = runTest {
        val vm = viewModel()
        assertEquals(OnboardingStep.Welcome, vm.step)
        assertFalse(vm.uiState.value.canGoBack)

        vm.onGetStarted()
        vm.onTourContinue()
        vm.onGoalContinue() // no pick yet: stays
        assertEquals(OnboardingStep.Goal, vm.step)
        vm.onGoalSelected(Goal.Understanding)
        vm.onGoalContinue()
        assertEquals(OnboardingStep.GoalResponse, vm.step)
        vm.onGoalResponseContinue()
        vm.onTimingSelected(Timing.Break)
        vm.onTimingContinue()
        assertEquals(OnboardingStep.Plan, vm.step)

        listOf(OnboardingStep.Timing, OnboardingStep.GoalResponse, OnboardingStep.Goal, OnboardingStep.Tour, OnboardingStep.Welcome)
            .forEach { expected ->
                assertTrue(vm.onBack())
                assertEquals(expected, vm.step)
            }
        assertFalse(vm.onBack()) // O2: let the system handle it
    }

    @Test
    fun spanishUiOffersRv1909AfterWelcomeAndSavesItOnFinish() = runTest {
        ContentLanguage.set("es")
        bible = FakeBibleRepository(withSpanish = true)
        val vm = viewModel()
        vm.onGetStarted()
        assertEquals(OnboardingStep.Bible, vm.step)
        assertEquals(listOf("RV1909", "WEB"), vm.uiState.value.bibleOptions.map { it.id })
        assertEquals("RV1909", vm.uiState.value.bibleChoice) // preselected
        vm.onBibleSelected("SHORT") // not offered: ignored
        assertEquals("RV1909", vm.uiState.value.bibleChoice)

        vm.onBibleContinue()
        assertEquals(OnboardingStep.Tour, vm.step)
        assertEquals("RV1909", vm.uiState.value.tour.card?.verse?.translationId) // tour follows the pick
        // Not saved before finishing.
        assertEquals("WEB", ReaderPreferences(dataStore).translationId.first())

        vm.onGoalSelected(Goal.Understanding)
        vm.onTourSkip()
        vm.onReminderToggle(false)
        vm.onPlanContinue()
        assertEquals("RV1909", ReaderPreferences(dataStore).translationId.first())
        assertEquals(VerseRef(19, 119, 105), userData.lastReadingPosition())

        assertTrue(vm.onBack()) // Plan -> Tour -> Bible
        assertTrue(vm.onBack())
        assertEquals(OnboardingStep.Bible, vm.step)
    }

    @Test
    fun spanishUiCanKeepEnglish() = runTest {
        ContentLanguage.set("es")
        bible = FakeBibleRepository(withSpanish = true)
        val vm = viewModel()
        vm.onGetStarted()
        vm.onBibleSelected("WEB")
        vm.onBibleContinue()
        vm.onTourSkip()
        vm.onReminderToggle(false)
        vm.onPlanContinue()
        assertEquals("WEB", ReaderPreferences(dataStore).translationId.first())
    }

    @Test
    fun noBibleStepInEnglishOrWithoutRv1909() = runTest {
        bible = FakeBibleRepository(withSpanish = true)
        viewModel().also { it.onGetStarted() }.let { assertEquals(OnboardingStep.Tour, it.step) }

        ContentLanguage.set("es")
        bible = FakeBibleRepository(withSpanish = false)
        val vm = viewModel()
        vm.onGetStarted()
        assertEquals(OnboardingStep.Tour, vm.step)
        assertTrue(vm.uiState.value.bibleOptions.isEmpty())
    }

    @Test
    fun skipFromTourJumpsToPlanAndBackReturnsToTour() = runTest {
        val vm = viewModel()
        vm.onGetStarted()
        vm.onTourSkip()
        assertEquals(OnboardingStep.Plan, vm.step)
        vm.onBack()
        assertEquals(OnboardingStep.Tour, vm.step)
    }

    @Test
    fun goalResponseShowsMatchingVerse() = runTest {
        val vm = viewModel()
        vm.onGoalSelected(Goal.Understanding)
        val shown = vm.uiState.value.goalVerse
        assertEquals(VerseRef(19, 119, 105), shown?.verse?.ref)
        assertEquals("Psalms", shown?.bookName)
    }

    @Test
    fun timingMapsToDefaultReminder() = runTest {
        val vm = viewModel()
        mapOf(
            Timing.Morning to LocalTime.of(8, 0),
            Timing.Break to LocalTime.of(12, 30),
            Timing.Bedtime to LocalTime.of(21, 30),
        ).forEach { (timing, time) ->
            vm.onTimingSelected(timing)
            assertTrue(vm.uiState.value.reminderOn)
            assertEquals(time, vm.uiState.value.reminderTime)
        }
        vm.onTimingSelected(Timing.Whenever)
        assertFalse(vm.uiState.value.reminderOn)
    }

    @Test
    fun reselectingSameTimingKeepsEditedTime() = runTest {
        val vm = viewModel()
        vm.onTimingSelected(Timing.Morning)
        vm.onReminderTimeChange(LocalTime.of(6, 45))
        vm.onTimingSelected(Timing.Morning)
        assertEquals(LocalTime.of(6, 45), vm.uiState.value.reminderTime)
    }

    @Test
    fun stateSurvivesProcessDeath() = runTest {
        val handle = SavedStateHandle()
        viewModel(handle).apply {
            onGetStarted()
            onTourContinue()
            onGoalSelected(Goal.Peace)
            onGoalContinue()
            onGoalResponseContinue()
            onTimingSelected(Timing.Bedtime)
            onTimingContinue()
            onReminderTimeChange(LocalTime.of(22, 15))
        }
        // A new view model over the same saved state, as after the process is recreated.
        val restored = viewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
        with(restored.uiState.value) {
            assertEquals(OnboardingStep.Plan, step)
            assertEquals(Goal.Peace, goal)
            assertEquals(Timing.Bedtime, timing)
            assertEquals(LocalTime.of(22, 15), reminderTime)
        }
        restored.onBack()
        assertEquals(OnboardingStep.Timing, restored.step)
    }

    @Test
    fun finishingSavesReminderFirstVerseAndFlag() = runTest {
        val vm = viewModel()
        vm.onGetStarted()
        vm.onTourContinue()
        vm.onGoalSelected(Goal.Understanding)
        vm.onGoalContinue()
        vm.onGoalResponseContinue()
        vm.onTimingSelected(Timing.Morning)
        vm.onTimingContinue()
        vm.onPlanContinue() // permission not needed

        assertEquals(ReminderSettings(enabled = true, time = LocalTime.of(8, 0)), reminders.settings.first())
        assertEquals(ReminderSettings(enabled = true, time = LocalTime.of(8, 0)), scheduler.synced.last())
        assertEquals(VerseRef(19, 119, 105), userData.lastReadingPosition())
        assertTrue(onboarding.complete.first())
    }

    @Test
    fun noReminderSkipsPermissionEvenWhenItWouldBeNeeded() = runTest {
        access.needsPermission = true
        val vm = viewModel()
        vm.onGetStarted()
        vm.onTourSkip()
        vm.onReminderToggle(false)
        vm.onPlanContinue()

        assertTrue(onboarding.complete.first())
        assertFalse(reminders.settings.first().enabled)
        assertNull(userData.lastReadingPosition()) // no goal picked: feed starts as usual
    }

    @Test
    fun deniedPermissionStillFinishesWithoutReminder() = runTest {
        access.needsPermission = true
        access.canPost = false
        val vm = viewModel()
        vm.onGetStarted()
        vm.onTourSkip()
        vm.onPlanContinue()
        assertEquals(OnboardingStep.Permission, vm.step)
        assertFalse(onboarding.complete.first())

        vm.onPermissionResult(granted = false)
        assertTrue(vm.uiState.value.permissionDeclined)
        vm.onFinishWithoutReminder()

        assertTrue(onboarding.complete.first())
        assertFalse(reminders.settings.first().enabled)
        assertFalse(scheduler.synced.last().enabled)
    }

    @Test
    fun grantedPermissionFinishesWithReminder() = runTest {
        access.needsPermission = true
        val vm = viewModel()
        vm.onGetStarted()
        vm.onTourSkip()
        vm.onPlanContinue()
        vm.onPermissionResult(granted = true)

        assertTrue(onboarding.complete.first())
        assertTrue(reminders.settings.first().enabled)
    }
}
