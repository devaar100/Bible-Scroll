package aarnav100.developer.biblescroll.data.community

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Sends Community votes saved while offline. Runs once a network is available; backs off on failure. */
@HiltWorker
class CommunityVoteWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val community: CommunityRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val done = community.sendPending()
        Log.i(TAG, if (done) "Community votes sent" else "Community vote send will retry") // local logcat only
        return if (done) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "community-vote-send"
        private const val TAG = "CommunityVote"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<CommunityVoteWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // KEEP: one run sends every pending vote.
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}

class WorkManagerVoteSendScheduler @Inject constructor(@ApplicationContext private val context: Context) : VoteSendScheduler {
    override fun schedule() = CommunityVoteWorker.schedule(context)
}
