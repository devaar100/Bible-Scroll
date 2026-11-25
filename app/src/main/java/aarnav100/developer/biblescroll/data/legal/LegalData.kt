package aarnav100.developer.biblescroll.data.legal

import aarnav100.developer.biblescroll.R
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/** The privacy text's "Last updated" date (S8 and web/privacy/; tools/build_privacy_page.py reads it from here). */
val PRIVACY_UPDATED: LocalDate = LocalDate.of(2026, 11, 24)

/**
 * bible.db keeps one `licence` text per translation with the attribution after "Source:", e.g.
 * "Public domain. … Source: eBible.org (engwebp)." Split for S8: (licence, attribution or null).
 */
object TranslationLicence {
    private const val MARKER = "Source:"

    fun split(text: String): Pair<String, String?> {
        val at = text.indexOf(MARKER)
        if (at < 0) return text.trim() to null
        val licence = text.substring(0, at).trim()
        val attribution = text.substring(at).trim().takeIf { it.length > MARKER.length }
        return licence to attribution
    }
}

@Immutable
data class OpenSourceLibrary(
    val name: String,
    /** groupId:artifactId */
    val coordinates: String,
    val version: String,
    /** SPDX ids ("Apache-2.0") or, for licences Licensee couldn't map, their name or URL. */
    val licences: List<String>,
    val url: String?,
)

/**
 * The Licensee report bundled at build time (assets/app/cash/licensee/artifacts.json; build plugin only, no runtime
 * library). One entry per runtime dependency of this variant, sorted by name.
 */
object OpenSourceLibraries {
    const val ASSET = "app/cash/licensee/artifacts.json"
    private val parser = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<OpenSourceLibrary> {
        val items = parser.parseToJsonElement(text) as? JsonArray ?: return emptyList()
        return items.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val group = o.string("groupId") ?: return@mapNotNull null
            val artifact = o.string("artifactId") ?: return@mapNotNull null
            val spdx = o["spdxLicenses"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.string("identifier") }
            val unknown = o["unknownLicenses"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.string("name") ?: it.jsonObject.string("url") }
            OpenSourceLibrary(
                name = o.string("name") ?: artifact,
                coordinates = "$group:$artifact",
                version = o.string("version").orEmpty(),
                licences = spdx + unknown,
                url = o["scm"]?.jsonObject?.string("url"),
            )
        }.sortedWith(compareBy({ it.name.lowercase() }, { it.coordinates }))
    }

    private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull
}

/** Full licence texts shipped in assets/licenses/ (Apache-2.0 and BSD require them; OFL requires them with the fonts). */
enum class LicenceText(@StringRes val title: Int, val asset: String) {
    APACHE(R.string.legal_text_apache, "licenses/apache-2.0.txt"),
    PROTOBUF(R.string.legal_text_protobuf, "licenses/protobuf-BSD-3-Clause.txt"),
    LITERATA(R.string.legal_text_literata, "licenses/literata-OFL.txt"),
    INTER(R.string.legal_text_inter, "licenses/inter-OFL.txt"),
}
