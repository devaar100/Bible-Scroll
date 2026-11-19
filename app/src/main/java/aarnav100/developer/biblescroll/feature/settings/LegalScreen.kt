package aarnav100.developer.biblescroll.feature.settings

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.legal.LicenceText
import aarnav100.developer.biblescroll.data.legal.OpenSourceLibrary
import aarnav100.developer.biblescroll.data.legal.PRIVACY_UPDATED
import aarnav100.developer.biblescroll.feature.report.ContentType
import aarnav100.developer.biblescroll.feature.report.ReportContext
import aarnav100.developer.biblescroll.feature.report.ReportProblemSheet
import aarnav100.developer.biblescroll.feature.report.SupportAddress
import aarnav100.developer.biblescroll.ui.text.localizedList
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** S8 Legal. Privacy first (S7's Privacy row opens this screen), then licences, sources, versions and contact. */
@Composable
fun LegalScreen(onBack: () -> Unit, viewModel: LegalViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val licence by viewModel.licenceText.collectAsStateWithLifecycle()
    var reporting by rememberSaveable { mutableStateOf(false) }
    LegalContent(state, onBack, onOpenLicence = viewModel::openLicence, onReport = { reporting = true })
    licence?.let { (text, body) ->
        AlertDialog(
            onDismissRequest = viewModel::closeLicence,
            title = { Text(stringResource(text.title)) },
            text = {
                SelectionContainer {
                    Text(body, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.verticalScroll(rememberScrollState()))
                }
            },
            confirmButton = { TextButton(onClick = viewModel::closeLicence) { Text(stringResource(R.string.legal_close)) } },
        )
    }
    if (reporting) ReportProblemSheet(ReportContext(ContentType.STUDY), onDismiss = { reporting = false })
}

/** Stateless S8 (PseudoLocaleScreenshotTest renders it). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalContent(state: LegalUiState, onBack: () -> Unit, onOpenLicence: (LicenceText) -> Unit, onReport: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.legal_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            privacySection()
            translationsSection(state)
            sourcesSection(onReport)
            fontsSection()
            librariesSection(state.libraries, onOpenLicence)
            versionsSection(state)
            item { Heading(stringResource(R.string.legal_contact)) }
            item { Body(SupportAddress) }
        }
    }
}

/** The privacy text: the same strings tools/build_privacy_page.py turns into web/privacy/. */
private fun LazyListScope.privacySection() {
    item { Heading(stringResource(R.string.privacy_title)) }
    item {
        val locale = Locale.getDefault()
        Caption(stringResource(R.string.privacy_updated, PRIVACY_UPDATED.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))))
    }
    item { Body(stringResource(R.string.privacy_intro)) }
    item { SubHeading(stringResource(R.string.privacy_on_phone_title)) }
    item { Body(stringResource(R.string.privacy_on_phone)) }
    item { SubHeading(stringResource(R.string.privacy_leaves_title)) }
    items(listOf(R.string.privacy_leaves_content, R.string.privacy_leaves_vote, R.string.privacy_leaves_email, R.string.privacy_leaves_links)) { Body(stringResource(it)) }
    item { SubHeading(stringResource(R.string.privacy_android_title)) }
    item { Body(stringResource(R.string.privacy_android_backup)) }
    item { Body(stringResource(R.string.privacy_android_crashes)) }
    item { SubHeading(stringResource(R.string.privacy_delete_title)) }
    item { Body(stringResource(R.string.privacy_delete)) }
    item { SubHeading(stringResource(R.string.privacy_contact_title)) }
    item { Body(stringResource(R.string.privacy_contact, SupportAddress)) }
    item { Body(stringResource(R.string.privacy_changes)) }
}

private fun LazyListScope.translationsSection(state: LegalUiState) {
    item { Heading(stringResource(R.string.legal_translations)) }
    items(state.translations, key = { "t-${it.id}" }) { t ->
        Column(Modifier.padding(vertical = 6.dp)) {
            SubHeading(stringResource(R.string.two_parts, t.name, t.id))
            Body(stringResource(R.string.two_parts, stringResource(R.string.legal_licence), t.licence))
            t.attribution?.let { Body(stringResource(R.string.two_parts, stringResource(R.string.legal_attribution), it)) }
        }
    }
}

private fun LazyListScope.sourcesSection(onReport: () -> Unit) {
    item { Heading(stringResource(R.string.legal_sources)) }
    items(listOf(R.string.legal_source_step, R.string.legal_source_openbible, R.string.legal_source_tsk, R.string.legal_source_lexicons)) {
        Body(stringResource(it))
    }
    item { Heading(stringResource(R.string.legal_studies_title)) }
    item { Body(stringResource(R.string.legal_studies_body)) }
    item {
        TextButton(onClick = onReport, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Icon(Icons.Outlined.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.report_title))
        }
    }
}

private fun LazyListScope.fontsSection() {
    item { Heading(stringResource(R.string.legal_fonts)) }
    item { Body(stringResource(R.string.legal_font_literata)) }
    item { Body(stringResource(R.string.legal_font_inter)) }
}

private fun LazyListScope.librariesSection(libraries: List<OpenSourceLibrary>?, onOpenLicence: (LicenceText) -> Unit) {
    item { Heading(stringResource(R.string.legal_licence_texts)) }
    items(LicenceText.entries.toList(), key = { "l-${it.name}" }) { text ->
        Text(
            stringResource(text.title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { onOpenLicence(text) }
                .padding(vertical = 12.dp),
        )
    }
    item { Heading(stringResource(R.string.legal_libraries)) }
    when {
        libraries == null -> Unit
        libraries.isEmpty() -> item { Body(stringResource(R.string.legal_libraries_unavailable)) }
        else -> {
            item { Body(pluralStringResource(R.plurals.legal_libraries_count, libraries.size, libraries.size)) }
            items(libraries, key = { "o-${it.coordinates}" }) { lib ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(stringResource(R.string.two_parts, lib.name, lib.version), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.two_parts, lib.coordinates, localizedList(lib.licences)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.versionsSection(state: LegalUiState) {
    item { Heading(stringResource(R.string.legal_versions)) }
    item { Body(stringResource(R.string.legal_app_version, state.appVersion)) }
    item { Body(stringResource(R.string.legal_bible_db, state.bibleDbVersion)) }
    item { Body(state.contentRelease?.let { stringResource(R.string.legal_content_release, it) } ?: stringResource(R.string.legal_content_none)) }
    item { Body(pluralStringResource(R.plurals.legal_study_books, state.studyBooks, state.studyBooks)) }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp).semantics { heading() })
}

@Composable
private fun SubHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() })
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
