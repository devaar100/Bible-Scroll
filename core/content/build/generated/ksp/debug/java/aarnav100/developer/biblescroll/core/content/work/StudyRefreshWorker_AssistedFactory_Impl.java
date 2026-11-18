package aarnav100.developer.biblescroll.core.content.work;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.InstanceFactory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class StudyRefreshWorker_AssistedFactory_Impl implements StudyRefreshWorker_AssistedFactory {
  private final StudyRefreshWorker_Factory delegateFactory;

  StudyRefreshWorker_AssistedFactory_Impl(StudyRefreshWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public StudyRefreshWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<StudyRefreshWorker_AssistedFactory> create(
      StudyRefreshWorker_Factory delegateFactory) {
    return InstanceFactory.create(new StudyRefreshWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<StudyRefreshWorker_AssistedFactory> createFactoryProvider(
      StudyRefreshWorker_Factory delegateFactory) {
    return InstanceFactory.create(new StudyRefreshWorker_AssistedFactory_Impl(delegateFactory));
  }
}
