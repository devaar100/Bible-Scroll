package aarnav100.developer.biblescroll.feature.community

import aarnav100.developer.biblescroll.core.content.model.Ministry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class CommunityFormatTest {

    // --- currency (history.json `currency`, never a hard-coded "$") ---

    @Test
    fun wholeAmountsDropTheDecimals() {
        assertEquals("$300", formatDonation("300".toBigDecimal(), "USD", Locale.US))
        assertEquals("$1,250", formatDonation("1250".toBigDecimal(), "USD", Locale.US))
    }

    @Test
    fun otherAmountsKeepTheCurrencysDigits() {
        assertEquals("$312.50", formatDonation("312.5".toBigDecimal(), "USD", Locale.US))
        assertEquals("€312.50", formatDonation("312.50".toBigDecimal(), "EUR", Locale.US))
    }

    @Test
    fun theCurrencyFieldAndLocaleDecideTheFormat() {
        val es = Locale.forLanguageTag("es-ES")
        val text = formatDonation("312.5".toBigDecimal(), "EUR", es).replace(' ', ' ')
        assertEquals("312,50 €", text)
        assertEquals("£300", formatDonation("300".toBigDecimal(), "GBP", Locale.UK))
        assertEquals("¥5,000", formatDonation("5000".toBigDecimal(), "JPY", Locale.JAPAN).replace('￥', '¥'))
    }

    @Test
    fun anUnknownCodeIsShownBesideTheNumber() {
        assertEquals("300 XQQ", formatDonation("300".toBigDecimal(), "XQQ", Locale.US))
    }

    // --- "Closes in N days" ---

    private val utc = ZoneId.of("UTC")
    private val closes = Instant.parse("2026-12-01T00:00:00Z") // November's round

    @Test
    fun daysLeftCountsCalendarDays() {
        assertEquals(3, daysLeft(Instant.parse("2026-11-27T09:00:00Z"), closes, utc))
        assertEquals(1, daysLeft(Instant.parse("2026-11-29T23:00:00Z"), closes, utc))
        assertEquals(0, daysLeft(Instant.parse("2026-11-30T01:00:00Z"), closes, utc))
        assertNull(daysLeft(closes, closes, utc))
    }

    @Test
    fun daysLeftUsesThePhonesTimeZone() {
        // 2026-11-30 20:00 in Chicago is already December 1 in UTC: the vote closes later "today" there.
        val chicago = ZoneId.of("America/Chicago")
        assertEquals(0, daysLeft(Instant.parse("2026-11-30T14:00:00Z"), closes, chicago))
        assertEquals(1, daysLeft(Instant.parse("2026-11-29T14:00:00Z"), closes, chicago))
    }

    // --- categories ---

    @Test
    fun categoriesSplitOnCommas() {
        val m = Ministry("river", "River", mapOf("en" to "d"), "https://r.org", "https://r.org/give", listOf("2026-11"), category = " Relief, Development ,, ")
        assertEquals(listOf("Relief", "Development"), m.categories)
        assertEquals(emptyList<String>(), m.copy(category = null).categories)
    }

    @Test
    fun aMinistryKeepsItsTintOnEveryScreen() {
        assertEquals(tintIndex("caritas"), tintIndex("caritas"))
        assertEquals(ministryTint("caritas", dark = false), ministryTint("caritas", dark = false))
        val spread = listOf("caritas", "acn", "pms", "peters-pence", "malteser", "river", "harbor").map(::tintIndex).toSet()
        assertTrue(spread.size >= 3) // ids spread over the palette instead of all landing on one colour
    }
}
