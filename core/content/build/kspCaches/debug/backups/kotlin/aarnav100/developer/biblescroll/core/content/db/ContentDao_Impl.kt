package aarnav100.developer.biblescroll.core.content.db

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performInTransactionSuspending
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ContentDao_Impl(
  __db: RoomDatabase,
) : ContentDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfStudyEntity: EntityInsertAdapter<StudyEntity>

  private val __insertAdapterOfBookEntity: EntityInsertAdapter<BookEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfStudyEntity = object : EntityInsertAdapter<StudyEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `studies` (`book_id`,`chapter`,`verse`,`content_version`,`json`) VALUES (?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: StudyEntity) {
        statement.bindLong(1, entity.bookId.toLong())
        statement.bindLong(2, entity.chapter.toLong())
        statement.bindLong(3, entity.verse.toLong())
        statement.bindText(4, entity.contentVersion)
        statement.bindText(5, entity.json)
      }
    }
    this.__insertAdapterOfBookEntity = object : EntityInsertAdapter<BookEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `books` (`book_id`,`code`,`content_version`,`sha256`,`source`,`verse_count`,`reviewed_count`,`stored_bytes`,`imported_at`) VALUES (?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: BookEntity) {
        statement.bindLong(1, entity.bookId.toLong())
        statement.bindText(2, entity.code)
        statement.bindText(3, entity.contentVersion)
        statement.bindText(4, entity.sha256)
        statement.bindText(5, entity.source)
        statement.bindLong(6, entity.verseCount.toLong())
        statement.bindLong(7, entity.reviewedCount.toLong())
        statement.bindLong(8, entity.storedBytes)
        statement.bindLong(9, entity.importedAt)
      }
    }
  }

  public override suspend fun insertStudies(studies: List<StudyEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfStudyEntity.insert(_connection, studies)
  }

  public override suspend fun upsertBook(book: BookEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfBookEntity.insert(_connection, book)
  }

  public override suspend fun replaceBook(book: BookEntity, studies: List<StudyEntity>): Unit = performInTransactionSuspending(__db) {
    super@ContentDao_Impl.replaceBook(book, studies)
  }

  public override suspend fun removeBook(bookId: Int): Unit = performInTransactionSuspending(__db) {
    super@ContentDao_Impl.removeBook(bookId)
  }

  public override fun observeStudyJson(
    bookId: Int,
    chapter: Int,
    verse: Int,
  ): Flow<String?> {
    val _sql: String = "SELECT json FROM studies WHERE book_id = ? AND chapter = ? AND verse = ?"
    return createFlow(__db, false, arrayOf("studies")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        _argIndex = 2
        _stmt.bindLong(_argIndex, chapter.toLong())
        _argIndex = 3
        _stmt.bindLong(_argIndex, verse.toLong())
        val _result: String?
        if (_stmt.step()) {
          if (_stmt.isNull(0)) {
            _result = null
          } else {
            _result = _stmt.getText(0)
          }
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeBook(bookId: Int): Flow<BookEntity?> {
    val _sql: String = "SELECT * FROM books WHERE book_id = ?"
    return createFlow(__db, false, arrayOf("books")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "book_id")
        val _columnIndexOfCode: Int = getColumnIndexOrThrow(_stmt, "code")
        val _columnIndexOfContentVersion: Int = getColumnIndexOrThrow(_stmt, "content_version")
        val _columnIndexOfSha256: Int = getColumnIndexOrThrow(_stmt, "sha256")
        val _columnIndexOfSource: Int = getColumnIndexOrThrow(_stmt, "source")
        val _columnIndexOfVerseCount: Int = getColumnIndexOrThrow(_stmt, "verse_count")
        val _columnIndexOfReviewedCount: Int = getColumnIndexOrThrow(_stmt, "reviewed_count")
        val _columnIndexOfStoredBytes: Int = getColumnIndexOrThrow(_stmt, "stored_bytes")
        val _columnIndexOfImportedAt: Int = getColumnIndexOrThrow(_stmt, "imported_at")
        val _result: BookEntity?
        if (_stmt.step()) {
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpCode: String
          _tmpCode = _stmt.getText(_columnIndexOfCode)
          val _tmpContentVersion: String
          _tmpContentVersion = _stmt.getText(_columnIndexOfContentVersion)
          val _tmpSha256: String
          _tmpSha256 = _stmt.getText(_columnIndexOfSha256)
          val _tmpSource: String
          _tmpSource = _stmt.getText(_columnIndexOfSource)
          val _tmpVerseCount: Int
          _tmpVerseCount = _stmt.getLong(_columnIndexOfVerseCount).toInt()
          val _tmpReviewedCount: Int
          _tmpReviewedCount = _stmt.getLong(_columnIndexOfReviewedCount).toInt()
          val _tmpStoredBytes: Long
          _tmpStoredBytes = _stmt.getLong(_columnIndexOfStoredBytes)
          val _tmpImportedAt: Long
          _tmpImportedAt = _stmt.getLong(_columnIndexOfImportedAt)
          _result = BookEntity(_tmpBookId,_tmpCode,_tmpContentVersion,_tmpSha256,_tmpSource,_tmpVerseCount,_tmpReviewedCount,_tmpStoredBytes,_tmpImportedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeBooks(): Flow<List<BookEntity>> {
    val _sql: String = "SELECT * FROM books ORDER BY book_id"
    return createFlow(__db, false, arrayOf("books")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "book_id")
        val _columnIndexOfCode: Int = getColumnIndexOrThrow(_stmt, "code")
        val _columnIndexOfContentVersion: Int = getColumnIndexOrThrow(_stmt, "content_version")
        val _columnIndexOfSha256: Int = getColumnIndexOrThrow(_stmt, "sha256")
        val _columnIndexOfSource: Int = getColumnIndexOrThrow(_stmt, "source")
        val _columnIndexOfVerseCount: Int = getColumnIndexOrThrow(_stmt, "verse_count")
        val _columnIndexOfReviewedCount: Int = getColumnIndexOrThrow(_stmt, "reviewed_count")
        val _columnIndexOfStoredBytes: Int = getColumnIndexOrThrow(_stmt, "stored_bytes")
        val _columnIndexOfImportedAt: Int = getColumnIndexOrThrow(_stmt, "imported_at")
        val _result: MutableList<BookEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: BookEntity
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpCode: String
          _tmpCode = _stmt.getText(_columnIndexOfCode)
          val _tmpContentVersion: String
          _tmpContentVersion = _stmt.getText(_columnIndexOfContentVersion)
          val _tmpSha256: String
          _tmpSha256 = _stmt.getText(_columnIndexOfSha256)
          val _tmpSource: String
          _tmpSource = _stmt.getText(_columnIndexOfSource)
          val _tmpVerseCount: Int
          _tmpVerseCount = _stmt.getLong(_columnIndexOfVerseCount).toInt()
          val _tmpReviewedCount: Int
          _tmpReviewedCount = _stmt.getLong(_columnIndexOfReviewedCount).toInt()
          val _tmpStoredBytes: Long
          _tmpStoredBytes = _stmt.getLong(_columnIndexOfStoredBytes)
          val _tmpImportedAt: Long
          _tmpImportedAt = _stmt.getLong(_columnIndexOfImportedAt)
          _item = BookEntity(_tmpBookId,_tmpCode,_tmpContentVersion,_tmpSha256,_tmpSource,_tmpVerseCount,_tmpReviewedCount,_tmpStoredBytes,_tmpImportedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun books(): List<BookEntity> {
    val _sql: String = "SELECT * FROM books ORDER BY book_id"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfBookId: Int = getColumnIndexOrThrow(_stmt, "book_id")
        val _columnIndexOfCode: Int = getColumnIndexOrThrow(_stmt, "code")
        val _columnIndexOfContentVersion: Int = getColumnIndexOrThrow(_stmt, "content_version")
        val _columnIndexOfSha256: Int = getColumnIndexOrThrow(_stmt, "sha256")
        val _columnIndexOfSource: Int = getColumnIndexOrThrow(_stmt, "source")
        val _columnIndexOfVerseCount: Int = getColumnIndexOrThrow(_stmt, "verse_count")
        val _columnIndexOfReviewedCount: Int = getColumnIndexOrThrow(_stmt, "reviewed_count")
        val _columnIndexOfStoredBytes: Int = getColumnIndexOrThrow(_stmt, "stored_bytes")
        val _columnIndexOfImportedAt: Int = getColumnIndexOrThrow(_stmt, "imported_at")
        val _result: MutableList<BookEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: BookEntity
          val _tmpBookId: Int
          _tmpBookId = _stmt.getLong(_columnIndexOfBookId).toInt()
          val _tmpCode: String
          _tmpCode = _stmt.getText(_columnIndexOfCode)
          val _tmpContentVersion: String
          _tmpContentVersion = _stmt.getText(_columnIndexOfContentVersion)
          val _tmpSha256: String
          _tmpSha256 = _stmt.getText(_columnIndexOfSha256)
          val _tmpSource: String
          _tmpSource = _stmt.getText(_columnIndexOfSource)
          val _tmpVerseCount: Int
          _tmpVerseCount = _stmt.getLong(_columnIndexOfVerseCount).toInt()
          val _tmpReviewedCount: Int
          _tmpReviewedCount = _stmt.getLong(_columnIndexOfReviewedCount).toInt()
          val _tmpStoredBytes: Long
          _tmpStoredBytes = _stmt.getLong(_columnIndexOfStoredBytes)
          val _tmpImportedAt: Long
          _tmpImportedAt = _stmt.getLong(_columnIndexOfImportedAt)
          _item = BookEntity(_tmpBookId,_tmpCode,_tmpContentVersion,_tmpSha256,_tmpSource,_tmpVerseCount,_tmpReviewedCount,_tmpStoredBytes,_tmpImportedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteStudies(bookId: Int) {
    val _sql: String = "DELETE FROM studies WHERE book_id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteBook(bookId: Int) {
    val _sql: String = "DELETE FROM books WHERE book_id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, bookId.toLong())
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
