package aarnav100.developer.biblescroll.data.devotionals

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A devotional the reader finished (user.db v9). Keyed by the calendar date it was for ("2026-11-18"), so
 * reading 18 November's devotional again next year is a new row. updated_at for the backup import merge.
 */
@Entity(tableName = "devotional_progress")
data class DevotionalProgressEntity(
    @PrimaryKey @ColumnInfo(name = "read_for") val readFor: String,
    @ColumnInfo(name = "devotional_id") val devotionalId: String,
    @ColumnInfo(name = "content_version") val contentVersion: String,
    @ColumnInfo(name = "finished_at") val finishedAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Dao
interface DevotionalProgressDao {
    /** Dates finished in [year] ("2026"). */
    @Query("SELECT read_for FROM devotional_progress WHERE read_for LIKE :year || '-%'")
    fun observeYear(year: String): Flow<List<String>>

    /** Returns -1 if [row]'s date was already finished (the first finish is kept). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: DevotionalProgressEntity): Long
}
