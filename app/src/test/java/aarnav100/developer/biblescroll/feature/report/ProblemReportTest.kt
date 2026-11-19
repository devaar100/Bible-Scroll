package aarnav100.developer.biblescroll.feature.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class ProblemReportTest {

    private val address = "help@example.org"
    private val device = DeviceInfo(appVersion = "1.0 (1)", androidVersion = "15 (API 35)", deviceModel = "Google Pixel 8", language = "es")
    private val study = ReportContext(
        contentType = ContentType.STUDY,
        contentId = "JHN.3.16",
        reference = "Juan 3:16",
        translationId = "KJV",
        contentVersion = "60a7844d3ae1",
        promptHash = "62631d6c8e7e",
    )

    private fun field(body: String, label: String): String =
        body.lineSequence().first { it.startsWith("$label: ") }.removePrefix("$label: ")

    /** Decodes a mailto query parameter the way an email app does. */
    private fun param(mailto: String, name: String): String =
        URLDecoder.decode(mailto.substringAfter("?").split("&").first { it.startsWith("$name=") }.removePrefix("$name="), "UTF-8")

    @Test
    fun subjectAndDetailsForEveryType() {
        for (type in ReportType.entries) {
            val email = ProblemReport.build(address, type, "note", study, device)

            assertEquals("Report: ${type.emailLabel} – Juan 3:16", email.subject)
            assertEquals(type.emailLabel, field(email.body, "Report type"))
            val expectedContent = if (type == ReportType.WRONG_TEXT) "translation" else "study"
            assertEquals(expectedContent, field(email.body, "Content type"))
            assertEquals("JHN.3.16", field(email.body, "Content id"))
            assertEquals("KJV", field(email.body, "Translation"))
            assertEquals("es", field(email.body, "Language"))
            assertEquals("60a7844d3ae1", field(email.body, "Content version"))
            assertEquals("62631d6c8e7e", field(email.body, "Prompt hash"))
            assertEquals("1.0 (1)", field(email.body, "App version"))
            assertEquals("15 (API 35)", field(email.body, "Android version"))
            assertEquals("Google Pixel 8", field(email.body, "Device model"))
        }
    }

    @Test
    fun everyContentTypeIsLabelled() {
        for (content in ContentType.entries) {
            val email = ProblemReport.build(address, ReportType.OTHER, "", ReportContext(content), device)
            assertEquals(content.emailLabel, field(email.body, "Content type"))
        }
    }

    @Test
    fun noteComesFirstThenTheDetailsBlock() {
        val body = ProblemReport.build(address, ReportType.FACTUAL_ERROR, "  The date is wrong.\nIt was 586 BC.  ", study, device).body
        assertTrue(body.startsWith("The date is wrong.\nIt was 586 BC.\n\n--- Details (please keep) ---\n"))
    }

    @Test
    fun emptyNoteLeavesOnlyTheDetails() {
        val body = ProblemReport.build(address, ReportType.APP_BUG, "   ", ReportContext.App, device).body
        assertTrue(body.startsWith("--- Details (please keep) ---"))
    }

    @Test
    fun missingMetaFieldsSayNotAvailable() {
        val email = ProblemReport.build(address, ReportType.APP_BUG, "", ReportContext.App, device)

        assertEquals("Report: App bug", email.subject) // no dangling " – "
        for (label in listOf("Content id", "Reference", "Translation", "Content version", "Prompt hash")) {
            assertEquals("not available", field(email.body, label))
        }
        // A study published without a prompt hash (today's book files) still reports everything else.
        val noHash = ProblemReport.build(address, ReportType.UNFAIR, "", study.copy(promptHash = null, contentVersion = " "), device)
        assertEquals("not available", field(noHash.body, "Prompt hash"))
        assertEquals("not available", field(noHash.body, "Content version"))
        assertEquals("JHN.3.16", field(noHash.body, "Content id"))
    }

    @Test
    fun detailValuesStayOnOneLine() {
        val email = ProblemReport.build(address, ReportType.OTHER, "", study.copy(reference = "Plan\nday 4"), device)
        assertEquals("Plan day 4", field(email.body, "Reference"))
    }

    @Test
    fun mailtoEscapesSpecialCharactersAndRoundTrips() {
        val note = "Ampersand & question? hash# percent% plus+ equals= \"quotes\" <tags> Génesis 1:1 — ünïcödé 🙏\nsecond line"
        val email = ProblemReport.build(address, ReportType.WRONG_TEXT, note, study.copy(reference = "Génesis 1:1 & más"), device)
        val mailto = email.mailto

        assertTrue(mailto.startsWith("mailto:help@example.org?subject="))
        // Nothing in the query that would end a parameter early or be read as a space.
        val query = mailto.substringAfter("?")
        assertEquals(2, query.split("&").size)
        assertFalse(query.contains("+"))
        assertFalse(query.contains("#"))
        assertFalse(query.contains(" "))
        assertTrue(query.contains("%0D%0A")) // RFC 6068 line breaks
        assertEquals(email.subject, param(mailto, "subject"))
        assertEquals(email.body.replace("\n", "\r\n"), param(mailto, "body"))
    }

    @Test
    fun placeholderAddressIsEncodedRatherThanBreakingTheLink() {
        val mailto = ProblemReport.build("<YOUR ADDRESS>", ReportType.OTHER, "", ReportContext.App, device).mailto
        assertTrue(mailto.startsWith("mailto:%3CYOUR%20ADDRESS%3E?subject="))
    }

    @Test
    fun shortNotesAreUntouched() {
        val note = "x".repeat(ProblemReport.MAX_NOTE_CHARS)
        assertEquals(note, ProblemReport.shorten(note))
    }

    @Test
    fun longNotesAreCutAtAWordAndMarked() {
        val note = "word ".repeat(1_000) // 5,000 characters
        val short = ProblemReport.shorten(note)

        assertTrue(short.length <= ProblemReport.MAX_NOTE_CHARS)
        assertTrue(short.endsWith(ProblemReport.SHORTENED_MARKER))
        assertTrue(short.removeSuffix(ProblemReport.SHORTENED_MARKER).endsWith("word"))
    }

    @Test
    fun longNotesWithoutSpacesAreStillCut() {
        val short = ProblemReport.shorten("a".repeat(10_000))
        assertTrue(short.length <= ProblemReport.MAX_NOTE_CHARS)
        assertTrue(short.endsWith(ProblemReport.SHORTENED_MARKER))
    }

    @Test
    fun cuttingNeverSplitsAnEmoji() {
        // 🙏 is two UTF-16 chars; try every alignment so one lands on the cut.
        for (offset in 0..1) {
            val short = ProblemReport.shorten("a".repeat(offset) + "🙏".repeat(2_000))
            val kept = short.removeSuffix(ProblemReport.SHORTENED_MARKER)
            assertFalse("offset $offset split a pair", Character.isHighSurrogate(kept.last()))
            assertEquals(kept.length, kept.codePoints().toArray().sumOf { Character.charCount(it) })
        }
    }

    @Test
    fun aVeryLongNoteKeepsTheWholeMailtoAtAWorkableLength() {
        val email = ProblemReport.build(address, ReportType.FACTUAL_ERROR, "é ".repeat(10_000), study, device)
        assertTrue(email.body.contains(ProblemReport.SHORTENED_MARKER))
        assertTrue(email.body.contains("--- Details (please keep) ---"))
        // Accented text triples when percent-encoded; still well under the ~32 KB some apps cap intents at.
        assertTrue("mailto is ${email.mailto.length} chars", email.mailto.length < 12_000)
    }

    @Test
    fun feedbackHasNoTypeOrContentButKeepsDeviceDetails() {
        val email = ProblemReport.feedback(address, "", device)
        assertEquals("Feedback – Bible Scroll 1.0 (1)", email.subject)
        assertFalse(email.body.contains("Report type"))
        assertEquals("Google Pixel 8", field(email.body, "Device model"))
    }

    @Test
    fun copyTextHasEverythingNeededToSendByHand() {
        val email = ProblemReport.build(address, ReportType.OFFENSIVE, "Please check", study, device)
        assertTrue(email.copyText.startsWith("To: help@example.org\nSubject: Report: Offensive or inappropriate – Juan 3:16\n\nPlease check"))
    }
}
