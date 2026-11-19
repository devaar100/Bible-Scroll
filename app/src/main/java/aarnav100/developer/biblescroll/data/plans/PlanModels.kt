package aarnav100.developer.biblescroll.data.plans

import aarnav100.developer.biblescroll.core.content.BookCodes
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/*
 * Reading plans as shipped in assets/plans/ (pipeline `plans bundle`, schema pipeline/schema/plan.json).
 * Unknown JSON keys are ignored, so the pipeline can add fields without breaking older apps.
 */

/** Only these statuses may be shown. Anything else (generated, flagged, a future status) is hidden. */
val PUBLISHABLE_PLAN_STATUSES = setOf("reviewed", "auto_checked")

@Serializable
data class PlanIndexFile(
    val formatVersion: Int,
    val startHere: List<String> = emptyList(),
    val categories: List<PlanCategoryFile>,
)

@Serializable
data class PlanCategoryFile(
    val id: String,
    val title: String,
    val plans: List<String>,
    /** Translated titles by language code (pipeline `translate plans`). */
    val i18n: Map<String, TitleLocaleFile> = emptyMap(),
) {
    fun title(lang: String): String = i18n[lang]?.title ?: title
}

@Serializable
data class TitleLocaleFile(val title: String)

@Serializable
data class PlanFile(
    val id: String,
    val contentVersion: String,
    val title: String,
    val shortDescription: String,
    val description: String,
    val category: String,
    val status: String,
    val origin: String,
    val credits: String = "",
    val estimatedMinutesPerDay: Int,
    val difficulty: String,
    val reviewed: Boolean = false,
    val days: List<PlanDayFile>,
    /** Translations by language code; only checked, up-to-date ones are bundled. English stays in the fields above. */
    val i18n: Map<String, PlanLocaleFile> = emptyMap(),
) {
    /** This plan in [lang], field by field: anything not translated stays English. */
    fun localized(lang: String): PlanFile {
        val loc = i18n[lang] ?: return this
        val days = loc.days.associateBy { it.dayNumber }
        return copy(
            title = loc.title ?: title,
            shortDescription = loc.shortDescription ?: shortDescription,
            description = loc.description ?: description,
            credits = loc.credits ?: credits,
            days = this.days.map { d ->
                days[d.dayNumber]?.let { t -> d.copy(title = t.title ?: d.title, intro = t.intro ?: d.intro) } ?: d
            },
        )
    }
}

@Serializable
data class PlanLocaleFile(
    val title: String? = null,
    val shortDescription: String? = null,
    val description: String? = null,
    val credits: String? = null,
    val days: List<PlanDayLocaleFile> = emptyList(),
)

@Serializable
data class PlanDayLocaleFile(val dayNumber: Int, val title: String? = null, val intro: String? = null)

private val LenientPlanJson = Json { ignoreUnknownKeys = true }

/** A started plan's title in [lang], from its own saved copy; [fallback] (the stored title) if it has none. */
fun localizedPlanTitle(planJson: String, fallback: String, lang: String): String = try {
    LenientPlanJson.decodeFromString(PlanFile.serializer(), planJson).i18n[lang]?.title ?: fallback
} catch (e: SerializationException) {
    fallback
} catch (e: IllegalArgumentException) {
    fallback
}

@Serializable
data class PlanDayFile(
    val dayNumber: Int,
    val title: String? = null,
    val intro: String? = null,
    val passages: List<PassageFile>,
)

@Serializable
data class PassageFile(
    val book: String,
    val startChapter: Int,
    val startVerse: Int,
    val endChapter: Int,
    val endVerse: Int,
) {
    val bookId: Int? get() = BookCodes.id(book)
}

/** A plan card in the browser (H8). */
@Immutable
data class PlanSummary(
    val id: String,
    val title: String,
    val shortDescription: String,
    val category: String,
    val days: Int,
    val minutesPerDay: Int,
    val difficulty: String,
)

@Immutable
data class PlanCategory(val id: String, val title: String, val plans: List<PlanSummary>)
