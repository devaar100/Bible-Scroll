package aarnav100.developer.biblescroll.core.content.work;

import aarnav100.developer.biblescroll.core.content.BookDownloader;
import aarnav100.developer.biblescroll.core.content.DevotionalDownloader;
import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
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
public final class StudyRefreshWorker_Factory {
  private final Provider<BookDownloader> downloaderProvider;

  private final Provider<DevotionalDownloader> devotionalsProvider;

  private StudyRefreshWorker_Factory(Provider<BookDownloader> downloaderProvider,
      Provider<DevotionalDownloader> devotionalsProvider) {
    this.downloaderProvider = downloaderProvider;
    this.devotionalsProvider = devotionalsProvider;
  }

  public StudyRefreshWorker get(Context context, WorkerParameters params) {
    return newInstance(context, params, downloaderProvider.get(), devotionalsProvider.get());
  }

  public static StudyRefreshWorker_Factory create(Provider<BookDownloader> downloaderProvider,
      Provider<DevotionalDownloader> devotionalsProvider) {
    return new StudyRefreshWorker_Factory(downloaderProvider, devotionalsProvider);
  }

  public static StudyRefreshWorker newInstance(Context context, WorkerParameters params,
      BookDownloader downloader, DevotionalDownloader devotionals) {
    return new StudyRefreshWorker(context, params, downloader, devotionals);
  }
}
