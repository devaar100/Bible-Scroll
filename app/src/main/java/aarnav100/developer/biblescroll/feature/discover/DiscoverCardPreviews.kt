package aarnav100.developer.biblescroll.feature.discover

import aarnav100.developer.biblescroll.core.content.model.Language
import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.settings.AppSettings
import aarnav100.developer.biblescroll.data.settings.ThemeMode
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** Sample cards for previews and PseudoLocaleScreenshotTest. Text is WEB (public domain). */
internal object DiscoverSamples {
    private const val JER_17_8 = "For he will be as a tree planted by the waters, who spreads out its roots by the river, and will not fear " +
        "when heat comes, but its leaf will be green, and will not be concerned in the year of drought. It won’t cease from yielding fruit."
    private const val ROM_15_13 = "Now may the God of hope fill you with all joy and peace in believing, that you may abound in hope, in the power of the Holy Spirit."
    private const val JHN_15_12 = "“This is my commandment, that you love one another, even as I have loved you."

    val noActions = DiscoverCardActions({}, {}, {}, {}, {}, {}, {})

    fun trust(fact: Boolean = true, aiHidden: Boolean = false) = card(
        "Trust and guidance", VerseRef(24, 17, 8), "Jeremiah 17:8", JER_17_8, emptyList(),
        hook = "Jeremiah pictures the person who trusts God as a tree whose roots find water even in a dry year.",
        fact = if (fact) "Trees along the Jordan could keep their leaves through summer because their roots reached the river." else null,
        aiHidden = aiHidden, liked = true, hasNote = true,
    )

    fun hope(fact: Boolean = true) = card(
        "Hope", VerseRef(45, 15, 13), "Romans 15:13", ROM_15_13, emptyList(),
        hook = "Paul closes his teaching with a prayer that hope would overflow, not by effort but by the Spirit’s power.",
        fact = if (fact) "Paul wrote Romans to churches he had never visited, planning to stop there on his way to Spain." else null,
        bookmarked = true,
    )

    fun love(fact: Boolean = true) = card(
        "Love one another", VerseRef(43, 15, 12), "John 15:12", JHN_15_12, listOf(1 until JHN_15_12.length),
        hook = "Jesus gives one command to sum up the rest: love each other the way he has loved you.",
        fact = if (fact) "Jesus said these words at the last supper, on the night before he died." else null,
    )

    private fun card(
        theme: String, ref: VerseRef, reference: String, text: String, jesus: List<IntRange>,
        hook: String?, fact: String?, aiHidden: Boolean = false, liked: Boolean = false, bookmarked: Boolean = false, hasNote: Boolean = false,
    ) = DiscoverCardUi(
        ref = ref, theme = theme, reference = reference, translationId = "WEB",
        verse = Verse("WEB", ref, 0, text, jesus),
        hook = if (aiHidden) null else hook, fact = if (aiHidden) null else fact,
        dataLine = if (aiHidden) StudyDataLine(Language.HEBREW, 12) else null,
        aiHidden = aiHidden, liked = liked, bookmarked = bookmarked, hasNote = hasNote,
    )
}

@Composable
private fun CardPreview(dark: Boolean, card: DiscoverCardUi) {
    BibleScrollTheme(AppSettings(themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light)) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.padding(16.dp)) {
                DiscoverCard(card, DiscoverSamples.noActions, Modifier.height(624.dp))
            }
        }
    }
}

@Preview(name = "Light, long verse", widthDp = 390, heightDp = 660) @Composable private fun LongLight() = CardPreview(false, DiscoverSamples.trust())
@Preview(name = "Dark, long verse", widthDp = 390, heightDp = 660) @Composable private fun LongDark() = CardPreview(true, DiscoverSamples.trust())
@Preview(name = "Light, medium verse", widthDp = 390, heightDp = 660) @Composable private fun MediumLight() = CardPreview(false, DiscoverSamples.hope())
@Preview(name = "Light, red letters, short verse", widthDp = 390, heightDp = 660) @Composable private fun ShortLight() = CardPreview(false, DiscoverSamples.love())
@Preview(name = "Dark, red letters, short verse", widthDp = 390, heightDp = 660) @Composable private fun ShortDark() = CardPreview(true, DiscoverSamples.love())
@Preview(name = "No Did you know", widthDp = 390, heightDp = 660) @Composable private fun NoFact() = CardPreview(false, DiscoverSamples.hope(fact = false))
@Preview(name = "Hide AI studies on", widthDp = 390, heightDp = 660) @Composable private fun AiHidden() = CardPreview(false, DiscoverSamples.trust(aiHidden = true))
