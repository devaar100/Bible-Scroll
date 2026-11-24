package aarnav100.developer.biblescroll.core.content.db

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * How a study's JSON is stored in content.db: one zlib stream per verse, made with a preset dictionary of sample
 * studies (resources …/db/study_dictionary.bin, built by tools/build_study_dictionary.py). A small stream can't learn
 * the JSON keys and the house style from itself; the dictionary does that. The Gospels and Psalms are 33 MB as
 * JSON, about 9.6 MB stored this way, 14 MB with plain zlib (2026-11-24).
 *
 * Reading one verse inflates ~2 KB: a fraction of a millisecond, less than decoding the JSON that follows. Each
 * stream names its dictionary (zlib's DICTID, the dictionary's Adler-32), so a row made with another dictionary is
 * rejected rather than misread. Changing the dictionary needs a ContentDatabase version bump (the cache is rebuilt).
 */
internal object StudyCompression {
    /** Absolute: R8 may move this class to another package in release builds, which breaks a relative path. */
    private const val DICTIONARY = "/aarnav100/developer/biblescroll/core/content/db/study_dictionary.bin"

    /** Level 6 (zlib's default): level 9 saves nothing measurable on studies and is no faster to read. */
    private const val LEVEL = 6

    val dictionary: ByteArray by lazy {
        requireNotNull(StudyCompression::class.java.getResourceAsStream(DICTIONARY)) { "$DICTIONARY missing" }
            .use { it.readBytes() }
    }

    fun compress(json: String): ByteArray {
        val input = json.encodeToByteArray()
        val deflater = Deflater(LEVEL)
        try {
            deflater.setDictionary(dictionary)
            deflater.setInput(input)
            deflater.finish()
            val out = ByteArrayOutputStream(input.size / 3 + 64)
            val buffer = ByteArray(8 * 1024)
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer))
            }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    /** null if [data] isn't a stream this app wrote with this dictionary, or is damaged (zlib's checksum). */
    fun decompress(data: ByteArray): String? {
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(data.size * 4)
            val buffer = ByteArray(16 * 1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0) {
                    when {
                        inflater.finished() -> Unit // e.g. an empty study: done, nothing more to write
                        inflater.needsDictionary() -> {
                            if (inflater.adler != dictionaryAdler) return null // written with another dictionary
                            inflater.setDictionary(dictionary)
                        }
                        inflater.needsInput() -> return null // truncated
                        else -> return null // no progress: never loop
                    }
                } else {
                    out.write(buffer, 0, n)
                }
            }
            return out.toByteArray().decodeToString()
        } catch (e: DataFormatException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        } finally {
            inflater.end()
        }
    }

    private val dictionaryAdler: Int by lazy {
        java.util.zip.Adler32().apply { update(dictionary) }.value.toInt()
    }
}
