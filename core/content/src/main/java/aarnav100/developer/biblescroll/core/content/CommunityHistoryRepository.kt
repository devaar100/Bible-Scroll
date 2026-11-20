package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.Donation
import aarnav100.developer.biblescroll.core.content.model.HistoryFile
import aarnav100.developer.biblescroll.core.content.model.Manifest
import aarnav100.developer.biblescroll.core.content.net.ContentApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

sealed interface HistoryState {
    data object Loading : HistoryState

    /** Nothing cached and the fetch failed (offline, or no file published yet). */
    data class Unavailable(val offline: Boolean) : HistoryState

    /** Finished months only, newest first (may be empty). */
    data class Ready(val donations: List<Donation>) : HistoryState
}

/**
 * Community donation history: /history.json from our CDN, pinned by sha256 in manifest.history and cached on disk
 * (filesDir/content-cache: not in Auto Backup, not in the export file). Fetched at most once per UTC calendar month
 * ([HistoryRules.shouldFetch]); offline, the cached copy is shown.
 */
class CommunityHistoryRepository(
    private val api: ContentApi,
    private val cache: ContentCache,
    private val json: Json,
    private val baseUrl: String,
    private val io: CoroutineDispatcher,
    private val clock: () -> Instant = Instant::now,
) {
    private val file = MutableStateFlow<HistoryFile?>(null)
    private val state = MutableStateFlow<HistoryState?>(null)
    private val lock = Mutex()
    private var lastFetch: Instant? = null

    /** Finished months only: the current month never appears, whatever the file says. */
    val history: Flow<HistoryState> = state.filterNotNull().onStart { if (state.value == null) loadCached() }

    /** Fetches only if [HistoryRules.shouldFetch] says so. Returns null when no fetch was needed, else whether it worked. */
    suspend fun refreshIfDue(): Boolean? = withContext(io) {
        lock.withLock {
            if (state.value == null) loadCached()
            val months = file.value?.donations?.map { it.month }?.toSet().orEmpty()
            if (!HistoryRules.shouldFetch(lastFetch, months, clock())) return@withLock null
            fetch()
        }
    }

    private suspend fun fetch(): Boolean = try {
        val manifest = json.decodeFromString(Manifest.serializer(), downloadText("manifest.json"))
        val entry = manifest.history
        if (entry == null) {
            // Nothing published (yet): an empty history, and no new fetch until the rule allows one.
            publish(HistoryFile(FORMAT_VERSION, emptyList()), null)
            true
        } else {
            val bytes = api.download(url(entry.url)).use { it.bytes() }
            if (Sha256.check(Hashed(Sha256.hex(bytes), bytes.size.toLong()), entry.sha256, entry.bytes) != Integrity.Ok) {
                fail(offline = false)
            } else {
                val parsed = json.decodeFromString(HistoryFile.serializer(), bytes.decodeToString())
                if (parsed.formatVersion != FORMAT_VERSION) fail(offline = false) else {
                    publish(parsed, bytes)
                    true
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        fail(offline = true)
    } catch (e: HttpException) {
        fail(offline = e.code() != 404)
    } catch (e: SerializationException) {
        fail(offline = false)
    } catch (e: IllegalArgumentException) {
        fail(offline = false)
    }

    private suspend fun downloadText(path: String): String = api.download(url(path)).use { it.bytes() }.decodeToString()

    private fun publish(parsed: HistoryFile, bytes: ByteArray?) {
        val now = clock()
        if (bytes != null) cache.write(ContentCache.HISTORY, bytes) else cache.delete(ContentCache.HISTORY)
        cache.write(ContentCache.HISTORY_META, now.toEpochMilli().toString().toByteArray())
        lastFetch = now
        file.value = parsed
        state.value = HistoryState.Ready(HistoryRules.finished(parsed.donations, now))
    }

    private fun fail(offline: Boolean): Boolean {
        // A cached copy keeps showing; only with nothing cached does the screen say why.
        if (file.value == null) state.value = HistoryState.Unavailable(offline)
        return false
    }

    private suspend fun loadCached() = withContext(io) {
        val cached = cache.read(ContentCache.HISTORY)?.let {
            try {
                json.decodeFromString(HistoryFile.serializer(), it.decodeToString()).takeIf { f -> f.formatVersion == FORMAT_VERSION }
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        lastFetch = cache.read(ContentCache.HISTORY_META)?.decodeToString()?.trim()?.toLongOrNull()?.let(Instant::ofEpochMilli)
        // A fetch that found nothing published leaves only the meta file: that's an empty history, not "never loaded".
        val known = cached ?: if (lastFetch != null) HistoryFile(FORMAT_VERSION, emptyList()) else null
        file.compareAndSet(null, known)
        state.compareAndSet(null, known?.let { HistoryState.Ready(HistoryRules.finished(it.donations, clock())) } ?: HistoryState.Loading)
        Unit
    }

    private fun url(path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) path else baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    companion object {
        const val FORMAT_VERSION = 1
    }
}

/** The history fetch and display rules, free of I/O so they can be tested. Months are UTC, like the vote rounds. */
object HistoryRules {

    /** While last month's record is missing, check again at most this often (still within the month). */
    val MISSING_RECHECK: Duration = Duration.ofDays(1)

    /**
     * Fetch when: never fetched; the last successful fetch was in an earlier UTC calendar month (or the clock went
     * back); or, within the same month, the cache lacks last month's record and the last fetch is over a day old
     * (the record is usually published a few days after the month ends).
     */
    fun shouldFetch(lastFetch: Instant?, cachedMonths: Set<String>, now: Instant): Boolean {
        lastFetch ?: return true
        if (lastFetch.isAfter(now)) return true
        val thisMonth = month(now)
        if (month(lastFetch) != thisMonth) return true
        val previous = thisMonth.minusMonths(1).toString()
        return previous !in cachedMonths && Duration.between(lastFetch, now) >= MISSING_RECHECK
    }

    /** Months strictly before the current UTC month, newest first; records with an unreadable amount are left out. */
    fun finished(donations: List<Donation>, now: Instant): List<Donation> {
        val current = month(now)
        return donations
            .filter { d -> runCatching { YearMonth.parse(d.month) }.getOrNull()?.isBefore(current) == true }
            .filter { it.amountValue != null }
            .sortedByDescending { it.month }
    }

    private fun month(instant: Instant): YearMonth = YearMonth.from(instant.atZone(ZoneOffset.UTC))
}
