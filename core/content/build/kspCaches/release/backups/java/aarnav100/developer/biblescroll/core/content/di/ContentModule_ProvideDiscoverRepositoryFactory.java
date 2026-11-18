package aarnav100.developer.biblescroll.core.content.di;

import aarnav100.developer.biblescroll.core.content.ContentStore;
import aarnav100.developer.biblescroll.core.content.DiscoverRepository;
import aarnav100.developer.biblescroll.core.content.net.ContentApi;
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
public final class ContentModule_ProvideDiscoverRepositoryFactory implements Factory<DiscoverRepository> {
  private final Provider<ContentApi> apiProvider;

  private final Provider<ContentStore> storeProvider;

  private final Provider<Json> jsonProvider;

  private final Provider<Context> contextProvider;

  private ContentModule_ProvideDiscoverRepositoryFactory(Provider<ContentApi> apiProvider,
      Provider<ContentStore> storeProvider, Provider<Json> jsonProvider,
      Provider<Context> contextProvider) {
    this.apiProvider = apiProvider;
    this.storeProvider = storeProvider;
    this.jsonProvider = jsonProvider;
    this.contextProvider = contextProvider;
  }

  @Override
  public DiscoverRepository get() {
    return provideDiscoverRepository(apiProvider.get(), storeProvider.get(), jsonProvider.get(), contextProvider.get());
  }

  public static ContentModule_ProvideDiscoverRepositoryFactory create(
      Provider<ContentApi> apiProvider, Provider<ContentStore> storeProvider,
      Provider<Json> jsonProvider, Provider<Context> contextProvider) {
    return new ContentModule_ProvideDiscoverRepositoryFactory(apiProvider, storeProvider, jsonProvider, contextProvider);
  }

  public static DiscoverRepository provideDiscoverRepository(ContentApi api, ContentStore store,
      Json json, Context context) {
    return Preconditions.checkNotNullFromProvides(ContentModule.INSTANCE.provideDiscoverRepository(api, store, json, context));
  }
}
