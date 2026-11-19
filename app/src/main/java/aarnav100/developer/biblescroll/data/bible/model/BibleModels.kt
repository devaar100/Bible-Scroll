package aarnav100.developer.biblescroll.data.bible.model

data class Translation(
    val id: String,
    val name: String,
    val languageCode: String,
    val licence: String,
    /** False when the source marks no words of Jesus (BSB, RV1909), so nothing shows in red. */
    val hasRedLetters: Boolean = true,
)

enum class Testament { OLD, NEW }

data class Book(
    val id: Int,
    val code: String,
    /** In the app's language (BookNames); bible.db's English name if none is available. */
    val name: String,
    val testament: Testament,
    val chapterCount: Int,
    val abbreviation: String = name,
    /** Normalized words the book search matches; empty means name and code only. */
    val searchTerms: List<String> = emptyList(),
)

data class Chapter(
    val bookId: Int,
    val number: Int,
    val verseCount: Int,
    val superscription: String?,
)

data class VerseRef(
    val bookId: Int,
    val chapter: Int,
    val verse: Int,
)

data class Verse(
    val translationId: String,
    val ref: VerseRef,
    /** Position within the translation; use with BibleRepository.versesFrom for the feed. */
    val ordinal: Int,
    val text: String,
    /** Character ranges of [text] spoken by Jesus (red letters). Empty if none. */
    val wordsOfJesus: List<IntRange>,
)
