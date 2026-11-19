package aarnav100.developer.biblescroll.data.plans

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import aarnav100.developer.biblescroll.data.settings.ContentLanguage
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.reminder.ReminderScheduler
import aarnav100.developer.biblescroll.data.streak.StreakRepository
import aarnav100.developer.biblescroll.data.user.UserDataRepository
import aarnav100.developer.biblescroll.domain.streak.StreakActivity
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** A plan the reader has started (H8 "My plans", H9). */
@Immutable
data class MyPlan(
    val enrollmentId: String,
    val planId: String,
    val title: String,
    val totalDays: Int,
    val daysDone: Int,
    val minutesPerDay: Int,
    val startDate: LocalDate,
    val reminder: LocalTime?,
    val completed: Boolean,
)

/** H9: the full plan. [custom] = one the reader built (H13/H14), which can be deleted. */
@Immutable
data class PlanDetail(val file: PlanFile, val custom: Boolean = false)

/** H11/H12: one started plan, its own copy of the plan, and the days read. */
@Immutable
data class EnrollmentDetail(val plan: MyPlan, val file: PlanFile, val done: Set<Int>)

/** What marking a day read did. */
data class MarkResult(
    /** False if the day was already read (nothing changed, no streak credit). */
    val newlyRead: Boolean,
    /** The plan's last unread day was this one. */
    val planCompleted: Boolean,
    /** The next unread day, or null when the plan is finished. */
    val nextDay: Int?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlanRepository(
    private val catalog: PlanCatalogDao,
    private val progress: PlanProgressDao,
    private val custom: CustomPlanDao,
    private val importer: PlanImporter,
    private val scheduler: ReminderScheduler,
    private val streaks: StreakRepository,
    private val userData: UserDataRepository,
    private val bible: BibleRepository,
    private val json: Json,
    private val clock: () -> Long,
    private val io: CoroutineDispatcher,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** Categories in index order; the "start-here" row first. Imports the bundle first if needed. */
    fun categories(): Flow<List<PlanCategory>> = ContentLanguage.code
        .flatMapLatest { catalog.observeCategoryItems().onStart { importer.importIfNeeded() } }
        .map { rows ->
            rows.groupBy { it.categoryId }.map { (id, items) ->
                PlanCategory(id, items.first().categoryTitle, items.map {
                    PlanSummary(it.id, it.title, it.shortDescription, it.category, it.days, it.minutesPerDay, it.difficulty)
                })
            }
        }
        .flowOn(io)

    /** Null while missing, deleted, or hidden (bundled plans whose status isn't reviewed/auto_checked). */
    fun plan(id: String): Flow<PlanDetail?> = if (isCustomPlan(id)) {
        custom.observe(id).map { entity -> entity?.let { PlanDetail(json.decodeFromString<PlanFile>(it.planJson), custom = true) } }.flowOn(io)
    } else {
        combine(catalog.observePlan(id).onStart { importer.importIfNeeded() }, ContentLanguage.code) { entity, lang ->
            entity?.let { PlanDetail(json.decodeFromString<PlanFile>(it.json).localized(lang)) }
        }.flowOn(io)
    }

    /** Saves a plan the reader built (H13/H14) and returns its id. Start it with [startPlan]. */
    suspend fun saveCustomPlan(file: PlanFile, bookIds: List<Int>, pace: Pace): String {
        val now = clock()
        custom.upsert(
            CustomPlanEntity(
                id = file.id, title = file.title, bookIds = bookIds.sorted().joinToString(","),
                paceKind = if (pace is Pace.Days) "days" else "minutes", paceValue = pace.value,
                totalDays = file.days.size, minutesPerDay = file.estimatedMinutesPerDay,
                planJson = json.encodeToString(PlanFile.serializer(), file), createdAt = now, updatedAt = now,
            ),
        )
        return file.id
    }

    fun newCustomPlanId(): String = CUSTOM_PLAN_PREFIX + newId().replace("-", "").take(16).lowercase()

    /** Deletes a custom plan (tombstone, so an import merge keeps it deleted) and every started copy of it, with progress and reminders. */
    suspend fun deleteCustomPlan(id: String) {
        require(isCustomPlan(id)) { "Only plans you built can be deleted" }
        val plan = custom.get(id) ?: return
        custom.upsert(plan.copy(deleted = true, updatedAt = clock()))
        progress.deletePlan(id)
        scheduler.syncPlanReminders()
    }

    /** Started plans; bundled plans show their title in the app's language (from the catalogue), custom ones as typed. */
    fun myPlans(): Flow<List<MyPlan>> =
        combine(progress.observeEnrollments(), categories()) { rows, categories ->
            val titles = categories.flatMap { it.plans }.associate { it.id to it.title }
            rows.map { row -> row.toMyPlan().let { p -> titles[p.planId]?.let { t -> p.copy(title = t) } ?: p } }
        }.flowOn(io)

    /**
     * Starts [planId] on [startDate] (H10). Returns the new enrollment id, or the existing one if the plan
     * is already active (one active copy per plan; different plans can run side by side).
     */
    suspend fun startPlan(planId: String, startDate: LocalDate, reminder: LocalTime?): String {
        importer.importIfNeeded()
        val existing = progress.activeEnrollment(planId)
        if (existing != null) return existing
        val entity = catalog.plan(planId) ?: custom.get(planId)?.let {
            PlanEntity(it.id, it.title, "", "custom", "custom", it.totalDays, it.minutesPerDay, "", it.updatedAt.toString(), it.planJson)
        } ?: throw IllegalArgumentException("Plan $planId isn't available")
        val now = clock()
        val id = newId()
        progress.insert(
            PlanEnrollmentEntity(
                id = id, planId = entity.id, planTitle = entity.title, planContentVersion = entity.contentVersion,
                planJson = entity.json, totalDays = entity.days, minutesPerDay = entity.minutesPerDay,
                startDate = startDate.toString(), reminderMinuteOfDay = reminder?.let(PlanSchedule::minuteOfDay),
                completedAt = null, createdAt = now, updatedAt = now,
            ),
        )
        scheduler.syncPlanReminders()
        return id
    }

    /** For tests and backup import: mark a day read or unread without the side effects of [markDayRead]. */
    suspend fun setDayCompleted(enrollmentId: String, dayNumber: Int, completed: Boolean) {
        progress.setDayCompleted(enrollmentId, dayNumber, completed, clock())
        scheduler.syncPlanReminders() // a finished plan stops reminding
    }

    fun enrollment(enrollmentId: String): Flow<EnrollmentDetail?> =
        combine(progress.observeEnrollment(enrollmentId), progress.observeCompletedDays(enrollmentId), ContentLanguage.code) { e, done, lang ->
            e?.let {
                // The started copy carries its translations, so a plan started in English reads in Spanish too.
                val file = json.decodeFromString<PlanFile>(it.planJson).localized(lang)
                EnrollmentDetail(it.toMyPlan(done.size).copy(title = file.title), file, done.toSet())
            }
        }.flowOn(io)

    /**
     * H11 "Mark day as read": records the day, credits the streak (first time only), and marks chapters in
     * ChapterRead once every verse of them is covered by this plan's read days.
     */
    suspend fun markDayRead(enrollmentId: String, dayNumber: Int): MarkResult {
        val enrollment = progress.enrollment(enrollmentId) ?: throw IllegalArgumentException("No plan $enrollmentId")
        val before = progress.completedDays(enrollmentId).toSet()
        val newlyRead = dayNumber !in before
        progress.setDayCompleted(enrollmentId, dayNumber, true, clock())
        val done = before + dayNumber
        if (newlyRead) {
            streaks.record(StreakActivity.PlanDayCompleted)
            markChapters(json.decodeFromString<PlanFile>(enrollment.planJson), done, dayNumber)
        }
        val next = PlanSchedule.nextDay(enrollment.totalDays, done)
        val completed = next == null && before.size < enrollment.totalDays
        scheduler.syncPlanReminders()
        return MarkResult(newlyRead, completed, next)
    }

    /** "Adjust schedule": move the start date so the next unread day is [today]'s. Never moves it earlier. */
    suspend fun adjustSchedule(enrollmentId: String, today: LocalDate) {
        val e = progress.enrollment(enrollmentId) ?: return
        val done = progress.completedDays(enrollmentId).toSet()
        val start = LocalDate.parse(e.startDate)
        val adjusted = PlanProgressMath.adjustedStart(e.totalDays, done, start, today)
        if (adjusted != start) {
            progress.upsert(e.copy(startDate = adjusted.toString(), updatedAt = clock()))
            scheduler.syncPlanReminders()
        }
    }

    private suspend fun markChapters(plan: PlanFile, done: Set<Int>, dayNumber: Int) {
        val readPassages = plan.days.filter { it.dayNumber in done }.flatMap { it.passages }
        val today = plan.days.firstOrNull { it.dayNumber == dayNumber }?.passages.orEmpty()
        val counts = HashMap<Int, Map<Int, Int>>()
        for (book in today.mapNotNull { it.bookId }.distinct()) {
            // Plans use KJV verse numbering, so chapter lengths come from the KJV.
            counts[book] = bible.chapters(VERSIFICATION, book).first().associate { it.number to it.verseCount }
        }
        val full = PlanProgressMath.fullyReadChapters(readPassages, today) { book, chapter -> counts[book]?.get(chapter) }
        full.forEach { (book, chapters) -> userData.setChaptersRead(book, chapters, true) }
    }

    private companion object {
        const val VERSIFICATION = "KJV"
    }
}

fun PlanEnrollmentEntity.toMyPlan(daysDone: Int) = MyPlan(
    enrollmentId = id, planId = planId, title = planTitle, totalDays = totalDays, daysDone = daysDone,
    minutesPerDay = minutesPerDay, startDate = LocalDate.parse(startDate),
    reminder = reminderMinuteOfDay?.let(PlanSchedule::timeOf), completed = completedAt != null,
)

fun EnrollmentRow.toMyPlan() = MyPlan(
    enrollmentId = id, planId = planId, title = planTitle, totalDays = totalDays, daysDone = daysDone,
    minutesPerDay = minutesPerDay, startDate = LocalDate.parse(startDate),
    reminder = reminderMinuteOfDay?.let(PlanSchedule::timeOf), completed = completedAt != null,
)
