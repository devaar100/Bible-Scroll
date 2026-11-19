package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.BundledDevotionalManifest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The current and next month ship in assets/devotionals/ (pipeline `devotionals bundle`). Imported when
 * missing, or when an app update ships a different month that hasn't been refreshed from the CDN since.
 */
class BundledDevotionals(
    private val assets: AssetSource,
    private val store: DevotionalStore,
    private val importer: DevotionalImporter,
    private val json: Json,
    private val io: CoroutineDispatcher,
) {
    data class Result(val imported: List<String>, val skipped: List<String>)

    suspend fun importMissingOrUpdated(): Result = withContext(io) {
        val manifest = try {
            assets.open("$DIR/manifest.json")?.use { json.decodeFromString<BundledDevotionalManifest>(it.readBytes().decodeToString()) }
        } catch (e: SerializationException) {
            null
        } ?: return@withContext Result(emptyList(), emptyList())
        val stored = store.months().associateBy { it.month }
        val imported = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        for (entry in manifest.months) {
            val current = stored[entry.month]
            val wanted = current == null || (current.source == BookSource.BUNDLED && current.contentVersion != entry.contentVersion)
            if (!wanted) continue
            val bytes = assets.open("$DIR/${entry.url}")?.use { it.readBytes() }
            if (bytes == null || Sha256.check(Hashed(Sha256.hex(bytes), bytes.size.toLong()), entry.sha256, entry.bytes) != Integrity.Ok) {
                skipped += entry.month
                continue
            }
            try {
                importer.import(bytes.inputStream(), entry, BookSource.BUNDLED)
                imported += entry.month
            } catch (e: InvalidMonthException) {
                skipped += entry.month
            }
        }
        Result(imported, skipped)
    }

    companion object {
        const val DIR = "devotionals"
    }
}
