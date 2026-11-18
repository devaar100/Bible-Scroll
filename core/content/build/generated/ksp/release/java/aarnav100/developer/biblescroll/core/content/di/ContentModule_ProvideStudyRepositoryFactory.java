package aarnav100.developer.biblescroll.core.content.di;

import aarnav100.developer.biblescroll.core.content.BookDownloader;
import aarnav100.developer.biblescroll.core.content.ContentStore;
import aarnav100.developer.biblescroll.core.content.StudyRepository;
import aarnav100.developer.biblescroll.core.content.net.NetworkMonitor;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import kotlinx.serialization.json.Json;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class ContentModule_ProvideStudyRepositoryFactory implements Factory<StudyRepository> {
  private final Provider<ContentStore> storeProvider;

  private final Provider<BookDownloader> downloaderProvider;

  private final Provider<NetworkMonitor> networkProvider;

  private final Provider<Json> jsonProvider;

  private ContentModule_ProvideStudyRepositoryFactory(Provider<ContentStore> storeProvider,
      Provider<BookDownloader> downloaderProvider, Provider<NetworkMonitor> networkProvider,
      Provider<Json> jsonProvider) {
    this.storeProvider = storeProvider;
    this.downloaderProvider = downloaderProvider;
    this.networkProvider = networkProvider;
    this.jsonProvider = jsonProvider;
  }

  @Override
  public StudyRepository get() {
    return provideStudyRepository(storeProvider.get(), downloaderProvider.get(), networkProvider.get(), jsonProvider.get());
  }

  public static ContentModule_ProvideStudyRepositoryFactory create(
      Provider<ContentStore> storeProvider, Provider<BookDownloader> downloaderProvider,
      Provider<NetworkMonitor> networkProvider, Provider<Json> jsonProvider) {
    return new ContentModule_ProvideStudyRepositoryFactory(storeProvider, downloaderProvider, networkProvider, jsonProvider);
  }

  public static StudyRepository provideStudyRepository(ContentStore store,
      BookDownloader downloader, NetworkMonitor network, Json json) {
    return Preconditions.checkNotNullFromProvides(ContentModule.INSTANCE.provideStudyRepository(store, downloader, network, json));
  }
}
