package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.Manifest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Versions for S8 Legal: the last content release this phone fetched, and how many study books it holds. */
class ContentInfo(
    private val cache: ContentCache,
    private val store: ContentStore,
    private val json: Json,
    private val io: CoroutineDispatcher,
) {
    /** manifest.release of the last manifest fetched (Discover or Community refresh), or null if none yet. */
    suspend fun release(): String? = withContext(io) {
        cache.read(ContentCache.MANIFEST)?.let {
            try {
                json.decodeFromString(Manifest.serializer(), it.decodeToString()).release
            } catch (e: SerializationException) {
                null
            }
        }
    }

    suspend fun storedBookCount(): Int = store.books().size
}
