package aarnav100.developer.biblescroll.core.content;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import kotlinx.serialization.json.Json;

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
public final class BookImporter_Factory implements Factory<BookImporter> {
  private final Provider<ContentStore> storeProvider;

  private final Provider<Json> jsonProvider;

  private BookImporter_Factory(Provider<ContentStore> storeProvider, Provider<Json> jsonProvider) {
    this.storeProvider = storeProvider;
    this.jsonProvider = jsonProvider;
  }

  @Override
  public BookImporter get() {
    return newInstance(storeProvider.get(), jsonProvider.get());
  }

  public static BookImporter_Factory create(Provider<ContentStore> storeProvider,
      Provider<Json> jsonProvider) {
    return new BookImporter_Factory(storeProvider, jsonProvider);
  }

  public static BookImporter newInstance(ContentStore store, Json json) {
    return new BookImporter(store, json);
  }
}
