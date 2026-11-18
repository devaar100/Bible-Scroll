package aarnav100.developer.biblescroll.feature.bible

import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** TalkBack contract for a feed page: one item, read in order, with every gesture as a custom action. */
class VersePageAccessibilityTest {

    @get:Rule
    val rule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun setPage(liked: Boolean = false, bookmarked: Boolean = false, hasNote: Boolean = false) {
        rule.setContent {
            BibleScrollTheme {
                VersePage(
                    verse = Verse("WEB", VerseRef(1, 1, 1), 1, "In the beginning, God created the heavens and the earth", emptyList()),
                    reference = "Genesis 1:1",
                    translationName = "World English Bible",
                    liked = liked,
                    bookmarked = bookmarked,
                    hasNote = hasNote,
                    onToggleLike = { calls += "like" },
                    onToggleBookmark = { calls += "bookmark" },
                    onNote = { calls += "note" },
                    onShare = { calls += "share" },
                    onCopy = { calls += "copy" },
                    onActions = { calls += "actions" },
                )
            }
        }
    }

    private val spoken = "In the beginning, God created the heavens and the earth. Genesis 1:1, World English Bible"

    @Test
    fun verseAndReferenceReadAsOneItem() {
        setPage()
        rule.onNodeWithContentDescription(spoken).assertExists()
        // Not separately focusable: the text and the reference only exist inside that one item.
        rule.onAllNodesWithText("Genesis 1:1").assertCountEquals(0)
    }

    @Test
    fun everyActionIsACustomAction() {
        setPage()
        val actions = rule.onNodeWithContentDescription(spoken).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(
            listOf("Like", "Bookmark verse", "Add note", "Copy verse", "Share", "Verse actions"),
            actions.map { it.label },
        )
        rule.runOnIdle { actions.forEach { it.action() } }
        assertEquals(listOf("like", "bookmark", "note", "copy", "share", "actions"), calls)
    }

    @Test
    fun actionLabelsFollowState() {
        setPage(liked = true, bookmarked = true, hasNote = true)
        val labels = rule.onNodeWithContentDescription(spoken).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("Unlike", "Remove bookmark", "Edit note", "Copy verse", "Share", "Verse actions"), labels)
    }

    @Test
    fun railButtonsAreAtLeast48dp() {
        setPage()
        listOf("Like", "Note", "Share").forEach { label ->
            rule.onNodeWithContentDescription(label).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
    }
}
