package aarnav100.developer.biblescroll.data.reminder

import aarnav100.developer.biblescroll.data.plans.localizedPlanTitle
import aarnav100.developer.biblescroll.data.settings.ContentLanguage
import aarnav100.developer.biblescroll.MainActivity
import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.plans.PlanProgressDao
import aarnav100.developer.biblescroll.data.plans.PlanSchedule
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.ui.navigation.PlanLinks
import aarnav100.developer.biblescroll.ui.navigation.VerseLinks
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Whether reminders can be shown, and whether asking (Android 13+) could change that. */
interface NotificationAccess {
    fun canPost(): Boolean

    /** True on Android 13+ when POST_NOTIFICATIONS hasn't been granted yet. */
    fun needsRuntimePermission(): Boolean
}

/** Posts the daily reminder: a short verse in the reader's translation; tap opens it in the feed. */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bible: BibleRepository,
    private val readerPreferences: ReaderPreferences,
    private val plans: PlanProgressDao,
) : NotificationAccess {
    /** Idempotent; called at app start so the channel exists in system settings before the first post. */
    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.reminder_channel_description) }
        val planChannel = NotificationChannel(
            PLAN_CHANNEL_ID,
            context.getString(R.string.plan_reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.plan_reminder_channel_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannels(listOf(channel, planChannel))
    }

    /**
     * H10 plan reminder: "Day 3 of John in 21 days". Nothing is posted before the plan's start date, after
     * it's finished, or once today's day is already read (the reader is ahead).
     */
    suspend fun showPlanReminder(enrollmentId: String, today: LocalDate = LocalDate.now()) {
        if (!canPost()) return
        val plan = plans.enrollment(enrollmentId) ?: return
        if (plan.completedAt != null) return
        val done = plans.completedDays(enrollmentId).toSet()
        val day = PlanSchedule.reminderDay(plan.totalDays, done, LocalDate.parse(plan.startDate), today) ?: return

        val open = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse(PlanLinks.build(plan.planId))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context, OPEN_PLAN_REQUEST_CODE, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, PLAN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.plan_reminder_title, day, localizedPlanTitle(plan.planJson, plan.planTitle, ContentLanguage.code.value)))
            .setContentText(context.resources.getQuantityString(R.plurals.plan_reminder_text, plan.minutesPerDay, plan.minutesPerDay))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            // One notification per plan: a stable id from the enrollment.
            NotificationManagerCompat.from(context).notify(enrollmentId, PLAN_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between canPost() and here.
        }
    }

    /** True if a post would actually be shown (app-level switch, and the Android 13+ permission). */
    override fun canPost(): Boolean =
        !needsRuntimePermission() && NotificationManagerCompat.from(context).areNotificationsEnabled()

    override fun needsRuntimePermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    suspend fun showDailyVerse(date: LocalDate = LocalDate.now()) {
        if (!canPost()) return
        val translationId = readerPreferences.translationId.first()
        // First candidate this translation actually has (some lack verses, e.g. textual variants).
        val verse = ReminderVerses.candidatesFor(date).firstNotNullOfOrNull { bible.verse(translationId, it).first() }
            ?: return
        val bookName = bible.books().first().firstOrNull { it.id == verse.ref.bookId }?.name.orEmpty()
        val reference = context.getString(
            R.string.ref_verse, bookName, verse.ref.chapter, verse.ref.verse,
        )

        val open = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse(VerseLinks.build(verse.ref))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context, OPEN_REQUEST_CODE, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title, reference, verse.translationId))
            .setContentText(verse.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(verse.text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between canPost() and here; nothing to show.
        }
    }

    companion object {
        const val CHANNEL_ID = "daily_reminder"
        const val PLAN_CHANNEL_ID = "plan_reminders"
        private const val PLAN_NOTIFICATION_ID = 1002
        private const val OPEN_PLAN_REQUEST_CODE = 2
        private const val NOTIFICATION_ID = 1001
        private const val OPEN_REQUEST_CODE = 1
    }
}
