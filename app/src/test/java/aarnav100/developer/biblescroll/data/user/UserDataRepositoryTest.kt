package aarnav100.developer.biblescroll.data.user

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.testing.FakeUserDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDataRepositoryTest {

    private val dao = FakeUserDao()
    private var now = 1_000L
    private val repo = LocalUserDataRepository(dao) { now }

    private val john316 = VerseRef(43, 3, 16)
    private val key = VerseKey(43, 3, 16)

    @Test
    fun likeAndBookmarkAreIndependentFlagsOnOneRow() = runTest {
        repo.setLiked(john316, true)
        now = 2_000L
        repo.setBookmarked(john316, true)

        val mark = dao.marks.value.getValue(key)
        assertTrue(mark.liked)
        assertTrue(mark.bookmarked)
        assertEquals(1_000L, mark.createdAt)
        assertEquals(2_000L, mark.updatedAt)
        assertEquals(setOf(john316), repo.likedVerses().first())
        assertEquals(setOf(john316), repo.bookmarkedVerses().first())
    }

    @Test
    fun unlikeKeepsRowForSyncAndLeavesBookmark() = runTest {
        repo.setLiked(john316, true)
        repo.setBookmarked(john316, true)
        now = 5_000L
        repo.setLiked(john316, false)

        val mark = dao.marks.value.getValue(key)
        assertFalse(mark.liked)
        assertTrue(mark.bookmarked)
        assertEquals(5_000L, mark.updatedAt)
        assertEquals(emptySet<VerseRef>(), repo.likedVerses().first())
        assertEquals(setOf(john316), repo.bookmarkedVerses().first())
    }

    @Test
    fun settingSameValueDoesNotBumpUpdatedAt() = runTest {
        repo.setLiked(john316, true)
        now = 9_000L
        repo.setLiked(john316, true)

        assertEquals(1_000L, dao.marks.value.getValue(key).updatedAt)
    }

    @Test
    fun clearingAFlagOnAnUnmarkedVerseWritesNothing() = runTest {
        repo.setBookmarked(john316, false)
        assertTrue(dao.marks.value.isEmpty())
    }

    @Test
    fun saveNoteCreatesThenEditsKeepingCreatedAt() = runTest {
        repo.saveNote(john316, "God so loved")
        now = 3_000L
        repo.saveNote(john316, "God so loved the world")

        val note = dao.notes.value.getValue(key)
        assertEquals("God so loved the world", note.text)
        assertEquals(1_000L, note.createdAt)
        assertEquals(3_000L, note.updatedAt)
        assertEquals("God so loved the world", repo.note(john316))
        assertEquals(setOf(john316), repo.notedVerses().first())
    }

    @Test
    fun blankNoteDeletesTheRow() = runTest {
        repo.saveNote(john316, "draft")
        repo.saveNote(john316, "   \n ")

        assertNull(repo.note(john316))
        assertTrue(repo.notedVerses().first().isEmpty())
    }

    @Test
    fun deleteNoteRemovesIt() = runTest {
        repo.saveNote(john316, "draft")
        repo.deleteNote(john316)

        assertNull(repo.note(john316))
    }
}
