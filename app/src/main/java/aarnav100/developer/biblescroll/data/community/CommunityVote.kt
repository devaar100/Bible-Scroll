package aarnav100.developer.biblescroll.data.community

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * This phone's Community vote for a month (user.db v10). The month (UTC, "2026-12") is the key, so a phone can
 * hold one vote per month, and it's final: the server only ever adds 1 (backend/firestore.rules). Nothing here
 * goes to the server except the ministry id and month of an unsent vote. updated_at for the backup merge.
 */
@Entity(tableName = "community_votes")
data class CommunityVoteEntity(
    @PrimaryKey val month: String,
    @ColumnInfo(name = "ministry_id") val ministryId: String,
    @ColumnInfo(name = "voted_at") val votedAt: Long,
    /** The server accepted the +1. False while waiting to send (offline) or retrying. */
    val sent: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Dao
interface CommunityVoteDao {
    @Query("SELECT * FROM community_votes")
    fun observeAll(): Flow<List<CommunityVoteEntity>>

    @Query("SELECT * FROM community_votes WHERE month = :month")
    suspend fun get(month: String): CommunityVoteEntity?

    /** Returns -1 if this month already has a vote (the first one stands). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(vote: CommunityVoteEntity): Long

    @Query("UPDATE community_votes SET sent = 1, updated_at = :now WHERE month = :month")
    suspend fun markSent(month: String, now: Long)

    /** Only for a vote the server refused, so it was never counted. */
    @Query("DELETE FROM community_votes WHERE month = :month AND sent = 0")
    suspend fun deleteUnsent(month: String)

    @Query("SELECT * FROM community_votes WHERE sent = 0")
    suspend fun pending(): List<CommunityVoteEntity>
}
