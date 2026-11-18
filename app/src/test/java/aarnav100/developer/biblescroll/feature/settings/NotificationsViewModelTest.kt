package aarnav100.developer.biblescroll.feature.settings

import aarnav100.developer.biblescroll.data.reminder.NotificationAccess
import aarnav100.developer.biblescroll.data.reminder.ReminderRepository
import aarnav100.developer.biblescroll.data.reminder.ReminderScheduler
import aarnav100.developer.biblescroll.data.reminder.ReminderSettings
import aarnav100.developer.biblescroll.testing.FakePreferencesDataStore
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    private class FakeAccess(var canPost: Boolean, var needsPermission: Boolean) : NotificationAccess {
        override fun canPost() = canPost
        override fun needsRuntimePermission() = needsPermission
    }

    private class RecordingScheduler(private val repo: ReminderRepository) : ReminderScheduler {
        val synced = mutableListOf<ReminderSettings>()
        override fun sync(settings: ReminderSettings) {
            synced += settings
        }
        override suspend fun syncFromStore() = sync(repo.settings.first())
    }

    private val repo = ReminderRepository(FakePreferencesDataStore())
    private val scheduler = RecordingScheduler(repo)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(access: NotificationAccess) = NotificationsViewModel(repo, scheduler, access).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    private fun TestScope.events(vm: NotificationsViewModel) = mutableListOf<NotificationsEvent>().also { list ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { list += it } }
    }

    @Test
    fun turningOnWhenAllowedEnablesAndSchedules() = runTest {
        val vm = viewModel(FakeAccess(canPost = true, needsPermission = false))

        vm.onToggle(true)

        assertTrue(vm.uiState.value.enabled)
        assertFalse(vm.uiState.value.blocked)
        assertEquals(true, scheduler.synced.last().enabled)
    }

    @Test
    fun turningOnOnAndroid13AsksForPermissionFirstAndStaysOffMeanwhile() = runTest {
        val vm = viewModel(FakeAccess(canPost = false, needsPermission = true))
        val events = events(vm)

        vm.onToggle(true)

        assertEquals(listOf(NotificationsEvent.RequestPermission), events)
        assertFalse(vm.uiState.value.enabled)
        assertTrue(scheduler.synced.isEmpty())
    }

    @Test
    fun grantedPermissionEnables() = runTest {
        val access = FakeAccess(canPost = false, needsPermission = true)
        val vm = viewModel(access)
        vm.onToggle(true)

        access.canPost = true
        access.needsPermission = false
        vm.onPermissionResult(granted = true)

        assertTrue(vm.uiState.value.enabled)
        assertEquals(true, scheduler.synced.last().enabled)
    }

    @Test
    fun deniedPermissionKeepsToggleOffAndShowsTheCard() = runTest {
        val vm = viewModel(FakeAccess(canPost = false, needsPermission = true))
        vm.onToggle(true)

        vm.onPermissionResult(granted = false)

        assertFalse(vm.uiState.value.enabled)
        assertTrue(vm.uiState.value.blocked)
        assertTrue(scheduler.synced.isEmpty())
    }

    @Test
    fun appNotificationsSwitchedOffShowsTheCardWithoutAsking() = runTest {
        val vm = viewModel(FakeAccess(canPost = false, needsPermission = false)) // pre-13, or blocked in settings
        val events = events(vm)

        vm.onToggle(true)

        assertTrue(events.isEmpty())
        assertFalse(vm.uiState.value.enabled)
        assertTrue(vm.uiState.value.blocked)
    }

    @Test
    fun cardClearsOnResumeOnceAllowedInSystemSettings() = runTest {
        val access = FakeAccess(canPost = false, needsPermission = false)
        val vm = viewModel(access)
        vm.onToggle(true)
        assertTrue(vm.uiState.value.blocked)

        access.canPost = true
        vm.onResume()

        assertFalse(vm.uiState.value.blocked)
        assertFalse(vm.uiState.value.enabled) // the user flips it on themselves
    }

    @Test
    fun revokedLaterShowsTheCardWhileStillEnabled() = runTest {
        val access = FakeAccess(canPost = true, needsPermission = false)
        val vm = viewModel(access)
        vm.onToggle(true)

        access.canPost = false
        vm.onResume()

        assertTrue(vm.uiState.value.enabled)
        assertTrue(vm.uiState.value.blocked)
    }

    @Test
    fun turningOffCancels() = runTest {
        val vm = viewModel(FakeAccess(canPost = true, needsPermission = false))
        vm.onToggle(true)

        vm.onToggle(false)

        assertFalse(vm.uiState.value.enabled)
        assertEquals(false, scheduler.synced.last().enabled)
    }

    @Test
    fun changingTimeReschedulesWithTheNewTime() = runTest {
        val vm = viewModel(FakeAccess(canPost = true, needsPermission = false))
        vm.onToggle(true)

        vm.onTimeChange(LocalTime.of(21, 15))

        assertEquals(LocalTime.of(21, 15), vm.uiState.value.time)
        assertEquals(ReminderSettings(true, LocalTime.of(21, 15)), scheduler.synced.last())
    }
}
