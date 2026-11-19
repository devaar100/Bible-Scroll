package aarnav100.developer.biblescroll.data.backup

import aarnav100.developer.biblescroll.data.devotionals.DevotionalProgressEntity
import aarnav100.developer.biblescroll.data.plans.CustomPlanEntity
import aarnav100.developer.biblescroll.data.plans.PlanDayProgressEntity
import aarnav100.developer.biblescroll.data.plans.PlanEnrollmentEntity
import aarnav100.developer.biblescroll.data.streak.StreakDayEntity
import aarnav100.developer.biblescroll.data.user.ChapterReadEntity
import aarnav100.developer.biblescroll.data.user.NoteEntity
import aarnav100.developer.biblescroll.data.user.ReadingPositionEntity
import aarnav100.developer.biblescroll.data.user.VerseMarkEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/*
 * The export file (S9 Data & backup). One JSON object:
 *   { format, schemaVersion, appVersion, exportedAt, tables: { <table>: [rows] }, settings: { <key>: value } }
 * Table and field names are the user.db table and column names, so the file reads like the database.
 * schemaVersion is UserDatabase.VERSION at export time. ExportCoversSchemaTest checks every table and
 * column of the latest exported Room schema is here: a new table or column must be added below.
 *
 * Columns that didn't exist in user.db v1 have defaults, so a newer app can read an older file.
 */

const val BACKUP_FORMAT = "biblescroll-backup"

@Serializable
data class BackupFile(
    val format: String,
    val schemaVersion: Int,
    val appVersion: String,
    /** ISO-8601 instant, e.g. 2026-11-18T14:05:00Z. */
    val exportedAt: String,
    val tables: BackupTables,
    /** Exportable settings only (see [BackupSettings]). */
    val settings: Map<String, JsonPrimitive> = emptyMap(),
)

@Serializable
data class BackupTables(
    @SerialName("verse_marks") val verseMarks: List<VerseMarkRow> = emptyList(),
    @SerialName("reading_positions") val readingPositions: List<ReadingPositionRow> = emptyList(),
    @SerialName("chapter_reads") val chapterReads: List<ChapterReadRow> = emptyList(),
    @SerialName("notes") val notes: List<NoteRow> = emptyList(),
    @SerialName("streak_days") val streakDays: List<StreakDayRow> = emptyList(),
    @SerialName("plan_enrollments") val planEnrollments: List<PlanEnrollmentRow> = emptyList(),
    @SerialName("plan_day_progress") val planDayProgress: List<PlanDayProgressRow> = emptyList(),
    @SerialName("custom_plans") val customPlans: List<CustomPlanRow> = emptyList(),
    @SerialName("devotional_progress") val devotionalProgress: List<DevotionalProgressRow> = emptyList(),
) {
    val rowCount: Int
        get() = verseMarks.size + readingPositions.size + chapterReads.size + notes.size + streakDays.size +
            planEnrollments.size + planDayProgress.size + customPlans.size + devotionalProgress.size
}

