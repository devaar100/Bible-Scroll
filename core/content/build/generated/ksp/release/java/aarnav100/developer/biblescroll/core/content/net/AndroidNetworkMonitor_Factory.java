package aarnav100.developer.biblescroll.core.content.net;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class AndroidNetworkMonitor_Factory implements Factory<AndroidNetworkMonitor> {
  private final Provider<Context> contextProvider;

  private AndroidNetworkMonitor_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AndroidNetworkMonitor get() {
    return newInstance(contextProvider.get());
  }

  public static AndroidNetworkMonitor_Factory create(Provider<Context> contextProvider) {
    return new AndroidNetworkMonitor_Factory(contextProvider);
  }

  public static AndroidNetworkMonitor newInstance(Context context) {
    return new AndroidNetworkMonitor(context);
  }
}
