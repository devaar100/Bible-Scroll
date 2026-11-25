package aarnav100.developer.biblescroll.core.content.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal

/** /ministries.json (pipeline/community/ministries.json, schema/ministries.json): the Community spotlight list. */
@Serializable
data class MinistriesFile(
    val formatVersion: Int,
    val ministries: List<Ministry>,
)

@Serializable
data class Ministry(
    /** Stable; names the Firestore counter "YYYY-MM_id". */
    val id: String,
    val name: String,
    /** By language code; "en" is always present. */
    val description: Map<String, String>,
    val website: String,
    @SerialName("give_url") val giveUrl: String,
    /** UTC months ("2026-12") in which this ministry is in the vote. */
    @SerialName("months_featured") val monthsFeatured: List<String>,
    /** Debug builds only. */
    val test: Boolean = false,
    /** Comma-separated categories, e.g. "Relief, Development"; shown above the name. */
    val category: String? = null,
    /** [category] in other languages, by language code ("es"); English [category] where one is missing. */
    @SerialName("category_i18n") val categoryI18n: Map<String, String> = emptyMap(),
    /** Card image (https, our Firebase Hosting site or Storage bucket; see MinistryImages). Optional. */
    val image: String? = null,
    /** Who took the image and its licence, shown on the card (CC BY / BY-SA need it), e.g. "Jane Doe, CC BY-SA 4.0". */
    @SerialName("image_credit") val imageCredit: String? = null,
    // amountPlanned is ignored on purpose: no amount is shown before a vote ends.
) {
    /** [category] split on commas, trimmed, blanks dropped. */
    val categories: List<String>
        get() = split(category)

    /** [categories] in [language] ("es"), falling back to English. */
    fun categories(language: String): List<String> = split(categoryI18n[language] ?: category)

    private fun split(text: String?): List<String> = text?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    /** In [language] ("es"), falling back to English. */
    fun description(language: String): String = description[language] ?: description["en"].orEmpty()
}

/** /history.json (pipeline community/history.json, schema/history.json): what was donated, one record per finished month. */
@Serializable
data class HistoryFile(
    val formatVersion: Int,
    val donations: List<Donation>,
)

@Serializable
data class Donation(
    /** UTC vote month, "2026-11". */
    val month: String,
    val ministryId: String,
    /** Shown as is, so the name resolves even after the ministry leaves ministries.json. */
    val ministryName: String,
    /** As published (e.g. 300 or 312.5). Kept as the JSON text and read as a BigDecimal: never rounded or computed. */
    val amount: JsonPrimitive,
    /** ISO 4217, e.g. "USD". */
    val currency: String,
) {
    val amountValue: BigDecimal? get() = amount.content.toBigDecimalOrNull()?.takeIf { !amount.isString && it.signum() > 0 }
}

/** manifest.history: where /history.json is and its hash. Absent until one is published. */
@Serializable
data class HistoryEntry(val url: String, val count: Int, val sha256: String, val bytes: Long)

/** manifest.community: where /ministries.json is and its hash. Absent until one is published. */
@Serializable
data class CommunityEntry(val url: String, val count: Int, val sha256: String, val bytes: Long)
