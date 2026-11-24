package aarnav100.developer.biblescroll.core.content.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

class StudyCompressionTest {

    /** Real published studies (2026-11-24): Psalm 23:1 (Hebrew words) and John 3:16 (Greek words). */
    private fun study(id: String): String =
        requireNotNull(javaClass.getResourceAsStream("/studies/$id.json")) { "fixture $id" }.use { it.readBytes().decodeToString() }

    @Test
    fun dictionaryShipsWithTheModule() {
        val size = StudyCompression.dictionary.size
        assertTrue("dictionary is $size bytes", size in 16 * 1024..32 * 1024) // zlib uses at most the last 32 KB
    }

    @Test
    fun roundTripsRealStudies() {
        for (id in listOf("PSA.23.1", "JHN.3.16")) {
            val json = study(id)
            assertEquals(id, json, StudyCompression.decompress(StudyCompression.compress(json)))
        }
    }

    @Test
    fun roundTripsAnyText() {
        for (text in listOf("", "a", "בְּרֵאשִׁית ἐν ἀρχῇ ἦν ὁ λόγος “quotes” — dash 🙏", "x".repeat(200_000))) {
            assertEquals(text, StudyCompression.decompress(StudyCompression.compress(text)))
        }
    }

    @Test
    fun storesRealStudiesAtLeastTwoAndAHalfTimesSmaller() {
        // Across the Gospels and Psalms the saving is 3.4x (tools/build_study_dictionary.py); one verse varies.
        for (id in listOf("PSA.23.1", "JHN.3.16")) {
            val json = study(id).encodeToByteArray()
            val stored = StudyCompression.compress(study(id)).size
            assertTrue("$id: ${json.size} -> $stored bytes", stored * 2.5 <= json.size)
        }
    }

    @Test
    fun rejectsDamagedRows() {
        val good = StudyCompression.compress(study("PSA.23.1"))
        assertNull(StudyCompression.decompress(good.copyOf(good.size / 2))) // truncated
        assertNull(StudyCompression.decompress(good.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }))
        assertNull(StudyCompression.decompress(ByteArray(0)))
        assertNull(StudyCompression.decompress("not zlib at all".encodeToByteArray()))
    }

    @Test
    fun rejectsRowsMadeWithAnotherDictionary() {
        val deflater = Deflater()
        deflater.setDictionary("some other dictionary".encodeToByteArray())
        deflater.setInput(study("PSA.23.1").encodeToByteArray())
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        assertNull(StudyCompression.decompress(out.toByteArray()))
    }
}
