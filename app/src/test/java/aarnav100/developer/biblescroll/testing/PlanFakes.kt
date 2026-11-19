package aarnav100.developer.biblescroll.testing

import aarnav100.developer.biblescroll.core.content.AssetSource
import aarnav100.developer.biblescroll.data.plans.CatalogMetaEntity
import aarnav100.developer.biblescroll.data.plans.CustomPlanDao
import aarnav100.developer.biblescroll.data.plans.CustomPlanEntity
import aarnav100.developer.biblescroll.data.plans.CategoryItemRow
import aarnav100.developer.biblescroll.data.plans.EnrollmentRow
import aarnav100.developer.biblescroll.data.plans.PUBLISHABLE_PLAN_STATUSES
import aarnav100.developer.biblescroll.data.plans.PlanCatalogDao
import aarnav100.developer.biblescroll.data.plans.PlanCategoryEntity
import aarnav100.developer.biblescroll.data.plans.PlanCategoryItemEntity
import aarnav100.developer.biblescroll.data.plans.PlanDayProgressEntity
import aarnav100.developer.biblescroll.data.plans.PlanEnrollmentEntity
import aarnav100.developer.biblescroll.data.plans.PlanEntity
import aarnav100.developer.biblescroll.data.plans.PlanProgressDao
import aarnav100.developer.biblescroll.data.reminder.ReminderScheduler
import aarnav100.developer.biblescroll.data.reminder.ReminderSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.InputStream

/** In-memory catalogue. Only the queries are faked; replaceAll is the real interface default. */
class FakePlanCatalogDao : PlanCatalogDao {
    val plans = MutableStateFlow<Map<String, PlanEntity>>(emptyMap())
    val categories = MutableStateFlow<List<PlanCategoryEntity>>(emptyList())
    val items = MutableStateFlow<List<PlanCategoryItemEntity>>(emptyList())
    val meta = mutableMapOf<String, String>()

    override fun observeCategoryItems() = combine(plans, categories, items) { p, c, i ->
        val cats = c.associateBy { it.id }
        i.mapNotNull { item ->
            val plan = p[item.planId]?.takeIf { it.status in PUBLISHABLE_PLAN_STATUSES } ?: return@mapNotNull null
            val cat = cats[item.categoryId] ?: return@mapNotNull null
            item to CategoryItemRow(cat.id, cat.title, cat.position, plan.id, plan.title, plan.shortDescription, plan.category, plan.days, plan.minutesPerDay, plan.difficulty)
        }.sortedWith(compareBy({ it.second.categoryPosition }, { it.first.position })).map { it.second }
    }
    override fun observePlan(id: String) = plans.map { it[id]?.takeIf { p -> p.status in PUBLISHABLE_PLAN_STATUSES } }
    override suspend fun plan(id: String) = plans.value[id]?.takeIf { it.status in PUBLISHABLE_PLAN_STATUSES }
    override suspend fun meta(key: String) = meta[key]
    override suspend fun putMeta(meta: CatalogMetaEntity) { this.meta[meta.key] = meta.value }
    override suspend fun clearPlans() { plans.value = emptyMap() }
    override suspend fun clearCategories() { categories.value = emptyList() }
    override suspend fun clearItems() { items.value = emptyList() }
    override suspend fun insertPlans(plans: List<PlanEntity>) { this.plans.update { it + plans.associateBy { p -> p.id } } }
    override suspend fun insertCategories(categories: List<PlanCategoryEntity>) { this.categories.update { it + categories } }
    override suspend fun insertItems(items: List<PlanCategoryItemEntity>) { this.items.update { it + items } }
}

/** In-memory progress tables; setDayCompleted is the real interface default. */
class FakePlanProgressDao : PlanProgressDao {
    val enrollments = MutableStateFlow<Map<String, PlanEnrollmentEntity>>(emptyMap())
    val days = MutableStateFlow<Map<Pair<String, Int>, PlanDayProgressEntity>>(emptyMap())

    private fun row(e: PlanEnrollmentEntity, d: Map<Pair<String, Int>, PlanDayProgressEntity>) = EnrollmentRow(
        e.id, e.planId, e.planTitle, e.totalDays, e.minutesPerDay, e.startDate, e.reminderMinuteOfDay, e.completedAt, e.createdAt,
        d.values.count { it.enrollmentId == e.id && it.completed },
    )

