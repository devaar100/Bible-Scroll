package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.ManifestBook

/**
 * contentVersion is a hash prefix of the book file, not a counter: "newer" can't be computed, only
 * "different". The manifest is always the source of truth for what's current.
 */
object ContentVersions {
    /** The published file layout this app can read. A new formatVersion means a breaking change. */
    const val FORMAT_VERSION = 1

    /** Newest study schema this app's models understand (older ones are a subset). */
    const val MAX_SCHEMA_VERSION = 2

    fun isSupported(formatVersion: Int, schemaVersion: Int): Boolean =
        formatVersion == FORMAT_VERSION && schemaVersion in 1..MAX_SCHEMA_VERSION

    /** Whether the stored copy of a book differs from the manifest's. */
    fun needsUpdate(stored: StoredBook, latest: ManifestBook): Boolean =
        stored.contentVersion != latest.contentVersion || !stored.sha256.equals(latest.sha256, ignoreCase = true)

    /**
     * What a background refresh should do: re-download only books already on the device whose version
     * changed, and drop downloaded books the manifest no longer lists (withdrawn content). Bundled books
     * missing from the manifest stay: they ship with the app.
     */
    fun refreshPlan(stored: List<StoredBook>, manifest: List<ManifestBook>): RefreshPlan {
        val latest = manifest.associateBy { it.bookId }
        return RefreshPlan(
            download = stored.mapNotNull { book -> latest[book.bookId]?.takeIf { needsUpdate(book, it) } },
            remove = stored.filter { it.bookId !in latest && it.source == BookSource.DOWNLOADED },
        )
    }
}

data class RefreshPlan(val download: List<ManifestBook>, val remove: List<StoredBook>)
