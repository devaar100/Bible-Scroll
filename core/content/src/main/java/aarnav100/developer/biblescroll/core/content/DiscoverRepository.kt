package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.DiscoverFile
import aarnav100.developer.biblescroll.core.content.model.Manifest
import aarnav100.developer.biblescroll.core.content.net.ContentApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

sealed interface DiscoverFeed {
    data object Loading : DiscoverFeed

    /** Nothing cached yet and the last refresh couldn't reach the server. */
    data object NeverLoaded : DiscoverFeed

    /** Curated verses whose book is on the device or downloadable, in feed order (may be empty). */
    data class Ready(val verses: List<DiscoverItem>) : DiscoverFeed
}

/** One Discover card: the verse and its theme label (free text from discover.json; every card has one). */
data class DiscoverItem(val key: VerseKey, val theme: String)

/** D1: the curated Discover list (discover.json), verified and cached for offline use. */
class DiscoverRepository(
    private val api: ContentApi,
    private val cache: ContentCache,
    private val store: ContentStore,
    private val json: Json,
    private val baseUrl: String,
    private val io: CoroutineDispatcher,
) {
    private data class Snapshot(val manifest: Manifest?, val discover: DiscoverFile?, val triedNetwork: Boolean)

    private val snapshot = MutableStateFlow<Snapshot?>(null)

    val feed: Flow<DiscoverFeed> = combine(snapshot, store.observeBooks()) { snap, stored ->
        when {
            snap == null -> DiscoverFeed.Loading
            snap.discover == null -> if (snap.triedNetwork) DiscoverFeed.NeverLoaded else DiscoverFeed.Loading
            else -> {
                val available = stored.map { it.bookId }.toSet() + snap.manifest?.books?.map { it.bookId }.orEmpty()
                DiscoverFeed.Ready(items(snap.discover).filter { it.key.bookId in available })
            }
        }
    }.onStart { if (snapshot.value == null) loadCached() }

    /** Fetch the current manifest + discover.json. Returns false if the network or the file let us down. */
    suspend fun refresh(): Boolean = withContext(io) {
        if (snapshot.value == null) loadCached()
        val ok = try {
            val manifestBytes = api.download(url("manifest.json")).use { it.bytes() }
            val manifest = json.decodeFromString(Manifest.serializer(), manifestBytes.decodeToString())
            if (!ContentVersions.isSupported(manifest.formatVersion, manifest.schemaVersion)) {
                false
            } else {
                val entry = manifest.discover
                val discoverBytes = api.download(url(entry.url)).use { it.bytes() }
                val check = Sha256.check(Hashed(Sha256.hex(discoverBytes), discoverBytes.size.toLong()), entry.sha256, entry.bytes)
                if (check != Integrity.Ok) {
                    false
                } else {
                    val discover = json.decodeFromString(DiscoverFile.serializer(), discoverBytes.decodeToString())
                    require(discover.formatVersion == FORMAT_VERSION) { "discover.json format ${discover.formatVersion}" }
                    cache.write(ContentCache.MANIFEST, manifestBytes)
                    cache.write(ContentCache.DISCOVER, discoverBytes)
                    snapshot.value = Snapshot(manifest, discover, triedNetwork = true)
                    true
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
        if (!ok) snapshot.value = (snapshot.value ?: Snapshot(null, null, false)).copy(triedNetwork = true)
        ok
    }

    private suspend fun loadCached() = withContext(io) {
        val manifest = cache.read(ContentCache.MANIFEST)?.let { decodeOrNull(Manifest.serializer(), it) }
        // An older cached file (format 1, no themes) is ignored: the next refresh replaces it.
        val discover = cache.read(ContentCache.DISCOVER)?.let { decodeOrNull(DiscoverFile.serializer(), it) }
            ?.takeIf { it.formatVersion == FORMAT_VERSION }
        snapshot.compareAndSet(null, Snapshot(manifest, discover, triedNetwork = false))
    }

    private fun <T> decodeOrNull(serializer: kotlinx.serialization.KSerializer<T>, bytes: ByteArray): T? =
        try {
            json.decodeFromString(serializer, bytes.decodeToString())
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun url(path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) path else baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    companion object {
        const val FORMAT_VERSION = 2

        /** Longest theme label shown; the pipeline refuses longer ones (publish.DISCOVER_THEME_MAX). */
        const val THEME_MAX = 40

        /** Cards in file order; an entry with a malformed id or a blank or over-long theme is left out. */
        fun items(file: DiscoverFile): List<DiscoverItem> = file.verses.mapNotNull { v ->
            val key = BookCodes.parse(v.id) ?: return@mapNotNull null
            val theme = v.theme.trim().takeIf { it.isNotEmpty() && it.length <= THEME_MAX } ?: return@mapNotNull null
            DiscoverItem(key, theme)
        }
    }
}
