package aarnav100.developer.biblescroll.feature.community

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.community.CommunityRules
import aarnav100.developer.biblescroll.data.community.CommunityRules.RoundState
import aarnav100.developer.biblescroll.data.community.CommunityRules.VoteStatus
import aarnav100.developer.biblescroll.data.community.VoteResult
import aarnav100.developer.biblescroll.ui.components.SheetDragHandle
import aarnav100.developer.biblescroll.ui.theme.AppShapes
import aarnav100.developer.biblescroll.ui.theme.AppText
import aarnav100.developer.biblescroll.ui.theme.Literata
import aarnav100.developer.biblescroll.ui.theme.Spacing
import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.text.format.DateFormat
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Community tab: C1 this month's anonymous vote for the month's donation, with no counts anywhere (decision 23).
 * Layout per docs/ui-direction.md: one headline, one card per ministry with a single Vote button, a bottom sheet to
 * confirm, and a thank-you card once the vote is in. "View history" opens the donation History. Pull down to refresh.
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
        val image by produceState<File?>(null, target.image) { value = target.image?.let { viewModel.imageFile(it) } }
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissConfirm,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = { SheetDragHandle() },
        ) {
            VoteConfirmContent(target, image, onConfirm = viewModel::confirmVote, onCancel = viewModel::dismissConfirm)
        }
    }
    if (showInfo) {
        ModalBottomSheet(
            onDismissRequest = { showInfo = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = { SheetDragHandle() },
        ) {
            HowItWorksContent()
        }
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Header(state, locale, onInfo)
        val retry: @Composable () -> Unit = {
            TextButton(
                onClick = onRetry,
                enabled = !state.refreshing,
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Text(stringResource(R.string.community_retry))
            }
        }
        when {
            state.loading -> Box(Modifier.fillMaxWidth().padding(Spacing.xxl), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant) }
            state.empty != null -> Notice(emptyMessage(state, locale), action = retry)
            else -> CurrentRound(state, onVote, onOpen, retry, imageFile)
        }
        HistoryRow(onOpenHistory)
        state.sources?.let { SourcesLine(it, locale) }
    }
}

@Composable
private fun Header(state: CommunityUiState, locale: Locale, onInfo: () -> Unit) {
    val voted = state.myPick != null && state.status == VoteStatus.COUNTED
    val open = state.empty == null && !state.loading && state.roundState != RoundState.NOT_OPEN_YET && state.roundState != RoundState.CLOSED
    Column(Modifier.padding(top = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        // "DECEMBER SPOTLIGHT · CLOSES IN 12 DAYS": the countdown is plain text after the month.
        val month = state.month.takeIf { it.isNotEmpty() } ?: CommunityRules.monthOf(Instant.now())
        val title = stringResource(R.string.community_round_title, monthName(month, locale))
        val countdown = state.daysLeft?.takeIf { open }?.let { days ->
            if (days == 0) stringResource(R.string.community_closes_today) else pluralStringResource(R.plurals.community_closes_in_days, days, days)
        }
        Text(
            (countdown?.let { stringResource(R.string.two_parts, title, it) } ?: title).uppercase(locale),
            style = AppText.sectionLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnimatedContent(
            targetState = voted,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "community header",
        ) { isVoted ->
            if (isVoted) ThankYou() else Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    stringResource(R.string.community_headline),
                    style = AppText.screenTitle,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.community_purpose_short),
                    style = AppText.screenSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HowItWorksLink(onInfo)
            }
        }
    }
}

@Composable
private fun HowItWorksLink(onInfo: () -> Unit) {
    val cd = stringResource(R.string.community_info_cd)
    TextButton(
        onClick = onInfo,
        contentPadding = PaddingValues(horizontal = 0.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = cd },
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.community_how_it_works),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline),
        )
    }
}

