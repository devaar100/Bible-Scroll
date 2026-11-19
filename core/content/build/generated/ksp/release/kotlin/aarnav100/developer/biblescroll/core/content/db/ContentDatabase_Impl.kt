package aarnav100.developer.biblescroll.core.content.db

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ContentDatabase_Impl : ContentDatabase() {
  private val _contentDao: Lazy<ContentDao> = lazy {
    ContentDao_Impl(this)
  }

  private val _devotionalDao: Lazy<DevotionalDao> = lazy {
    DevotionalDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(2, "0993d1937e7c899daec5f1f75fe5b357", "43f09c84aaa0899fff5c4281050b1599") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `studies` (`book_id` INTEGER NOT NULL, `chapter` INTEGER NOT NULL, `verse` INTEGER NOT NULL, `content_version` TEXT NOT NULL, `json` TEXT NOT NULL, PRIMARY KEY(`book_id`, `chapter`, `verse`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `books` (`book_id` INTEGER NOT NULL, `code` TEXT NOT NULL, `content_version` TEXT NOT NULL, `sha256` TEXT NOT NULL, `source` TEXT NOT NULL, `verse_count` INTEGER NOT NULL, `reviewed_count` INTEGER NOT NULL, `stored_bytes` INTEGER NOT NULL, `imported_at` INTEGER NOT NULL, PRIMARY KEY(`book_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `devotionals` (`date` TEXT NOT NULL, `month` TEXT NOT NULL, `content_version` TEXT NOT NULL, `json` TEXT NOT NULL, PRIMARY KEY(`date`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `devotional_months` (`month` TEXT NOT NULL, `content_version` TEXT NOT NULL, `sha256` TEXT NOT NULL, `source` TEXT NOT NULL, `count` INTEGER NOT NULL, `stored_bytes` INTEGER NOT NULL, `imported_at` INTEGER NOT NULL, PRIMARY KEY(`month`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '0993d1937e7c899daec5f1f75fe5b357')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `studies`")
        connection.execSQL("DROP TABLE IF EXISTS `books`")
        connection.execSQL("DROP TABLE IF EXISTS `devotionals`")
        connection.execSQL("DROP TABLE IF EXISTS `devotional_months`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsStudies: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsStudies.put("book_id", TableInfo.Column("book_id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStudies.put("chapter", TableInfo.Column("chapter", "INTEGER", true, 2, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStudies.put("verse", TableInfo.Column("verse", "INTEGER", true, 3, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStudies.put("content_version", TableInfo.Column("content_version", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsStudies.put("json", TableInfo.Column("json", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysStudies: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesStudies: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoStudies: TableInfo = TableInfo("studies", _columnsStudies, _foreignKeysStudies, _indicesStudies)
        val _existingStudies: TableInfo = read(connection, "studies")
        if (!_infoStudies.equals(_existingStudies)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |studies(aarnav100.developer.biblescroll.core.content.db.StudyEntity).
              | Expected:
              |""".trimMargin() + _infoStudies + """
              |
              | Found:
              |""".trimMargin() + _existingStudies)
        }
        val _columnsBooks: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsBooks.put("book_id", TableInfo.Column("book_id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("code", TableInfo.Column("code", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("content_version", TableInfo.Column("content_version", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("sha256", TableInfo.Column("sha256", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("source", TableInfo.Column("source", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("verse_count", TableInfo.Column("verse_count", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("reviewed_count", TableInfo.Column("reviewed_count", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("stored_bytes", TableInfo.Column("stored_bytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBooks.put("imported_at", TableInfo.Column("imported_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysBooks: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesBooks: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoBooks: TableInfo = TableInfo("books", _columnsBooks, _foreignKeysBooks, _indicesBooks)
        val _existingBooks: TableInfo = read(connection, "books")
        if (!_infoBooks.equals(_existingBooks)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |books(aarnav100.developer.biblescroll.core.content.db.BookEntity).
              | Expected:
              |""".trimMargin() + _infoBooks + """
              |
              | Found:
              |""".trimMargin() + _existingBooks)
        }
        val _columnsDevotionals: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsDevotionals.put("date", TableInfo.Column("date", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDevotionals.put("month", TableInfo.Column("month", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDevotionals.put("content_version", TableInfo.Column("content_version", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDevotionals.put("json", TableInfo.Column("json", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysDevotionals: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesDevotionals: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoDevotionals: TableInfo = TableInfo("devotionals", _columnsDevotionals, _foreignKeysDevotionals, _indicesDevotionals)
        val _existingDevotionals: TableInfo = read(connection, "devotionals")
        if (!_infoDevotionals.equals(_existingDevotionals)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |devotionals(aarnav100.developer.biblescroll.core.content.db.DevotionalEntity).
              | Expected:
              |""".trimMargin() + _infoDevotionals + """
              |
              | Found:
              |""".trimMargin() + _existingDevotionals)
        }
        val _columnsDevotionalMonths: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsDevotionalMonths.put("month", TableInfo.Column("month", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDevotionalMonths.put("content_version", TableInfo.Column("content_version", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDevotionalMonths.put("sha256", TableInfo.Column("sha256", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDevotionalMonths.put("source", TableInfo.Column("source", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDevotionalMonths.put("count", TableInfo.Column("count", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDevotionalMonths.put("stored_bytes", TableInfo.Column("stored_bytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDevotionalMonths.put("imported_at", TableInfo.Column("imported_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysDevotionalMonths: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesDevotionalMonths: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoDevotionalMonths: TableInfo = TableInfo("devotional_months", _columnsDevotionalMonths, _foreignKeysDevotionalMonths, _indicesDevotionalMonths)
        val _existingDevotionalMonths: TableInfo = read(connection, "devotional_months")
        if (!_infoDevotionalMonths.equals(_existingDevotionalMonths)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |devotional_months(aarnav100.developer.biblescroll.core.content.db.DevotionalMonthEntity).
              | Expected:
              |""".trimMargin() + _infoDevotionalMonths + """
              |
              | Found:
              |""".trimMargin() + _existingDevotionalMonths)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "studies", "books", "devotionals", "devotional_months")
  }

  public override fun clearAllTables() {
    super.performClear(false, "studies", "books", "devotionals", "devotional_months")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(ContentDao::class, ContentDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(DevotionalDao::class, DevotionalDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    _autoMigrations.add(ContentDatabase_AutoMigration_1_2_Impl())
    return _autoMigrations
  }

  public override fun contentDao(): ContentDao = _contentDao.value

  public override fun devotionalDao(): DevotionalDao = _devotionalDao.value
}
