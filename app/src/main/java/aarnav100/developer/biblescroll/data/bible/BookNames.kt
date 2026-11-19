package aarnav100.developer.biblescroll.data.bible

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.settings.AppLanguage
import android.content.res.Configuration
import android.content.res.Resources
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** One book's names in the app's language, plus everything the book search should match. */
data class LocalizedBook(
    val name: String,
    val abbreviation: String,
    /** Normalized ([normalizeForSearch]): localized name, abbreviation and aliases, the English ones, and the USFM code. */
    val searchTerms: List<String>,
)

/** Book names by language (res/values/books.xml, values-es/books.xml). Emits again when the app language changes. */
interface BookNames {
    /** Book id (1..66) -> names. Null means "use the names stored in bible.db". */
    val books: Flow<Map<Int, LocalizedBook>?>

    /** For tests and tools: keep bible.db's English names. */
    object Stored : BookNames {
        override val books: Flow<Map<Int, LocalizedBook>?> = flowOf(null)
    }
}

@Singleton
class ResourceBookNames @Inject constructor(private val language: AppLanguage) : BookNames {
    override val books: Flow<Map<Int, LocalizedBook>?> = language.locale.map { load(language.resources()) }

    private fun load(res: Resources): Map<Int, LocalizedBook> {
        val english = res.inLocale(Locale.ENGLISH)
        val names = res.getStringArray(R.array.book_names)
        val abbreviations = res.getStringArray(R.array.book_abbreviations)
        val aliases = res.getStringArray(R.array.book_search_aliases)
        val enNames = english.getStringArray(R.array.book_names)
        val enAbbreviations = english.getStringArray(R.array.book_abbreviations)
        val enAliases = english.getStringArray(R.array.book_search_aliases)
        return names.indices.associate { i ->
            val terms = listOf(names[i], abbreviations[i], enNames[i], enAbbreviations[i]) +
                aliases[i].split('|') + enAliases[i].split('|')
            (i + 1) to LocalizedBook(
                name = names[i],
                abbreviation = abbreviations[i],
                searchTerms = terms.map(::normalizeForSearch).filter { it.isNotEmpty() }.distinct(),
            )
        }
    }

    private fun Resources.inLocale(locale: Locale): Resources {
        val config = Configuration(configuration).apply { setLocale(locale) }
        @Suppress("DEPRECATION") // a throwaway Resources for the English names; no Context needed
        return Resources(assets, displayMetrics, config)
    }
}

/** Lower case, accents and punctuation removed: "1 Corintios" -> "1corintios", "Génesis" -> "genesis". */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKD)
        .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        .lowercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() }
