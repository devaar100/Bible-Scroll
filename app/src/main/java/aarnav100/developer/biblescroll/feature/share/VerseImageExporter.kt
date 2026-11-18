package aarnav100.developer.biblescroll.feature.share

import aarnav100.developer.biblescroll.R
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Writes rendered verse cards to the share cache or the user's Pictures folder. */
@Singleton
class VerseImageExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val shareDir get() = File(context.cacheDir, SHARE_DIR)

    /** Writes [bitmap] to cacheDir and returns a FileProvider URI other apps can read. */
    suspend fun writeShareFile(bitmap: Bitmap, baseName: String): Uri = withContext(Dispatchers.IO) {
        val dir = shareDir.apply { mkdirs() }
        // Old cards are only needed while the receiving app reads them; keep the folder from growing.
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "${baseName.toSafeFileName()}.png")
        file.outputStream().use { bitmap.writePng(it) }
        FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
    }

    /** Android Sharesheet with the image plus the verse as text (for apps that take both). */
    fun shareIntent(uri: Uri, text: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            // ClipData carries the read grant through the chooser and gives it a thumbnail.
            clipData = android.content.ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, context.getString(R.string.share_image_chooser))
    }

    /**
     * Saves to Pictures/<app name>. API 29+ uses MediaStore and needs no permission; on 26-28 the
     * caller must already hold WRITE_EXTERNAL_STORAGE.
     */
    suspend fun saveToPictures(bitmap: Bitmap, baseName: String) = withContext(Dispatchers.IO) {
        val name = "${baseName.toSafeFileName()}-${System.currentTimeMillis()}.png"
        val folder = context.getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveScoped(bitmap, name, folder)
        else saveLegacy(bitmap, name, folder)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveScoped(bitmap: Bitmap, name: String, folder: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$folder")
            // Hidden from galleries until fully written.
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { bitmap.writePng(it) } ?: throw IOException("No output stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    @Suppress("DEPRECATION") // getExternalStoragePublicDirectory is the API 26-28 way.
    private fun saveLegacy(bitmap: Bitmap, name: String, folder: String) {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), folder)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Can't create $dir")
        val file = File(dir, name)
        file.outputStream().use { bitmap.writePng(it) }
        // Make it show up in the gallery.
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
    }

    private fun Bitmap.writePng(out: OutputStream) {
        if (!compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("PNG encode failed")
    }

    private companion object {
        const val SHARE_DIR = "shared_cards"
        const val AUTHORITY_SUFFIX = ".fileprovider"
    }
}

/** "John 3:16" -> "John-3-16". */
internal fun String.toSafeFileName(): String =
    replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifEmpty { "verse" }
