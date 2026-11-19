package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.Manifest
import aarnav100.developer.biblescroll.core.content.model.MinistriesFile
import aarnav100.developer.biblescroll.core.content.net.ContentApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.Duration
import java.time.Instant

/** Why there's no ministry list to show (or, with a cached list, why the last check failed). */
enum class MinistriesProblem {
    /** The CDN couldn't be reached (offline, timeout, server error). */
    OFFLINE,

    /** The manifest lists no ministries file, or the file isn't there (404). */
    NOT_PUBLISHED,

    /** A file was there but failed the hash, the format version or parsing. Never used or cached. */
    INVALID,
}

sealed interface MinistriesState {
    data object Loading : MinistriesState

    /** Nothing usable cached and the last check couldn't get a verified file. */
    data class Unavailable(val problem: MinistriesProblem) : MinistriesState

    data class Ready(val file: MinistriesFile) : MinistriesState
}

/** Where the list came from and when it was last checked (debug line, staleness). */
data class MinistriesFetchInfo(
    /** Last time the CDN manifest was read and the list confirmed current; null if never (this install). */
    val checkedAt: Instant? = null,
    /** sha256 of the cached ministries.json. */
    val sha256: String? = null,
    /** The last check's failure, if it failed (a cached list may still be shown). */
    val lastProblem: MinistriesProblem? = null,
    val refreshing: Boolean = false,
)

/**
 * C1/C2: /ministries.json from our CDN, checked against the sha256 in manifest.json and cached on disk so the
 * Community tab works offline. Same rules as DiscoverRepository: a file that fails the hash is never used or cached.
 *
 * Refresh policy ([refreshIfStale]): fetch when nothing is cached, when the last check is older than [MAX_AGE], or
 * when the manifest cached by another download (Discover, studies) pins a different ministries hash. ministries.json
 * itself is only downloaded when the manifest's hash differs from the cached file's. [refresh] always checks.
 */
class CommunityContentRepository(
    private val api: ContentApi,
    private val cache: ContentCache,
    private val json: Json,
    private val baseUrl: String,
    private val io: CoroutineDispatcher,
    private val clock: () -> Instant = Instant::now,
) {
    private val state = MutableStateFlow<MinistriesState?>(null)
    private val _info = MutableStateFlow(MinistriesFetchInfo())
    private val lock = Mutex()

    val ministries: Flow<MinistriesState> = state.filterNotNull().onStart { if (state.value == null) loadCached() }
    val info: StateFlow<MinistriesFetchInfo> = _info.asStateFlow()

    /** Checks the CDN only if [needsRefresh]. Returns null when no check was needed, else whether it worked. */
    suspend fun refreshIfStale(): Boolean? = withContext(io) {
        if (state.value == null) loadCached()
        if (needsRefresh()) refresh() else null
    }

    /** Fetch manifest.json (+ ministries.json if its hash changed). Returns false if the network or the file let us down. */
    suspend fun refresh(): Boolean = withContext(io) {
        lock.withLock {
            if (state.value == null) loadCached()
            _info.update { it.copy(refreshing = true) }
            val problem = try {
                check()
            } catch (e: CancellationException) {
                _info.update { it.copy(refreshing = false) }
                throw e
            } catch (e: IOException) {
                MinistriesProblem.OFFLINE
            } catch (e: HttpException) {
                if (e.code() == 404) MinistriesProblem.NOT_PUBLISHED else MinistriesProblem.OFFLINE
            } catch (e: SerializationException) {
                MinistriesProblem.INVALID
            } catch (e: IllegalArgumentException) {
                MinistriesProblem.INVALID
            }
            _info.update { it.copy(refreshing = false, lastProblem = problem) }
            if (problem != null && state.value !is MinistriesState.Ready) state.value = MinistriesState.Unavailable(problem)
            problem == null
        }
    }

    /** null = list current (state Ready, or Unavailable(NOT_PUBLISHED) when the manifest has none). */
    private suspend fun check(): MinistriesProblem? {
        val manifest = json.decodeFromString(Manifest.serializer(), api.download(url("manifest.json")).use { it.bytes() }.decodeToString())
        if (!ContentVersions.isSupported(manifest.formatVersion, manifest.schemaVersion)) return MinistriesProblem.INVALID
        val entry = manifest.community
        if (entry == null) {
            // Our own CDN says there is no list right now (never published, or withdrawn): don't keep showing an old one.
            cache.delete(ContentCache.MINISTRIES)
            cache.delete(ContentCache.MINISTRIES_META)
            state.value = MinistriesState.Unavailable(MinistriesProblem.NOT_PUBLISHED)
            markChecked(null)
            return null
        }
        if (entry.sha256 == _info.value.sha256 && state.value is MinistriesState.Ready) {
            markChecked(entry.sha256) // unchanged: no download
            return null
        }
        val bytes = api.download(url(entry.url)).use { it.bytes() }
        if (Sha256.check(Hashed(Sha256.hex(bytes), bytes.size.toLong()), entry.sha256, entry.bytes) != Integrity.Ok) return MinistriesProblem.INVALID
        val file = json.decodeFromString(MinistriesFile.serializer(), bytes.decodeToString())
        if (file.formatVersion != FORMAT_VERSION) return MinistriesProblem.INVALID
        cache.write(ContentCache.MINISTRIES, bytes)
        state.value = MinistriesState.Ready(file)
        markChecked(entry.sha256)
        return null
    }

    private fun needsRefresh(): Boolean {
        val info = _info.value
        val checkedAt = info.checkedAt ?: return true
        if (state.value !is MinistriesState.Ready) return true
        if (Duration.between(checkedAt, clock()) > MAX_AGE || checkedAt.isAfter(clock())) return true
        val pinned = cachedManifestHash() ?: return false
        return pinned != info.sha256
    }

    /** The ministries hash in the manifest last saved by another downloader, if any. */
    private fun cachedManifestHash(): String? = cache.read(ContentCache.MANIFEST)?.let {
        try {
            json.decodeFromString(Manifest.serializer(), it.decodeToString()).community?.sha256
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun markChecked(sha256: String?) {
        val now = clock()
        if (sha256 != null) cache.write(ContentCache.MINISTRIES_META, "$sha256\n${now.toEpochMilli()}".toByteArray())
        _info.update { it.copy(checkedAt = now, sha256 = sha256) }
    }

    private suspend fun loadCached() = withContext(io) {
        val cached = cache.read(ContentCache.MINISTRIES)?.let {
            try {
                json.decodeFromString(MinistriesFile.serializer(), it.decodeToString()).takeIf { f -> f.formatVersion == FORMAT_VERSION }
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        val meta = cache.read(ContentCache.MINISTRIES_META)?.decodeToString()?.lines()
        if (cached != null && meta != null && meta.size == 2) {
            _info.update { it.copy(sha256 = meta[0], checkedAt = meta[1].toLongOrNull()?.let(Instant::ofEpochMilli)) }
        }
        state.compareAndSet(null, cached?.let { MinistriesState.Ready(it) } ?: MinistriesState.Loading)
    }

    private fun url(path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) path else baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    companion object {
        const val FORMAT_VERSION = 1

        /** A cached list older than this is re-checked when the Community tab is shown. */
        val MAX_AGE: Duration = Duration.ofHours(6)
    }
}
