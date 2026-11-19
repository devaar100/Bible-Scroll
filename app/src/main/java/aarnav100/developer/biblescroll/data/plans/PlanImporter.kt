package aarnav100.developer.biblescroll.data.plans

import aarnav100.developer.biblescroll.data.settings.ContentLanguage
import aarnav100.developer.biblescroll.core.content.AssetSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Copies assets/plans/ into the catalogue on first launch, after an app update, and after a language change.
 * "Update" = the app's versionCode, the bundled index.json or the app language changed; otherwise
 * [importIfNeeded] is a quick no-op. Titles, hooks and category names are stored in the app's language (falling
 * back to English); each plan's full JSON, translations included, is stored as is.
 *
 * Only plans whose status is reviewed or auto_checked are imported. A plan file that's missing or can't
 * be parsed is skipped and reported, never half-imported.
 */
class PlanImporter(
    private val assets: AssetSource,
    private val dao: PlanCatalogDao,
    private val json: Json,
    private val appVersionCode: Long,
    private val io: CoroutineDispatcher,
    /** The app's language: titles, hooks and category names are stored in it (and re-imported when it changes). */
    private val language: () -> String = { ContentLanguage.code.value },
) {
    data class Result(val imported: Int, val hidden: List<String>, val broken: List<String>, val skipped: Boolean)

    private val lock = Mutex()

    suspend fun importIfNeeded(): Result = lock.withLock {
        withContext(io) {
            val indexBytes = assets.open("$DIR/index.json")?.use { it.readBytes() }
                ?: return@withContext Result(0, emptyList(), emptyList(), skipped = true)
            val lang = language()
            val fingerprint = "$appVersionCode:${sha256(indexBytes)}:$lang"
            if (dao.meta(FINGERPRINT_KEY) == fingerprint) {
                return@withContext Result(0, emptyList(), emptyList(), skipped = true)
            }
            val index = json.decodeFromString<PlanIndexFile>(indexBytes.decodeToString())
            val wanted = (index.startHere + index.categories.flatMap { it.plans }).distinct()
            val plans = mutableListOf<PlanEntity>()
            val hidden = mutableListOf<String>()
            val broken = mutableListOf<String>()
            for (id in wanted) {
                val text = assets.open("$DIR/$id.json")?.use { it.readBytes().decodeToString() }
                val plan = try {
                    text?.let { json.decodeFromString<PlanFile>(it) }
                } catch (e: SerializationException) {
                    null
                } catch (e: IllegalArgumentException) {
                    null
                }
                when {
                    plan == null || plan.id != id || plan.days.isEmpty() -> broken += id
                    plan.status !in PUBLISHABLE_PLAN_STATUSES -> hidden += id
                    else -> plans += plan.localized(lang).let { shown -> PlanEntity(
                        id = plan.id, title = shown.title, shortDescription = shown.shortDescription,
                        category = plan.category, status = plan.status, days = plan.days.size,
                        minutesPerDay = plan.estimatedMinutesPerDay, difficulty = plan.difficulty,
                        contentVersion = plan.contentVersion, json = text!!,
                    ) }
                }
            }
            val ids = plans.map { it.id }.toSet()
            val categories = buildList {
                if (index.startHere.any { it in ids }) add(PlanCategoryEntity(START_HERE, "", 0))
                index.categories.forEachIndexed { i, c -> if (c.plans.any { it in ids }) add(PlanCategoryEntity(c.id, c.title(lang), i + 1)) }
            }
            val items = buildList {
                index.startHere.filter { it in ids }.forEachIndexed { i, id -> add(PlanCategoryItemEntity(START_HERE, id, i)) }
                index.categories.forEach { c ->
                    c.plans.filter { it in ids }.forEachIndexed { i, id -> add(PlanCategoryItemEntity(c.id, id, i)) }
                }
            }
            dao.replaceAll(plans, categories, items, fingerprint)
            Result(plans.size, hidden, broken, skipped = false)
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        const val DIR = "plans"
        const val FINGERPRINT_KEY = "imported"
        /** The featured row; its title comes from strings.xml. */
        const val START_HERE = "start-here"
    }
}
