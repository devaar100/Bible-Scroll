package aarnav100.developer.biblescroll.feature.report

import aarnav100.developer.biblescroll.BuildConfig
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/** Hands a [ReportEmail] to an email app. Abstracted so the fallback is testable without Android. */
interface EmailApps {
    /** True if some app handles mailto: (needs the <queries> entry in the manifest on Android 11+). */
    fun available(): Boolean

    /** Opens the email app with everything filled in; the person reviews and sends. Throws if none can. */
    fun compose(email: ReportEmail)
}

sealed interface SendResult {
    /** The email app is open with the report filled in. Nothing has been sent. */
    data object Opened : SendResult

    /** No email app: show the address and the text with a Copy button. */
    data class NoEmailApp(val email: ReportEmail) : SendResult
}

/** Never sends anything itself and attaches nothing: it only opens the person's email app, or says it can't. */
class ReportSender(private val apps: EmailApps) {
    fun send(email: ReportEmail): SendResult {
        if (!apps.available()) return SendResult.NoEmailApp(email)
        return try {
            apps.compose(email)
            SendResult.Opened
        } catch (e: ActivityNotFoundException) {
            // The app that answered the query went away, or refused: fall back rather than fail silently.
            SendResult.NoEmailApp(email)
        } catch (e: SecurityException) {
            SendResult.NoEmailApp(email)
        }
    }
}

class AndroidEmailApps(private val context: Context) : EmailApps {
    override fun available(): Boolean =
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).resolveActivity(context.packageManager) != null

    override fun compose(email: ReportEmail) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(email.mailto)).apply {
            // Some email apps read only the extras, some only the mailto query: send both.
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email.address))
            putExtra(Intent.EXTRA_SUBJECT, email.subject)
            putExtra(Intent.EXTRA_TEXT, email.body)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

/** Support address: defined once, in app/build.gradle.kts (supportEmail). */
val SupportAddress: String get() = BuildConfig.SUPPORT_EMAIL

fun currentDeviceInfo(context: Context): DeviceInfo = DeviceInfo(
    appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
    androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    deviceModel = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.distinct().joinToString(" "),
    language = context.resources.configuration.locales[0]?.toLanguageTag() ?: "unknown",
)
