package aarnav100.developer.biblescroll.di

import aarnav100.developer.biblescroll.BuildConfig
import aarnav100.developer.biblescroll.core.content.AndroidAssetSource
import aarnav100.developer.biblescroll.data.plans.CustomPlanBuilder
import aarnav100.developer.biblescroll.data.plans.CustomPlanDao
import aarnav100.developer.biblescroll.data.plans.PlanCatalogDao
import aarnav100.developer.biblescroll.data.plans.PlanCatalogDatabase
import aarnav100.developer.biblescroll.data.plans.PlanImporter
import aarnav100.developer.biblescroll.data.plans.PlanProgressDao
import aarnav100.developer.biblescroll.data.plans.PlanRepository
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.reminder.ReminderScheduler
import aarnav100.developer.biblescroll.data.streak.StreakRepository
import aarnav100.developer.biblescroll.data.user.UserDataRepository
import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlansModule {

    @Provides
    @Singleton
    fun provideCatalogDatabase(@ApplicationContext context: Context): PlanCatalogDatabase =
        // Only bundled plans live here; they're re-imported from assets, so a schema change may wipe it.
        Room.databaseBuilder(context, PlanCatalogDatabase::class.java, PlanCatalogDatabase.FILE_NAME)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideCatalogDao(db: PlanCatalogDatabase): PlanCatalogDao = db.catalogDao()

    @Provides
    @Singleton
    fun provideImporter(@ApplicationContext context: Context, dao: PlanCatalogDao, json: Json): PlanImporter =
        PlanImporter(AndroidAssetSource(context.assets), dao, json, BuildConfig.VERSION_CODE.toLong(), Dispatchers.IO)

    @Provides
    @Singleton
    fun provideCustomPlanBuilder(@ApplicationContext context: Context): CustomPlanBuilder =
        CustomPlanBuilder(AndroidAssetSource(context.assets), Dispatchers.IO)

    @Provides
    @Singleton
    fun provideRepository(
        catalog: PlanCatalogDao,
        progress: PlanProgressDao,
        custom: CustomPlanDao,
        importer: PlanImporter,
        scheduler: ReminderScheduler,
        streaks: StreakRepository,
        userData: UserDataRepository,
        bible: BibleRepository,
        json: Json,
    ): PlanRepository = PlanRepository(
        catalog, progress, custom, importer, scheduler, streaks, userData, bible, json, System::currentTimeMillis, Dispatchers.IO,
    )
}
