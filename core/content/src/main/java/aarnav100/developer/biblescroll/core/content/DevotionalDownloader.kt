package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.ManifestMonth
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

/**
 * Month files for devotionals, the same way [BookDownloader] fetches books: manifest first, download to a temp
 * file while hashing, check SHA-256 and size, then import. One shared download per month; states per month.
 */
class DevotionalDownloader(
    private val api: ContentApi,
    private val store: DevotionalStore,
    private val importer: DevotionalImporter,
    private val tempDir: File,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val baseUrl: String,
) {
    private val states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())

    fun state(month: String): Flow<DownloadState> = states.map { it[month] ?: DownloadState.Idle }.distinctUntilChanged()

    fun ensure(month: String) {
        if (claim(month, retrying = false)) scope.launch { finish(month, downloadLatest(month)) }
    }

    fun retry(month: String) {
        if (claim(month, retrying = true)) scope.launch { finish(month, downloadLatest(month)) }
    }

    private fun claim(month: String, retrying: Boolean): Boolean {
        var claimed = false
        states.update { current ->
            val state = current[month]
            val free = state == null || state is DownloadState.Idle || (retrying && state is DownloadState.Failed)
            if (!free) return@update current
            claimed = true
            current + (month to DownloadState.Downloading(DownloadProgress(0, null)))
        }
        return claimed
    }

    private fun finish(month: String, failure: DownloadFailure?) {
        states.update { it + (month to (if (failure == null) DownloadState.Done else DownloadState.Failed(failure))) }
    }

    private suspend fun months(): Pair<List<ManifestMonth>?, DownloadFailure?> = try {
        val manifest = withContext(io) { api.manifest() }
        if (!ContentVersions.isSupported(manifest.formatVersion, manifest.schemaVersion)) null to DownloadFailure.APP_UPDATE_REQUIRED
        else manifest.devotionals.months to null
    } catch (e: CancellationException) {
        throw e
    } catch (e: SerializationException) {
        null to DownloadFailure.INTEGRITY
    } catch (e: IOException) {
        null to DownloadFailure.NETWORK
    } catch (e: HttpException) {
        null to DownloadFailure.NETWORK
    }

    private suspend fun downloadLatest(month: String): DownloadFailure? {
        val (months, failure) = months()
        if (months == null) return failure
        val entry = months.firstOrNull { it.month == month } ?: return DownloadFailure.NOT_PUBLISHED
        return download(entry) { read ->
            states.update { it + (month to DownloadState.Downloading(DownloadProgress(read, entry.bytes))) }
        }
    }

    /** Downloads, verifies and imports [entry]. Returns null on success. The temp file is always deleted. */
    suspend fun download(entry: ManifestMonth, onProgress: (Long) -> Unit = {}): DownloadFailure? = withContext(io) {
        tempDir.mkdirs()
        val temp = File(tempDir, "devotionals-${entry.month}-${entry.contentVersion}.part")
        try {
            val hashed = api.download(resolve(entry.url)).use { body ->
                temp.outputStream().buffered().use { out -> Sha256.copyAndHash(body.byteStream(), out, onProgress) }
            }
            if (Sha256.check(hashed, entry.sha256, entry.bytes) != Integrity.Ok) return@withContext DownloadFailure.INTEGRITY
            temp.inputStream().buffered().use { importer.import(it, entry, BookSource.DOWNLOADED) }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: InvalidMonthException) {
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
     * Weekly refresh: update months already on the device whose version changed; remove downloaded months the
     * manifest no longer lists (bundled ones stay). Returns a network failure to retry, else null.
     */
    suspend fun refreshStoredMonths(): DownloadFailure? {
        val (months, failure) = months()
        if (months == null) return failure
        val latest = months.associateBy { it.month }
        var networkFailure: DownloadFailure? = null
        for (stored in store.months()) {
            val entry = latest[stored.month]
            when {
                entry == null -> if (stored.source == BookSource.DOWNLOADED) {
                    store.removeMonth(stored.month)
                    states.update { it - stored.month }
                }
                entry.contentVersion != stored.contentVersion ->
                    if (download(entry) == DownloadFailure.NETWORK) networkFailure = DownloadFailure.NETWORK
            }
        }
        return networkFailure
    }

    private fun resolve(url: String): String =
        if (url.startsWith("http://") || url.startsWith("https://")) url else baseUrl.trimEnd('/') + "/" + url.trimStart('/')
}
