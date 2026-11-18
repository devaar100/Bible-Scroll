package aarnav100.developer.biblescroll.ui.navigation

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerseLinksTest {

    @Test
    fun roundTrips() {
        val ref = VerseRef(43, 3, 16)
        assertEquals("biblescroll://verse/43/3/16", VerseLinks.build(ref))
        assertEquals(ref, VerseLinks.parse(VerseLinks.build(ref)))
        assertEquals(VerseRef(19, 119, 176), VerseLinks.parse("biblescroll://verse/19/119/176/"))
    }

    @Test
    fun rejectsAnythingElse() {
        listOf(
            null, "", "https://example.com", "biblescroll://verse/43/3",
            "biblescroll://verse/0/1/1", "biblescroll://verse/67/1/1", "biblescroll://verse/43/0/16",
            "biblescroll://verse/43/3/x", "biblescroll://verse/43/3/16?evil=1", "biblescroll://other/43/3/16",
        ).forEach { assertNull(it, VerseLinks.parse(it)) }
    }

    @Test
    fun consumeOnlyClearsTheMatchingLink() {
        val pending = PendingVerseLink()
        pending.post(VerseRef(1, 1, 1))
        pending.post(VerseRef(2, 1, 1)) // a newer tap

        pending.consume(VerseRef(1, 1, 1))
        assertEquals(VerseRef(2, 1, 1), pending.ref.value)

        pending.consume(VerseRef(2, 1, 1))
        assertNull(pending.ref.value)
    }
}
