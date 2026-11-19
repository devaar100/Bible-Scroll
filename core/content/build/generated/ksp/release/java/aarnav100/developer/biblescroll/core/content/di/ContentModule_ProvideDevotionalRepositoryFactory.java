package aarnav100.developer.biblescroll.core.content.di;

import aarnav100.developer.biblescroll.core.content.DevotionalDownloader;
import aarnav100.developer.biblescroll.core.content.DevotionalRepository;
import aarnav100.developer.biblescroll.core.content.DevotionalStore;
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
public final class ContentModule_ProvideDevotionalRepositoryFactory implements Factory<DevotionalRepository> {
  private final Provider<DevotionalStore> storeProvider;

  private final Provider<DevotionalDownloader> downloaderProvider;

  private final Provider<NetworkMonitor> networkProvider;

  private final Provider<Json> jsonProvider;

  private ContentModule_ProvideDevotionalRepositoryFactory(Provider<DevotionalStore> storeProvider,
      Provider<DevotionalDownloader> downloaderProvider, Provider<NetworkMonitor> networkProvider,
      Provider<Json> jsonProvider) {
    this.storeProvider = storeProvider;
    this.downloaderProvider = downloaderProvider;
    this.networkProvider = networkProvider;
    this.jsonProvider = jsonProvider;
  }

  @Override
  public DevotionalRepository get() {
    return provideDevotionalRepository(storeProvider.get(), downloaderProvider.get(), networkProvider.get(), jsonProvider.get());
  }

  public static ContentModule_ProvideDevotionalRepositoryFactory create(
      Provider<DevotionalStore> storeProvider, Provider<DevotionalDownloader> downloaderProvider,
      Provider<NetworkMonitor> networkProvider, Provider<Json> jsonProvider) {
    return new ContentModule_ProvideDevotionalRepositoryFactory(storeProvider, downloaderProvider, networkProvider, jsonProvider);
  }

  public static DevotionalRepository provideDevotionalRepository(DevotionalStore store,
      DevotionalDownloader downloader, NetworkMonitor network, Json json) {
    return Preconditions.checkNotNullFromProvides(ContentModule.INSTANCE.provideDevotionalRepository(store, downloader, network, json));
  }
}
