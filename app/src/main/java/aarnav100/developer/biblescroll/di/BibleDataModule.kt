package aarnav100.developer.biblescroll.di

import aarnav100.developer.biblescroll.data.bible.BibleDao
import aarnav100.developer.biblescroll.data.bible.BibleDatabase
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.BookNames
import aarnav100.developer.biblescroll.data.bible.OfflineBibleRepository
import aarnav100.developer.biblescroll.data.bible.ResourceBookNames
import aarnav100.developer.biblescroll.core.content.AndroidAssetSource
import aarnav100.developer.biblescroll.data.votd.LocalClock
import aarnav100.developer.biblescroll.data.votd.VotdSource
import kotlinx.coroutines.Dispatchers
import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BibleDatabaseModule {

    @Provides
    @Singleton
    fun provideBibleDatabase(@ApplicationContext context: Context): BibleDatabase =
        Room.databaseBuilder(context, BibleDatabase::class.java, BibleDatabase.FILE_NAME)
            .createFromAsset(BibleDatabase.ASSET_PATH)
            // Holds no user data: on a version bump, drop it and re-copy the new asset.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideBibleDao(db: BibleDatabase): BibleDao = db.bibleDao()

    /** Verse of the Day list (assets/votd.csv), parsed once per process. */
    @Provides
    @Singleton
    fun provideVotdSource(@ApplicationContext context: Context): VotdSource =
        VotdSource(AndroidAssetSource(context.assets), Dispatchers.IO)

    @Provides
    fun provideLocalClock(): LocalClock = LocalClock.System
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BibleRepositoryModule {
    @Binds
    abstract fun bindBibleRepository(impl: OfflineBibleRepository): BibleRepository

    @Binds
    abstract fun bindBookNames(impl: ResourceBookNames): BookNames
}
