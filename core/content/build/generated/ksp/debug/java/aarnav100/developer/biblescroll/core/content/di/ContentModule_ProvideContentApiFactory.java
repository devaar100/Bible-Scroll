package aarnav100.developer.biblescroll.core.content.di;

import aarnav100.developer.biblescroll.core.content.net.ContentApi;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import kotlinx.serialization.json.Json;
import okhttp3.OkHttpClient;

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
public final class ContentModule_ProvideContentApiFactory implements Factory<ContentApi> {
  private final Provider<OkHttpClient> clientProvider;

  private final Provider<Json> jsonProvider;

  private ContentModule_ProvideContentApiFactory(Provider<OkHttpClient> clientProvider,
      Provider<Json> jsonProvider) {
    this.clientProvider = clientProvider;
    this.jsonProvider = jsonProvider;
  }

  @Override
  public ContentApi get() {
    return provideContentApi(clientProvider.get(), jsonProvider.get());
  }

  public static ContentModule_ProvideContentApiFactory create(Provider<OkHttpClient> clientProvider,
      Provider<Json> jsonProvider) {
    return new ContentModule_ProvideContentApiFactory(clientProvider, jsonProvider);
  }

  public static ContentApi provideContentApi(OkHttpClient client, Json json) {
    return Preconditions.checkNotNullFromProvides(ContentModule.INSTANCE.provideContentApi(client, json));
  }
}
