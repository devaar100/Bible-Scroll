package aarnav100.developer.biblescroll.ui.components.scrubber

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.settings.AppSettings
import aarnav100.developer.biblescroll.data.settings.ReadingBackground
import aarnav100.developer.biblescroll.data.settings.ScriptureSize
import aarnav100.developer.biblescroll.data.settings.ScrubberMode
import aarnav100.developer.biblescroll.feature.bible.VersePage
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import aarnav100.developer.biblescroll.ui.theme.ReadingTheme
import aarnav100.developer.biblescroll.ui.theme.colors
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * The scrubber over a feed page (360 x 720 dp, the track from 76 dp to 12 dp above the bottom, as in B1) for every
 * case in [Case] on every reading background. Each shot also renders the scrubber alone and checks its rightmost ink
 * (labels included, enlarged while dragging) stays left of the verse text. PNGs for review:
 *   adb pull /sdcard/Android/data/aarnav100.developer.biblescroll/files/scrubber
 */
@RunWith(Parameterized::class)
class ScrubberScreenshotTest(private val background: ReadingBackground, private val case: Case) {

    enum class Case(
        val position: Float,
        val count: Int,
        val dragging: Boolean = false,
        val fontScale: Float = 1f,
        val size: ScriptureSize = ScriptureSize.Default,
        val locale: Locale = Locale.ENGLISH,
    ) {
        First(0f, 31),
        Middle(15f, 31),
        Last(30f, 31),
        MidTransition(11.5f, 31),
        Dragging(11f, 31, dragging = true),
        Chapter31(4f, 31),
        Chapter176(88f, 176),
        // Widest label ("176"), held, at system 2.0x (labels cap at 1.2x) and Scripture "Largest".
        LargestFont(175f, 176, dragging = true, fontScale = 2f, size = ScriptureSize.Largest),
        Spanish(11f, 31, locale = Locale.forLanguageTag("es"), fontScale = 2f, size = ScriptureSize.Largest),
    }

    companion object {
        private const val FRAME = "scrubber_frame"

        @JvmStatic
        @Parameterized.Parameters(name = "{0}_{1}")
        fun cases() = ReadingBackground.entries.flatMap { bg -> Case.entries.map { arrayOf(bg, it) } }
    }

    @get:Rule
    val rule = createComposeRule()

    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val outDir = File(target.getExternalFilesDir(null), "scrubber").apply { mkdirs() }

    private val verse = Verse(
        "WEB", VerseRef(19, 119, 12), ordinal = 16_000,
        text = "Blessed are you, Yahweh. Teach me your statutes.", wordsOfJesus = emptyList(),
    )

    @Test
    fun shot() {
        val config = Configuration(target.resources.configuration).apply {
            setLocale(case.locale)
            fontScale = case.fontScale
        }
        val context = target.createConfigurationContext(config)
        val res = context.resources
        val showPage = mutableStateOf(true)
        rule.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides config,
                LocalResources provides res,
                LocalDensity provides Density(LocalDensity.current.density, case.fontScale),
            ) {
                BibleScrollTheme(AppSettings(readingBackground = background, scriptureSize = case.size)) {
                    ReadingTheme {
                        Box(Modifier.testTag(FRAME).size(360.dp, 720.dp).background(MaterialTheme.colorScheme.background)) {
                            if (showPage.value) {
                                VersePage(
                                    verse = verse,
                                    reference = res.getString(R.string.ref_verse, "Psalms", 119, 12),
                                    translationName = "World English Bible",
                                    liked = false, bookmarked = false, hasNote = false,
                                    onToggleLike = {}, onToggleBookmark = {}, onNote = {}, onShare = {}, onCopy = {}, onActions = {},
                                    textStartInset = ScrubberVisibility.textStartInsetDp(ScrubberMode.Always).dp,
                                )
                            }
                            VerseScrubber(
                                position = { case.position },
                                count = case.count,
                                labelNumber = { it + 1 },
                                stateDescription = {
                                    res.getString(R.string.scrubber_chapter_state, it + 1, case.count)
                                },
                                contentDescription = res.getString(R.string.scrubber_chapter_cd),
                                mode = ScrubberMode.Always,
                                scrolling = { false },
                                onScrub = {},
                                onScrubbingChange = {},
                                forceDragging = case.dragging,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(top = 64.dp + VerseScrubberDefaults.VerticalGap, bottom = VerseScrubberDefaults.VerticalGap),
                            )
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()

        val name = "${background.name.lowercase()}_${case.name.lowercase()}"
        val full = rule.onNodeWithTag(FRAME).captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { full.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val textLeft = rule.onAllNodes(hasContentDescription("Teach me", substring = true))
            .fetchSemanticsNodes().single().boundsInRoot.left

        // Scrubber alone: its rightmost non-background pixel must be left of the text.
        showPage.value = false
        rule.waitForIdle()
        val alone = rule.onNodeWithTag(FRAME).captureToImage().asAndroidBitmap()
        val bg = background.colors().background.toArgb()
        val inkRight = rightmostInk(alone, bg)
        assertTrue("$name: scrubber ink reaches x=$inkRight, text starts at $textLeft", inkRight < textLeft)
    }

    private fun rightmostInk(bitmap: Bitmap, bg: Int): Int {
        var right = -1
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in bitmap.width - 1 downTo right + 1) {
                if (differs(row[x], bg)) { right = x; break }
            }
        }
        return right
    }

    private fun differs(a: Int, b: Int): Boolean {
        fun ch(c: Int, s: Int) = (c shr s) and 0xFF
        return abs(ch(a, 16) - ch(b, 16)) + abs(ch(a, 8) - ch(b, 8)) + abs(ch(a, 0) - ch(b, 0)) > 12
    }
}
