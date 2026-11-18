package aarnav100.developer.biblescroll.core.content.di;

import aarnav100.developer.biblescroll.core.content.BookDownloader;
import aarnav100.developer.biblescroll.core.content.BookImporter;
import aarnav100.developer.biblescroll.core.content.ContentStore;
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
public final class ContentModule_ProvideDownloaderFactory implements Factory<BookDownloader> {
  private final Provider<ContentApi> apiProvider;

  private final Provider<ContentStore> storeProvider;

  private final Provider<BookImporter> importerProvider;

  private final Provider<Context> contextProvider;

  private ContentModule_ProvideDownloaderFactory(Provider<ContentApi> apiProvider,
      Provider<ContentStore> storeProvider, Provider<BookImporter> importerProvider,
      Provider<Context> contextProvider) {
    this.apiProvider = apiProvider;
    this.storeProvider = storeProvider;
    this.importerProvider = importerProvider;
    this.contextProvider = contextProvider;
  }

  @Override
  public BookDownloader get() {
    return provideDownloader(apiProvider.get(), storeProvider.get(), importerProvider.get(), contextProvider.get());
  }

  public static ContentModule_ProvideDownloaderFactory create(Provider<ContentApi> apiProvider,
      Provider<ContentStore> storeProvider, Provider<BookImporter> importerProvider,
      Provider<Context> contextProvider) {
    return new ContentModule_ProvideDownloaderFactory(apiProvider, storeProvider, importerProvider, contextProvider);
  }

  public static BookDownloader provideDownloader(ContentApi api, ContentStore store,
      BookImporter importer, Context context) {
    return Preconditions.checkNotNullFromProvides(ContentModule.INSTANCE.provideDownloader(api, store, importer, context));
  }
}
