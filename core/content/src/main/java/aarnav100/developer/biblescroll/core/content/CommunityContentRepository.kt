package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.Manifest
import aarnav100.developer.biblescroll.core.content.model.MinistriesFile
import aarnav100.developer.biblescroll.core.content.net.ContentApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

sealed interface MinistriesState {
    data object Loading : MinistriesState

    /** Nothing cached and the last refresh couldn't get a verified file (offline, or none published yet). */
    data object Unavailable : MinistriesState

    data class Ready(val file: MinistriesFile) : MinistriesState
}

/**
 * C1/C2: /ministries.json from our CDN, checked against the sha256 in manifest.json and cached on disk so the
 * Community tab works offline. Same rules as DiscoverRepository: a file that fails the hash is never used or cached.
 */
class CommunityContentRepository(
    private val api: ContentApi,
    private val cache: ContentCache,
    private val json: Json,
    private val baseUrl: String,
    private val io: CoroutineDispatcher,
) {
    private val state = MutableStateFlow<MinistriesState?>(null)

    val ministries: Flow<MinistriesState> = state.filterNotNull().onStart { if (state.value == null) loadCached() }

    /** Fetch manifest.json + ministries.json. Returns false if the network or the file let us down. */
    suspend fun refresh(): Boolean = withContext(io) {
        if (state.value == null) loadCached()
        val ok = try {
            val manifestBytes = api.download(url("manifest.json")).use { it.bytes() }
            val manifest = json.decodeFromString(Manifest.serializer(), manifestBytes.decodeToString())
            val entry = manifest.community
            if (!ContentVersions.isSupported(manifest.formatVersion, manifest.schemaVersion) || entry == null) {
                false
            } else {
                val bytes = api.download(url(entry.url)).use { it.bytes() }
                if (Sha256.check(Hashed(Sha256.hex(bytes), bytes.size.toLong()), entry.sha256, entry.bytes) != Integrity.Ok) {
                    false
                } else {
                    val file = json.decodeFromString(MinistriesFile.serializer(), bytes.decodeToString())
                    if (file.formatVersion != FORMAT_VERSION) {
                        false
                    } else {
                        cache.write(ContentCache.MINISTRIES, bytes)
                        state.value = MinistriesState.Ready(file)
                        true
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            false
        } catch (e: HttpException) {
            false
        } catch (e: SerializationException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
        if (!ok && state.value !is MinistriesState.Ready) state.value = MinistriesState.Unavailable
        ok
    }

    private suspend fun loadCached() = withContext(io) {
        val cached = cache.read(ContentCache.MINISTRIES)?.let {
            try {
                json.decodeFromString(MinistriesFile.serializer(), it.decodeToString()).takeIf { f -> f.formatVersion == FORMAT_VERSION }
            } catch (e: SerializationException) {
                null
            }
        }
        state.compareAndSet(null, cached?.let { MinistriesState.Ready(it) } ?: MinistriesState.Loading)
    }

    private fun url(path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) path else baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    companion object {
        const val FORMAT_VERSION = 1
    }
}
