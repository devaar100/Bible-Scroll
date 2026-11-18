package aarnav100.developer.biblescroll.feature.bible

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.bible.model.Verse
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

/**
 * Puts the verse on the clipboard. Confirmation (snackbar + haptic) is the caller's job; no toast,
 * since Android 13+ already shows its own clipboard confirmation.
 */
internal fun Context.copyVerse(verse: Verse, reference: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(reference, verse.clipText(reference)))
}

/** Plain-text share. Share-as-image is B10. */
internal fun Context.shareVerse(verse: Verse, reference: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, verse.clipText(reference))
    }
    startActivity(Intent.createChooser(send, getString(R.string.feed_share_chooser)))
}
