package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.BookFile
import aarnav100.developer.biblescroll.core.content.model.ManifestBook
import aarnav100.developer.biblescroll.core.content.model.Study
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject

/** Decodes a verified book file (gzip JSON) and stores it in one transaction. */
class BookImporter @Inject constructor(
    private val store: ContentStore,
    private val json: Json,
) {
    /**
     * [gzipped] must already be verified against [entry]'s SHA-256. Throws [InvalidBookException] if the
     * contents don't match the entry or aren't a format this app reads; nothing is stored then.
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun import(gzipped: InputStream, entry: ManifestBook, source: BookSource): StoredBook {
        val file = try {
            GZIPInputStream(gzipped).use { json.decodeFromStream<BookFile>(it) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw InvalidBookException("${entry.book}: not a readable book file (${e.message})", e)
        }
        if (file.book != entry.book) throw InvalidBookException("File holds ${file.book}, manifest says ${entry.book}")
        if (!ContentVersions.isSupported(file.formatVersion, file.schemaVersion)) {
            throw InvalidBookException("${entry.book}: format ${file.formatVersion}/schema ${file.schemaVersion} not supported")
        }
        val rows = file.verses.map { study ->
            if (!study.id.startsWith("${entry.book}.")) throw InvalidBookException("${study.id} is not in ${entry.book}")
            val (chapter, verse) = study.chapterAndVerse()
            VerseKey(entry.bookId, chapter, verse) to StudyRow(study.contentVersion, json.encodeToString(Study.serializer(), study))
        }
        val book = StoredBook(
            bookId = entry.bookId, code = entry.book, contentVersion = entry.contentVersion, sha256 = entry.sha256,
            source = source, verseCount = rows.size, reviewedCount = file.verses.count { it.reviewed },
            storedBytes = rows.sumOf { (_, row) -> row.json.toByteArray().size.toLong() },
        )
        store.replaceBook(book, rows)
        return book
    }
}

class InvalidBookException(message: String, cause: Throwable? = null) : Exception(message, cause)
