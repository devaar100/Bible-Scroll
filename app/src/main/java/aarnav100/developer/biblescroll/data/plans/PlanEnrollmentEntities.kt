package aarnav100.developer.biblescroll.data.plans

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * The reader's plans (user.db v7). Rows carry updated_at for the backup import merge. Several plans can be
 * active at once. Each enrollment keeps a copy of the plan as it was when started, so an app update that
 * changes or removes a plan never breaks someone's progress.
 */

@Entity(tableName = "plan_enrollments")
data class PlanEnrollmentEntity(
    /** Random UUID, so enrollments from another device's backup never clash on import. */
    @PrimaryKey val id: String,
    @ColumnInfo(name = "plan_id") val planId: String,
    @ColumnInfo(name = "plan_title") val planTitle: String,
    @ColumnInfo(name = "plan_content_version") val planContentVersion: String,
    @ColumnInfo(name = "plan_json") val planJson: String,
    @ColumnInfo(name = "total_days") val totalDays: Int,
    @ColumnInfo(name = "minutes_per_day") val minutesPerDay: Int,
    /** ISO local date the reader chose (today or tomorrow, H10). */
    @ColumnInfo(name = "start_date") val startDate: String,
    /** Plan reminder, minutes after local midnight; null = no reminder. */
    @ColumnInfo(name = "reminder_minute_of_day") val reminderMinuteOfDay: Int?,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "plan_day_progress", primaryKeys = ["enrollment_id", "day_number"])
data class PlanDayProgressEntity(
    @ColumnInfo(name = "enrollment_id") val enrollmentId: String,
    @ColumnInfo(name = "day_number") val dayNumber: Int,
    /** Unmarking keeps the row (completed = false) so an import merge sees the newer "unread". */
    val completed: Boolean,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** An enrollment without the plan copy, plus how many days are done. */
data class EnrollmentRow(
    val id: String,
    @ColumnInfo(name = "plan_id") val planId: String,
    @ColumnInfo(name = "plan_title") val planTitle: String,
    @ColumnInfo(name = "total_days") val totalDays: Int,
    @ColumnInfo(name = "minutes_per_day") val minutesPerDay: Int,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "reminder_minute_of_day") val reminderMinuteOfDay: Int?,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "days_done") val daysDone: Int,
)

@Dao
interface PlanProgressDao {
    @Query(
        "SELECT e.id, e.plan_id, e.plan_title, e.total_days, e.minutes_per_day, e.start_date, e.reminder_minute_of_day, " +
            "e.completed_at, e.created_at, (SELECT COUNT(*) FROM plan_day_progress p WHERE p.enrollment_id = e.id " +
            "AND p.completed = 1) AS days_done FROM plan_enrollments e ORDER BY e.created_at DESC",
    )
    fun observeEnrollments(): Flow<List<EnrollmentRow>>

    @Query(
        "SELECT e.id, e.plan_id, e.plan_title, e.total_days, e.minutes_per_day, e.start_date, e.reminder_minute_of_day, " +
            "e.completed_at, e.created_at, (SELECT COUNT(*) FROM plan_day_progress p WHERE p.enrollment_id = e.id " +
            "AND p.completed = 1) AS days_done FROM plan_enrollments e WHERE e.completed_at IS NULL AND e.reminder_minute_of_day IS NOT NULL",
    )
    suspend fun activeWithReminders(): List<EnrollmentRow>

    @Query("SELECT id FROM plan_enrollments WHERE plan_id = :planId AND completed_at IS NULL LIMIT 1")
    suspend fun activeEnrollment(planId: String): String?

    @Query("SELECT * FROM plan_enrollments WHERE id = :id")
    suspend fun enrollment(id: String): PlanEnrollmentEntity?

    @Query("SELECT * FROM plan_enrollments WHERE id = :id")
    fun observeEnrollment(id: String): Flow<PlanEnrollmentEntity?>

    @Query("SELECT day_number FROM plan_day_progress WHERE enrollment_id = :enrollmentId AND completed = 1")
    fun observeCompletedDays(enrollmentId: String): Flow<List<Int>>

    @Insert
    suspend fun insert(enrollment: PlanEnrollmentEntity)

    @Query("DELETE FROM plan_day_progress WHERE enrollment_id IN (SELECT id FROM plan_enrollments WHERE plan_id = :planId)")
    suspend fun deleteProgressForPlan(planId: String)

    @Query("DELETE FROM plan_enrollments WHERE plan_id = :planId")
    suspend fun deleteEnrollmentsForPlan(planId: String)

    /** Removes every copy of [planId] the reader started, with its progress (deleting a custom plan). */
    @Transaction
    suspend fun deletePlan(planId: String) {
        deleteProgressForPlan(planId)
        deleteEnrollmentsForPlan(planId)
    }

    @Upsert
    suspend fun upsert(enrollment: PlanEnrollmentEntity)

    @Query("SELECT * FROM plan_day_progress WHERE enrollment_id = :enrollmentId AND day_number = :day")
    suspend fun day(enrollmentId: String, day: Int): PlanDayProgressEntity?

    @Upsert
    suspend fun upsertDay(day: PlanDayProgressEntity)

    @Query("SELECT day_number FROM plan_day_progress WHERE enrollment_id = :enrollmentId AND completed = 1")
    suspend fun completedDays(enrollmentId: String): List<Int>

    @Query("SELECT COUNT(*) FROM plan_day_progress WHERE enrollment_id = :enrollmentId AND completed = 1")
    suspend fun daysDone(enrollmentId: String): Int

    /** Marks a day; the enrollment completes when its last day is done (and reopens if one is unmarked). */
    @Transaction
    suspend fun setDayCompleted(enrollmentId: String, dayNumber: Int, completed: Boolean, now: Long) {
        val enrollment = enrollment(enrollmentId) ?: return
        require(dayNumber in 1..enrollment.totalDays) { "Day $dayNumber isn't in this plan" }
        val existing = day(enrollmentId, dayNumber)
        if (existing?.completed != completed) {
            upsertDay(PlanDayProgressEntity(enrollmentId, dayNumber, completed, if (completed) now else null, now))
        }
        val allDone = daysDone(enrollmentId) == enrollment.totalDays
        val completedAt = if (allDone) enrollment.completedAt ?: now else null
        if (completedAt != enrollment.completedAt) upsert(enrollment.copy(completedAt = completedAt, updatedAt = now))
    }
}
