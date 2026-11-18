package aarnav100.developer.biblescroll.core.content

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Settings → Storage: what's on the device, and removing downloads (bundled books stay). */
class ContentStorage @Inject constructor(
    private val store: ContentStore,
    private val bundled: BundledContent,
    private val downloader: BookDownloader,
) {
    val books: Flow<List<StoredBook>> = store.observeBooks()

    /**
     * Deletes every downloaded book. A bundled book that had been refreshed from the CDN goes back to the
     * copy in the app's assets. Returns how many books were removed.
     */
    suspend fun removeDownloaded(): Int {
        val downloaded = store.books().filter { it.source == BookSource.DOWNLOADED }
        downloaded.forEach { store.removeBook(it.bookId) }
        downloader.forget(downloaded.map { it.bookId })
        bundled.importMissingOrUpdated()
        return downloaded.size
    }
}
