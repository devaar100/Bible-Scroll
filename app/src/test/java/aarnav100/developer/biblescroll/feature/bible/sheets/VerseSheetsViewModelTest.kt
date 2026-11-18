package aarnav100.developer.biblescroll.feature.bible.sheets

import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.testing.FakeBibleRepository
import aarnav100.developer.biblescroll.testing.FakeHomeScreenWidgets
import aarnav100.developer.biblescroll.testing.FakePreferencesDataStore
import aarnav100.developer.biblescroll.testing.FakeWidgetRefresher
import aarnav100.developer.biblescroll.widget.WidgetVerseRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VerseSheetsViewModelTest {

    private val john315Short = Verse("SHORT", VerseRef(43, 3, 15), 4, "SHORT", emptyList())
    private val john316 = Verse("WEB", VerseRef(43, 3, 16), 5, "WEB", emptyList())

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val home = FakeHomeScreenWidgets()
    private val refresher = FakeWidgetRefresher()
    private val widgetVerses = WidgetVerseRepository(FakePreferencesDataStore(), FakeBibleRepository(), refresher)

    private fun kotlinx.coroutines.test.TestScope.viewModel() = VerseSheetsViewModel(FakeBibleRepository(), widgetVerses, home).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.sheet.collect {} }
    }

    @Test
    fun actionsThenCompareThenClose() = runTest {
        val vm = viewModel()

        vm.openActions(john316)
        assertEquals(VerseSheet.Actions(john316), vm.sheet.value)

        vm.openCompare(john316)
        val compare = vm.sheet.value as VerseSheet.Compare
        assertEquals(listOf("WEB", "SHORT"), compare.rows!!.map { it.translation.id })

        vm.close()
        assertEquals(VerseSheet.None, vm.sheet.value)
    }

    @Test
    fun compareMarksVerseMissingFromATranslation() = runTest {
        val vm = viewModel()

        vm.openCompare(john316) // the fake's SHORT translation omits John 3:16

        val rows = (vm.sheet.value as VerseSheet.Compare).rows!!
        assertEquals("WEB", rows[0].verse?.translationId)
        assertNull(rows[1].verse)
    }

    @Test
    fun compareFromAnotherTranslationPutsThatOneFirst() = runTest {
        val vm = viewModel()

        vm.openCompare(john315Short)

        val rows = (vm.sheet.value as VerseSheet.Compare).rows!!
        assertEquals(listOf("SHORT", "WEB"), rows.map { it.translation.id })
        assertEquals(listOf(true, false), rows.map { it.isCurrent })
    }

    @Test
    fun shareImageOpensFromActions() = runTest {
        val vm = viewModel()
        vm.openActions(john316)

        vm.openShareImage(john316)

        assertEquals(VerseSheet.ShareImage(john316), vm.sheet.value)
    }

    @Test
    fun widgetConfirmReadsPlacementAndPinSupport() = runTest {
        val vm = viewModel()
        home.placed = false
        home.pinSupported = false

        vm.openWidgetConfirm(john316)

        assertEquals(VerseSheet.WidgetConfirm(john316, widgetPlaced = false, canPin = false), vm.sheet.value)
    }

    @Test
    fun confirmWithoutPinSetsVerseAndReportsIt() = runTest {
        val vm = viewModel()
        home.placed = true
        val events = mutableListOf<VerseSheetEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }

        vm.openWidgetConfirm(john316)
        vm.confirmWidgetVerse(pin = false)

        assertEquals(VerseSheet.None, vm.sheet.value)
        assertEquals(john316.ref, widgetVerses.widgetVerse.first().ref)
        assertEquals(1, refresher.refreshes)
        assertEquals(0, home.pinRequests)
        assertEquals(listOf(VerseSheetEvent.WidgetVerseSet), events)
    }

    @Test
    fun confirmWithPinSetsVerseAndAsksLauncherWithoutOurSnackbar() = runTest {
        val vm = viewModel()
        val events = mutableListOf<VerseSheetEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }

        vm.openWidgetConfirm(john316)
        vm.confirmWidgetVerse(pin = true)

        assertEquals(john316.ref, widgetVerses.widgetVerse.first().ref)
        assertEquals(1, home.pinRequests)
        assertEquals(emptyList<VerseSheetEvent>(), events)
    }

    @Test
    fun refusedPinStillConfirmsTheVerseWasSet() = runTest {
        val vm = viewModel()
        home.pinAccepted = false
        val events = mutableListOf<VerseSheetEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }

        vm.openWidgetConfirm(john316)
        vm.confirmWidgetVerse(pin = true)

        assertEquals(listOf(VerseSheetEvent.WidgetVerseSet), events)
    }

    @Test
    fun confirmDoesNothingWhenDialogIsNotOpen() = runTest {
        val vm = viewModel()

        vm.confirmWidgetVerse(pin = false)

        assertEquals(0, refresher.refreshes)
    }
}
