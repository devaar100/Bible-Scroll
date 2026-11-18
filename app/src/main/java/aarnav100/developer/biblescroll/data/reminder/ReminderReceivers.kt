package aarnav100.developer.biblescroll.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Runs [block] off the main thread while keeping the broadcast alive (goAsync) until it's done. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** The reminder alarm: show today's verse, then schedule tomorrow's. (Hilt's Gradle transform injects fields before onReceive.) */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        runAsync {
            try {
                notifier.showDailyVerse()
            } finally {
                // Always line up the next one, even if posting failed.
                scheduler.syncFromStore()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "aarnav100.developer.biblescroll.action.DAILY_REMINDER"
    }
}

/** Alarms don't survive reboot, and a clock/zone change moves "08:00 local": reschedule. */
@AndroidEntryPoint
class ReminderRescheduleReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ACTIONS) return
        runAsync { scheduler.syncFromStore() }
    }

    private companion object {
        val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED, // "android.intent.action.TIME_SET"
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
