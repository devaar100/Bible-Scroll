package aarnav100.developer.biblescroll.core.content.di

import aarnav100.developer.biblescroll.core.content.AndroidAssetSource
import aarnav100.developer.biblescroll.core.content.BookDownloader
import aarnav100.developer.biblescroll.core.content.BookImporter
import aarnav100.developer.biblescroll.core.content.BuildConfig
import aarnav100.developer.biblescroll.core.content.BundledContent
import aarnav100.developer.biblescroll.core.content.ContentCache
import aarnav100.developer.biblescroll.core.content.ContentStore
import aarnav100.developer.biblescroll.core.content.DiscoverRepository
import aarnav100.developer.biblescroll.core.content.StudyRepository
import aarnav100.developer.biblescroll.core.content.db.ContentDao
import aarnav100.developer.biblescroll.core.content.db.ContentDatabase
import aarnav100.developer.biblescroll.core.content.db.RoomContentStore
import aarnav100.developer.biblescroll.core.content.net.AndroidNetworkMonitor
import aarnav100.developer.biblescroll.core.content.net.ContentApi
import aarnav100.developer.biblescroll.core.content.net.NetworkMonitor
import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ContentModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideContentDatabase(@ApplicationContext context: Context): ContentDatabase =
        // A cache of re-downloadable content: rebuilding it on a schema change is fine (bundled books re-import).
        Room.databaseBuilder(context, ContentDatabase::class.java, ContentDatabase.FILE_NAME)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideContentDao(db: ContentDatabase): ContentDao = db.contentDao()

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build() // no cookie jar, no interceptors that add identifiers

    @Provides
    @Singleton
    fun provideContentApi(client: OkHttpClient, json: Json): ContentApi = Retrofit.Builder()
        .baseUrl(BuildConfig.CONTENT_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ContentApi::class.java)

    /** Downloads outlive the screen that started them, so a half-read book isn't thrown away. */
    @Provides
    @Singleton
    fun provideDownloader(
        api: ContentApi,
        store: ContentStore,
        importer: BookImporter,
        @ApplicationContext context: Context,
    ): BookDownloader = BookDownloader(
        api, store, importer, File(context.cacheDir, "study-downloads"),
        CoroutineScope(SupervisorJob() + Dispatchers.IO), Dispatchers.IO, BuildConfig.CONTENT_BASE_URL,
    )

    @Provides
    @Singleton
    fun provideStudyRepository(
        store: ContentStore,
        downloader: BookDownloader,
        network: NetworkMonitor,
        json: Json,
    ): StudyRepository = StudyRepository(store, downloader, network, json, Dispatchers.Default)

    @Provides
    @Singleton
    fun provideDiscoverRepository(
        api: ContentApi,
        store: ContentStore,
        json: Json,
        @ApplicationContext context: Context,
    ): DiscoverRepository = DiscoverRepository(
        api, ContentCache(File(context.filesDir, "content-cache")), store, json, BuildConfig.CONTENT_BASE_URL, Dispatchers.IO,
    )

    @Provides
    @Singleton
    fun provideBundledContent(
        @ApplicationContext context: Context,
        store: ContentStore,
        importer: BookImporter,
        json: Json,
    ): BundledContent = BundledContent(AndroidAssetSource(context.assets), store, importer, json, Dispatchers.IO)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ContentBindings {
    @Binds
    abstract fun bindContentStore(impl: RoomContentStore): ContentStore

    @Binds
    abstract fun bindNetworkMonitor(impl: AndroidNetworkMonitor): NetworkMonitor
}
