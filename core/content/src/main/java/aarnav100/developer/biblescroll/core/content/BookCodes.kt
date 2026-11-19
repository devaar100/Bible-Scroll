package aarnav100.developer.biblescroll.core.content

/** USFM codes in canonical order; index + 1 = the app's book id (matches the bundled Bible DB). */
object BookCodes {
    private val CODES = listOf(
        "GEN", "EXO", "LEV", "NUM", "DEU", "JOS", "JDG", "RUT", "1SA", "2SA", "1KI", "2KI", "1CH", "2CH", "EZR",
        "NEH", "EST", "JOB", "PSA", "PRO", "ECC", "SNG", "ISA", "JER", "LAM", "EZK", "DAN", "HOS", "JOL", "AMO",
        "OBA", "JON", "MIC", "NAM", "HAB", "ZEP", "HAG", "ZEC", "MAL",
        "MAT", "MRK", "LUK", "JHN", "ACT", "ROM", "1CO", "2CO", "GAL", "EPH", "PHP", "COL", "1TH", "2TH", "1TI",
        "2TI", "TIT", "PHM", "HEB", "JAS", "1PE", "2PE", "1JN", "2JN", "3JN", "JUD", "REV",
    )
    private val IDS = CODES.withIndex().associate { (i, code) -> code to i + 1 }

    fun code(bookId: Int): String = CODES[bookId - 1]

    /** "JHN" -> 43, or null for an unknown code. */
    fun id(code: String): Int? = IDS[code]

    /** "JHN.3.16" -> VerseKey(43, 3, 16), or null if it isn't a valid study id. */
    fun parse(studyId: String): VerseKey? {
        val parts = studyId.split(".")
        if (parts.size != 3) return null
        val book = IDS[parts[0]] ?: return null
        val chapter = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val verse = parts[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
        return VerseKey(book, chapter, verse)
    }
}
