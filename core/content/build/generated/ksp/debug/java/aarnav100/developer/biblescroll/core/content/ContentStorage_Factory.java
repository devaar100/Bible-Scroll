package aarnav100.developer.biblescroll.core.content;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class ContentStorage_Factory implements Factory<ContentStorage> {
  private final Provider<ContentStore> storeProvider;

  private final Provider<BundledContent> bundledProvider;

  private final Provider<BookDownloader> downloaderProvider;

  private ContentStorage_Factory(Provider<ContentStore> storeProvider,
      Provider<BundledContent> bundledProvider, Provider<BookDownloader> downloaderProvider) {
    this.storeProvider = storeProvider;
    this.bundledProvider = bundledProvider;
    this.downloaderProvider = downloaderProvider;
  }

  @Override
  public ContentStorage get() {
    return newInstance(storeProvider.get(), bundledProvider.get(), downloaderProvider.get());
  }

  public static ContentStorage_Factory create(Provider<ContentStore> storeProvider,
      Provider<BundledContent> bundledProvider, Provider<BookDownloader> downloaderProvider) {
    return new ContentStorage_Factory(storeProvider, bundledProvider, downloaderProvider);
  }

  public static ContentStorage newInstance(ContentStore store, BundledContent bundled,
      BookDownloader downloader) {
    return new ContentStorage(store, bundled, downloader);
  }
}
