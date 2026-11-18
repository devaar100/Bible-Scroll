package aarnav100.developer.biblescroll.core.content

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Integrity checks for downloaded and bundled book files. */
object Sha256 {
    fun hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    /**
     * Copies [input] to [output] while hashing it, reporting the running byte count after every chunk.
     * Returns the SHA-256 (lowercase hex) and the number of bytes copied.
     */
    fun copyAndHash(input: InputStream, output: OutputStream, onProgress: (Long) -> Unit = {}): Hashed {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
            output.write(buffer, 0, read)
            total += read
            onProgress(total)
        }
        return Hashed(digest.digest().toHex(), total)
    }

    /** Compare what arrived with what the manifest promised. Hashes compare case-insensitively. */
    fun check(actual: Hashed, expectedSha256: String, expectedBytes: Long): Integrity = when {
        actual.bytes != expectedBytes -> Integrity.SizeMismatch(expectedBytes, actual.bytes)
        !actual.sha256.equals(expectedSha256, ignoreCase = true) -> Integrity.HashMismatch(expectedSha256, actual.sha256)
        else -> Integrity.Ok
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

data class Hashed(val sha256: String, val bytes: Long)

sealed interface Integrity {
    data object Ok : Integrity
    data class HashMismatch(val expected: String, val actual: String) : Integrity
    data class SizeMismatch(val expected: Long, val actual: Long) : Integrity
}
