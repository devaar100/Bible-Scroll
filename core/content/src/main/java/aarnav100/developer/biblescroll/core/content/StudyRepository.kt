package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.Study
import aarnav100.developer.biblescroll.core.content.net.NetworkMonitor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Per-verse study state. Opening a verse whose book isn't on the device starts its download (when
 * online); progress shows as [StudyState.Loading]. Offline, the state says so instead of failing.
 */
class StudyRepository(
    private val store: ContentStore,
    private val downloader: BookDownloader,
    private val network: NetworkMonitor,
    private val json: Json,
    private val decodeDispatcher: CoroutineDispatcher,
) {
    private val reconnect = ReconnectRetry<Int>(downloader::retry)

    fun studyState(bookId: Int, chapter: Int, verse: Int): Flow<StudyState> {
        val key = VerseKey(bookId, chapter, verse)
        return combine(
            store.observeStudyJson(key),
            store.observeBook(bookId),
            downloader.state(bookId),
            network.isOnline,
        ) { studyJson, book, download, online ->
            resolve(bookId, studyJson, book, download, online)
        }
            .onStart { emit(StudyState.Loading()) }
            .distinctUntilChanged()
            .flowOn(decodeDispatcher)
    }

    /** The study if it's already on the device; never starts a download (for Discover cards). */
    fun storedStudy(bookId: Int, chapter: Int, verse: Int): Flow<Study?> =
        store.observeStudyJson(VerseKey(bookId, chapter, verse))
            .map { json -> json?.let { runCatching { this.json.decodeFromString(Study.serializer(), it) }.getOrNull() } }
            .distinctUntilChanged()
            .flowOn(decodeDispatcher)

    /** Whether this book's studies are on the phone (for Discover's "why is the study missing" line). */
    fun bookStored(bookId: Int): Flow<Boolean> = store.observeBook(bookId).map { it != null }.distinctUntilChanged()

    /**
     * True while this book's studies are downloading and not stored yet. Never starts a download (Discover cards show
     * placeholders for the study while this is true).
     */
    fun downloading(bookId: Int): Flow<Boolean> =
        combine(store.observeBook(bookId), downloader.state(bookId)) { book, download ->
            book == null && (download is DownloadState.Downloading || download == DownloadState.Done)
        }.distinctUntilChanged()

    /** Start downloading any of these books that aren't on the device yet (callers check for Wi-Fi). */
    suspend fun prefetch(bookIds: Collection<Int>) {
        val stored = store.books().map { it.bookId }.toSet()
        bookIds.filter { it !in stored }.distinct().forEach(downloader::ensure)
    }

    /** After [StudyState.NotDownloaded] with DOWNLOAD_FAILED. */
    fun retry(bookId: Int) = downloader.retry(bookId)

    private fun resolve(
        bookId: Int,
        studyJson: String?,
        book: StoredBook?,
        download: DownloadState,
        online: Boolean,
    ): StudyState {
        if (studyJson != null) {
            return try {
                StudyState.Available(json.decodeFromString(Study.serializer(), studyJson))
            } catch (e: SerializationException) {
                StudyState.Unavailable(StudyState.Unavailable.Reason.NO_STUDY)
            } catch (e: IllegalArgumentException) {
                StudyState.Unavailable(StudyState.Unavailable.Reason.NO_STUDY)
            }
        }
        // The book is here but this verse isn't in it: it has no published study (flagged or not written yet).
        if (book != null) return StudyState.Unavailable(StudyState.Unavailable.Reason.NO_STUDY)

        return when (download) {
            is DownloadState.Downloading -> StudyState.Loading(download.progress)
            DownloadState.Done -> StudyState.Loading() // imported; local storage is about to report it
            is DownloadState.Failed -> when (download.reason) {
                DownloadFailure.NOT_PUBLISHED -> StudyState.Unavailable(StudyState.Unavailable.Reason.NO_STUDY)
                DownloadFailure.APP_UPDATE_REQUIRED -> StudyState.Unavailable(StudyState.Unavailable.Reason.APP_UPDATE_REQUIRED)
                DownloadFailure.NETWORK, DownloadFailure.INTEGRITY -> when {
                    // Failed while offline and now back online: try once more by itself.
                    reconnect.onFailure(bookId, online) -> StudyState.Loading(DownloadProgress(0, null))
                    !online -> StudyState.NotDownloaded(bookId, StudyState.NotDownloaded.Reason.OFFLINE)
                    else -> StudyState.NotDownloaded(bookId, StudyState.NotDownloaded.Reason.DOWNLOAD_FAILED)
                }
            }
            DownloadState.Idle ->
                if (online) {
                    downloader.ensure(bookId) // first open of a study in this book
                    StudyState.Loading(DownloadProgress(0, null))
                } else {
                    StudyState.NotDownloaded(bookId, StudyState.NotDownloaded.Reason.OFFLINE)
                }
        }
    }
}
