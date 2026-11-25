package aarnav100.developer.biblescroll.feature.community

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.core.content.CommunityHistoryRepository
import aarnav100.developer.biblescroll.core.content.HistoryState
import aarnav100.developer.biblescroll.core.content.model.Donation
import aarnav100.developer.biblescroll.ui.theme.AppShapes
import aarnav100.developer.biblescroll.ui.theme.AppText
import aarnav100.developer.biblescroll.ui.theme.Literata
import aarnav100.developer.biblescroll.ui.theme.Spacing
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

@Immutable
sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Unavailable(val offline: Boolean) : HistoryUiState
    /** Finished months only, newest first; empty = "No donations yet". */
    data class Ready(val donations: List<Donation>) : HistoryUiState
}

/** Community → History: the published donation record per finished month. Amounts are shown exactly as published. */
@HiltViewModel
class HistoryViewModel @Inject constructor(private val history: CommunityHistoryRepository) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = history.history.map { state ->
        when (state) {
            HistoryState.Loading -> HistoryUiState.Loading
            is HistoryState.Unavailable -> HistoryUiState.Unavailable(state.offline)
            is HistoryState.Ready -> HistoryUiState.Ready(state.donations)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState.Loading)

    /** Fetches at most once per calendar month (HistoryRules.shouldFetch); otherwise the cached copy shows. */
    fun onShown() {
        viewModelScope.launch { history.refreshIfDue() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.onShown() }
    Scaffold(
        topBar = {
            // The title is the large heading in the content (approved mockup), so the bar holds only the back arrow.
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = stringResource(R.string.settings_back), modifier = Modifier.size(20.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        HistoryContent(state, onRetry = viewModel::onShown, modifier = Modifier.padding(padding))
    }
}

/** Stateless History list (previews and PseudoLocaleScreenshotTest). */
@Composable
fun HistoryContent(state: HistoryUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val locale = Locale.getDefault()
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Column(Modifier.padding(bottom = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(
                stringResource(R.string.history_title),
                style = AppText.screenTitle,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.history_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (state) {
            HistoryUiState.Loading -> Box(Modifier.fillMaxWidth().padding(Spacing.xxl), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant) }
            is HistoryUiState.Unavailable -> Message(stringResource(if (state.offline) R.string.history_offline else R.string.history_unavailable)) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.community_retry)) }
            }
            is HistoryUiState.Ready -> if (state.donations.isEmpty()) {
                Message(stringResource(R.string.history_empty))
            } else {
                state.donations.forEach { DonationCard(it, locale) }
            }
        }
    }
}

@Composable
private fun DonationCard(donation: Donation, locale: Locale) {
    Surface(
        shape = AppShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
    ) {
        Row(Modifier.padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            MinistryPlaceholder(donation.ministryName, letterSize = 24.sp, modifier = Modifier.size(56.dp).clip(AppShapes.small))
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    historyMonth(donation.month, locale).uppercase(locale),
                    style = AppText.sectionLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(donation.ministryName, style = AppText.itemTitle)
                donation.amountValue?.let { amount ->
                    Text(donatedLine(formatDonation(amount, donation.currency, locale)), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** "$300 donated" with the amount in bold Literata; the words around it come from the translated string. */
@Composable
private fun donatedLine(amount: String): AnnotatedString {
    val full = stringResource(R.string.history_amount_donated, amount)
    val at = full.indexOf(amount)
    val bold = SpanStyle(fontFamily = Literata, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.titleMedium.fontSize)
    return buildAnnotatedString {
        append(full)
        if (at >= 0) addStyle(bold, at, at + amount.length)
    }
}

@Composable
private fun Message(text: String, action: (@Composable () -> Unit)? = null) {
    Surface(shape = AppShapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.card), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            action?.invoke()
        }
    }
}

/** "November 2026" / "Noviembre de 2026", in the app's language. */
internal fun historyMonth(month: String, locale: Locale): String =
    YearMonth.parse(month)
        .format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMMyyyy"), locale))
        .replaceFirstChar { it.titlecase(locale) }

/**
 * The published amount in its own currency (history.json `currency`), formatted for [locale]: "$300", "312,50 €".
 * Whole amounts drop the decimals; others keep the currency's usual digits. Never rounds a published amount up or
 * down beyond that. An unknown currency code shows as "300 XYZ".
 */
fun formatDonation(amount: BigDecimal, currencyCode: String, locale: Locale): String {
    val fraction = amount.stripTrailingZeros().scale().coerceAtLeast(0)
    val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
    val format = if (currency != null) NumberFormat.getCurrencyInstance(locale).apply { this.currency = currency } else NumberFormat.getNumberInstance(locale)
    val digits = when {
        fraction == 0 -> 0
        currency != null -> maxOf(currency.defaultFractionDigits, fraction)
        else -> fraction
    }
    format.minimumFractionDigits = digits
    format.maximumFractionDigits = digits
    val text = format.format(amount)
    return if (currency != null) text else "$text $currencyCode"
}
