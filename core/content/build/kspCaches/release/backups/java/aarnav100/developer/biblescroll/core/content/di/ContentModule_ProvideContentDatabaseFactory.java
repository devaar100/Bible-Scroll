package aarnav100.developer.biblescroll.core.content.di;

import aarnav100.developer.biblescroll.core.content.db.ContentDatabase;
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
public final class ContentModule_ProvideContentDatabaseFactory implements Factory<ContentDatabase> {
  private final Provider<Context> contextProvider;

  private ContentModule_ProvideContentDatabaseFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public ContentDatabase get() {
    return provideContentDatabase(contextProvider.get());
  }

  public static ContentModule_ProvideContentDatabaseFactory create(
      Provider<Context> contextProvider) {
    return new ContentModule_ProvideContentDatabaseFactory(contextProvider);
  }

  public static ContentDatabase provideContentDatabase(Context context) {
    return Preconditions.checkNotNullFromProvides(ContentModule.INSTANCE.provideContentDatabase(context));
  }
}
