package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.Devotional
import aarnav100.developer.biblescroll.core.content.model.DevotionalMonthFile
import aarnav100.developer.biblescroll.core.content.model.ManifestMonth
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

class InvalidMonthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Decodes a verified month file (gzip JSON) and stores it in one transaction. */
class DevotionalImporter(
    private val store: DevotionalStore,
    private val json: Json,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * [gzipped] must already be verified against [entry]'s SHA-256. Throws [InvalidMonthException] if it isn't a
     * month file this app reads, or holds days that don't belong to [entry]'s month; nothing is stored then.
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun import(gzipped: InputStream, entry: ManifestMonth, source: BookSource): StoredMonth {
        val file = try {
            GZIPInputStream(gzipped).use { json.decodeFromStream<DevotionalMonthFile>(it) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw InvalidMonthException("${entry.month}: not a readable month file (${e.message})", e)
        }
        if (!DevotionalVersions.isSupported(file.formatVersion, file.schemaVersion)) {
            throw InvalidMonthException("${entry.month}: format ${file.formatVersion}/${file.schemaVersion} isn't supported")
        }
        if (file.month != entry.month) throw InvalidMonthException("File holds month ${file.month}, manifest says ${entry.month}")
        val bad = file.devotionals.filterNot { it.belongsTo(entry.month) }
        if (bad.isNotEmpty()) throw InvalidMonthException("${entry.month}: days from another month or malformed: ${bad.map { it.id }}")
        if (file.devotionals.map { it.date }.toSet().size != file.devotionals.size) throw InvalidMonthException("${entry.month}: a day appears twice")
        val rows = file.devotionals.map { Triple(it.date, it.contentVersion, json.encodeToString(Devotional.serializer(), it)) }
        val stored = StoredMonth(entry.month, entry.contentVersion, entry.sha256, source, rows.size, rows.sumOf { it.third.length.toLong() }, clock())
        store.replaceMonth(stored, rows)
        return stored
    }

    private fun Devotional.belongsTo(month: String): Boolean =
        DATE.matches(date) && date.startsWith("$month-") && id == "devotional-$date" && verses.isNotEmpty()

    private companion object {
        val DATE = Regex("""^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$""")
    }
}

/** Month file versions this app reads (pipeline devotionals/publish.py FORMAT_VERSION / SCHEMA_VERSION). */
object DevotionalVersions {
    const val FORMAT_VERSION = 1
    const val SCHEMA_VERSION = 1

    fun isSupported(formatVersion: Int, schemaVersion: Int) = formatVersion == FORMAT_VERSION && schemaVersion == SCHEMA_VERSION
}
