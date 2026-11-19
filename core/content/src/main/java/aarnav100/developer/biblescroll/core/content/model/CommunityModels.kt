package aarnav100.developer.biblescroll.core.content.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
) {
    /** In [language] ("es"), falling back to English. */
    fun description(language: String): String = description[language] ?: description["en"].orEmpty()
}

/** manifest.community: where /ministries.json is and its hash. Absent until one is published. */
@Serializable
data class CommunityEntry(val url: String, val count: Int, val sha256: String, val bytes: Long)
