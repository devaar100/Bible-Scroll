package aarnav100.developer.biblescroll.data.plans

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A plan the reader built (H13/H14), user.db v8. updated_at on every change, and deletion is a tombstone
 * (deleted = 1) rather than a missing row, so importing an older backup doesn't bring a deleted plan back.
 */
@Entity(tableName = "custom_plans")
data class CustomPlanEntity(
    /** "custom-" + random id, so it never clashes with a bundled plan id. */
    @PrimaryKey val id: String,
    val title: String,
    /** Book ids in canonical order, comma-separated ("40,41,42,43"). */
    @ColumnInfo(name = "book_ids") val bookIds: String,
    /** How the pace was chosen: "minutes" or "days", and the number picked. */
    @ColumnInfo(name = "pace_kind") val paceKind: String,
    @ColumnInfo(name = "pace_value") val paceValue: Int,
    @ColumnInfo(name = "total_days") val totalDays: Int,
    @ColumnInfo(name = "minutes_per_day") val minutesPerDay: Int,
    /** The generated plan in the bundled plan format (PlanFile JSON). */
    @ColumnInfo(name = "plan_json") val planJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted", defaultValue = "0") val deleted: Boolean = false,
)

@Dao
interface CustomPlanDao {
    @Query("SELECT * FROM custom_plans WHERE id = :id AND deleted = 0")
    fun observe(id: String): Flow<CustomPlanEntity?>

    @Query("SELECT * FROM custom_plans WHERE id = :id AND deleted = 0")
    suspend fun get(id: String): CustomPlanEntity?

    @Upsert
    suspend fun upsert(plan: CustomPlanEntity)
}

const val CUSTOM_PLAN_PREFIX = "custom-"

fun isCustomPlan(planId: String) = planId.startsWith(CUSTOM_PLAN_PREFIX)
