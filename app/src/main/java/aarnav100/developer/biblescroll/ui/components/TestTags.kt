package aarnav100.developer.biblescroll.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/** Tags the :baselineprofile journeys and benchmarks look up via UiAutomator (By.res). */
object TestTags {
    const val FEED_PAGER = "feed_pager"
    const val BOOK_PICKER_BUTTON = "feed_book_picker"
    const val BOOK_PICKER = "book_picker"
    const val COMPARE_SHEET = "compare_sheet"
    const val VERSE_SCRUBBER = "verse_scrubber"
}

/**
 * [testTag] exposed as an Android resource id, so UiAutomator can find it. Set on the node itself
 * because bottom sheets live in their own window and don't inherit it from the activity's root.
 */
fun Modifier.automationTag(tag: String): Modifier = semantics { testTagsAsResourceId = true }.testTag(tag)
