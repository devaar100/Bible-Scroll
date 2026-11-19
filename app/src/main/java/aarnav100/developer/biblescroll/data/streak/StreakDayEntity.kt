package aarnav100.developer.biblescroll.data.streak

import aarnav100.developer.biblescroll.domain.streak.StreakDay
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** One local calendar day of reading activity (H2). Added in user.db v6. */
@Entity(tableName = "streak_days")
data class StreakDayEntity(
    /** ISO local date (yyyy-MM-dd) on the device when the activity happened. */
    @PrimaryKey
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "verses_viewed") val versesViewed: Int,
    @ColumnInfo(name = "plan_days_completed") val planDaysCompleted: Int,
    @ColumnInfo(name = "devotionals_finished") val devotionalsFinished: Int,
    /** When the day first counted (epoch millis), or null if it hasn't. */
    @ColumnInfo(name = "counted_at") val countedAt: Long?,
    @ColumnInfo(name = "utc_offset_minutes") val utcOffsetMinutes: Int?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

fun StreakDayEntity.toDomain() = StreakDay(
    date = LocalDate.parse(date),
    versesViewed = versesViewed,
    planDaysCompleted = planDaysCompleted,
    devotionalsFinished = devotionalsFinished,
    countedAt = countedAt,
    utcOffsetMinutes = utcOffsetMinutes,
    updatedAt = updatedAt,
)

fun StreakDay.toEntity() = StreakDayEntity(
    date = date.toString(),
    versesViewed = versesViewed,
    planDaysCompleted = planDaysCompleted,
    devotionalsFinished = devotionalsFinished,
    countedAt = countedAt,
    utcOffsetMinutes = utcOffsetMinutes,
    updatedAt = updatedAt,
)
