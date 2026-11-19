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
public class DevotionalDao_Impl(
  __db: RoomDatabase,
) : DevotionalDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfDevotionalEntity: EntityInsertAdapter<DevotionalEntity>

  private val __insertAdapterOfDevotionalMonthEntity: EntityInsertAdapter<DevotionalMonthEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfDevotionalEntity = object : EntityInsertAdapter<DevotionalEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `devotionals` (`date`,`month`,`content_version`,`json`) VALUES (?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: DevotionalEntity) {
        statement.bindText(1, entity.date)
        statement.bindText(2, entity.month)
        statement.bindText(3, entity.contentVersion)
        statement.bindText(4, entity.json)
      }
    }
    this.__insertAdapterOfDevotionalMonthEntity = object : EntityInsertAdapter<DevotionalMonthEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `devotional_months` (`month`,`content_version`,`sha256`,`source`,`count`,`stored_bytes`,`imported_at`) VALUES (?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: DevotionalMonthEntity) {
        statement.bindText(1, entity.month)
        statement.bindText(2, entity.contentVersion)
        statement.bindText(3, entity.sha256)
        statement.bindText(4, entity.source)
        statement.bindLong(5, entity.count.toLong())
        statement.bindLong(6, entity.storedBytes)
        statement.bindLong(7, entity.importedAt)
      }
    }
  }

  public override suspend fun insertDevotionals(devotionals: List<DevotionalEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfDevotionalEntity.insert(_connection, devotionals)
  }

  public override suspend fun upsertMonth(month: DevotionalMonthEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfDevotionalMonthEntity.insert(_connection, month)
  }

  public override suspend fun replaceMonth(month: DevotionalMonthEntity, devotionals: List<DevotionalEntity>): Unit = performInTransactionSuspending(__db) {
    super@DevotionalDao_Impl.replaceMonth(month, devotionals)
  }

  public override suspend fun removeMonth(month: String): Unit = performInTransactionSuspending(__db) {
    super@DevotionalDao_Impl.removeMonth(month)
  }

  public override fun observeJson(date: String): Flow<String?> {
    val _sql: String = "SELECT json FROM devotionals WHERE date = ?"
    return createFlow(__db, false, arrayOf("devotionals")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, date)
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

  public override fun observeMonthJson(month: String): Flow<List<String>> {
    val _sql: String = "SELECT json FROM devotionals WHERE month = ? ORDER BY date"
    return createFlow(__db, false, arrayOf("devotionals")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, month)
        val _result: MutableList<String> = mutableListOf()
        while (_stmt.step()) {
          val _item: String
          _item = _stmt.getText(0)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeMonth(month: String): Flow<DevotionalMonthEntity?> {
    val _sql: String = "SELECT * FROM devotional_months WHERE month = ?"
    return createFlow(__db, false, arrayOf("devotional_months")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, month)
        val _columnIndexOfMonth: Int = getColumnIndexOrThrow(_stmt, "month")
        val _columnIndexOfContentVersion: Int = getColumnIndexOrThrow(_stmt, "content_version")
        val _columnIndexOfSha256: Int = getColumnIndexOrThrow(_stmt, "sha256")
        val _columnIndexOfSource: Int = getColumnIndexOrThrow(_stmt, "source")
        val _columnIndexOfCount: Int = getColumnIndexOrThrow(_stmt, "count")
        val _columnIndexOfStoredBytes: Int = getColumnIndexOrThrow(_stmt, "stored_bytes")
        val _columnIndexOfImportedAt: Int = getColumnIndexOrThrow(_stmt, "imported_at")
        val _result: DevotionalMonthEntity?
        if (_stmt.step()) {
          val _tmpMonth: String
          _tmpMonth = _stmt.getText(_columnIndexOfMonth)
          val _tmpContentVersion: String
          _tmpContentVersion = _stmt.getText(_columnIndexOfContentVersion)
          val _tmpSha256: String
          _tmpSha256 = _stmt.getText(_columnIndexOfSha256)
          val _tmpSource: String
          _tmpSource = _stmt.getText(_columnIndexOfSource)
          val _tmpCount: Int
          _tmpCount = _stmt.getLong(_columnIndexOfCount).toInt()
          val _tmpStoredBytes: Long
          _tmpStoredBytes = _stmt.getLong(_columnIndexOfStoredBytes)
          val _tmpImportedAt: Long
          _tmpImportedAt = _stmt.getLong(_columnIndexOfImportedAt)
          _result = DevotionalMonthEntity(_tmpMonth,_tmpContentVersion,_tmpSha256,_tmpSource,_tmpCount,_tmpStoredBytes,_tmpImportedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun months(): List<DevotionalMonthEntity> {
    val _sql: String = "SELECT * FROM devotional_months ORDER BY month"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfMonth: Int = getColumnIndexOrThrow(_stmt, "month")
        val _columnIndexOfContentVersion: Int = getColumnIndexOrThrow(_stmt, "content_version")
        val _columnIndexOfSha256: Int = getColumnIndexOrThrow(_stmt, "sha256")
        val _columnIndexOfSource: Int = getColumnIndexOrThrow(_stmt, "source")
        val _columnIndexOfCount: Int = getColumnIndexOrThrow(_stmt, "count")
        val _columnIndexOfStoredBytes: Int = getColumnIndexOrThrow(_stmt, "stored_bytes")
        val _columnIndexOfImportedAt: Int = getColumnIndexOrThrow(_stmt, "imported_at")
        val _result: MutableList<DevotionalMonthEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: DevotionalMonthEntity
          val _tmpMonth: String
          _tmpMonth = _stmt.getText(_columnIndexOfMonth)
          val _tmpContentVersion: String
          _tmpContentVersion = _stmt.getText(_columnIndexOfContentVersion)
          val _tmpSha256: String
          _tmpSha256 = _stmt.getText(_columnIndexOfSha256)
          val _tmpSource: String
          _tmpSource = _stmt.getText(_columnIndexOfSource)
          val _tmpCount: Int
          _tmpCount = _stmt.getLong(_columnIndexOfCount).toInt()
          val _tmpStoredBytes: Long
          _tmpStoredBytes = _stmt.getLong(_columnIndexOfStoredBytes)
          val _tmpImportedAt: Long
          _tmpImportedAt = _stmt.getLong(_columnIndexOfImportedAt)
          _item = DevotionalMonthEntity(_tmpMonth,_tmpContentVersion,_tmpSha256,_tmpSource,_tmpCount,_tmpStoredBytes,_tmpImportedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteDevotionals(month: String) {
    val _sql: String = "DELETE FROM devotionals WHERE month = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, month)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteMonth(month: String) {
    val _sql: String = "DELETE FROM devotional_months WHERE month = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, month)
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
