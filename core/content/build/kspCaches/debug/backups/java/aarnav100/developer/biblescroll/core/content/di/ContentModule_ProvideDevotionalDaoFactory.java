package aarnav100.developer.biblescroll.core.content.di;

import aarnav100.developer.biblescroll.core.content.db.ContentDatabase;
import aarnav100.developer.biblescroll.core.content.db.DevotionalDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class ContentModule_ProvideDevotionalDaoFactory implements Factory<DevotionalDao> {
  private final Provider<ContentDatabase> dbProvider;

  private ContentModule_ProvideDevotionalDaoFactory(Provider<ContentDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public DevotionalDao get() {
    return provideDevotionalDao(dbProvider.get());
  }

  public static ContentModule_ProvideDevotionalDaoFactory create(
      Provider<ContentDatabase> dbProvider) {
    return new ContentModule_ProvideDevotionalDaoFactory(dbProvider);
  }

  public static DevotionalDao provideDevotionalDao(ContentDatabase db) {
    return Preconditions.checkNotNullFromProvides(ContentModule.INSTANCE.provideDevotionalDao(db));
  }
}
