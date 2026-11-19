package aarnav100.developer.biblescroll.di

import aarnav100.developer.biblescroll.BuildConfig
import aarnav100.developer.biblescroll.data.backup.BackupExporter
import aarnav100.developer.biblescroll.data.backup.BackupImporter
import aarnav100.developer.biblescroll.data.backup.BackupStore
import aarnav100.developer.biblescroll.data.backup.RoomBackupStore
import aarnav100.developer.biblescroll.data.reminder.ReminderScheduler
import aarnav100.developer.biblescroll.widget.WidgetRefresher
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {
    @Binds
    abstract fun bindBackupStore(impl: RoomBackupStore): BackupStore

    companion object {
        @Provides
        fun provideExporter(store: BackupStore, dataStore: DataStore<Preferences>): BackupExporter =
            BackupExporter(store, dataStore, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")

        @Provides
        fun provideImporter(
            store: BackupStore,
            dataStore: DataStore<Preferences>,
            scheduler: ReminderScheduler,
            widgets: WidgetRefresher,
        ): BackupImporter = BackupImporter(store, dataStore) {
            // Imported reminders, plan reminders and widget verse take effect straight away.
            scheduler.syncFromStore()
            widgets.refresh()
        }
    }
}
