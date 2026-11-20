package aarnav100.developer.biblescroll.feature.discover

import aarnav100.developer.biblescroll.core.content.BookDownloader
import aarnav100.developer.biblescroll.core.content.BookImporter
import aarnav100.developer.biblescroll.core.content.ContentCache
import aarnav100.developer.biblescroll.core.content.DiscoverRepository
import aarnav100.developer.biblescroll.core.content.DiscoverTheme
import aarnav100.developer.biblescroll.core.content.StudyRepository
import aarnav100.developer.biblescroll.core.content.model.Language
import aarnav100.developer.biblescroll.data.user.LocalUserDataRepository
import aarnav100.developer.biblescroll.ui.navigation.PendingVerseLink
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.data.settings.SettingsRepository
import aarnav100.developer.biblescroll.testing.ContentJson
import aarnav100.developer.biblescroll.testing.FakeBibleRepository
import aarnav100.developer.biblescroll.testing.FakeContent
import aarnav100.developer.biblescroll.testing.FakePreferencesDataStore
import aarnav100.developer.biblescroll.testing.FakeUserDao
import aarnav100.developer.biblescroll.testing.sampleStudy
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val content = FakeContent()
    private val dataStore = FakePreferencesDataStore()
    private val settings = SettingsRepository(dataStore)
    private val userData = LocalUserDataRepository(FakeUserDao()) { 1L }
    private val link = PendingVerseLink()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(savedState: SavedStateHandle = SavedStateHandle()): DiscoverViewModel {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val downloader = BookDownloader(content.api, content.store, BookImporter(content.store, ContentJson), temp.newFolder(),
            CoroutineScope(backgroundScope.coroutineContext + dispatcher), dispatcher, "https://cdn.test/")
        val studies = StudyRepository(content.store, downloader, content.network, ContentJson, dispatcher)
        val discover = DiscoverRepository(content.api, ContentCache(temp.newFolder()), content.store, ContentJson,
            "https://cdn.test/", dispatcher)
        return DiscoverViewModel(savedState, discover, studies, content.network, settings, FakeBibleRepository(),
            userData, link, ReaderPreferences(dataStore)).also { vm -> backgroundScope.launch(dispatcher) { vm.uiState.collect {} } }
    }

    private fun publishFeed() {
        content.publish("GEN", 1, sampleStudy("GEN.1.1"))
        content.publish("PSA", 19, sampleStudy("PSA.119.105"))
        content.publish("JHN", 43, sampleStudy("JHN.3.16"), sampleStudy("JHN.14.6"),
            discover = listOf("JHN.3.16", "GEN.1.1", "PSA.119.105", "JHN.14.6", "REV.22.21"))
    }

    @Test
    fun cardsOnlyForAvailableBooksInOrder() = runTest {
        publishFeed() // REV isn't published or on the phone: no card
        val vm = viewModel()
        advanceUntilIdle()
        val ready = vm.uiState.value as DiscoverUiState.Ready
        assertEquals(listOf(VerseRef(43, 3, 16), VerseRef(1, 1, 1), VerseRef(19, 119, 105), VerseRef(43, 14, 6)), ready.cards.map { it.ref })
        assertTrue(ready.cards.all { it.theme == DiscoverTheme.TRUST })
        assertEquals(0, ready.startIndex)
    }

    @Test
    fun cardsNeverDownloadButShowTheTeaserOnceStored() = runTest {
        publishFeed()
        content.network.unmeteredState.value = false
        val vm = viewModel()
        advanceUntilIdle()
        val card = vm.card(ref(43, 3, 16)).first()
        assertEquals("John 3:16", card.reference)
        assertNull(card.hook)
        assertNull(card.fact)
        assertTrue(content.api.downloads.none { it.endsWith(".json.gz") })
    }

    @Test
    fun positionIsRememberedAndRestored() = runTest {
        publishFeed()
        val saved = SavedStateHandle()
        val vm = viewModel(saved)
        advanceUntilIdle()
        vm.onCardShown(2)
        // A new view model over the same saved state (e.g. after the tab or the process was recreated).
        val again = viewModel(saved)
        advanceUntilIdle()
        assertEquals(2, (again.uiState.value as DiscoverUiState.Ready).startIndex)
    }

    @Test
    fun prefetchesTheNextCardsBooksOnlyOnWifi() = runTest {
        publishFeed()
        content.network.unmeteredState.value = false
        val vm = viewModel()
        advanceUntilIdle()
        vm.onCardShown(0)
        advanceUntilIdle()
        assertTrue(content.api.downloads.none { it.endsWith(".json.gz") }) // on mobile data: nothing

        content.network.unmeteredState.value = true
        vm.onCardShown(0)
        advanceUntilIdle()
        // Next 3 cards: GEN, PSA, JHN -> each book once.
        assertEquals(setOf("GEN", "PSA", "JHN"), content.api.downloads.filter { it.endsWith(".json.gz") }
            .map { it.substringAfterLast('/').removeSuffix(".json.gz") }.toSet())
        assertEquals("God loves the world.", vm.card(ref(1, 1, 1)).first().hook)
    }

    @Test
    fun hiddenKeepsTheCardsButLeavesOutAiTextAndPrefetchesNothing() = runTest {
        settings.setHideAiStudies(true)
        publishFeed()
        content.network.unmeteredState.value = true
        val vm = viewModel()
        advanceUntilIdle()
        val ready = vm.uiState.value as DiscoverUiState.Ready
        vm.onCardShown(0)
        advanceUntilIdle()
        assertTrue(content.api.downloads.none { it.endsWith(".json.gz") })
        val card = vm.card(ready.cards[0]).first()
        assertTrue(card.aiHidden)
        assertNull(card.hook)
        assertNull(card.fact)
    }

    @Test
    fun withAiHiddenAStoredStudyGivesOnlyTheDataLine() = runTest {
        publishFeed()
        content.network.unmeteredState.value = true
        val vm = viewModel()
        advanceUntilIdle()
        vm.onCardShown(0) // stores GEN, PSA, JHN
        advanceUntilIdle()
        assertEquals("d", vm.card(ref(1, 1, 1)).first().fact)
        settings.setHideAiStudies(true)
        advanceUntilIdle()
        val card = vm.card(ref(1, 1, 1)).first()
        assertNull(card.hook)
        assertNull(card.fact)
        assertEquals(StudyDataLine(Language.GREEK, 1), card.dataLine) // sampleStudy: Greek, one cross reference
    }

    @Test
    fun likeBookmarkAndNoteUseTheSharedVerseStorage() = runTest {
        publishFeed()
        val vm = viewModel()
        advanceUntilIdle()
        val first = vm.card(ref(43, 3, 16)).first()
        vm.toggleLike(first)
        vm.toggleBookmark(first)
        userData.saveNote(VerseRef(43, 3, 16), "mine")
        advanceUntilIdle()
        val after = vm.card(ref(43, 3, 16)).first()
        assertTrue(after.liked && after.bookmarked && after.hasNote)
        assertEquals(setOf(VerseRef(43, 3, 16)), userData.bookmarkedVerses().first())
        vm.readInContext(after.ref)
        assertEquals(VerseRef(43, 3, 16), link.ref.value)
    }

    @Test
    fun offlineWithNothingCachedAndEmptyFeed() = runTest {
        content.api.offline = true
        val offline = viewModel()
        advanceUntilIdle()
        assertEquals(DiscoverUiState.Offline, offline.uiState.value)

        content.api.offline = false
        content.publish("JHN", 43, sampleStudy("JHN.3.16"), discover = emptyList())
        offline.retry()
        advanceUntilIdle()
        assertEquals(DiscoverUiState.Empty, offline.uiState.value)
    }

    private fun ref(book: Int, chapter: Int, verse: Int) = DiscoverCardRef(VerseRef(book, chapter, verse), DiscoverTheme.TRUST)
}
