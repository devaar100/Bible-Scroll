package aarnav100.developer.biblescroll.feature.community

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.community.CommunityRules
import aarnav100.developer.biblescroll.ui.theme.Literata
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Church
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import java.io.File
import aarnav100.developer.biblescroll.data.community.CommunityRules.RoundState
import aarnav100.developer.biblescroll.data.community.CommunityRules.VoteStatus
import aarnav100.developer.biblescroll.data.community.VoteResult
import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.text.format.DateFormat
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Community tab: C1 this month's anonymous vote for the month's donation, with no counts anywhere (decision 23).
 * "View history" opens the donation History. Pull down to refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(onOpenHistory: () -> Unit, modifier: Modifier = Modifier, viewModel: CommunityViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val confirm by viewModel.confirm.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showInfo by rememberSaveable { mutableStateOf(false) }
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
    // Every time the tab is shown: re-check the list if it's missing, over 6 hours old, or its hash changed.
    LifecycleResumeEffect(viewModel) {
        viewModel.onShown()
        onPauseOrDispose { }
    }
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = modifier.fillMaxSize()) {
        CommunityContent(
            state = state,
            onVote = viewModel::askToVote,
            onOpen = { url -> if (!openInCustomTab(context, url)) scope.launch { snackbar.showSnackbar(openFailed) } },
            onRetry = viewModel::refresh,
            onOpenHistory = onOpenHistory,
            onInfo = { showInfo = true },
            imageFile = viewModel::imageFile,
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
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(stringResource(R.string.community_info_title)) },
            text = { Text(stringResource(R.string.community_intro)) },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text(stringResource(R.string.legal_close)) } },
        )
    }
}

/** Stateless C1 (previews and PseudoLocaleScreenshotTest render it). */
@Composable
fun CommunityContent(
    state: CommunityUiState,
    onVote: (MinistryUi) -> Unit,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onInfo: () -> Unit = {},
    imageFile: suspend (String) -> File? = { null },
) {
    val locale = Locale.getDefault()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(state, locale, onInfo)
        val retry: @Composable () -> Unit = {
            TextButton(onClick = onRetry, enabled = !state.refreshing) { Text(stringResource(R.string.community_retry)) }
        }
        when {
            state.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.empty != null -> Notice(emptyMessage(state, locale), action = retry)
            else -> CurrentRound(state, onVote, onOpen, retry, imageFile)
        }
        OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.community_view_history))
        }
        state.sources?.let { SourcesLine(it, locale) }
    }
}

