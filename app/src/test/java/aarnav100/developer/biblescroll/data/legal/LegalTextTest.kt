package aarnav100.developer.biblescroll.data.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** S8 Legal and the privacy page: exact attributions, notices that match the shipped files, and no drift. */
class LegalTextTest {

    private fun strings(folder: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$folder/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val e = nodes.item(i) as Element
            e.getAttribute("name") to e.textContent.replace(Regex("""\\(.)""")) { it.groupValues[1] }.trim()
        }
    }

    private val en = strings("values")
    private val es = strings("values-es")
    private val privacyKeys = en.keys.filter { it.startsWith("privacy_") }

    // ---- bible.db licence text ----

    @Test
    fun translationLicenceSplitsAtSource() {
        val (licence, attribution) = TranslationLicence.split(
            "Public domain. \"World English Bible\" is a trademark of eBible.org; modified text may not use that name. Source: eBible.org (engwebp).",
        )
        assertEquals("Public domain. \"World English Bible\" is a trademark of eBible.org; modified text may not use that name.", licence)
        assertEquals("Source: eBible.org (engwebp).", attribution)
        assertEquals("Public domain." to null, TranslationLicence.split(" Public domain. "))
        assertNull(TranslationLicence.split("Public domain. Source:").second)
    }

    // ---- Open-source libraries (Licensee report) ----

    @Test
    fun licenseeReportParsesSpdxAndUnknownLicences() {
        val libs = OpenSourceLibraries.parse(
            """[
              {"groupId":"com.squareup.okhttp3","artifactId":"okhttp","version":"5.5.1","name":"okhttp",
               "spdxLicenses":[{"identifier":"Apache-2.0","name":"Apache License 2.0","url":"https://www.apache.org/licenses/LICENSE-2.0"}],
               "scm":{"url":"https://github.com/square/okhttp"}},
              {"groupId":"androidx.activity","artifactId":"activity","version":"1.13.0","name":"Activity",
               "unknownLicenses":[{"name":"Custom","url":"https://example.org/licence"}]},
              {"groupId":"x.y","artifactId":"no-name","version":"1"}
            ]""",
        )
        assertEquals(listOf("Activity", "no-name", "okhttp"), libs.map { it.name }) // sorted by name
        assertEquals(listOf("Apache-2.0"), libs.last().licences)
        assertEquals(listOf("Custom"), libs.first().licences)
        assertEquals("https://github.com/square/okhttp", libs.last().url)
        assertEquals(emptyList<OpenSourceLibrary>(), OpenSourceLibraries.parse("""{"not":"an array"}"""))
    }

    // ---- Licence texts and fonts ----

    @Test
    fun everyLicenceTextIsShipped() {
        for (text in LicenceText.entries) {
            val file = File("src/main/assets/${text.asset}")
            assertTrue("${text.asset} missing", file.isFile && file.length() > 500)
        }
    }

    @Test
    fun fontNoticesMatchTheShippedLicenceFiles() {
        val literata = File("src/main/assets/licenses/literata-OFL.txt").readLines().first()
        val inter = File("src/main/assets/licenses/inter-OFL.txt").readLines().first()
        assertTrue(literata.startsWith("Copyright 2017 The Literata Project Authors"))
        assertTrue(inter.startsWith("Copyright (c) 2016 The Inter Project Authors"))
        assertTrue(en.getValue("legal_font_literata").contains("Copyright 2017 The Literata Project Authors"))
        assertTrue(en.getValue("legal_font_inter").contains("Copyright 2016 The Inter Project Authors"))
    }

    // ---- Study sources: exact text from pipeline/LICENCES.md ----

    @Test
    fun studySourceAttributionsAreVerbatimFromLicences() {
        val licences = File("../pipeline/LICENCES.md").readText().replace(Regex("\\s*\\n\\s*"), " ")
        for (key in listOf("legal_source_step", "legal_source_openbible", "legal_source_tsk")) {
            val text = en.getValue(key)
            assertTrue("$key isn't word for word in pipeline/LICENCES.md: $text", licences.contains("*\"$text\"*"))
            assertTrue("$key must not be translated (exact attribution)", key !in es)
        }
    }

    // ---- Privacy text ----

    @Test
    fun privacyTextIsCompleteInBothLanguages() {
        assertTrue(privacyKeys.size >= 15)
        for (key in privacyKeys) {
            assertTrue("$key is empty", en.getValue(key).isNotBlank())
            assertTrue("$key has no Spanish translation", !es[key].isNullOrBlank())
        }
    }

    @Test
    fun privacyTextNeverClaimsDataNeverLeavesThePhone() {
        val banned = Regex("""never leaves|doesn.t leave your phone|stays only on|nunca sale|no sale de tu teléfono""", RegexOption.IGNORE_CASE)
        for (key in privacyKeys) {
            for (lang in listOf(en, es)) {
                val text = lang[key] ?: continue
                assertTrue("$key: \"$text\"", !banned.containsMatchIn(text))
            }
        }
        // The IP-address disclosure for the CDN and the vote must be there.
        assertTrue(en.getValue("privacy_leaves_content").contains("IP address"))
        assertTrue(en.getValue("privacy_leaves_vote").contains("IP address"))
    }

    @Test
    fun webPrivacyPagesAreGeneratedFromTheCurrentStrings() {
        fun escaped(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#x27;")
        val pages = mapOf(en to File("../web/privacy/index.html"), es to File("../web/privacy/es/index.html"))
        for ((lang, page) in pages) {
            val html = page.readText()
            for (key in privacyKeys - setOf("privacy_updated", "privacy_contact", "privacy_who")) {
                assertTrue("${page.path} is out of date for $key: run python3 tools/build_privacy_page.py", html.contains(escaped(lang.getValue(key))))
            }
            // privacy_who has the developer's name filled in: check the text on either side of it.
            for (part in lang.getValue("privacy_who").split("%1\$s")) {
                assertTrue("${page.path} is out of date for privacy_who: run python3 tools/build_privacy_page.py", html.contains(escaped(part)))
            }
        }
    }
}
