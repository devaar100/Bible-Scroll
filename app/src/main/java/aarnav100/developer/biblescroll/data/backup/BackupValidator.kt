package aarnav100.developer.biblescroll.data.backup

import aarnav100.developer.biblescroll.data.plans.CUSTOM_PLAN_PREFIX
import aarnav100.developer.biblescroll.data.plans.PlanFile
import aarnav100.developer.biblescroll.data.user.UserDatabase
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Why a file was refused. Nothing on the phone has changed when one of these comes back. */
sealed interface ImportError {
    /** Not JSON, or JSON that isn't one of our export files. */
    data object NotABackup : ImportError

    /** Made by a newer version of the app than this one. */
    data class TooNew(val schemaVersion: Int) : ImportError

    data object TooLarge : ImportError

    /** The picked file couldn't be opened or read. */
    data object Unreadable : ImportError

    /** Our format, but something inside is wrong. [detail] is for logs, not the UI. */
    data class Damaged(val detail: String) : ImportError
}

sealed interface Validated {
    data class Ok(val file: BackupFile) : Validated
    data class Rejected(val error: ImportError) : Validated
}

/**
 * Checks an export file completely before anything is written: format and schemaVersion first, then the
 * structure (strict: unknown or missing fields are rejected), then the values in every row.
 */
object BackupValidator {
    /** Far above a real export (a heavy reader is ~2 MB), low enough to read into memory safely. */
    const val MAX_BYTES = 20L * 1024 * 1024

    private val planJson = Json { ignoreUnknownKeys = true }

