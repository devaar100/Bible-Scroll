package aarnav100.developer.biblescroll.data.content

import aarnav100.developer.biblescroll.core.content.BookSource
import aarnav100.developer.biblescroll.core.content.StoredBook
import aarnav100.developer.biblescroll.core.content.StudyRow
import aarnav100.developer.biblescroll.core.content.VerseKey
import aarnav100.developer.biblescroll.core.content.db.ContentDatabase
import aarnav100.developer.biblescroll.core.content.db.RoomContentStore
import aarnav100.developer.biblescroll.core.content.db.StudyEntity
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * content.db v3 stores each study compressed (core/content StudyCompression) on a real phone: the dictionary is
 * packaged in the APK, rows round-trip, Settings → Storage gets the stored size, and a damaged row drops its book so
 * it is fetched again. Also logs import and read times for a Psalms-sized book (tag CompressedStudyStore).
 */
@RunWith(AndroidJUnit4::class)
class CompressedStudyStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, ContentDatabase::class.java).build()
    private val store = RoomContentStore(db.contentDao())

    @After
    fun close() = db.close()

    /** Real published studies: Psalm 23:1 (Hebrew words) and John 3:16 (Greek words). */
    private fun fixture(id: String): String =
        InstrumentationRegistry.getInstrumentation().context.assets.open("studies/$id.json").use { it.readBytes().decodeToString() }

    private fun book(bookId: Int, code: String, verses: Int) =
        StoredBook(bookId, code, "c0ffee000000", "ab".repeat(32), BookSource.BUNDLED, verses, 0, storedBytes = -1)

    @Test
    fun storesCompressedAndReadsBackTheSameJson() = runBlocking {
        val psalm = fixture("PSA.23.1")
        val john = fixture("JHN.3.16")
        store.replaceBook(book(19, "PSA", 1), listOf(VerseKey(19, 23, 1) to StudyRow("v1", psalm)))
        store.replaceBook(book(43, "JHN", 1), listOf(VerseKey(43, 3, 16) to StudyRow("v2", john)))

        assertEquals(psalm, store.observeStudyJson(VerseKey(19, 23, 1)).first())
        assertEquals(john, store.observeStudyJson(VerseKey(43, 3, 16)).first())
        assertNull(store.observeStudyJson(VerseKey(43, 3, 17)).first())

        // Storage shows what's really on the device: the compressed size, well under the JSON's.
        val stored = store.books().associateBy { it.code }
        val jsonBytes = psalm.encodeToByteArray().size
        assertTrue("PSA ${stored.getValue("PSA").storedBytes} of $jsonBytes", stored.getValue("PSA").storedBytes * 2.5 <= jsonBytes)
        assertTrue(stored.getValue("JHN").storedBytes > 0)
    }

    @Test
    fun aDamagedRowRemovesItsBookSoItIsFetchedAgain() = runBlocking {
        store.replaceBook(book(19, "PSA", 2), listOf(
            VerseKey(19, 23, 1) to StudyRow("v1", fixture("PSA.23.1")),
            VerseKey(19, 23, 2) to StudyRow("v1", fixture("PSA.23.1")),
        ))
        db.contentDao().insertStudies(listOf(StudyEntity(19, 23, 2, "v1", "damaged".encodeToByteArray())))

        assertNull(store.observeStudyJson(VerseKey(19, 23, 2)).first())
        assertNull(store.observeBook(19).first()) // not on the device any more: StudyRepository downloads it
        assertNull(store.observeStudyJson(VerseKey(19, 23, 1)).first())
    }

    @Test
    fun psalmsSizedBookImportsAndReadsQuickly() = runBlocking {
        // 2,461 verses, like the published Psalms; the real study text, so the sizes are realistic.
        val text = fixture("PSA.23.1")
        val rows = (1..2461).map { n -> VerseKey(19, 1 + n / 200, 1 + n % 200) to StudyRow("v$n", text.replace("PSA.23.1", "PSA.$n")) }

        var start = SystemClock.elapsedRealtime()
        store.replaceBook(book(19, "PSA", rows.size), rows)
        val importMs = SystemClock.elapsedRealtime() - start

        start = SystemClock.elapsedRealtimeNanos()
        val sample = rows.filterIndexed { i, _ -> i % 25 == 0 }
        for ((key, row) in sample) assertEquals(row.json, store.observeStudyJson(key).first())
        val readUs = (SystemClock.elapsedRealtimeNanos() - start) / 1000 / sample.size

        // The same reads without inflating: what the Room query and Flow set-up cost on their own.
        start = SystemClock.elapsedRealtimeNanos()
        for ((key, _) in sample) db.contentDao().observeStudyData(key.bookId, key.chapter, key.verse).first()
        val queryUs = (SystemClock.elapsedRealtimeNanos() - start) / 1000 / sample.size

        val jsonMb = rows.sumOf { it.second.json.encodeToByteArray().size } / 1e6
        val storedMb = store.books().single().storedBytes / 1e6
        Log.i("CompressedStudyStore", "Psalms-sized import: ${rows.size} verses, %.1f MB JSON -> %.2f MB stored, %d ms; read %d us/verse, of which Room query %d us"
            .format(jsonMb, storedMb, importMs, readUs, queryUs))
        assertTrue("import took $importMs ms", importMs < 30_000)
        assertTrue("read took $readUs us", readUs < 50_000)
    }
}
