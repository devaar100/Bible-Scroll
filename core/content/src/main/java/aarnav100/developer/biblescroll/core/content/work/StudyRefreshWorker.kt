package aarnav100.developer.biblescroll.core.content.work

import aarnav100.developer.biblescroll.core.content.BookDownloader
import aarnav100.developer.biblescroll.core.content.DownloadFailure
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Weekly: refresh only books already on the device whose content changed. */
@HiltWorker
class StudyRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val downloader: BookDownloader,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val failure = downloader.refreshStoredBooks()
        // Local logcat only (no analytics): enough to diagnose a refresh that keeps failing.
        Log.i(TAG, if (failure == null) "Study refresh done" else "Study refresh failed: $failure")
        return when (failure) {
            null -> Result.success()
            DownloadFailure.NETWORK -> Result.retry()
            // Integrity or format problems won't fix themselves by retrying now; try again next week.
            else -> Result.success()
        }
    }

    companion object {
        private const val NAME = "study-content-refresh"
        private const val TAG = "StudyRefresh"

        /** Idempotent: keeps an existing schedule. Unmetered network, battery not low. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StudyRefreshWorker>(7, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
