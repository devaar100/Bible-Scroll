package aarnav100.developer.biblescroll.di

import aarnav100.developer.biblescroll.data.backup.NoDeleteOpenHelperFactory
import aarnav100.developer.biblescroll.data.devotionals.DevotionalProgressDao
import aarnav100.developer.biblescroll.data.plans.CustomPlanDao
import aarnav100.developer.biblescroll.data.plans.PlanProgressDao
import aarnav100.developer.biblescroll.data.streak.LocalStreakRepository
import aarnav100.developer.biblescroll.data.streak.StreakDao
import aarnav100.developer.biblescroll.data.streak.StreakRepository
import aarnav100.developer.biblescroll.data.user.LocalUserDataRepository
import aarnav100.developer.biblescroll.data.user.UserDao
import aarnav100.developer.biblescroll.data.user.UserDataRepository
import aarnav100.developer.biblescroll.data.user.UserDatabase
import aarnav100.developer.biblescroll.data.user.USER_DB_MIGRATIONS
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
object UserDatabaseModule {

    @Provides
    @Singleton
    fun provideUserDatabase(@ApplicationContext context: Context): UserDatabase =
        // No destructive fallback: this holds the user's likes, notes and progress. And a corrupt file is
        // kept, not deleted (NoDeleteOpenHelperFactory); UserDbHealth shows the error screen instead.
        Room.databaseBuilder(context, UserDatabase::class.java, UserDatabase.FILE_NAME)
            .addMigrations(*USER_DB_MIGRATIONS)
            .openHelperFactory(NoDeleteOpenHelperFactory(NoDeleteOpenHelperFactory.rescueDir(context)))
            .build()

    @Provides
    fun provideUserDao(db: UserDatabase): UserDao = db.userDao()

    @Provides
    fun provideStreakDao(db: UserDatabase): StreakDao = db.streakDao()

    @Provides
    fun providePlanProgressDao(db: UserDatabase): PlanProgressDao = db.planProgressDao()

    @Provides
    fun provideCustomPlanDao(db: UserDatabase): CustomPlanDao = db.customPlanDao()

    @Provides
    fun provideDevotionalProgressDao(db: UserDatabase): DevotionalProgressDao = db.devotionalProgressDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UserRepositoryModule {
    @Binds
    abstract fun bindUserDataRepository(impl: LocalUserDataRepository): UserDataRepository

    @Binds
    abstract fun bindStreakRepository(impl: LocalStreakRepository): StreakRepository
}
