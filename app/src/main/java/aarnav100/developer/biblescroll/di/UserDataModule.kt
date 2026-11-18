package aarnav100.developer.biblescroll.di

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
        // No destructive fallback: this holds the user's likes, notes and progress.
        Room.databaseBuilder(context, UserDatabase::class.java, UserDatabase.FILE_NAME)
            .addMigrations(*USER_DB_MIGRATIONS)
            .build()

    @Provides
    fun provideUserDao(db: UserDatabase): UserDao = db.userDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UserRepositoryModule {
    @Binds
    abstract fun bindUserDataRepository(impl: LocalUserDataRepository): UserDataRepository
}