@Composable
private fun Header(state: CommunityUiState, locale: Locale, onInfo: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val month = state.month.takeIf { it.isNotEmpty() } ?: CommunityRules.monthOf(Instant.now())
        Text(
            stringResource(R.string.community_round_title, monthName(month, locale)).uppercase(locale),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.14.em),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onInfo) {
            Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.community_info_cd), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Text(
        stringResource(R.string.community_headline),
        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Literata),
        modifier = Modifier.semantics { heading() },
    )
    Text(stringResource(R.string.community_purpose), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val open = state.empty == null && !state.loading && state.roundState != RoundState.NOT_OPEN_YET && state.roundState != RoundState.CLOSED
    if (open) state.daysLeft?.let { days ->
        Text(
            if (days == 0) stringResource(R.string.community_closes_today) else pluralStringResource(R.plurals.community_closes_in_days, days, days),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun emptyMessage(state: CommunityUiState, locale: Locale): String = when (state.empty) {
    CommunityEmpty.OFFLINE_NOTHING_CACHED -> stringResource(R.string.community_empty_offline)
    CommunityEmpty.LIST_MISSING -> stringResource(R.string.community_empty_missing)
    CommunityEmpty.LIST_INVALID -> stringResource(R.string.community_empty_invalid)
    CommunityEmpty.LIST_EMPTY -> stringResource(R.string.community_empty_list)
    CommunityEmpty.NO_MINISTRY_THIS_MONTH, null -> state.nextMonth
        ?.let { stringResource(R.string.community_empty_month_next, monthLabel(it, locale)) }
        ?: stringResource(R.string.community_empty_month)
}

/** Debug builds: each data source and when it was last fetched (local time). */
@Composable
private fun SourcesLine(sources: CommunitySources, locale: Locale) {
    @Composable
    fun fetched(at: Instant?, ok: Boolean?): String = when {
        at == null -> stringResource(R.string.community_source_never)
        ok == false -> stringResource(R.string.community_source_failed, localDateTime(at, locale))
        else -> stringResource(R.string.community_source_ok, localDateTime(at, locale))
    }
    val m = sources.ministries
    val ministries = fetched(m.checkedAt, m.lastProblem == null).let { base ->
        listOfNotNull(base, m.sha256?.take(8), m.lastProblem?.name?.lowercase(), if (m.refreshing) "…" else null).joinToString(" ")
    }
    Text(
        stringResource(R.string.community_sources_debug, ministries, fetched(sources.roundReadAt, sources.roundReadOk)),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CurrentRound(
    state: CommunityUiState,
    onVote: (MinistryUi) -> Unit,
    onOpen: (String) -> Unit,
    retry: @Composable () -> Unit,
    imageFile: suspend (String) -> File?,
) {
    when (state.roundState) {
        RoundState.NOT_OPEN_YET -> Notice(stringResource(R.string.community_round_not_open), action = retry)
        RoundState.CLOSED -> Notice(stringResource(R.string.community_round_closed))
        else -> Unit
    }
    // Only the cases that need words: the "Voted ✓" button covers a counted vote.
    state.myPick?.let { pick ->
        val text = when (state.status) {
            VoteStatus.PENDING -> stringResource(R.string.community_pick_pending, pick.name)
            VoteStatus.NOT_COUNTED -> stringResource(R.string.community_pick_not_counted, pick.name)
            else -> null
        }
        text?.let {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) {
                Text(it, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    state.round.forEach { ministry ->
        MinistryCard(
            ministry = ministry,
            isPick = ministry.id == state.myPick?.id,
            canVote = state.canVote,
            onVote = { onVote(ministry) },
            onOpen = onOpen,
            imageFile = imageFile,
        )
    }
}

@Composable
private fun MinistryCard(
    ministry: MinistryUi,
    isPick: Boolean,
    canVote: Boolean,
    onVote: () -> Unit,
    onOpen: (String) -> Unit,
    imageFile: suspend (String) -> File?,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column {
            MinistryImage(ministry, imageFile)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                categoryLine(ministry.categories)?.let {
                    Text(
                        it.uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.1.em),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(ministry.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(ministry.description, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                if (isPick) {
                    val cd = stringResource(R.string.community_voted_cd, ministry.name)
                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().semantics { contentDescription = cd }) {
                        Text(stringResource(R.string.community_voted))
                    }
                } else {
                    val cd = stringResource(R.string.community_vote_cd, ministry.name)
                    Button(onClick = onVote, enabled = canVote, modifier = Modifier.fillMaxWidth().semantics { contentDescription = cd }) {
                        Text(stringResource(R.string.community_vote))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinkButton(stringResource(R.string.community_website), stringResource(R.string.community_website_cd, ministry.name)) { onOpen(ministry.website) }
                    LinkButton(stringResource(R.string.community_give), stringResource(R.string.community_give_cd, ministry.name)) { onOpen(ministry.giveUrl) }
                }
            }
        }
    }
}

/** "Relief · Development" via R.string.two_parts, or null with no categories. */
@Composable
private fun categoryLine(categories: List<String>): String? {
    if (categories.isEmpty()) return null
    val resources = LocalContext.current.resources
    return categories.reduce { a, b -> resources.getString(R.string.two_parts, a, b) }
}

/** The image from ministries.json, or a tinted panel with the category icon while it loads or when there's none. */
@Composable
private fun MinistryImage(ministry: MinistryUi, imageFile: suspend (String) -> File?) {
    val file by produceState<File?>(null, ministry.image) { value = ministry.image?.let { imageFile(it) } }
    Box(
        Modifier.fillMaxWidth().height(140.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            categoryIcon(ministry.categories),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(40.dp),
        )
        file?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

/** A Material icon for the first category we recognise; a church otherwise. */
internal fun categoryIcon(categories: List<String>): ImageVector {
    val text = categories.joinToString(" ").lowercase(Locale.ROOT)
    return when {
        listOf("translat", "bible", "scripture", "literacy").any { it in text } -> Icons.AutoMirrored.Outlined.MenuBook
        listOf("persecut").any { it in text } -> Icons.Outlined.Shield
        listOf("child", "orphan").any { it in text } -> Icons.Outlined.ChildCare
        listOf("health", "medic", "hospital").any { it in text } -> Icons.Outlined.MedicalServices
        listOf("water").any { it in text } -> Icons.Outlined.WaterDrop
        listOf("food", "hunger").any { it in text } -> Icons.Outlined.Restaurant
        listOf("educat", "school").any { it in text } -> Icons.Outlined.School
        listOf("relief", "disaster", "humanitarian", "poverty", "development").any { it in text } -> Icons.Outlined.VolunteerActivism
        listOf("mission", "evangel").any { it in text } -> Icons.Outlined.Public
        else -> Icons.Outlined.Church
    }
}

@Composable
private fun LinkButton(label: String, description: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = description }) {
        Text(label)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
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

/** "November" / "noviembre": the round month's name on its own, for "November spotlight". */
internal fun monthName(month: String, locale: Locale): String =
    YearMonth.parse(month).format(DateTimeFormatter.ofPattern("LLLL", locale))

/** "diciembre de 2026" / "December 2026": the UTC round month, named in the app's language. */
/**
 * "December 2026" / "diciembre de 2026": the language's own month-year pattern, lowercase where the language writes
 * months that way mid-sentence. [standalone] capitalises it for a heading ("Diciembre de 2026").
 */
private fun monthLabel(month: String, locale: Locale, standalone: Boolean = false): String {
    val text = YearMonth.parse(month).format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMMyyyy"), locale))
    return if (standalone) text.replaceFirstChar { it.titlecase(locale) } else text
}

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
