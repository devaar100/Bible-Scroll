package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.Study
import kotlinx.coroutines.flow.Flow

data class VerseKey(val bookId: Int, val chapter: Int, val verse: Int)

enum class BookSource { BUNDLED, DOWNLOADED }

data class StoredBook(
    val bookId: Int,
    val code: String,
    val contentVersion: String,
    val sha256: String,
    val source: BookSource,
    val verseCount: Int,
    val reviewedCount: Int,
    /** Bytes of study data stored on the device. */
    val storedBytes: Long,
)

/** Local study storage (Room in the app; a fake in tests). */
interface ContentStore {
    fun observeStudyJson(key: VerseKey): Flow<String?>
    fun observeBook(bookId: Int): Flow<StoredBook?>
    fun observeBooks(): Flow<List<StoredBook>>
    suspend fun books(): List<StoredBook>

    /** Atomically replace a book's studies: readers never see a half-imported book. */
    suspend fun replaceBook(book: StoredBook, studies: List<Pair<VerseKey, StudyRow>>)
    suspend fun removeBook(bookId: Int)
}

/** A study ready to store: its own contentVersion and its JSON. */
data class StudyRow(val contentVersion: String, val json: String)

/** "JHN.3.16" -> chapter 3, verse 16 (the book comes from the file). */
internal fun Study.chapterAndVerse(): Pair<Int, Int> {
    val parts = id.split(".")
    require(parts.size == 3) { "Bad study id $id" }
    return parts[1].toInt() to parts[2].toInt()
}
