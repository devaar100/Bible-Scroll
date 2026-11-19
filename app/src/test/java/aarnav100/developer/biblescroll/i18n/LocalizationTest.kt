package aarnav100.developer.biblescroll.i18n

import aarnav100.developer.biblescroll.data.bible.ReferencePatterns
import aarnav100.developer.biblescroll.data.bible.References
import aarnav100.developer.biblescroll.data.bible.model.Book
import aarnav100.developer.biblescroll.data.bible.model.Testament
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.bible.normalizeForSearch
import aarnav100.developer.biblescroll.feature.bible.picker.matchesBookQuery
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/** Reference formatting, book names and search, and the resource files behind the language switch. */
class LocalizationTest {

    @After fun reset() { References.patterns = ReferencePatterns.ENGLISH }

    // ---- References ----

    @Test
    fun englishReferences() {
        assertEquals("John 3:16", References.verse("John", VerseRef(43, 3, 16)))
        assertEquals("John 3:16–18", References.range("John", VerseRef(43, 3, 16), VerseRef(43, 3, 18)))
        assertEquals("Genesis 1:1–2:3", References.range("Genesis", VerseRef(1, 1, 1), VerseRef(1, 2, 3)))
        assertEquals(
            "Leviticus 27:34 – Numbers 1:1",
            References.range("Leviticus", VerseRef(3, 27, 34), VerseRef(4, 1, 1), endBook = "Numbers"),
        )
        assertEquals("Psalms 23", References.chapter("Psalms", 23))
    }

    @Test
    fun patternsComeFromTheLanguage() {
        // e.g. a language that writes "3,16 Juan": order and punctuation come from its strings.
        References.patterns = ReferencePatterns(
            Locale.forLanguageTag("es"), "%2\$d,%3\$d %1\$s", "%2\$d,%3\$d-%4\$d %1\$s", "%2\$d,%3\$d-%4\$d,%5\$d %1\$s",
            "%1\$s / %2\$s", "%1\$s %2\$d",
        )
        assertEquals("3,16 Juan", References.verse("Juan", VerseRef(43, 3, 16)))
        assertEquals("1,1-2,3 Génesis", References.range("Génesis", VerseRef(1, 1, 1), VerseRef(1, 2, 3)))
    }

    @Test
    fun numbersUseTheLanguagesDigits() {
        References.patterns = ReferencePatterns.ENGLISH.copy(locale = Locale.forLanguageTag("ar-EG"))
        assertEquals("يوحنا ٣:١٦", References.verse("يوحنا", VerseRef(43, 3, 16)))
    }

    // ---- Book search ----

    private fun book(name: String, code: String, vararg terms: String) =
        Book(0, code, name, Testament.NEW, 1, searchTerms = terms.map(::normalizeForSearch))

    @Test
    fun searchIgnoresAccentsCaseAndPunctuation() {
        assertEquals("genesis", normalizeForSearch("Génesis"))
        assertEquals("1corintios", normalizeForSearch("1 Corintios"))
        val genesis = book("Génesis", "GEN", "Génesis", "Gn", "Genesis", "Gen")
        assertTrue(matchesBookQuery(genesis, "genesis"))
        assertTrue(matchesBookQuery(genesis, "GÉN"))
        assertTrue(matchesBookQuery(genesis, "gn"))
    }

    @Test
    fun spanishAliasesAndEnglishNamesFindTheBook() {
        val cor = book("1 Corintios", "1CO", "1 Corintios", "1 Co", "1 Corinthians", "1 Cor", "Primera de Corintios", "I Corintios")
        assertTrue(matchesBookQuery(cor, "primera de corintios"))
        assertTrue(matchesBookQuery(cor, "1 cor"))
        assertTrue(matchesBookQuery(cor, "1 corinthians"))
        assertTrue(matchesBookQuery(cor, "1co"))
        assertFalse(matchesBookQuery(cor, "2 cor"))
        val santiago = book("Santiago", "JAS", "Santiago", "Stg", "James", "Jas", "Jacobo")
        assertTrue(matchesBookQuery(santiago, "james"))
        assertTrue(matchesBookQuery(santiago, "jacobo"))
    }

    // ---- Resource files ----

    private fun arrays(path: String): Map<String, List<String>> {
        val text = File(path).readText()
        return Regex("""<string-array name="(\w+)"[^>]*>(.*?)</string-array>""", RegexOption.DOT_MATCHES_ALL).findAll(text)
            .associate { m -> m.groupValues[1] to Regex("<item>(.*?)</item>").findAll(m.groupValues[2]).map { it.groupValues[1] }.toList() }
    }

    @Test
    fun everyLanguageHas66BookNamesAbbreviationsAndAliasRows() {
        for (path in listOf("src/main/res/values/books.xml", "src/main/res/values-es/books.xml")) {
            val a = arrays(path)
            for (name in listOf("book_names", "book_abbreviations", "book_search_aliases")) {
                assertEquals("$path $name", 66, a.getValue(name).size)
            }
            assertEquals("$path names must be unique", 66, a.getValue("book_names").toSet().size)
            assertEquals("$path abbreviations must be unique", 66, a.getValue("book_abbreviations").toSet().size)
        }
        val es = arrays("src/main/res/values-es/books.xml").getValue("book_names")
        assertEquals(listOf("Génesis", "Salmos", "Cantares", "Juan", "Santiago", "Apocalipsis"), listOf(es[0], es[18], es[21], es[42], es[58], es[65]))
    }

    private fun localeConfig(path: String) =
        Regex("""<locale android:name="([^"]+)"""").findAll(File(path).readText()).map { it.groupValues[1] }.toList()

    @Test
    fun languagesOfferedMatchTheLocaleConfigAndHaveBookNames() {
        for ((config, list) in listOf("src/main/res/xml/locales_config.xml" to "src/main/res/values/arrays.xml")) {
            val languages = localeConfig(config)
            assertEquals("$config vs $list", languages, arrays(list).getValue("app_languages"))
            assertTrue("English must stay offered", "en" in languages)
            languages.filter { it != "en" }.forEach { lang ->
                assertTrue("$lang offered but values-$lang/books.xml is missing", File("src/main/res/values-$lang/books.xml").exists())
                assertTrue("$lang offered but values-$lang/strings.xml is missing", File("src/main/res/values-$lang/strings.xml").exists())
            }
        }
    }

    @Test
    fun stringsWithSeveralArgumentsUsePositions() {
        val text = File("src/main/res/values/strings.xml").readText()
        val bad = Regex("""<(string|item)[^>]*>([^<]*)</\1>""").findAll(text)
            .map { it.groupValues[2] }
            .filter { Regex("%[sdf]").containsMatchIn(it) && Regex("%(\\d+\\$)?[sdf]").findAll(it).count() > 1 }
            .toList()
        assertTrue("Use %1\$s, %2\$d… so translators can reorder: $bad", bad.isEmpty())
    }

    @Test
    fun codeDoesNotGlueVerseReferencesOrSentencesTogether() {
        val glued = Regex("""\$\{?[\w.\[\]()]*\}? ?\$\{?[\w.]*chapter\}?:\$|" · "|joinToString\(", "\)|stringResource\([^)]*\) \+ "|Text\("[A-Za-z]{2,} """)
        val offenders = File("src/main/java").walkTopDown().filter { it.extension == "kt" }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, line -> if (glued.containsMatchIn(line)) "${f.name}:${i + 1}" else null } }
            .toList()
        assertTrue("Build user-facing text with string resources, References or formatList: $offenders", offenders.isEmpty())
    }
}
