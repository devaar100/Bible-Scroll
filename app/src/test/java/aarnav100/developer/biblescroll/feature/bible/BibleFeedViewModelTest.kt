package aarnav100.developer.biblescroll.feature.bible

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.data.user.LocalUserDataRepository
import aarnav100.developer.biblescroll.testing.FakeBibleRepository
import aarnav100.developer.biblescroll.testing.FakePreferencesDataStore
import aarnav100.developer.biblescroll.testing.FakeStreakRepository
import aarnav100.developer.biblescroll.testing.FakeUserDao
import aarnav100.developer.biblescroll.ui.navigation.PendingVerseLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BibleFeedViewModelTest {

    private val bible = FakeBibleRepository()
    private val userData = LocalUserDataRepository(FakeUserDao()) { 1L }
    private val preferences = ReaderPreferences(FakePreferencesDataStore())
    private val link = PendingVerseLink()
    private val streaks = FakeStreakRepository()

    private val gen11 = VerseRef(1, 1, 1)
    private val gen13 = VerseRef(1, 1, 3)
    private val john315 = VerseRef(43, 3, 15)
    private val john316 = VerseRef(43, 3, 16)
    private val john317 = VerseRef(43, 3, 17)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): BibleFeedViewModel {
        val vm = BibleFeedViewModel(bible, userData, preferences, link, streaks)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private val BibleFeedViewModel.ready get() = uiState.value as BibleFeedUiState.Ready

    private fun BibleFeedViewModel.settleOn(ref: VerseRef) =
        onVerseSettled(ready.pages.values.first { it.ref == ref })

    @Test
    fun toggleLikeLikesThenUnlikes() = runTest {
        val vm = viewModel()

        vm.onToggleLike(john316)
        assertEquals(setOf(john316), vm.ready.liked)

        vm.onToggleLike(john316)
        assertTrue(vm.ready.liked.isEmpty())
    }

    @Test
    fun likeFollowsTheVerseAcrossTranslations() = runTest {
        val vm = viewModel()
        vm.onToggleLike(john315)

        vm.onSelectTranslation("SHORT")

        assertEquals("SHORT", vm.ready.translationId)
        assertEquals(setOf(john315), vm.ready.liked)
    }

    @Test
    fun toggleBookmarkDoesNotTouchLike() = runTest {
        val vm = viewModel()
        vm.onToggleLike(john316)

        vm.onToggleBookmark(john316)
        assertEquals(setOf(john316), vm.ready.bookmarked)
        assertEquals(setOf(john316), vm.ready.liked)

        vm.onToggleBookmark(john316)
        assertTrue(vm.ready.bookmarked.isEmpty())
        assertEquals(setOf(john316), vm.ready.liked)
    }

    @Test
    fun noteShowsInNotedSet() = runTest {
        val vm = viewModel()
        userData.saveNote(gen13, "light")

        assertEquals(setOf(gen13), vm.ready.noted)
    }

    @Test
    fun likedFilterShowsLikedVersesInBibleOrder() = runTest {
        val vm = viewModel()
        vm.onToggleLike(john317)
        vm.onToggleLike(gen13)
        vm.onToggleLike(john315)

        vm.onToggleLikedFilter()

        val feed = vm.ready.likedFeed!!
        assertEquals(listOf(gen13, john315, john317), feed.verses!!.map { it.ref })
        assertEquals(0, feed.startPage)
    }

    @Test
    fun likedFilterIsEmptyWhenNothingLiked() = runTest {
        val vm = viewModel()

        vm.onToggleLikedFilter()

        assertEquals(emptyList<Any>(), vm.ready.likedFeed!!.verses)
    }

    @Test
    fun unlikingInsideFilterKeepsThePageButShowsItUnliked() = runTest {
        val vm = viewModel()
        vm.onToggleLike(gen13)
        vm.onToggleLike(john315)
        vm.onToggleLikedFilter()

        vm.onToggleLike(gen13)

        assertEquals(listOf(gen13, john315), vm.ready.likedFeed!!.verses!!.map { it.ref })
        assertEquals(setOf(john315), vm.ready.liked)
    }

    @Test
    fun likedFilterSkipsVersesMissingFromTranslation() = runTest {
        val vm = viewModel()
        vm.onToggleLike(john316)
        vm.onToggleLike(john317)
        vm.onSelectTranslation("SHORT")

        vm.onToggleLikedFilter()

        assertEquals(listOf(john317), vm.ready.likedFeed!!.verses!!.map { it.ref })
    }

    @Test
    fun turningFilterOffReturnsToTheVerseYouWereOn() = runTest {
        val vm = viewModel()
        vm.onToggleLike(gen11)
        vm.onToggleLike(john317)
        vm.settleOn(john315)
        val keyBefore = vm.ready.pagerKey

        vm.onToggleLikedFilter()
        val likedVerses = vm.ready.likedFeed!!.verses!!
        vm.onLikedVerseSettled(likedVerses.last()) // browse to John 3:17 in the liked feed
        vm.onToggleLikedFilter()

        assertNull(vm.ready.likedFeed)
        assertNotEquals(keyBefore, vm.ready.pagerKey)
        assertEquals(bible.ordinalOf("WEB", john315) - 1, vm.ready.startPage)
    }

    @Test
    fun jumpingToAChapterLeavesTheFilter() = runTest {
        val vm = viewModel()
        vm.onToggleLike(gen11)
        vm.onToggleLikedFilter()

        vm.jumpToChapter(43, 3)

        assertNull(vm.ready.likedFeed)
        assertEquals(bible.ordinalOf("WEB", john315) - 1, vm.ready.startPage)
    }

    @Test
    fun openingALinkedVerseJumpsThereLeavesTheFilterAndConsumesTheLink() = runTest {
        val vm = viewModel()
        vm.onToggleLike(gen11)
        vm.onToggleLikedFilter()
        link.post(john316)
        assertEquals(john316, vm.pendingLink.value)

        vm.openLinkedVerse(john316)

        assertNull(vm.ready.likedFeed)
        assertEquals(bible.ordinalOf("WEB", john316) - 1, vm.ready.startPage)
        assertNull(vm.pendingLink.value)
    }

    @Test
    fun staleSettleFromTheOldPagerDoesNotHijackAJump() = runTest {
        val vm = viewModel()
        vm.settleOn(gen13)
        val stale = vm.ready.pages.values.first { it.ref == gen13 }
        bible.verseCountDelayMs = 100

        vm.openLinkedVerse(john316)
        // The old pager is re-shown (e.g. returning from another tab) and reports its verse again
        // while the jump is still resolving.
        vm.onVerseSettled(stale)
        advanceUntilIdle()

        assertEquals(bible.ordinalOf("WEB", john316) - 1, vm.ready.startPage)
    }

    @Test
    fun settlesCountAgainOnceTheJumpIsOnScreen() = runTest {
        val vm = viewModel()
        vm.jumpToChapter(43, 3)
        vm.settleOn(john317)

        vm.onSelectTranslation("SHORT") // re-resolves the anchor in the new translation

        assertEquals(bible.ordinalOf("SHORT", john317) - 1, vm.ready.startPage)
    }

    @Test
    fun dwelledVersesAreRecordedForTheStreak() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        val verse = vm.ready.pages.values.first()
        vm.onVerseDwelled(verse)
        advanceUntilIdle()
        assertEquals(listOf(verse.ref), streaks.versesViewed)
    }
}