/** After a counted vote the headline becomes this: plain text with a check, no card or colour. */
@Composable
private fun ThankYou() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        // A status, not the screen's title: one step smaller than "Choose this month's spotlight".
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(
                stringResource(R.string.community_headline_voted),
                style = AppText.cardTitle,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            stringResource(R.string.community_purpose_voted),
            style = AppText.screenSubtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryRow(onOpenHistory: () -> Unit) {
    Surface(
        onClick = onOpenHistory,
        shape = AppShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(Modifier.padding(horizontal = Spacing.card, vertical = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.community_view_history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The "How it works" sheet: what the vote is, then the privacy and money rules. */
@Composable
internal fun HowItWorksContent() {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl).padding(bottom = Spacing.xl).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Text(
            stringResource(R.string.community_info_title),
            style = AppText.cardTitle,
            modifier = Modifier.semantics { heading() },
        )
        InfoPoint(Icons.Outlined.HowToVote, stringResource(R.string.community_purpose))
        InfoPoint(Icons.Outlined.Lock, stringResource(R.string.community_intro))
    }
}

@Composable
private fun InfoPoint(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(Spacing.m).size(20.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(top = Spacing.s))
    }
}

/** The confirm sheet: who you're voting for and that it's final, then one button. */
@Composable
internal fun VoteConfirmContent(ministry: MinistryUi, image: File?, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.xl).padding(bottom = Spacing.l).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Box(Modifier.size(64.dp).clip(AppShapes.small)) {
                MinistryPlaceholder(ministry.name, letterSize = 28.sp, modifier = Modifier.matchParentSize())
                image?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    stringResource(R.string.community_confirm_title, ministry.name),
                    style = AppText.itemTitle,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.community_confirm_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.community_confirm_action), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            }
            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.community_confirm_cancel))
            }
        }
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
    // Only the cases that need words: the thank-you card and "Your vote" pill cover a counted vote.
    state.myPick?.let { pick ->
        val text = when (state.status) {
            VoteStatus.PENDING -> stringResource(R.string.community_pick_pending, pick.name)
            VoteStatus.NOT_COUNTED -> stringResource(R.string.community_pick_not_counted, pick.name)
            else -> null
        }
        text?.let {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = AppShapes.medium, modifier = Modifier.fillMaxWidth()) {
                Text(it, modifier = Modifier.padding(Spacing.card), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    state.round.forEach { ministry ->
        MinistryCard(
            ministry = ministry,
            isPick = ministry.id == state.myPick?.id,
            // Once this phone has voted, the other cards drop their Vote button instead of showing it greyed out.
            showVote = state.myPick == null,
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
    showVote: Boolean,
    canVote: Boolean,
    onVote: () -> Unit,
    onOpen: (String) -> Unit,
    imageFile: suspend (String) -> File?,
) {
    Surface(
        shape = AppShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
    ) {
        Column {
            MinistryImage(ministry, isPick, imageFile)
            Column(Modifier.padding(bottom = Spacing.m)) {
                // Tap the name or description to show the whole description; tap again for 3 lines. Only clickable
                // when the text is actually cut. TalkBack reads the full text either way.
                var expanded by rememberSaveable(ministry.id) { mutableStateOf(false) }
                var cut by remember(ministry.id, ministry.description) { mutableStateOf(false) }
                val toggleLabel = stringResource(if (expanded) R.string.community_description_less else R.string.community_description_more)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(if (cut || expanded) Modifier.clickable(onClickLabel = toggleLabel) { expanded = !expanded } else Modifier)
                        .padding(start = Spacing.card, end = Spacing.card, top = Spacing.card, bottom = Spacing.l),
                ) {
                    Text(
                        ministry.name,
                        style = AppText.itemTitle,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        ministry.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!expanded) cut = it.hasVisualOverflow },
                    )
                }
                FlowRow(
                    modifier = Modifier.padding(horizontal = Spacing.card),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedVisibility(visible = showVote, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                        val cd = stringResource(R.string.community_vote_cd, ministry.name)
                        Button(
                            onClick = onVote,
                            enabled = canVote,
                            contentPadding = PaddingValues(horizontal = 28.dp),
                            modifier = Modifier.padding(end = Spacing.s).heightIn(min = 48.dp).semantics { contentDescription = cd },
                        ) {
                            Text(stringResource(R.string.community_vote), style = AppText.button)
                        }
                    }
                    LinkButton(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        stringResource(R.string.community_website),
                        stringResource(R.string.community_website_cd, ministry.name),
                    ) { onOpen(ministry.website) }
                    LinkButton(
                        Icons.Outlined.FavoriteBorder,
                        stringResource(R.string.community_give),
                        stringResource(R.string.community_give_cd, ministry.name),
                    ) { onOpen(ministry.giveUrl) }
                }
            }
        }
    }
}

/** On the chosen ministry's image: a check pill (TalkBack: "You voted for X"). */
@Composable
private fun YourVotePill(name: String, modifier: Modifier = Modifier) {
    val cd = stringResource(R.string.community_voted_cd, name)
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier.clearAndSetSemantics { contentDescription = cd },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.community_your_vote), style = AppText.button)
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

/**
 * The image from ministries.json (or the ministry's tinted placeholder while it loads or when there's none), with the
 * category as a pill on top and the "Your vote" pill on the chosen ministry.
 */
@Composable
private fun MinistryImage(ministry: MinistryUi, isPick: Boolean, imageFile: suspend (String) -> File?) {
    val file by produceState<File?>(null, ministry.image) { value = ministry.image?.let { imageFile(it) } }
    Box(Modifier.fillMaxWidth().height(176.dp)) {
        MinistryPlaceholder(ministry.name, letterSize = 56.sp, modifier = Modifier.matchParentSize())
        file?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
        Row(
            Modifier.fillMaxWidth().padding(Spacing.m),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            // Weighted without fill: a long category ellipsizes instead of pushing the "Your vote" pill off the card.
            categoryLine(ministry.categories)
                ?.let { CategoryPill(it, Modifier.weight(1f, fill = false).padding(end = Spacing.s)) }
                ?: Spacer(Modifier.width(0.dp))
            AnimatedVisibility(visible = isPick, enter = fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)), exit = fadeOut()) {
                YourVotePill(ministry.name)
            }
        }
    }
}

@Composable
private fun CategoryPill(text: String, modifier: Modifier = Modifier) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun LinkButton(icon: ImageVector, label: String, description: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = description },
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = AppText.button, maxLines = 1)
    }
}

@Composable
private fun Notice(title: String, body: String? = null, action: (@Composable () -> Unit)? = null) {
    Surface(shape = AppShapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.card), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            action?.invoke()
        }
    }
}

/** "November" / "noviembre": the round month's name on its own, for "November spotlight". */
internal fun monthName(month: String, locale: Locale): String =
    YearMonth.parse(month).format(DateTimeFormatter.ofPattern("LLLL", locale))

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
