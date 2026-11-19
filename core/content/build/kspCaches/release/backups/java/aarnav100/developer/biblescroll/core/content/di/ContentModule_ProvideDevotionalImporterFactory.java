package aarnav100.developer.biblescroll.core.content.di;

import aarnav100.developer.biblescroll.core.content.DevotionalImporter;
import aarnav100.developer.biblescroll.core.content.DevotionalStore;
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
public final class ContentModule_ProvideDevotionalImporterFactory implements Factory<DevotionalImporter> {
  private final Provider<DevotionalStore> storeProvider;

  private final Provider<Json> jsonProvider;

  private ContentModule_ProvideDevotionalImporterFactory(Provider<DevotionalStore> storeProvider,
      Provider<Json> jsonProvider) {
    this.storeProvider = storeProvider;
    this.jsonProvider = jsonProvider;
  }

  @Override
  public DevotionalImporter get() {
    return provideDevotionalImporter(storeProvider.get(), jsonProvider.get());
  }

  public static ContentModule_ProvideDevotionalImporterFactory create(
      Provider<DevotionalStore> storeProvider, Provider<Json> jsonProvider) {
    return new ContentModule_ProvideDevotionalImporterFactory(storeProvider, jsonProvider);
  }

  public static DevotionalImporter provideDevotionalImporter(DevotionalStore store, Json json) {
    return Preconditions.checkNotNullFromProvides(ContentModule.INSTANCE.provideDevotionalImporter(store, json));
  }
}
