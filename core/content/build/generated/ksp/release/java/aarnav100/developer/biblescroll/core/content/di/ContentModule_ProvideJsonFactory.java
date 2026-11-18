package aarnav100.developer.biblescroll.core.content.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class ContentModule_ProvideJsonFactory implements Factory<Json> {
  @Override
  public Json get() {
    return provideJson();
  }

  public static ContentModule_ProvideJsonFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static Json provideJson() {
    return Preconditions.checkNotNullFromProvides(ContentModule.INSTANCE.provideJson());
  }

  private static final class InstanceHolder {
    static final ContentModule_ProvideJsonFactory INSTANCE = new ContentModule_ProvideJsonFactory();
  }
}
