package aarnav100.developer.biblescroll.feature.community

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.community.CommunityRules.VoteStatus
import aarnav100.developer.biblescroll.data.community.VoteResult
import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Community tab: C1 this month's anonymous spotlight vote, C2 past spotlights. */
@Composable
fun CommunityScreen(modifier: Modifier = Modifier, viewModel: CommunityViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val confirm by viewModel.confirm.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val openFailed = stringResource(R.string.community_open_failed)
    val messages = mapOf(
        VoteResult.COUNTED to stringResource(R.string.community_result_counted),
        VoteResult.PENDING to stringResource(R.string.community_result_pending),
        VoteResult.REFUSED to stringResource(R.string.community_result_refused),
        VoteResult.CLOSED to stringResource(R.string.community_result_closed),
        VoteResult.ALREADY_VOTED to stringResource(R.string.community_result_already),
        VoteResult.NOT_IN_ROUND to stringResource(R.string.community_result_closed),
    )
    LaunchedEffect(viewModel) {
        viewModel.results.collect { result -> messages[result]?.let { snackbar.showSnackbar(it) } }
    }
    Box(modifier.fillMaxSize()) {
        CommunityContent(
            state = state,
            onVote = viewModel::askToVote,
            onOpen = { url -> if (!openInCustomTab(context, url)) scope.launch { snackbar.showSnackbar(openFailed) } },
            onRetry = viewModel::refresh,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    confirm?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = { Text(stringResource(R.string.community_confirm_title, target.name)) },
            text = { Text(stringResource(R.string.community_confirm_body)) },
            confirmButton = { TextButton(onClick = viewModel::confirmVote) { Text(stringResource(R.string.community_confirm_vote)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissConfirm) { Text(stringResource(R.string.community_confirm_cancel)) } },
        )
    }
}

/** Stateless C1 + C2 (PseudoLocaleScreenshotTest renders it). */
@Composable
fun CommunityContent(state: CommunityUiState, onVote: (MinistryUi) -> Unit, onOpen: (String) -> Unit, onRetry: () -> Unit) {
    val locale = Locale.getDefault()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.tab_community),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
        Text(stringResource(R.string.community_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            state.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.unavailable -> Notice(stringResource(R.string.community_unavailable)) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.community_retry)) }
            }
            state.round.isEmpty() -> Notice(
                stringResource(R.string.community_no_spotlight_title),
                stringResource(R.string.community_no_spotlight_body),
            )
            else -> CurrentRound(state, locale, onVote, onOpen)
        }
        if (state.past.isNotEmpty()) PastRounds(state.past, locale)
    }
}

@Composable
private fun CurrentRound(state: CommunityUiState, locale: Locale, onVote: (MinistryUi) -> Unit, onOpen: (String) -> Unit) {
    Text(
        stringResource(R.string.community_round_title, monthLabel(state.month, locale)),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() },
    )
    state.closesAt?.let { Text(stringResource(R.string.community_open_until, localDateTime(it, locale)), style = MaterialTheme.typography.bodyMedium) }
    state.myPick?.let { pick ->
        val text = when (state.status) {
            VoteStatus.PENDING -> stringResource(R.string.community_pick_pending, pick.name)
            VoteStatus.NOT_COUNTED -> stringResource(R.string.community_pick_not_counted, pick.name)
            else -> stringResource(R.string.community_pick_counted, pick.name)
        }
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) {
            Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
    state.round.forEach { ministry ->
        MinistryCard(
            ministry = ministry,
            isPick = ministry.id == state.myPick?.id,
            votes = state.currentTotals?.get(ministry.id),
            canVote = state.status == VoteStatus.CAN_VOTE && !state.sending,
            onVote = { onVote(ministry) },
            onOpen = onOpen,
        )
    }
    Text(stringResource(R.string.community_no_money), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MinistryCard(ministry: MinistryUi, isPick: Boolean, votes: Long?, canVote: Boolean, onVote: () -> Unit, onOpen: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ministry.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                if (isPick) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.community_your_pick), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(ministry.description, style = MaterialTheme.typography.bodyMedium)
            votes?.let {
                Text(pluralStringResource(R.plurals.community_votes, it.toInt(), it.toInt()), style = MaterialTheme.typography.labelLarge)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canVote) {
                    val cd = stringResource(R.string.community_vote_cd, ministry.name)
                    Button(onClick = onVote, modifier = Modifier.semantics { contentDescription = cd }) {
                        Icon(Icons.Outlined.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.community_vote))
                    }
                }
                LinkButton(stringResource(R.string.community_give), stringResource(R.string.community_give_cd, ministry.name)) { onOpen(ministry.giveUrl) }
                LinkButton(stringResource(R.string.community_website), stringResource(R.string.community_website_cd, ministry.name)) { onOpen(ministry.website) }
            }
        }
    }
}

@Composable
private fun LinkButton(label: String, description: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Text(label)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun PastRounds(past: List<PastRoundUi>, locale: Locale) {
    Text(
        stringResource(R.string.community_past_title),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 12.dp).semantics { heading() },
    )
    past.forEach { round ->
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(monthLabel(round.month, locale), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                val loaded = round.ministries.all { it.second != null }
                val top = round.ministries.firstOrNull()?.second
                round.ministries.forEach { (ministry, count) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ministry.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (count != null) Text(pluralStringResource(R.plurals.community_votes, count.toInt(), count.toInt()), style = MaterialTheme.typography.bodyMedium)
                    }
                    val mostVotes = loaded && count != null && count == top && count > 0
                    val myPick = ministry.id == round.myPickId
                    val marks = when {
                        mostVotes && myPick -> stringResource(R.string.two_parts, stringResource(R.string.community_most_votes), stringResource(R.string.community_your_pick))
                        mostVotes -> stringResource(R.string.community_most_votes)
                        myPick -> stringResource(R.string.community_your_pick)
                        else -> null
                    }
                    marks?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                }
                if (!loaded) Text(stringResource(R.string.community_totals_offline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Notice(title: String, body: String? = null, action: (@Composable () -> Unit)? = null) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            action?.invoke()
        }
    }
}

/** "diciembre de 2026" / "December 2026": the UTC round month, named in the app's language. */
private fun monthLabel(month: String, locale: Locale): String =
    YearMonth.parse(month).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) }

/** The round's UTC close, shown in the phone's own time zone. */
private fun localDateTime(instant: Instant, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(ZoneId.systemDefault()).format(instant)

/**
 * Opens a ministry page in a Custom Tab (the person's browser). https only. No warm-up or prefetch, so nothing loads
 * until they tap. Returns false if no browser could open it.
 */
private fun openInCustomTab(context: Context, url: String): Boolean {
    val uri = Uri.parse(url)
    if (uri.scheme != "https") return false
    return try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
