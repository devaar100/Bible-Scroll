package aarnav100.developer.biblescroll.core.content.di;

import aarnav100.developer.biblescroll.core.content.DevotionalDownloader;
import aarnav100.developer.biblescroll.core.content.DevotionalImporter;
import aarnav100.developer.biblescroll.core.content.DevotionalStore;
import aarnav100.developer.biblescroll.core.content.net.ContentApi;
import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class ContentModule_ProvideDevotionalDownloaderFactory implements Factory<DevotionalDownloader> {
  private final Provider<ContentApi> apiProvider;

  private final Provider<DevotionalStore> storeProvider;

  private final Provider<DevotionalImporter> importerProvider;

  private final Provider<Context> contextProvider;

  private ContentModule_ProvideDevotionalDownloaderFactory(Provider<ContentApi> apiProvider,
      Provider<DevotionalStore> storeProvider, Provider<DevotionalImporter> importerProvider,
      Provider<Context> contextProvider) {
    this.apiProvider = apiProvider;
    this.storeProvider = storeProvider;
    this.importerProvider = importerProvider;
    this.contextProvider = contextProvider;
  }

  @Override
  public DevotionalDownloader get() {
    return provideDevotionalDownloader(apiProvider.get(), storeProvider.get(), importerProvider.get(), contextProvider.get());
  }

  public static ContentModule_ProvideDevotionalDownloaderFactory create(
      Provider<ContentApi> apiProvider, Provider<DevotionalStore> storeProvider,
      Provider<DevotionalImporter> importerProvider, Provider<Context> contextProvider) {
    return new ContentModule_ProvideDevotionalDownloaderFactory(apiProvider, storeProvider, importerProvider, contextProvider);
  }

  public static DevotionalDownloader provideDevotionalDownloader(ContentApi api,
      DevotionalStore store, DevotionalImporter importer, Context context) {
    return Preconditions.checkNotNullFromProvides(ContentModule.INSTANCE.provideDevotionalDownloader(api, store, importer, context));
  }
}
