package aarnav100.developer.biblescroll.feature.community

import aarnav100.developer.biblescroll.core.content.model.Donation
import aarnav100.developer.biblescroll.data.community.CommunityRules
import aarnav100.developer.biblescroll.data.settings.AppSettings
import aarnav100.developer.biblescroll.data.settings.ThemeMode
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

/** Sample Community and History states for previews and PseudoLocaleScreenshotTest (made-up ministries). */
internal object CommunitySamples {
    val river = MinistryUi(
        "river", "River Bible Translation", "Translates Scripture into languages that have none yet, working with local churches and " +
            "trained translators so every reader can hear the Bible in their own words.",
        "https://example.org", "https://example.org/give", categories = listOf("Bible translation", "Literacy"),
    )
    val harbor = MinistryUi(
        "harbor", "Harbor Relief", "Feeds families in port towns every week and rebuilds homes after storms.",
        "https://example.org", "https://example.org/give", categories = listOf("Disaster relief"),
    )

    fun beforeVoting() = CommunityUiState(
        loading = false, month = "2026-12", closesAt = Instant.parse("2027-01-01T00:00:00Z"), daysLeft = 12,
        round = listOf(river, harbor), status = CommunityRules.VoteStatus.CAN_VOTE,
        roundState = CommunityRules.RoundState.OPEN,
    )

    fun afterVoting() = beforeVoting().copy(status = CommunityRules.VoteStatus.COUNTED, myPick = river)

    val history = HistoryUiState.Ready(
        listOf(
            Donation("2026-11", "harbor", "Harbor Relief", JsonPrimitive(300), "USD"),
            Donation("2026-10", "river", "River Bible Translation", JsonPrimitive(312.5), "USD"),
        ),
    )
}

@Composable
private fun Themed(dark: Boolean = false, content: @Composable () -> Unit) {
    BibleScrollTheme(AppSettings(themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light)) { Surface { content() } }
}

@Preview(name = "Community, before voting", widthDp = 390, heightDp = 1200)
@Composable
private fun BeforeVoting() = Themed { CommunityContent(CommunitySamples.beforeVoting(), {}, {}, {}) }

@Preview(name = "Community, after voting", widthDp = 390, heightDp = 1200)
@Composable
private fun AfterVoting() = Themed { CommunityContent(CommunitySamples.afterVoting(), {}, {}, {}) }

@Preview(name = "Community, after voting, dark", widthDp = 390, heightDp = 1200)
@Composable
private fun AfterVotingDark() = Themed(dark = true) { CommunityContent(CommunitySamples.afterVoting(), {}, {}, {}) }

@Preview(name = "History", widthDp = 390, heightDp = 600)
@Composable
private fun HistoryPreview() = Themed { HistoryContent(CommunitySamples.history, onRetry = {}) }

@Preview(name = "History, empty", widthDp = 390, heightDp = 400)
@Composable
private fun HistoryEmpty() = Themed { HistoryContent(HistoryUiState.Ready(emptyList()), onRetry = {}) }
