package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.ManifestBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentVersionsTest {

    private fun stored(bookId: Int, code: String, version: String, source: BookSource = BookSource.DOWNLOADED) =
        StoredBook(bookId, code, version, version.padEnd(64, '0'), source, 1, 0, 100)

    private fun entry(bookId: Int, code: String, version: String) =
        ManifestBook(code, bookId, "https://cdn.test/$code", version, 1, 0, version.padEnd(64, '0'), 10)

    @Test
    fun supportedFormats() {
        assertTrue(ContentVersions.isSupported(1, 2))
        assertTrue(ContentVersions.isSupported(1, 1))
        assertFalse(ContentVersions.isSupported(1, 3)) // newer schema than this app knows
        assertFalse(ContentVersions.isSupported(2, 2)) // breaking file-format change
        assertFalse(ContentVersions.isSupported(0, 2))
    }

    @Test
    fun versionsCompareByEqualityNotOrder() {
        assertFalse(ContentVersions.needsUpdate(stored(43, "JHN", "aaaaaaaaaaaa"), entry(43, "JHN", "aaaaaaaaaaaa")))
        // Hashes have no order: "older-looking" values are still updates.
        assertTrue(ContentVersions.needsUpdate(stored(43, "JHN", "ffffffffffff"), entry(43, "JHN", "000000000000")))
    }

    @Test
    fun refreshPlanTouchesOnlyBooksOnTheDevice() {
        val plan = ContentVersions.refreshPlan(
            stored = listOf(
                stored(43, "JHN", "aaaaaaaaaaaa"),                         // changed upstream -> download
                stored(40, "MAT", "bbbbbbbbbbbb"),                         // unchanged -> nothing
                stored(45, "ROM", "cccccccccccc"),                         // withdrawn, downloaded -> remove
                stored(19, "PSA", "dddddddddddd", BookSource.BUNDLED),     // withdrawn but bundled -> keep
            ),
            manifest = listOf(
                entry(43, "JHN", "eeeeeeeeeeee"),
                entry(40, "MAT", "bbbbbbbbbbbb"),
                entry(1, "GEN", "ffffffffffff"),                            // never downloaded -> left alone
            ),
        )
        assertEquals(listOf("JHN"), plan.download.map { it.book })
        assertEquals(listOf("ROM"), plan.remove.map { it.code })
    }
}
