package aarnav100.developer.biblescroll.core.content.di;

import aarnav100.developer.biblescroll.core.content.BookImporter;
import aarnav100.developer.biblescroll.core.content.BundledContent;
import aarnav100.developer.biblescroll.core.content.ContentStore;
import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import kotlinx.serialization.json.Json;

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
public final class ContentModule_ProvideBundledContentFactory implements Factory<BundledContent> {
  private final Provider<Context> contextProvider;

  private final Provider<ContentStore> storeProvider;

  private final Provider<BookImporter> importerProvider;

  private final Provider<Json> jsonProvider;

  private ContentModule_ProvideBundledContentFactory(Provider<Context> contextProvider,
      Provider<ContentStore> storeProvider, Provider<BookImporter> importerProvider,
      Provider<Json> jsonProvider) {
    this.contextProvider = contextProvider;
    this.storeProvider = storeProvider;
    this.importerProvider = importerProvider;
    this.jsonProvider = jsonProvider;
  }

  @Override
  public BundledContent get() {
    return provideBundledContent(contextProvider.get(), storeProvider.get(), importerProvider.get(), jsonProvider.get());
  }

  public static ContentModule_ProvideBundledContentFactory create(Provider<Context> contextProvider,
      Provider<ContentStore> storeProvider, Provider<BookImporter> importerProvider,
      Provider<Json> jsonProvider) {
    return new ContentModule_ProvideBundledContentFactory(contextProvider, storeProvider, importerProvider, jsonProvider);
  }

  public static BundledContent provideBundledContent(Context context, ContentStore store,
      BookImporter importer, Json json) {
    return Preconditions.checkNotNullFromProvides(ContentModule.INSTANCE.provideBundledContent(context, store, importer, json));
  }
}
