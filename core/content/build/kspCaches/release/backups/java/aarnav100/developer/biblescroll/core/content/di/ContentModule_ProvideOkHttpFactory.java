package aarnav100.developer.biblescroll.core.content.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
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
public final class ContentModule_ProvideOkHttpFactory implements Factory<OkHttpClient> {
  @Override
  public OkHttpClient get() {
    return provideOkHttp();
  }

  public static ContentModule_ProvideOkHttpFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static OkHttpClient provideOkHttp() {
    return Preconditions.checkNotNullFromProvides(ContentModule.INSTANCE.provideOkHttp());
  }

  private static final class InstanceHolder {
    static final ContentModule_ProvideOkHttpFactory INSTANCE = new ContentModule_ProvideOkHttpFactory();
  }
}
