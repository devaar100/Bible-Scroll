package aarnav100.developer.biblescroll

import aarnav100.developer.biblescroll.data.reminder.ReminderNotifier
import aarnav100.developer.biblescroll.data.reminder.ReminderScheduler
import aarnav100.developer.biblescroll.core.content.BundledContent
import aarnav100.developer.biblescroll.core.content.work.StudyRefreshWorker
import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BibleScrollApplication : Application(), Configuration.Provider {

    @Inject lateinit var reminderNotifier: ReminderNotifier
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var bundledContent: BundledContent
    @Inject lateinit var workerFactory: HiltWorkerFactory

    /** WorkManager is initialised on demand with Hilt's factory (default initializer removed in the manifest). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        if (BuildConfig.DEBUG) enableStrictMode()
        super.onCreate()
        reminderNotifier.ensureChannel()
        // A force-stop clears our alarms without any broadcast; re-arm whenever the app starts.
        appScope.launch { reminderScheduler.syncFromStore() }
        // Study content: import the bundled Gospels and Psalms if they're missing or the app shipped a new
        // bundle (a no-op once done), then keep the weekly refresh scheduled.
        appScope.launch { bundledContent.importMissingOrUpdated() }
        StudyRefreshWorker.schedule(this)
    }

    /** Debug builds only: logs main-thread disk/network access and leaked resources (tag "StrictMode"). */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectActivityLeaks()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .detectLeakedRegistrationObjects()
                .detectFileUriExposure()
                .detectContentUriWithoutPermission()
                .penaltyLog()
                .build(),
        )
    }
}
