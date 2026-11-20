package aarnav100.developer.biblescroll.core.content

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Community ministry card images. Downloaded with the app's own OkHttp client (no cookies, no identifiers) and kept in
 * cacheDir, which is never backed up; Coil then loads the local file (the app has no Coil network module). Only our
 * Firebase Hosting site and our project's Firebase Storage bucket are allowed ([isAllowed]); anything else is never
 * fetched and the card shows its tinted fallback. Listed in docs/network-calls.md.
 */
class MinistryImages(
    private val client: OkHttpClient,
    private val directory: File,
    private val contentBaseUrl: String,
    private val firebaseProject: String,
    private val io: CoroutineDispatcher,
) {
    /** The cached file for [url], downloading it once if needed; null if not allowed or it couldn't be fetched. */
    suspend fun file(url: String): File? = withContext(io) {
        if (!isAllowed(url)) return@withContext null
        val target = File(directory, Sha256.hex(url.toByteArray()).take(32) + ".img")
        if (target.isFile && target.length() > 0) return@withContext target
        try {
            directory.mkdirs()
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) return@withContext null
                val type = body.contentType()?.type
                if (type != null && type != "image") return@withContext null
                if (body.contentLength() > MAX_BYTES) return@withContext null
                val bytes = body.byteStream().use { it.readNBytesCompat(MAX_BYTES + 1) }
                if (bytes.isEmpty() || bytes.size > MAX_BYTES) return@withContext null
                val temp = File(directory, target.name + ".tmp")
                temp.writeBytes(bytes)
                if (!temp.renameTo(target)) temp.delete()
            }
            target.takeIf { it.isFile }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** https only, no login or port; our Hosting site (CONTENT_BASE_URL's host) or firebasestorage.googleapis.com/v0/b/<our bucket>/o/…. */
    fun isAllowed(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        if (parsed.scheme != "https" || parsed.username.isNotEmpty() || parsed.password.isNotEmpty() || parsed.port != 443) return false
        val host = parsed.host.lowercase()
        val ownHosting = setOfNotNull(contentBaseUrl.toHttpUrlOrNull()?.host?.lowercase(), "$firebaseProject.web.app", "$firebaseProject.firebaseapp.com")
        if (host in ownHosting) return true
        if (host != STORAGE_HOST) return false
        val segments = parsed.pathSegments
        val buckets = setOf("$firebaseProject.firebasestorage.app", "$firebaseProject.appspot.com")
        return segments.size >= 4 && segments[0] == "v0" && segments[1] == "b" && segments[2] in buckets && segments[3] == "o"
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() < limit) {
            val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        const val STORAGE_HOST = "firebasestorage.googleapis.com"

        /** A card image larger than this is refused (2 MB). */
        const val MAX_BYTES = 2 * 1024 * 1024
    }
}
