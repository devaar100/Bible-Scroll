package aarnav100.developer.biblescroll.testing

import aarnav100.developer.biblescroll.data.backup.BackupStore
import aarnav100.developer.biblescroll.data.backup.BackupTables
import aarnav100.developer.biblescroll.data.backup.ChapterReadRow
import aarnav100.developer.biblescroll.data.backup.CustomPlanRow
import aarnav100.developer.biblescroll.data.backup.DevotionalProgressRow
import aarnav100.developer.biblescroll.data.backup.NoteRow
import aarnav100.developer.biblescroll.data.backup.PlanDayProgressRow
import aarnav100.developer.biblescroll.data.backup.PlanEnrollmentRow
import aarnav100.developer.biblescroll.data.backup.ReadingPositionRow
import aarnav100.developer.biblescroll.data.backup.StreakDayRow
import aarnav100.developer.biblescroll.data.backup.VerseMarkRow
import java.io.IOException

/**
 * In-memory user database for backup tests, with the same transaction promise as RoomBackupStore:
 * the merge's rows are applied all together, or (if anything throws) not at all.
 */
class FakeBackupStore(var tables: BackupTables = BackupTables()) : BackupStore {
    /** Simulates the disk failing halfway through the write. */
    var failWrites = false
    var transactions = 0

    override suspend fun readAll() = tables

    override suspend fun mergeInTransaction(merge: (existing: BackupTables) -> BackupTables) {
        transactions++
        val write = merge(tables)
        if (failWrites) throw IOException("disk full")
        tables = upsert(tables, write)
    }
}

/** Upserts [write] into [base] by primary key, like Room's @Upsert. */
fun upsert(base: BackupTables, write: BackupTables) = BackupTables(
    verseMarks = merge(base.verseMarks, write.verseMarks) { Triple(it.bookId, it.chapter, it.verse) },
    readingPositions = merge(base.readingPositions, write.readingPositions) { it.bookId },
    chapterReads = merge(base.chapterReads, write.chapterReads) { it.bookId to it.chapter },
    notes = merge(base.notes, write.notes) { Triple(it.bookId, it.chapter, it.verse) },
    streakDays = merge(base.streakDays, write.streakDays) { it.date },
    planEnrollments = merge(base.planEnrollments, write.planEnrollments) { it.id },
    planDayProgress = merge(base.planDayProgress, write.planDayProgress) { it.enrollmentId to it.dayNumber },
    customPlans = merge(base.customPlans, write.customPlans) { it.id },
    devotionalProgress = merge(base.devotionalProgress, write.devotionalProgress) { it.readFor },
)

private fun <T, K> merge(base: List<T>, write: List<T>, key: (T) -> K): List<T> =
    (base.associateBy(key) + write.associateBy(key)).values.toList()

// ---- Sample rows ----

fun like(book: Int, chapter: Int, verse: Int, at: Long, liked: Boolean = true, bookmarked: Boolean = false) =
    VerseMarkRow(book, chapter, verse, liked = liked, updatedAt = at, bookmarked = bookmarked, createdAt = at,
        likedAt = if (liked) at else null, bookmarkedAt = if (bookmarked) at else null)

fun note(book: Int, chapter: Int, verse: Int, text: String, at: Long) = NoteRow(book, chapter, verse, text, at, at)

fun enrollment(id: String, at: Long, days: Int = 3) = PlanEnrollmentRow(
    id = id, planId = "gospel-$id", planTitle = "Plan $id", planContentVersion = "abc123abc123",
    planJson = planJson("gospel-$id", days = days), totalDays = days, minutesPerDay = 5, startDate = "2026-11-01",
    reminderMinuteOfDay = 480, completedAt = null, createdAt = at, updatedAt = at,
)

fun customPlan(id: String, at: Long, deleted: Boolean = false) = CustomPlanRow(
    id = id, title = "My plan", bookIds = "40,41", paceKind = "days", paceValue = 3, totalDays = 3, minutesPerDay = 10,
    planJson = planJson(id, days = 3), createdAt = at, updatedAt = at, deleted = deleted,
)

/** One row in every table, so a round trip touches them all. */
fun everyTable(at: Long = 1_000) = BackupTables(
    verseMarks = listOf(like(43, 3, 16, at, bookmarked = true).copy(studied = true, studySaved = true, studiedAt = at, studySavedAt = at)),
    readingPositions = listOf(ReadingPositionRow(43, 3, 16, "WEB", at)),
    chapterReads = listOf(ChapterReadRow(1, 1, read = true, updatedAt = at)),
    notes = listOf(note(43, 3, 16, "God's love, line 1\nline 2 with \"quotes\" and emoji 🙏", at)),
    streakDays = listOf(StreakDayRow("2026-11-17", 12, 1, 1, at, -300, at)),
    planEnrollments = listOf(enrollment("e1", at)),
    planDayProgress = listOf(PlanDayProgressRow("e1", 1, completed = true, completedAt = at, updatedAt = at)),
    customPlans = listOf(customPlan("custom-1", at)),
    devotionalProgress = listOf(DevotionalProgressRow("2026-11-17", "devotional-1117", "v1", at, at)),
)
