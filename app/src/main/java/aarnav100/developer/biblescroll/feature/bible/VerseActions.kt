package aarnav100.developer.biblescroll.feature.bible

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.bible.model.Verse
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Puts the verse on the clipboard. Returns true when the caller should confirm it (snackbar): only below Android 13,
 * since 13+ shows its own clipboard confirmation and a second one read as a duplicate.
 */
internal fun Context.copyVerse(verse: Verse, reference: String): Boolean {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText(reference, verse.clipText(reference)))
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
}

/** Plain-text share. Share-as-image is B10. */
internal fun Context.shareVerse(verse: Verse, reference: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, verse.clipText(reference))
    }
    startActivity(Intent.createChooser(send, getString(R.string.feed_share_chooser)))
}
