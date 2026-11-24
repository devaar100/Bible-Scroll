package aarnav100.developer.biblescroll.core.content.db

import aarnav100.developer.biblescroll.core.content.BookSource
import aarnav100.developer.biblescroll.core.content.ContentStore
import aarnav100.developer.biblescroll.core.content.StoredBook
import aarnav100.developer.biblescroll.core.content.StudyRow
import aarnav100.developer.biblescroll.core.content.VerseKey
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Room-backed [ContentStore]. Studies are stored compressed ([StudyCompression]); callers only see JSON. */
class RoomContentStore @Inject constructor(private val dao: ContentDao) : ContentStore {

    /**
     * Inflates on the collector's dispatcher (StudyRepository applies flowOn(decode) downstream). A row that
     * won't inflate (damaged, or another dictionary) removes its whole book, so the book reads as not on the
     * device: StudyRepository then downloads it again, and BundledContent re-imports a bundled one at next start.
     */
    override fun observeStudyJson(key: VerseKey): Flow<String?> =
        dao.observeStudyData(key.bookId, key.chapter, key.verse)
            .map { data ->
                data?.let {
                    StudyCompression.decompress(it) ?: run {
                        Log.w(TAG, "Unreadable study row $key: removing book ${key.bookId} so it is fetched again")
                        dao.removeBook(key.bookId)
                        null
                    }
                }
            }
            .distinctUntilChanged()

    override fun observeBook(bookId: Int): Flow<StoredBook?> =
        dao.observeBook(bookId).map { it?.toModel() }.distinctUntilChanged()

    override fun observeBooks(): Flow<List<StoredBook>> = dao.observeBooks().map { list -> list.map { it.toModel() } }

    override suspend fun books(): List<StoredBook> = dao.books().map { it.toModel() }

    /** Compresses on the caller's dispatcher (importers run on io). [StoredBook.storedBytes] becomes the stored size. */
    override suspend fun replaceBook(book: StoredBook, studies: List<Pair<VerseKey, StudyRow>>) {
        val rows = studies.map { (key, row) ->
            StudyEntity(key.bookId, key.chapter, key.verse, row.contentVersion, StudyCompression.compress(row.json))
        }
        dao.replaceBook(
            BookEntity(
                bookId = book.bookId, code = book.code, contentVersion = book.contentVersion, sha256 = book.sha256,
                source = book.source.name, verseCount = book.verseCount, reviewedCount = book.reviewedCount,
                storedBytes = rows.sumOf { it.data.size.toLong() }, importedAt = System.currentTimeMillis(),
            ),
            rows,
        )
    }

    override suspend fun removeBook(bookId: Int) = dao.removeBook(bookId)

    private fun BookEntity.toModel() = StoredBook(
        bookId, code, contentVersion, sha256,
        BookSource.entries.firstOrNull { it.name == source } ?: BookSource.DOWNLOADED,
        verseCount, reviewedCount, storedBytes,
    )

    private companion object {
        const val TAG = "ContentStore"
    }
}
