package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.Manifest
import aarnav100.developer.biblescroll.core.content.model.ManifestBook
import aarnav100.developer.biblescroll.core.content.net.ContentApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.File
import java.io.IOException

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progress: DownloadProgress) : DownloadState
    data class Failed(val reason: DownloadFailure) : DownloadState

    /**
     * Imported. Kept (rather than going back to Idle) so a screen that sees the store a moment later than
     * this state doesn't start the same download again. Cleared by [BookDownloader.forget].
     */
    data object Done : DownloadState
}

data class DownloadProgress(val bytesRead: Long, val totalBytes: Long?) {
    /** 0..1, or null before the size is known. */
    val fraction: Float? get() = totalBytes?.takeIf { it > 0 }?.let { (bytesRead.toFloat() / it).coerceIn(0f, 1f) }
}

enum class DownloadFailure {
    /** No connection, timeout, or a server error: worth retrying. */
    NETWORK,
    /** The file didn't match its SHA-256/size, or couldn't be read. Nothing was imported. */
    INTEGRITY,
    /** The manifest doesn't list this book: there are no published studies for it. */
    NOT_PUBLISHED,
    /** The content is in a newer format than this app reads. */
    APP_UPDATE_REQUIRED,
}

/**
 * Fetches the manifest and book files, verifies each file's SHA-256 and size before importing, and keeps a
 * per-book download state. Concurrent requests for the same book share one download.
 */
class BookDownloader(
    private val api: ContentApi,
    private val store: ContentStore,
    private val importer: BookImporter,
    private val tempDir: File,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val baseUrl: String,
) {
    private val states = MutableStateFlow<Map<Int, DownloadState>>(emptyMap())

    fun state(bookId: Int): Flow<DownloadState> = states.map { it[bookId] ?: DownloadState.Idle }.distinctUntilChanged()

    /** Start downloading [bookId] unless it's already downloading or has failed (use [retry] for that). */
    fun ensure(bookId: Int) {
        if (!claim(bookId, retrying = false)) return
        scope.launch { finish(bookId, downloadLatest(bookId)) }
    }

    /** Clear a failure and try again. */
    fun retry(bookId: Int) {
        if (!claim(bookId, retrying = true)) return
        scope.launch { finish(bookId, downloadLatest(bookId)) }
    }

    private fun claim(bookId: Int, retrying: Boolean): Boolean {
        var claimed = false
        states.update { current ->
            val state = current[bookId]
            val free = state == null || state is DownloadState.Idle || (retrying && state is DownloadState.Failed)
            if (!free) return@update current
            claimed = true
            current + (bookId to DownloadState.Downloading(DownloadProgress(0, null)))
        }
        return claimed
    }

    private fun finish(bookId: Int, failure: DownloadFailure?) {
        states.update { it + (bookId to (if (failure == null) DownloadState.Done else DownloadState.Failed(failure))) }
    }

    /** Forget past downloads of these books (after they're removed from the device), so they can download again. */
    fun forget(bookIds: Collection<Int>) {
        states.update { it - bookIds.toSet() }
    }

    /** Download the manifest's current copy of one book. Returns null on success. */
    private suspend fun downloadLatest(bookId: Int): DownloadFailure? {
        val manifest = when (val m = fetchManifest()) {
            is ManifestResult.Ok -> m.manifest
            is ManifestResult.Failed -> return m.reason
        }
        val entry = manifest.books.firstOrNull { it.bookId == bookId } ?: return DownloadFailure.NOT_PUBLISHED
        return download(entry) { read -> report(bookId, read, entry.bytes) }
    }

    private fun report(bookId: Int, read: Long, total: Long) {
        states.update { it + (bookId to DownloadState.Downloading(DownloadProgress(read, total))) }
    }

    sealed interface ManifestResult {
        data class Ok(val manifest: Manifest) : ManifestResult
        data class Failed(val reason: DownloadFailure) : ManifestResult
    }

    suspend fun fetchManifest(): ManifestResult = try {
        val manifest = withContext(io) { api.manifest() }
        if (ContentVersions.isSupported(manifest.formatVersion, manifest.schemaVersion)) ManifestResult.Ok(manifest)
        else ManifestResult.Failed(DownloadFailure.APP_UPDATE_REQUIRED)
    } catch (e: CancellationException) {
        throw e
    } catch (e: SerializationException) {
        ManifestResult.Failed(DownloadFailure.INTEGRITY)
    } catch (e: IOException) {
        ManifestResult.Failed(DownloadFailure.NETWORK)
    } catch (e: HttpException) {
        ManifestResult.Failed(DownloadFailure.NETWORK)
    }

    /**
     * Download [entry] to a temp file while hashing it, check SHA-256 and size, then import. The temp file
     * is always deleted. Returns null on success.
     */
    suspend fun download(entry: ManifestBook, onProgress: (Long) -> Unit = {}): DownloadFailure? = withContext(io) {
        tempDir.mkdirs()
        val temp = File(tempDir, "${entry.book}-${entry.contentVersion}.part")
        try {
            val hashed = api.download(resolve(entry.url)).use { body ->
                temp.outputStream().buffered().use { out -> Sha256.copyAndHash(body.byteStream(), out, onProgress) }
            }
            if (Sha256.check(hashed, entry.sha256, entry.bytes) != Integrity.Ok) return@withContext DownloadFailure.INTEGRITY
            temp.inputStream().buffered().use { importer.import(it, entry, BookSource.DOWNLOADED) }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: InvalidBookException) {
            DownloadFailure.INTEGRITY
        } catch (e: IOException) {
            DownloadFailure.NETWORK
        } catch (e: HttpException) {
            DownloadFailure.NETWORK
        } finally {
            temp.delete()
        }
    }

    /**
     * Weekly refresh: update only books already on the device whose version changed, and remove downloaded
     * books the manifest no longer lists. Returns the first network failure (to retry later), else null.
     */
    suspend fun refreshStoredBooks(): DownloadFailure? {
        val manifest = when (val m = fetchManifest()) {
            is ManifestResult.Ok -> m.manifest
            is ManifestResult.Failed -> return m.reason
        }
        val plan = ContentVersions.refreshPlan(store.books(), manifest.books)
        plan.remove.forEach { store.removeBook(it.bookId) }
        forget(plan.remove.map { it.bookId })
        var networkFailure: DownloadFailure? = null
        for (entry in plan.download) {
            if (download(entry) == DownloadFailure.NETWORK) networkFailure = DownloadFailure.NETWORK
        }
        return networkFailure
    }

    private fun resolve(url: String): String =
        if (url.startsWith("http://") || url.startsWith("https://")) url else baseUrl.trimEnd('/') + "/" + url.trimStart('/')
}