    fun validate(text: String): Validated {
        val root = try {
            BackupJson.parseToJsonElement(text) as? JsonObject
        } catch (e: SerializationException) {
            null
        } ?: return Validated.Rejected(ImportError.NotABackup)

        val format = (root["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (format != BACKUP_FORMAT) return Validated.Rejected(ImportError.NotABackup)
        val version = (root["schemaVersion"] as? JsonPrimitive)?.intOrNull
            ?: return Validated.Rejected(ImportError.Damaged("schemaVersion missing or not a number"))
        if (version > UserDatabase.VERSION) return Validated.Rejected(ImportError.TooNew(version))
        if (version < 1) return Validated.Rejected(ImportError.Damaged("schemaVersion $version"))

        val file = try {
            BackupJson.decodeFromJsonElement(BackupFile.serializer(), root)
        } catch (e: SerializationException) {
            return Validated.Rejected(ImportError.Damaged(e.message ?: "structure"))
        } catch (e: IllegalArgumentException) {
            return Validated.Rejected(ImportError.Damaged(e.message ?: "structure"))
        }
        val problem = firstProblem(file)
        return if (problem == null) Validated.Ok(file) else Validated.Rejected(ImportError.Damaged(problem))
    }

    /** The first thing wrong with [file]'s values, or null if it's fine. */
    private fun firstProblem(file: BackupFile): String? {
        if (!isInstant(file.exportedAt)) return "exportedAt"
        val t = file.tables
        t.verseMarks.forEach { if (!isRef(it.bookId, it.chapter, it.verse) || it.updatedAt < 0) return "verse_marks $it" }
        t.readingPositions.forEach {
            if (!isRef(it.bookId, it.chapter, it.verse) || it.translationId.isBlank() || it.updatedAt < 0) return "reading_positions $it"
        }
        t.chapterReads.forEach { if (!isRef(it.bookId, it.chapter, 1) || it.updatedAt < 0) return "chapter_reads $it" }
        t.notes.forEach { if (!isRef(it.bookId, it.chapter, it.verse) || it.updatedAt < 0) return "notes ${it.bookId}.${it.chapter}.${it.verse}" }
        t.streakDays.forEach { if (!isDate(it.date) || it.versesViewed < 0 || it.updatedAt < 0) return "streak_days ${it.date}" }
        t.planEnrollments.forEach {
            if (it.id.isBlank() || it.planId.isBlank() || it.totalDays !in 1..MAX_PLAN_DAYS || !isDate(it.startDate) ||
                it.reminderMinuteOfDay?.let { m -> m !in 0 until 24 * 60 } == true || !isPlan(it.planJson) || it.updatedAt < 0
            ) return "plan_enrollments ${it.id}"
        }
        val enrollmentDays = t.planEnrollments.associate { it.id to it.totalDays }
        t.planDayProgress.forEach {
            val total = enrollmentDays[it.enrollmentId] ?: return "plan_day_progress for unknown plan ${it.enrollmentId}"
            if (it.dayNumber !in 1..total || it.updatedAt < 0) return "plan_day_progress ${it.enrollmentId} day ${it.dayNumber}"
        }
        t.customPlans.forEach {
            if (!it.id.startsWith(CUSTOM_PLAN_PREFIX) || it.paceKind !in PACE_KINDS || it.totalDays !in 1..MAX_PLAN_DAYS ||
                it.bookIds.split(',').any { b -> b.toIntOrNull() !in 1..66 } || !isPlan(it.planJson) || it.updatedAt < 0
            ) return "custom_plans ${it.id}"
        }
        t.devotionalProgress.forEach {
            if (!isDate(it.readFor) || it.devotionalId.isBlank() || it.updatedAt < 0) return "devotional_progress ${it.readFor}"
        }
        t.communityVotes.forEach {
            if (!COMMUNITY_MONTH.matches(it.month) || !COMMUNITY_MINISTRY.matches(it.ministryId) || it.votedAt < 0 || it.updatedAt < 0) {
                return "community_votes ${it.month}"
            }
        }

        duplicate(t.verseMarks) { Triple(it.bookId, it.chapter, it.verse) }?.let { return "duplicate verse_marks $it" }
        duplicate(t.readingPositions) { it.bookId }?.let { return "duplicate reading_positions $it" }
        duplicate(t.chapterReads) { it.bookId to it.chapter }?.let { return "duplicate chapter_reads $it" }
        duplicate(t.notes) { Triple(it.bookId, it.chapter, it.verse) }?.let { return "duplicate notes $it" }
        duplicate(t.streakDays) { it.date }?.let { return "duplicate streak_days $it" }
        duplicate(t.planEnrollments) { it.id }?.let { return "duplicate plan_enrollments $it" }
        duplicate(t.planDayProgress) { it.enrollmentId to it.dayNumber }?.let { return "duplicate plan_day_progress $it" }
        duplicate(t.customPlans) { it.id }?.let { return "duplicate custom_plans $it" }
        duplicate(t.devotionalProgress) { it.readFor }?.let { return "duplicate devotional_progress $it" }
        duplicate(t.communityVotes) { it.month }?.let { return "duplicate community_votes $it" }

        file.settings.forEach { (key, value) ->
            val ok = when (BackupSettings.EXPORTED[key]) {
                null -> true // a setting this version doesn't know: ignored on import
                BackupSettings.Type.BOOLEAN -> !value.isString && value.booleanOrNull != null
                BackupSettings.Type.INT -> !value.isString && value.intOrNull != null
                BackupSettings.Type.STRING -> value.isString
            }
            if (!ok) return "setting $key"
        }
        return null
    }

    private const val MAX_PLAN_DAYS = 3_650
    private val PACE_KINDS = setOf("minutes", "days")

    /** Loose bounds (the longest chapter is Psalm 119 with 176 verses); exact existence is the reader's job. */
    private fun isRef(book: Int, chapter: Int, verse: Int) = book in 1..66 && chapter in 1..150 && verse in 1..200

    private fun isDate(text: String) = try {
        LocalDate.parse(text); true
    } catch (e: DateTimeParseException) {
        false
    }

    private fun isInstant(text: String) = try {
        Instant.parse(text); true
    } catch (e: DateTimeParseException) {
        false
    }

    private fun isPlan(json: String) = try {
        planJson.decodeFromString(PlanFile.serializer(), json).days.isNotEmpty()
    } catch (e: SerializationException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }

    private fun <T, K> duplicate(rows: List<T>, key: (T) -> K): K? {
        val seen = HashSet<K>()
        return rows.map(key).firstOrNull { !seen.add(it) }
    }
}

/** Reads a JSON primitive setting into the type DataStore stores. */
internal fun JsonPrimitive.settingValue(type: BackupSettings.Type): Any? = when (type) {
    BackupSettings.Type.BOOLEAN -> booleanOrNull
    BackupSettings.Type.INT -> intOrNull
    BackupSettings.Type.STRING -> content
}

/** Same rules as the Firestore doc ids the votes name (CommunityCounter.docId). */
private val COMMUNITY_MONTH = Regex("^[0-9]{4}-(0[1-9]|1[0-2])$")
private val COMMUNITY_MINISTRY = Regex("^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$")
