package aarnav100.developer.biblescroll

import aarnav100.developer.biblescroll.data.backup.RestoreFixups
import aarnav100.developer.biblescroll.data.plans.PlanImporter
import aarnav100.developer.biblescroll.data.settings.AppLanguage
import aarnav100.developer.biblescroll.data.reminder.ReminderNotifier
import aarnav100.developer.biblescroll.data.reminder.ReminderScheduler
import aarnav100.developer.biblescroll.core.content.BundledContent
import aarnav100.developer.biblescroll.core.content.BundledDevotionals
import aarnav100.developer.biblescroll.core.content.work.StudyRefreshWorker
import android.app.Application
import android.util.Log
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
    @Inject lateinit var restoreFixups: RestoreFixups
    @Inject lateinit var appLanguage: AppLanguage
    @Inject lateinit var bundledContent: BundledContent
    @Inject lateinit var bundledDevotionals: BundledDevotionals
    @Inject lateinit var planImporter: PlanImporter
    @Inject lateinit var workerFactory: HiltWorkerFactory

    /** WorkManager is initialised on demand with Hilt's factory (default initializer removed in the manifest). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        if (BuildConfig.DEBUG) enableStrictMode()
        // Per-app language: below Android 13 apply the stored choice to this context; on 13+ move an old one to the system.
        AppLanguage.applyToApplication(this)
        AppLanguage.migrateToSystem(this)
        super.onCreate()
        appLanguage.refresh()
        reminderNotifier.ensureChannel()
        // A force-stop clears our alarms without any broadcast; re-arm whenever the app starts. Alarms aren't
        // part of a backup either, so this also re-arms them after a restore. Plan reminders read user.db: if
        // it won't open, log and carry on so MainActivity can show the data error screen instead of crashing.
        appScope.launch {
            try {
                reminderScheduler.syncFromStore()
            } catch (e: Exception) {
                Log.e("BibleScrollApp", "Couldn't re-arm reminders", e)
            }
        }
        // First launch after Android restored a backup: fix settings that point at missing data.
        appScope.launch { restoreFixups.runIfRestored() }
        // Study content: import the bundled Gospels and Psalms if they're missing or the app shipped a new
        // bundle (a no-op once done), then keep the weekly refresh scheduled.
        appScope.launch { bundledContent.importMissingOrUpdated() }
        // Devotionals: this month and next ship in assets; others download when opened.
        appScope.launch { bundledDevotionals.importMissingOrUpdated() }
        StudyRefreshWorker.schedule(this)
        // Reading plans: copy assets/plans/ into the catalogue on first launch and after an app update.
        appScope.launch { planImporter.importIfNeeded() }
    }

    /**
     * Language or other configuration change. Below Android 13 the framework resets our resources to the device
     * language here, so re-apply the chosen one; then refresh book names, reference formats and channel names.
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLanguage.applyToApplication(this)
        appLanguage.refresh()
        reminderNotifier.ensureChannel()
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
