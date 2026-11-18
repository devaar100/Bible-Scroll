package aarnav100.developer.biblescroll.data.user

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.library.LibraryList
import aarnav100.developer.biblescroll.data.library.LibraryRepository
import aarnav100.developer.biblescroll.data.library.LibrarySort
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The real SQL behind the Library (counts, LIKE escaping, live flows) on SQLite. */
@RunWith(AndroidJUnit4::class)
class LibraryDaoTest {
    private lateinit var db: UserDatabase
    private lateinit var userData: LocalUserDataRepository
    private lateinit var library: LibraryRepository
    private var now = 0L

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, UserDatabase::class.java).build()
        userData = LocalUserDataRepository(db.userDao()) { now }
        library = LibraryRepository(db.userDao()) { now }
    }

    @After
    fun close() = db.close()

    private val a = VerseRef(43, 3, 16)
    private val b = VerseRef(1, 1, 1)

    @Test
    fun countsFromAnEmptyAndAFilledDatabase() = runBlocking {
        assertEquals(LibraryCounts(0, 0, 0, 0, 0), library.counts().first()) // SUM over no rows is NULL -> 0
        userData.setLiked(a, true); userData.setBookmarked(a, true); userData.setStudied(b, true)
        userData.setStudySaved(b, true); userData.saveNote(a, "x")
        userData.setLiked(b, true); userData.setLiked(b, false) // unliked rows don't count
        assertEquals(LibraryCounts(1, 1, 1, 1, 1), library.counts().first())
    }

    @Test
    fun noteSearchIsLiteralAndCaseInsensitive() = runBlocking {
        userData.saveNote(a, "God's LOVE: 100% sure")
        userData.saveNote(b, "a_b and 100 percent")
        suspend fun search(q: String) = library.entries(LibraryList.NOTES, LibrarySort.BIBLE, q).first().map { it.ref }
        assertEquals(listOf(a), search("love"))
        assertEquals(listOf(a), search("100%"))
        assertEquals(listOf(b), search("a_b"))
        assertEquals(emptyList<VerseRef>(), search("a%b"))
        assertEquals(emptyList<VerseRef>(), search("\\"))
    }

    @Test
    fun listsUpdateLive() = runBlocking {
        val updates = async { library.entries(LibraryList.LIKED, LibrarySort.RECENT).take(3).toList() }
        kotlinx.coroutines.delay(200)
        now = 1; userData.setLiked(a, true)
        kotlinx.coroutines.delay(200)
        now = 2; userData.setLiked(b, true)
        assertEquals(listOf(emptyList(), listOf(a), listOf(b, a)), updates.await().map { list -> list.map { it.ref } })
    }
}
