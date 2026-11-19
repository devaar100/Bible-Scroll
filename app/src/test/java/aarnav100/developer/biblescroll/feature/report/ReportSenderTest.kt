package aarnav100.developer.biblescroll.feature.report

import android.content.ActivityNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportSenderTest {

    private val email = ReportEmail("help@example.org", "Report: App bug", "--- Details (please keep) ---\n")

    private class FakeEmailApps(var installed: Boolean, var failure: Exception? = null) : EmailApps {
        val composed = mutableListOf<ReportEmail>()
        override fun available() = installed
        override fun compose(email: ReportEmail) {
            failure?.let { throw it }
            composed += email
        }
    }

    @Test
    fun opensTheEmailAppWhenThereIsOne() {
        val apps = FakeEmailApps(installed = true)

        assertEquals(SendResult.Opened, ReportSender(apps).send(email))
        assertEquals(listOf(email), apps.composed)
    }

    @Test
    fun noEmailAppFallsBackToCopyWithoutTryingToOpen() {
        val apps = FakeEmailApps(installed = false)

        assertEquals(SendResult.NoEmailApp(email), ReportSender(apps).send(email))
        assertEquals(emptyList<ReportEmail>(), apps.composed)
    }

    @Test
    fun anAppThatDisappearsOrRefusesAlsoFallsBack() {
        for (failure in listOf(ActivityNotFoundException(), SecurityException())) {
            val apps = FakeEmailApps(installed = true, failure = failure)
            assertEquals(SendResult.NoEmailApp(email), ReportSender(apps).send(email))
        }
    }
}
