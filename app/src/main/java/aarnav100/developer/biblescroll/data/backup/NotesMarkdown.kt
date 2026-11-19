package aarnav100.developer.biblescroll.data.backup

import aarnav100.developer.biblescroll.data.bible.References
import aarnav100.developer.biblescroll.data.bible.model.VerseRef

/** Section titles and labels, from string resources (kept out of this file so it stays plain Kotlin). */
data class MarkdownLabels(
    val title: String,
    /** "Exported %1$s · %2$s" (date, translation). */
    val exportedLine: String,
    val notes: String,
    val likes: String,
    val bookmarks: String,
    val none: String,
)

/**
 * The readable copy of an export: notes, likes and bookmarks with the verse text, in Bible order.
 * [verseText] and [bookNames] come from the reader's translation; a missing verse is listed without text.
 */
fun notesMarkdown(
    tables: BackupTables,
    verseText: Map<VerseRef, String>,
    bookNames: Map<Int, String>,
    translationId: String,
    date: String,
    labels: MarkdownLabels,
): String = buildString {
    fun ref(r: VerseRef) = References.verse(bookNames[r.bookId] ?: r.bookId.toString(), r)
    fun quote(r: VerseRef) = verseText[r]?.let { "> " + it.trim().replace("\n", "\n> ") }
    val order = compareBy<VerseRef>({ it.bookId }, { it.chapter }, { it.verse })

    appendLine("# ${labels.title}")
    appendLine()
    appendLine(labels.exportedLine.format(date, translationId))

    val notes = tables.notes.sortedWith(compareBy(order) { VerseRef(it.bookId, it.chapter, it.verse) })
    appendLine()
    appendLine("## ${labels.notes} (${notes.size})")
    if (notes.isEmpty()) appendLine().appendLine(labels.none)
    notes.forEach { n ->
        val r = VerseRef(n.bookId, n.chapter, n.verse)
        appendLine()
        appendLine("### ${ref(r)}")
        quote(r)?.let { appendLine(); appendLine(it) }
        appendLine()
        appendLine(n.text.trim())
    }

    fun list(heading: String, flag: (VerseMarkRow) -> Boolean) {
        val refs = tables.verseMarks.filter(flag).map { VerseRef(it.bookId, it.chapter, it.verse) }.sortedWith(order)
        appendLine()
        appendLine("## $heading (${refs.size})")
        appendLine()
        if (refs.isEmpty()) appendLine(labels.none)
        refs.forEach { r ->
            val text = verseText[r]?.trim()?.replace("\n", " ")
            appendLine(if (text == null) "- **${ref(r)}**" else "- **${ref(r)}**: $text")
        }
    }
    list(labels.likes) { it.liked }
    list(labels.bookmarks) { it.bookmarked }
}
