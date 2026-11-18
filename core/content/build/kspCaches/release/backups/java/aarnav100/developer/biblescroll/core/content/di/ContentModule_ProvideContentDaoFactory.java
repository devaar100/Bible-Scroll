package aarnav100.developer.biblescroll.core.content.di;

import aarnav100.developer.biblescroll.core.content.db.ContentDao;
import aarnav100.developer.biblescroll.core.content.db.ContentDatabase;
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
public final class ContentModule_ProvideContentDaoFactory implements Factory<ContentDao> {
  private final Provider<ContentDatabase> dbProvider;

  private ContentModule_ProvideContentDaoFactory(Provider<ContentDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public ContentDao get() {
    return provideContentDao(dbProvider.get());
  }

  public static ContentModule_ProvideContentDaoFactory create(
      Provider<ContentDatabase> dbProvider) {
    return new ContentModule_ProvideContentDaoFactory(dbProvider);
  }

  public static ContentDao provideContentDao(ContentDatabase db) {
    return Preconditions.checkNotNullFromProvides(ContentModule.INSTANCE.provideContentDao(db));
  }
}
