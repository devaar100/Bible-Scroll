package aarnav100.developer.biblescroll.core.content

import java.io.File

/**
 * The last manifest.json, discover.json and ministries.json fetched, kept on disk so Discover and Community work offline. Writes are
 * atomic (temp file + rename), so a crash never leaves a half-written file.
 */
class ContentCache(private val directory: File) {
    fun read(name: String): ByteArray? = File(directory, name).takeIf { it.isFile }?.readBytes()

    fun write(name: String, bytes: ByteArray) {
        directory.mkdirs()
        val temp = File(directory, "$name.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(File(directory, name))) {
            temp.delete()
            error("Couldn't save $name")
        }
    }

    companion object {
        const val MANIFEST = "manifest.json"
        const val DISCOVER = "discover.json"
        const val MINISTRIES = "ministries.json"
    }
}
