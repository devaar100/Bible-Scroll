package aarnav100.developer.biblescroll.feature.report

import aarnav100.developer.biblescroll.R
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import java.net.URLEncoder

/**
 * "Report a problem" (S7). There's no server: a report is an email the person writes and sends from their own
 * email app. [emailLabel]s are English on purpose so the support inbox can sort reports from any UI language;
 * [label] is what the sheet shows.
 */
enum class ReportType(@StringRes val label: Int, val emailLabel: String) {
    FACTUAL_ERROR(R.string.report_type_factual, "Factual error"),
    UNFAIR(R.string.report_type_unfair, "Unfair or one-sided"),
    OFFENSIVE(R.string.report_type_offensive, "Offensive or inappropriate"),
    WRONG_TEXT(R.string.report_type_wrong_text, "Wrong verse or translation text"),
    APP_BUG(R.string.report_type_app_bug, "App bug"),
    OTHER(R.string.report_type_other, "Something else"),
}

enum class ContentType(val emailLabel: String) {
    STUDY("study"),
    DEVOTIONAL("devotional"),
    PLAN("plan"),
    TRANSLATION("translation"),
    APP("app"),
}

/** Where the report was started and what it's about. Every field but [contentType] may be unknown. */
@Immutable
data class ReportContext(
    val contentType: ContentType,
    /** e.g. "JHN.3.16", a devotional id, "gospels-30-days day 4". */
    val contentId: String? = null,
    /** Human-readable, in the app's language, for the subject: "Juan 3:16", "Psalms 60 days, day 4". */
    val reference: String? = null,
    val translationId: String? = null,
    val contentVersion: String? = null,
    /** The study prompt's hash, when the published study carries it. */
    val promptHash: String? = null,
) {
    companion object {
        val App = ReportContext(ContentType.APP)
    }
}

/** The phone and app details added to every report. No identifiers: nothing that names the person or the phone. */
data class DeviceInfo(
    val appVersion: String,
    val androidVersion: String,
    val deviceModel: String,
    /** The app's UI language, e.g. "es". */
    val language: String,
)

/** An email ready to hand to the email app, or to show for copying. */
data class ReportEmail(val address: String, val subject: String, val body: String) {
    /** RFC 6068 mailto: subject and body percent-encoded (spaces as %20, line breaks as %0D%0A). */
    val mailto: String
        get() = "mailto:${encode(address).replace("%40", "@")}?subject=${encode(subject)}&body=${encode(body.replace("\n", "\r\n"))}"

    /** What the no-email-app fallback copies: everything someone needs to send it by hand. */
    val copyText: String
        get() = "To: $address\nSubject: $subject\n\n$body"

    private fun encode(text: String): String = URLEncoder.encode(text, Charsets.UTF_8.name()).replace("+", "%20")
}

object ProblemReport {
    /**
     * Notes longer than this are shortened so the mailto stays within what email apps accept (some refuse very
     * long links). The sheet's text field shows the same limit.
     */
    const val MAX_NOTE_CHARS = 1_500

    const val SHORTENED_MARKER = "… [note shortened]"
    private const val NOT_AVAILABLE = "not available"

    /** The content type the report is filed under: wrong Bible text is a translation problem wherever it was seen. */
    fun contentTypeFor(type: ReportType, context: ReportContext): ContentType =
        if (type == ReportType.WRONG_TEXT) ContentType.TRANSLATION else context.contentType

    fun build(address: String, type: ReportType, note: String, context: ReportContext, device: DeviceInfo): ReportEmail {
        val subject = listOfNotNull("Report: ${type.emailLabel}", context.reference?.takeIf { it.isNotBlank() })
            .joinToString(" – ")
        val details = listOf(
            "Report type" to type.emailLabel,
            "Content type" to contentTypeFor(type, context).emailLabel,
            "Content id" to context.contentId,
            "Reference" to context.reference,
            "Translation" to context.translationId,
            "Language" to device.language,
            "Content version" to context.contentVersion,
            "Prompt hash" to context.promptHash,
            "App version" to device.appVersion,
            "Android version" to device.androidVersion,
            "Device model" to device.deviceModel,
        )
        return ReportEmail(address, subject, body(note, details))
    }

    /** "Send feedback": no type, no content; just the note and the app/phone details. */
    fun feedback(address: String, note: String, device: DeviceInfo): ReportEmail = ReportEmail(
        address,
        "Feedback – Bible Scroll ${device.appVersion}",
        body(
            note,
            listOf(
                "Language" to device.language,
                "App version" to device.appVersion,
                "Android version" to device.androidVersion,
                "Device model" to device.deviceModel,
            ),
        ),
    )

    private fun body(note: String, details: List<Pair<String, String?>>): String = buildString {
        val trimmed = shorten(note.trim())
        if (trimmed.isNotEmpty()) {
            append(trimmed)
            append("\n\n")
        }
        append("--- Details (please keep) ---\n")
        details.forEach { (label, value) ->
            append(label).append(": ").append(value?.takeIf { it.isNotBlank() }?.let(::oneLine) ?: NOT_AVAILABLE).append('\n')
        }
    }

    /**
     * Cuts [note] to [MAX_NOTE_CHARS] at the last space in the final fifth if there is one, never inside a surrogate
     * pair (emoji, rare scripts), and marks it as shortened.
     */
    fun shorten(note: String, max: Int = MAX_NOTE_CHARS): String {
        if (note.length <= max) return note
        var end = max - SHORTENED_MARKER.length
        if (Character.isLowSurrogate(note[end])) end-- // don't split a pair
        val space = note.lastIndexOf(' ', end)
        if (space >= end * 4 / 5) end = space
        return note.substring(0, end).trimEnd() + SHORTENED_MARKER
    }

    /** Detail values stay on their own line so the block remains readable and parseable. */
    private fun oneLine(value: String) = value.replace(Regex("\\s*[\\r\\n]+\\s*"), " ").trim()
}
