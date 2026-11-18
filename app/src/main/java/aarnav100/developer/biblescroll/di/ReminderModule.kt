package aarnav100.developer.biblescroll.di

import aarnav100.developer.biblescroll.data.reminder.AlarmReminderScheduler
import aarnav100.developer.biblescroll.data.reminder.NotificationAccess
import aarnav100.developer.biblescroll.data.reminder.ReminderNotifier
import aarnav100.developer.biblescroll.data.reminder.ReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {
    @Binds
    abstract fun bindReminderScheduler(impl: AlarmReminderScheduler): ReminderScheduler

    @Binds
    abstract fun bindNotificationAccess(impl: ReminderNotifier): NotificationAccess
}
