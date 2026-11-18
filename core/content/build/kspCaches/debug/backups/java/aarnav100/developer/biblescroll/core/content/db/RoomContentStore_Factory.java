package aarnav100.developer.biblescroll.core.content.db;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class RoomContentStore_Factory implements Factory<RoomContentStore> {
  private final Provider<ContentDao> daoProvider;

  private RoomContentStore_Factory(Provider<ContentDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public RoomContentStore get() {
    return newInstance(daoProvider.get());
  }

  public static RoomContentStore_Factory create(Provider<ContentDao> daoProvider) {
    return new RoomContentStore_Factory(daoProvider);
  }

  public static RoomContentStore newInstance(ContentDao dao) {
    return new RoomContentStore(dao);
  }
}
