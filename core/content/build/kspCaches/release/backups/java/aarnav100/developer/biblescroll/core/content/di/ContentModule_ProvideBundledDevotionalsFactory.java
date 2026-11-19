package aarnav100.developer.biblescroll.core.content.di;

import aarnav100.developer.biblescroll.core.content.BundledDevotionals;
import aarnav100.developer.biblescroll.core.content.DevotionalImporter;
import aarnav100.developer.biblescroll.core.content.DevotionalStore;
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
public final class ContentModule_ProvideBundledDevotionalsFactory implements Factory<BundledDevotionals> {
  private final Provider<Context> contextProvider;

  private final Provider<DevotionalStore> storeProvider;

  private final Provider<DevotionalImporter> importerProvider;

  private final Provider<Json> jsonProvider;

  private ContentModule_ProvideBundledDevotionalsFactory(Provider<Context> contextProvider,
      Provider<DevotionalStore> storeProvider, Provider<DevotionalImporter> importerProvider,
      Provider<Json> jsonProvider) {
    this.contextProvider = contextProvider;
    this.storeProvider = storeProvider;
    this.importerProvider = importerProvider;
    this.jsonProvider = jsonProvider;
  }

  @Override
  public BundledDevotionals get() {
    return provideBundledDevotionals(contextProvider.get(), storeProvider.get(), importerProvider.get(), jsonProvider.get());
  }

  public static ContentModule_ProvideBundledDevotionalsFactory create(
      Provider<Context> contextProvider, Provider<DevotionalStore> storeProvider,
      Provider<DevotionalImporter> importerProvider, Provider<Json> jsonProvider) {
    return new ContentModule_ProvideBundledDevotionalsFactory(contextProvider, storeProvider, importerProvider, jsonProvider);
  }

  public static BundledDevotionals provideBundledDevotionals(Context context, DevotionalStore store,
      DevotionalImporter importer, Json json) {
    return Preconditions.checkNotNullFromProvides(ContentModule.INSTANCE.provideBundledDevotionals(context, store, importer, json));
  }
}