/** Likes, bookmarks, studied verses and saved studies. */
@Serializable
data class VerseMarkRow(
    @SerialName("book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val liked: Boolean,
    @SerialName("updated_at") val updatedAt: Long,
    val bookmarked: Boolean = false,
    @SerialName("created_at") val createdAt: Long = updatedAt,
    val studied: Boolean = false,
    @SerialName("study_saved") val studySaved: Boolean = false,
    @SerialName("liked_at") val likedAt: Long? = null,
    @SerialName("bookmarked_at") val bookmarkedAt: Long? = null,
    @SerialName("studied_at") val studiedAt: Long? = null,
    @SerialName("study_saved_at") val studySavedAt: Long? = null,
)

@Serializable
data class ReadingPositionRow(
    @SerialName("book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    @SerialName("translation_id") val translationId: String,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class ChapterReadRow(
    @SerialName("book_id") val bookId: Int,
    val chapter: Int,
    val read: Boolean,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class NoteRow(
    @SerialName("book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val text: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class StreakDayRow(
    val date: String,
    @SerialName("verses_viewed") val versesViewed: Int,
    @SerialName("plan_days_completed") val planDaysCompleted: Int = 0,
    @SerialName("devotionals_finished") val devotionalsFinished: Int = 0,
    @SerialName("counted_at") val countedAt: Long? = null,
    @SerialName("utc_offset_minutes") val utcOffsetMinutes: Int? = null,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class PlanEnrollmentRow(
    val id: String,
    @SerialName("plan_id") val planId: String,
    @SerialName("plan_title") val planTitle: String,
    @SerialName("plan_content_version") val planContentVersion: String,
    @SerialName("plan_json") val planJson: String,
    @SerialName("total_days") val totalDays: Int,
    @SerialName("minutes_per_day") val minutesPerDay: Int,
    @SerialName("start_date") val startDate: String,
    @SerialName("reminder_minute_of_day") val reminderMinuteOfDay: Int? = null,
    @SerialName("completed_at") val completedAt: Long? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class PlanDayProgressRow(
    @SerialName("enrollment_id") val enrollmentId: String,
    @SerialName("day_number") val dayNumber: Int,
    val completed: Boolean,
    @SerialName("completed_at") val completedAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class CustomPlanRow(
    val id: String,
    val title: String,
    @SerialName("book_ids") val bookIds: String,
    @SerialName("pace_kind") val paceKind: String,
    @SerialName("pace_value") val paceValue: Int,
    @SerialName("total_days") val totalDays: Int,
    @SerialName("minutes_per_day") val minutesPerDay: Int,
    @SerialName("plan_json") val planJson: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
)

@Serializable
data class DevotionalProgressRow(
    @SerialName("read_for") val readFor: String,
    @SerialName("devotional_id") val devotionalId: String,
    @SerialName("content_version") val contentVersion: String,
    @SerialName("finished_at") val finishedAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
)

/** Settings in the shared DataStore and whether they travel in an export. */
object BackupSettings {
    enum class Type { BOOLEAN, INT, STRING }

    /** User choices that belong to the person, not the phone. */
    val EXPORTED: Map<String, Type> = mapOf(
        "theme_mode" to Type.STRING,
        "reading_background" to Type.STRING,
        "accent" to Type.STRING,
        "dynamic_color" to Type.BOOLEAN,
        "red_letters" to Type.BOOLEAN,
        "scripture_size" to Type.STRING,
        "hide_ai_studies" to Type.BOOLEAN,
        "streak_grace_day" to Type.BOOLEAN,
        "show_devotionals_when_ai_hidden" to Type.BOOLEAN,
        "translation_id" to Type.STRING,
        "reminder_enabled" to Type.BOOLEAN,
        "reminder_minute_of_day" to Type.INT,
        "widget_book" to Type.INT,
        "widget_chapter" to Type.INT,
        "widget_verse" to Type.INT,
        "widget_translation" to Type.STRING,
    )

    /** Keys that only make sense on this phone, or are one-off UI state. BackupSettingsKeysTest checks every key is in one list. */
    val NOT_EXPORTED: Set<String> = setOf(
        "onboarding_complete", // import happens after onboarding on the new phone anyway
        "plan_reminder_alarms", // alarm ids scheduled on this phone
        "notes_export_tip_dismissed",
        "restore_notice",
    )

    /** Keys imported together or not at all, so a widget verse is never half from each phone. */
    val GROUPS: List<Set<String>> = listOf(setOf("widget_book", "widget_chapter", "widget_verse", "widget_translation"))
}

/** Strict on input (unknown fields mean a malformed file); writes every column, nulls included. */
val BackupJson = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
    explicitNulls = true
    prettyPrint = true
    prettyPrintIndent = "  "
}

// ---- Entity <-> row ----

fun VerseMarkEntity.toRow() = VerseMarkRow(
    bookId, chapter, verse, liked, updatedAt, bookmarked, createdAt, studied, studySaved,
    likedAt, bookmarkedAt, studiedAt, studySavedAt,
)

fun VerseMarkRow.toEntity() = VerseMarkEntity(
    bookId, chapter, verse, liked, updatedAt, bookmarked, createdAt, studied, studySaved,
    likedAt, bookmarkedAt, studiedAt, studySavedAt,
)

fun ReadingPositionEntity.toRow() = ReadingPositionRow(bookId, chapter, verse, translationId, updatedAt)
fun ReadingPositionRow.toEntity() = ReadingPositionEntity(bookId, chapter, verse, translationId, updatedAt)

fun ChapterReadEntity.toRow() = ChapterReadRow(bookId, chapter, read, updatedAt)
fun ChapterReadRow.toEntity() = ChapterReadEntity(bookId, chapter, read, updatedAt)

fun NoteEntity.toRow() = NoteRow(bookId, chapter, verse, text, createdAt, updatedAt)
fun NoteRow.toEntity() = NoteEntity(bookId, chapter, verse, text, createdAt, updatedAt)

fun StreakDayEntity.toRow() =
    StreakDayRow(date, versesViewed, planDaysCompleted, devotionalsFinished, countedAt, utcOffsetMinutes, updatedAt)
fun StreakDayRow.toEntity() =
    StreakDayEntity(date, versesViewed, planDaysCompleted, devotionalsFinished, countedAt, utcOffsetMinutes, updatedAt)

fun PlanEnrollmentEntity.toRow() = PlanEnrollmentRow(
    id, planId, planTitle, planContentVersion, planJson, totalDays, minutesPerDay, startDate,
    reminderMinuteOfDay, completedAt, createdAt, updatedAt,
)
fun PlanEnrollmentRow.toEntity() = PlanEnrollmentEntity(
    id, planId, planTitle, planContentVersion, planJson, totalDays, minutesPerDay, startDate,
    reminderMinuteOfDay, completedAt, createdAt, updatedAt,
)

fun PlanDayProgressEntity.toRow() = PlanDayProgressRow(enrollmentId, dayNumber, completed, completedAt, updatedAt)
fun PlanDayProgressRow.toEntity() = PlanDayProgressEntity(enrollmentId, dayNumber, completed, completedAt, updatedAt)

fun CustomPlanEntity.toRow() = CustomPlanRow(
    id, title, bookIds, paceKind, paceValue, totalDays, minutesPerDay, planJson, createdAt, updatedAt, deleted,
)
fun CustomPlanRow.toEntity() = CustomPlanEntity(
    id, title, bookIds, paceKind, paceValue, totalDays, minutesPerDay, planJson, createdAt, updatedAt, deleted,
)

fun DevotionalProgressEntity.toRow() = DevotionalProgressRow(readFor, devotionalId, contentVersion, finishedAt, updatedAt)
fun DevotionalProgressRow.toEntity() = DevotionalProgressEntity(readFor, devotionalId, contentVersion, finishedAt, updatedAt)
