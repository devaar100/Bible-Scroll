package aarnav100.developer.biblescroll.data.reminder

import aarnav100.developer.biblescroll.widget.WidgetVerseRepository
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Runs [block] off the main thread while keeping the broadcast alive (goAsync) until it's done. */
internal fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } catch (e: Exception) {
            // e.g. user.db won't open: never crash in the background; the app shows the error when opened.
            Log.e("Reminders", "Reminder work failed", e)
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
        when (intent.action) {
            ACTION_FIRE -> runAsync {
                try {
                    notifier.showDailyVerse()
                } finally {
                    // Always line up the next one, even if posting failed.
                    scheduler.syncFromStore()
                }
            }
            ACTION_PLAN -> {
                val enrollmentId = intent.data?.lastPathSegment ?: return
                runAsync {
                    try {
                        notifier.showPlanReminder(enrollmentId)
                    } finally {
                        scheduler.syncPlanReminders()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "aarnav100.developer.biblescroll.action.DAILY_REMINDER"
        const val ACTION_PLAN = "aarnav100.developer.biblescroll.action.PLAN_REMINDER"
    }
}

/**
 * Alarms don't survive reboot, and a clock/zone change moves "08:00 local": reschedule. The same change can move
 * the local date, so the Verse of the Day widget is redrawn and its midnight alarm re-armed here too.
 */
@AndroidEntryPoint
class ReminderRescheduleReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var widgetVerses: WidgetVerseRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ACTIONS) return
        runAsync {
            try {
                scheduler.syncFromStore()
            } finally {
                widgetVerses.onDayOrClockChanged()
            }
        }
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
