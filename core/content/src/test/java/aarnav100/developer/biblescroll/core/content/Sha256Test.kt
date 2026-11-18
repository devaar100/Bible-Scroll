package aarnav100.developer.biblescroll.core.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class Sha256Test {

    @Test
    fun knownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex("abc".toByteArray()))
    }

    @Test
    fun copyAndHashCopiesHashesAndReportsProgress() {
        val data = ByteArray(200_000) { (it % 251).toByte() }
        val out = ByteArrayOutputStream()
        val progress = mutableListOf<Long>()
        val hashed = Sha256.copyAndHash(data.inputStream(), out) { progress += it }
        assertTrue(out.toByteArray().contentEquals(data))
        assertEquals(Hashed(Sha256.hex(data), data.size.toLong()), hashed)
        assertEquals(data.size.toLong(), progress.last())
        assertEquals(progress.sorted(), progress)
    }

    @Test
    fun checkAcceptsOnlyTheExactFile() {
        val good = Hashed("ab".repeat(32), 10)
        assertEquals(Integrity.Ok, Sha256.check(good, "AB".repeat(32), 10)) // hex case doesn't matter
        assertEquals(Integrity.HashMismatch("cd".repeat(32), good.sha256), Sha256.check(good, "cd".repeat(32), 10))
        assertEquals(Integrity.SizeMismatch(11, 10), Sha256.check(good, good.sha256, 11))
    }

    @Test
    fun truncatedDownloadFailsTheCheck() {
        val (gz, entry) = bookFile("JHN", 43, study("JHN.3.16"))
        val truncated = gz.copyOf(gz.size - 5)
        val hashed = Sha256.copyAndHash(truncated.inputStream(), ByteArrayOutputStream())
        assertTrue(Sha256.check(hashed, entry.sha256, entry.bytes) is Integrity.SizeMismatch)
        val tampered = gz.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        val tamperedHash = Sha256.copyAndHash(tampered.inputStream(), ByteArrayOutputStream())
        assertTrue(Sha256.check(tamperedHash, entry.sha256, entry.bytes) is Integrity.HashMismatch)
    }
}
