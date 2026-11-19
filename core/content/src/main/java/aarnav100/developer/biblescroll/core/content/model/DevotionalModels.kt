package aarnav100.developer.biblescroll.core.content.model

import kotlinx.serialization.Serializable

/*
 * Daily devotionals as published by the pipeline (pipeline/schema/devotional_month.json): one gzip JSON file
 * per month, listed in manifest.json under devotionals.months. Unknown keys are ignored.
 */

@Serializable
data class DevotionalMonthFile(
    val formatVersion: Int,
    val schemaVersion: Int,
    /** "01".."12". February includes the 02-29 bonus. */
    val month: String,
    val devotionals: List<Devotional>,
)

@Serializable
data class Devotional(
    /** "devotional-MM-DD" */
    val id: String,
    val contentVersion: String,
    /** "MM-DD": shown on that month and day every year (02-29 only in leap years). */
    val date: String,
    val dayOfYear: Int,
    val title: String,
    val theme: String,
    val series: DevotionalSeries,
    /** One of the app's drawn cover designs (dawn, meadow, ocean, ember, dusk, stone, linen, night). */
    val coverStyle: String,
    /** The anchor passage first, then up to two more. Book codes are USFM ("JHN"). */
    val verses: List<DevotionalPassage>,
    val context: String,
    val reflect: String,
    val applyIt: String,
    val prayer: String? = null,
    val reviewed: Boolean = false,
    val model: String? = null,
    /** Checked translations by language code (pipeline `translate devotionals`). English stays in the fields above. */
    val i18n: Map<String, DevotionalLocale> = emptyMap(),
) {
    /** This devotional in [lang], field by field; anything not translated stays English. Scripture comes from the reader's Bible. */
    fun localized(lang: String): Devotional {
        val loc = i18n[lang] ?: return this
        return copy(
            title = loc.title ?: title,
            theme = loc.theme ?: theme,
            series = loc.seriesTitle?.let { series.copy(title = it) } ?: series,
            context = loc.context ?: context,
            reflect = loc.reflect ?: reflect,
            applyIt = loc.applyIt ?: applyIt,
            prayer = loc.prayer ?: prayer,
        )
    }
}

@Serializable
data class DevotionalLocale(
    val title: String? = null,
    val theme: String? = null,
    val seriesTitle: String? = null,
    val context: String? = null,
    val reflect: String? = null,
    val applyIt: String? = null,
    val prayer: String? = null,
)

@Serializable
data class DevotionalSeries(val title: String, val season: String, val day: Int, val length: Int)

@Serializable
data class DevotionalPassage(
    val book: String,
    val startChapter: Int,
    val startVerse: Int,
    val endChapter: Int,
    val endVerse: Int,
)

@Serializable
data class ManifestDevotionals(val months: List<ManifestMonth> = emptyList())

@Serializable
data class ManifestMonth(
    /** "01".."12" */
    val month: String,
    val url: String,
    val contentVersion: String,
    val count: Int,
    val reviewedCount: Int,
    val sha256: String,
    val bytes: Long,
)

/** assets/devotionals/manifest.json: the bundled months (url = asset file name). */
@Serializable
data class BundledDevotionalManifest(val formatVersion: Int, val release: String, val months: List<ManifestMonth>)
