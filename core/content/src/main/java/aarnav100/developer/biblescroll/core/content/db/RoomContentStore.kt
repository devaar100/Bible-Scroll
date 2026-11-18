package aarnav100.developer.biblescroll.core.content.db

import aarnav100.developer.biblescroll.core.content.BookSource
import aarnav100.developer.biblescroll.core.content.ContentStore
import aarnav100.developer.biblescroll.core.content.StoredBook
import aarnav100.developer.biblescroll.core.content.StudyRow
import aarnav100.developer.biblescroll.core.content.VerseKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomContentStore @Inject constructor(private val dao: ContentDao) : ContentStore {

    override fun observeStudyJson(key: VerseKey): Flow<String?> =
        dao.observeStudyJson(key.bookId, key.chapter, key.verse).distinctUntilChanged()

    override fun observeBook(bookId: Int): Flow<StoredBook?> =
        dao.observeBook(bookId).map { it?.toModel() }.distinctUntilChanged()

    override fun observeBooks(): Flow<List<StoredBook>> = dao.observeBooks().map { list -> list.map { it.toModel() } }

    override suspend fun books(): List<StoredBook> = dao.books().map { it.toModel() }

    override suspend fun replaceBook(book: StoredBook, studies: List<Pair<VerseKey, StudyRow>>) {
        dao.replaceBook(
            BookEntity(
                bookId = book.bookId, code = book.code, contentVersion = book.contentVersion, sha256 = book.sha256,
                source = book.source.name, verseCount = book.verseCount, reviewedCount = book.reviewedCount,
                storedBytes = book.storedBytes, importedAt = System.currentTimeMillis(),
            ),
            studies.map { (key, row) -> StudyEntity(key.bookId, key.chapter, key.verse, row.contentVersion, row.json) },
        )
    }

    override suspend fun removeBook(bookId: Int) = dao.removeBook(bookId)

    private fun BookEntity.toModel() = StoredBook(
        bookId, code, contentVersion, sha256,
        BookSource.entries.firstOrNull { it.name == source } ?: BookSource.DOWNLOADED,
        verseCount, reviewedCount, storedBytes,
    )
}
