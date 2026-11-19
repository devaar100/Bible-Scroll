package aarnav100.developer.biblescroll.data.streak

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StreakDao {
    @Query("SELECT * FROM streak_days ORDER BY date")
    fun observeAll(): Flow<List<StreakDayEntity>>

    @Query("SELECT * FROM streak_days WHERE date = :date")
    suspend fun get(date: String): StreakDayEntity?

    @Upsert
    suspend fun upsert(day: StreakDayEntity)

    /** Read-modify-write in one transaction, so two quick events can't lose a count. */
    @Transaction
    suspend fun update(date: String, change: (StreakDayEntity?) -> StreakDayEntity?) {
        val existing = get(date)
        val changed = change(existing)
        if (changed != null && changed != existing) upsert(changed)
    }
}
