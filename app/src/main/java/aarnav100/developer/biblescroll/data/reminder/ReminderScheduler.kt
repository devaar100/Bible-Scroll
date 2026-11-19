package aarnav100.developer.biblescroll.data.reminder

import aarnav100.developer.biblescroll.data.plans.PlanProgressDao
import aarnav100.developer.biblescroll.data.plans.PlanSchedule
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the daily-reminder alarm in line with [ReminderSettings] (S6, onboarding O7) and one alarm per
 * active reading plan that has a reminder (H10).
 */
interface ReminderScheduler {
    /** Schedules the next reminder if [settings] is enabled, otherwise cancels any pending one. */
    fun sync(settings: ReminderSettings)

    /** [sync] with the stored settings plus [syncPlanReminders]: for boot, time changes, app updates and app start. */
    suspend fun syncFromStore()

    /** One alarm per active plan with a reminder; cancels alarms for plans that finished or lost theirs. */
    suspend fun syncPlanReminders()
}

/**
 * Inexact alarms via setAndAllowWhileIdle: no SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM needed, and a
 * few minutes' drift is fine for a reading reminder. Each firing schedules the next day's.
 */
@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ReminderRepository,
    private val plans: PlanProgressDao,
    private val dataStore: DataStore<Preferences>,
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

    override suspend fun syncFromStore() {
        sync(repository.settings.first())
        syncPlanReminders()
    }

    override suspend fun syncPlanReminders() {
        val alarms = alarmManager ?: return
        val now = ZonedDateTime.now()
        val wanted = plans.activeWithReminders()
        for (plan in wanted) {
            val trigger = PlanSchedule.nextReminder(now, PlanSchedule.timeOf(plan.reminderMinuteOfDay!!), LocalDate.parse(plan.startDate))
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.toInstant().toEpochMilli(), planAlarmIntent(context, plan.id))
        }
        // Alarms can't be listed, so remember which ones we set and cancel the ones no longer wanted.
        val ids = wanted.map { it.id }.toSet()
        val previous = dataStore.data.first()[PLAN_ALARMS].orEmpty()
        (previous - ids).forEach { alarms.cancel(planAlarmIntent(context, it)) }
        if (previous != ids) dataStore.edit { it[PLAN_ALARMS] = ids }
    }

    companion object {
        private val PLAN_ALARMS = stringSetPreferencesKey("plan_reminder_alarms")

        /** Same request code and intent every time, so scheduling replaces rather than stacks. */
        fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderAlarmReceiver::class.java).setAction(ReminderAlarmReceiver.ACTION_FIRE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** One per enrollment: the data URI makes each enrollment's intent distinct. */
        fun planAlarmIntent(context: Context, enrollmentId: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderAlarmReceiver::class.java)
                .setAction(ReminderAlarmReceiver.ACTION_PLAN)
                .setData(Uri.parse("biblescroll://plan-reminder/$enrollmentId")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
