package aarnav100.developer.biblescroll.widget

import aarnav100.developer.biblescroll.data.reminder.runAsync
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** The once-a-night redraw of a widget in Verse of the Day mode. */
interface VotdWidgetScheduler {
    /** Replaces any pending redraw with one at [at]. */
    fun schedule(at: ZonedDateTime)

    fun cancel()
}

/**
 * Inexact and non-waking (setAndAllowWhileIdle with RTC): no exact-alarm permission, and the phone isn't woken
 * just to redraw a widget. If it's asleep at midnight the redraw runs as soon as it wakes, so the widget shows
 * the new verse by the time anyone looks at the screen. Boot, clock and zone changes re-arm it through
 * ReminderRescheduleReceiver, like the daily reminder.
 */
@Singleton
class AlarmVotdWidgetScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : VotdWidgetScheduler {

    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    override fun schedule(at: ZonedDateTime) {
        alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC, at.toInstant().toEpochMilli(), operation(context))
    }

    override fun cancel() {
        alarmManager?.cancel(operation(context))
    }

    private companion object {
        /** Same request code and intent every time, so scheduling replaces rather than stacks. */
        fun operation(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, VotdWidgetAlarmReceiver::class.java).setAction(VotdWidgetAlarmReceiver.ACTION_MIDNIGHT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** Just after local midnight: redraw the widget with the new day's verse and book the next night. */
@AndroidEntryPoint
class VotdWidgetAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var widgetVerses: WidgetVerseRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MIDNIGHT) return
        runAsync { widgetVerses.onDayOrClockChanged() }
    }

    companion object {
        const val ACTION_MIDNIGHT = "aarnav100.developer.biblescroll.action.VOTD_WIDGET_MIDNIGHT"
    }
}
