package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.BundledManifest
import android.content.res.AssetManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream

/** Reads files from the app's assets (an interface so tests can fake it). */
fun interface AssetSource {
    /** null if the asset doesn't exist. */
    fun open(path: String): InputStream?
}

class AndroidAssetSource(private val assets: AssetManager) : AssetSource {
    override fun open(path: String): InputStream? = try {
        assets.open(path)
    } catch (e: FileNotFoundException) {
        null
    }
}

/**
 * The Gospels and Psalms ship in assets/studies/ (copied there by `pipeline bundle`). Imported when
 * missing, or when an app update ships a different bundle for a book that hasn't been refreshed from the
 * CDN since. A CDN-refreshed copy is kept: the weekly refresh keeps it current.
 */
class BundledContent(
    private val assets: AssetSource,
    private val store: ContentStore,
    private val importer: BookImporter,
    private val json: Json,
    private val io: CoroutineDispatcher,
) {
    /** Returns the codes of books imported. Never throws for a bad bundle: it's skipped (logged by the caller). */
    suspend fun importMissingOrUpdated(): ImportResult = withContext(io) {
        val manifest = assets.open("$DIR/manifest.json")?.use { json.decodeFromString<BundledManifest>(it.readBytes().decodeToString()) }
            ?: return@withContext ImportResult(emptyList(), emptyList())
        val stored = store.books().associateBy { it.bookId }
        val imported = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        for (entry in manifest.books) {
            val current = stored[entry.bookId]
            val wanted = current == null ||
                (current.source == BookSource.BUNDLED && ContentVersions.needsUpdate(current, entry))
            if (!wanted) continue
            val bytes = assets.open("$DIR/${entry.url}")?.use { it.readAll() }
            if (bytes == null || Sha256.check(Hashed(Sha256.hex(bytes), bytes.size.toLong()), entry.sha256, entry.bytes) != Integrity.Ok) {
                skipped += entry.book
                continue
            }
            try {
                importer.import(bytes.inputStream(), entry, BookSource.BUNDLED)
                imported += entry.book
            } catch (e: InvalidBookException) {
                skipped += entry.book
            }
        }
        ImportResult(imported, skipped)
    }

    /** Book ids that ship with the app (for "Remove downloaded studies": these come back from assets). */
    suspend fun bundledBookIds(): Set<Int> = withContext(io) {
        assets.open("$DIR/manifest.json")?.use { json.decodeFromString<BundledManifest>(it.readBytes().decodeToString()) }
            ?.books?.map { it.bookId }?.toSet().orEmpty()
    }

    data class ImportResult(val imported: List<String>, val skipped: List<String>)

    private fun InputStream.readAll(): ByteArray = ByteArrayOutputStream().also { copyTo(it) }.toByteArray()

    companion object {
        const val DIR = "studies"
    }
}
