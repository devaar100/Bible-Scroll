package aarnav100.developer.biblescroll.core.content.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Suppress

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
internal class ContentDatabase_AutoMigration_1_2_Impl : Migration {
  public constructor() : super(1, 2)

  public override fun migrate(connection: SQLiteConnection) {
    connection.execSQL("CREATE TABLE IF NOT EXISTS `devotionals` (`date` TEXT NOT NULL, `month` TEXT NOT NULL, `content_version` TEXT NOT NULL, `json` TEXT NOT NULL, PRIMARY KEY(`date`))")
    connection.execSQL("CREATE TABLE IF NOT EXISTS `devotional_months` (`month` TEXT NOT NULL, `content_version` TEXT NOT NULL, `sha256` TEXT NOT NULL, `source` TEXT NOT NULL, `count` INTEGER NOT NULL, `stored_bytes` INTEGER NOT NULL, `imported_at` INTEGER NOT NULL, PRIMARY KEY(`month`))")
  }
}
