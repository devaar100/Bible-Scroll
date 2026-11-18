package aarnav100.developer.biblescroll.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps exactly one daily-reminder alarm in line with [ReminderSettings]. Shared by S6 and onboarding (O7).
 */
interface ReminderScheduler {
    /** Schedules the next reminder if [settings] is enabled, otherwise cancels any pending one. */
    fun sync(settings: ReminderSettings)

    /** [sync] with the stored settings: for boot, time changes, app updates and app start. */
    suspend fun syncFromStore()
}

/**
 * Inexact alarms via setAndAllowWhileIdle: no SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM needed, and a
 * few minutes' drift is fine for a reading reminder. Each firing schedules the next day's.
 */
@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ReminderRepository,
) : ReminderScheduler {

    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    override fun sync(settings: ReminderSettings) {
        val alarms = alarmManager ?: return
        val operation = alarmIntent(context)
        if (!settings.enabled) {
            alarms.cancel(operation)
            return
        }
        // Zone read at call time, so TIMEZONE_CHANGED -> sync() picks up the new zone.
        val trigger = nextTrigger(ZonedDateTime.now(), settings.time)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.toInstant().toEpochMilli(), operation)
    }

    override suspend fun syncFromStore() = sync(repository.settings.first())

    companion object {
        /** Same request code and intent every time, so scheduling replaces rather than stacks. */
        fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderAlarmReceiver::class.java).setAction(ReminderAlarmReceiver.ACTION_FIRE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