    override fun observeEnrollments() = combine(enrollments, days) { e, d -> e.values.sortedByDescending { it.createdAt }.map { row(it, d) } }
    override suspend fun activeWithReminders() =
        enrollments.value.values.filter { it.completedAt == null && it.reminderMinuteOfDay != null }.map { row(it, days.value) }
    override suspend fun activeEnrollment(planId: String) = enrollments.value.values.firstOrNull { it.planId == planId && it.completedAt == null }?.id
    override suspend fun enrollment(id: String) = enrollments.value[id]
    override fun observeEnrollment(id: String) = enrollments.map { it[id] }
    override fun observeCompletedDays(enrollmentId: String) =
        days.map { d -> d.values.filter { it.enrollmentId == enrollmentId && it.completed }.map { it.dayNumber } }
    override suspend fun insert(enrollment: PlanEnrollmentEntity) {
        check(enrollment.id !in enrollments.value) { "duplicate id" }
        enrollments.update { it + (enrollment.id to enrollment) }
    }
    override suspend fun upsert(enrollment: PlanEnrollmentEntity) { enrollments.update { it + (enrollment.id to enrollment) } }
    override suspend fun day(enrollmentId: String, day: Int) = days.value[enrollmentId to day]
    override suspend fun upsertDay(day: PlanDayProgressEntity) { days.update { it + ((day.enrollmentId to day.dayNumber) to day) } }
    override suspend fun completedDays(enrollmentId: String) = days.value.values.filter { it.enrollmentId == enrollmentId && it.completed }.map { it.dayNumber }
    override suspend fun daysDone(enrollmentId: String) = completedDays(enrollmentId).size
    override suspend fun deleteProgressForPlan(planId: String) {
        val ids = enrollments.value.values.filter { it.planId == planId }.map { it.id }.toSet()
        days.update { d -> d.filterKeys { it.first !in ids } }
    }
    override suspend fun deleteEnrollmentsForPlan(planId: String) { enrollments.update { e -> e.filterValues { it.planId != planId } } }
}

class FakeCustomPlanDao : CustomPlanDao {
    val rows = MutableStateFlow<Map<String, CustomPlanEntity>>(emptyMap())
    override fun observe(id: String) = rows.map { it[id]?.takeIf { p -> !p.deleted } }
    override suspend fun get(id: String) = rows.value[id]?.takeIf { !it.deleted }
    override suspend fun upsert(plan: CustomPlanEntity) { rows.update { it + (plan.id to plan) } }
}

class RecordingPlanScheduler : ReminderScheduler {
    var planSyncs = 0
    override fun sync(settings: ReminderSettings) = Unit
    override suspend fun syncFromStore() = Unit
    override suspend fun syncPlanReminders() { planSyncs++ }
}

/** Assets from a directory on disk (e.g. the app's real src/main/assets). */
class DirectoryAssets(private val root: File) : AssetSource {
    override fun open(path: String): InputStream? = File(root, path).takeIf { it.isFile }?.inputStream()
}

/** Assets from a map of path -> text. */
class MapAssets(val files: MutableMap<String, String>) : AssetSource {
    override fun open(path: String): InputStream? = files[path]?.byteInputStream()
}

fun planJson(
    id: String, status: String = "auto_checked", days: Int = 3, category: String = "gospels-and-acts", title: String = "Plan $id",
    daysJson: String? = null,
) = """
{"id":"$id","schemaVersion":1,"contentVersion":"abc123abc123","title":"$title","shortDescription":"Short $id",
 "description":"About $id","category":"$category","status":"$status","origin":"structural","credits":"Credits",
 "estimatedMinutesPerDay":5,"difficulty":"easy","reviewed":false,"futureField":{"ignored":true},
 "days":${daysJson ?: "[" + (1..days).joinToString(",") { """{"dayNumber":$it,"passages":[{"book":"JHN","startChapter":$it,"startVerse":1,"endChapter":$it,"endVerse":10}]}""" } + "]"}}
""".trimIndent()

fun planIndex(startHere: List<String>, categories: Map<String, List<String>>) = """
{"formatVersion":1,"startHere":[${startHere.joinToString(",") { "\"$it\"" }}],
 "categories":[${categories.entries.joinToString(",") { (id, plans) -> """{"id":"$id","title":"Title $id","plans":[${plans.joinToString(",") { "\"$it\"" }}]}""" }}],
 "plans":[]}
""".trimIndent()

/** A JSON passage. */
fun passage(book: String, c1: Int, v1: Int, c2: Int = c1, v2: Int = v1) =
    """{"book":"$book","startChapter":$c1,"startVerse":$v1,"endChapter":$c2,"endVerse":$v2}"""

fun day(n: Int, vararg passages: String, intro: String? = null) =
    """{"dayNumber":$n${intro?.let { ",\"intro\":\"$it\"" } ?: ""},"passages":[${passages.joinToString(",")}]}"""

/** FakeBibleRepository with KJV chapter lengths for John (3: 36 verses, 4: 54). */
fun bibleWithChapters(): aarnav100.developer.biblescroll.data.bible.BibleRepository {
    val base = FakeBibleRepository()
    return object : aarnav100.developer.biblescroll.data.bible.BibleRepository by base {
        override fun chapters(translationId: String, bookId: Int) = kotlinx.coroutines.flow.flowOf(
            if (bookId == 43) listOf(
                aarnav100.developer.biblescroll.data.bible.model.Chapter(43, 3, 36, null),
                aarnav100.developer.biblescroll.data.bible.model.Chapter(43, 4, 54, null),
            ) else emptyList(),
        )
    }
}
