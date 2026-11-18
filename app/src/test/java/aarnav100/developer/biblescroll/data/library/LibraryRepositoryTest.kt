package aarnav100.developer.biblescroll.data.library

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.user.LocalUserDataRepository
import aarnav100.developer.biblescroll.testing.FakeUserDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryRepositoryTest {
    private var now = 0L
    private val dao = FakeUserDao()
    private val userData = LocalUserDataRepository(dao) { now }
    private val library = LibraryRepository(dao) { now }

    private val john316 = VerseRef(43, 3, 16)
    private val gen11 = VerseRef(1, 1, 1)
    private val psa23 = VerseRef(19, 23, 1)

    private suspend fun likeAt(ref: VerseRef, at: Long) { now = at; userData.setLiked(ref, true) }
    private suspend fun ids(list: LibraryList, sort: LibrarySort, query: String = "") =
        library.entries(list, sort, query).first().map { it.ref }

    @Test
    fun sortsByBibleOrderOrMostRecentlyAdded() = runTest {
        likeAt(john316, 10); likeAt(gen11, 30); likeAt(psa23, 20)
        assertEquals(listOf(gen11, psa23, john316), ids(LibraryList.LIKED, LibrarySort.BIBLE))
        assertEquals(listOf(gen11, psa23, john316), ids(LibraryList.LIKED, LibrarySort.RECENT))
        now = 40; userData.setBookmarked(john316, true) // touching another flag doesn't reorder Liked
        assertEquals(listOf(gen11, psa23, john316), ids(LibraryList.LIKED, LibrarySort.RECENT))
        assertEquals(listOf(john316), ids(LibraryList.BOOKMARKED, LibrarySort.RECENT))
    }

    @Test
    fun removeAndUndoRestoresTheItemInPlace() = runTest {
        likeAt(john316, 10); likeAt(gen11, 30); likeAt(psa23, 20)
        now = 50
        val removal = library.remove(LibraryList.LIKED, psa23)!!
        assertEquals(listOf(gen11, john316), ids(LibraryList.LIKED, LibrarySort.RECENT))
        library.undo(removal)
        assertEquals(listOf(gen11, psa23, john316), ids(LibraryList.LIKED, LibrarySort.RECENT)) // same place, not top
        assertNull(library.remove(LibraryList.BOOKMARKED, psa23)) // nothing to remove
    }

    @Test
    fun studyListsAndCounts() = runTest {
        now = 5; userData.setStudied(john316, true); userData.setStudySaved(john316, true)
        now = 6; userData.setStudied(gen11, true)
        userData.setLiked(psa23, true)
        userData.saveNote(psa23, "Shepherd")
        assertEquals(listOf(gen11, john316), ids(LibraryList.STUDIED, LibrarySort.RECENT))
        assertEquals(listOf(john316), ids(LibraryList.SAVED_STUDIES, LibrarySort.BIBLE))
        val counts = library.counts().first()
        assertEquals(listOf(1, 0, 2, 1, 1), listOf(counts.liked, counts.bookmarked, counts.studied, counts.saved, counts.notes))
    }

    @Test
    fun notesShowTextSearchAndUndoDelete() = runTest {
        now = 1; userData.saveNote(john316, "God's love for the world")
        now = 2; userData.saveNote(gen11, "In the beginning: 100% grace")
        val notes = library.entries(LibraryList.NOTES, LibrarySort.RECENT).first()
        assertEquals(listOf(gen11, john316), notes.map { it.ref })
        assertEquals("God's love for the world", notes[1].note)
        assertEquals(listOf(john316), ids(LibraryList.NOTES, LibrarySort.BIBLE, "LOVE"))
        assertEquals(listOf(gen11), ids(LibraryList.NOTES, LibrarySort.BIBLE, "100%")) // % taken literally
        assertEquals(emptyList<VerseRef>(), ids(LibraryList.NOTES, LibrarySort.BIBLE, "1_0"))

        val removal = library.remove(LibraryList.NOTES, john316)!!
        assertEquals(listOf(gen11), ids(LibraryList.NOTES, LibrarySort.BIBLE))
        library.undo(removal)
        assertEquals("God's love for the world", userData.note(john316))
    }

    @Test
    fun likeWildcardsAreEscaped() {
        assertEquals("100\\%", LibraryRepository.escapeLike("100%"))
        assertEquals("a\\_b\\\\c", LibraryRepository.escapeLike("a_b\\c"))
    }
}
