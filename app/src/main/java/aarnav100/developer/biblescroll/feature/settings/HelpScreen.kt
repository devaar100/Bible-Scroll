package aarnav100.developer.biblescroll.feature.settings

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.feature.report.AndroidEmailApps
import aarnav100.developer.biblescroll.feature.report.NoEmailAppDialog
import aarnav100.developer.biblescroll.feature.report.ProblemReport
import aarnav100.developer.biblescroll.feature.report.ReportContext
import aarnav100.developer.biblescroll.feature.report.ReportEmail
import aarnav100.developer.biblescroll.feature.report.ReportProblemSheet
import aarnav100.developer.biblescroll.feature.report.ReportSender
import aarnav100.developer.biblescroll.feature.report.SendResult
import aarnav100.developer.biblescroll.feature.report.SupportAddress
import aarnav100.developer.biblescroll.feature.report.currentDeviceInfo
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** One question and answer in S7. No billing topics (there's nothing to buy). */
enum class HelpTopic(@StringRes val question: Int, @StringRes val answer: Int) {
    Reading(R.string.help_q_reading, R.string.help_a_reading),
    LikesAndNotes(R.string.help_q_likes, R.string.help_a_likes),
    Translations(R.string.help_q_translations, R.string.help_a_translations),
    Studies(R.string.help_q_studies, R.string.help_a_studies),
    Backup(R.string.help_q_backup, R.string.help_a_backup),
    RemindersAndWidget(R.string.help_q_reminders, R.string.help_a_reminders),
    Privacy(R.string.help_q_privacy, R.string.help_a_privacy),
}

/** S7 Help & feedback, with a Privacy row that opens S8 Legal (Privacy is its first section). */
@Composable
fun HelpScreen(onBack: () -> Unit, onOpenPrivacy: () -> Unit) {
    val android = LocalContext.current
    var reporting by rememberSaveable { mutableStateOf(false) }
    var feedbackFallback by remember { mutableStateOf<ReportEmail?>(null) }
    HelpContent(
        onBack = onBack,
        onPrivacy = onOpenPrivacy,
        onReport = { reporting = true },
        onFeedback = {
            val email = ProblemReport.feedback(SupportAddress, note = "", device = currentDeviceInfo(android))
            (ReportSender(AndroidEmailApps(android)).send(email) as? SendResult.NoEmailApp)?.let { feedbackFallback = it.email }
        },
    )
    if (reporting) ReportProblemSheet(ReportContext.App, onDismiss = { reporting = false })
    feedbackFallback?.let { NoEmailAppDialog(it, onDismiss = { feedbackFallback = null }) }
}

/** Stateless S7 content (PseudoLocaleScreenshotTest renders it). */
@Composable
fun HelpContent(onBack: () -> Unit, onReport: () -> Unit, onFeedback: () -> Unit, onPrivacy: () -> Unit = {}) {
    var open by rememberSaveable { mutableStateOf<HelpTopic?>(null) }
    SettingsPage(title = stringResource(R.string.help_title), onBack = onBack) {
        NavRow(Icons.Outlined.Flag, stringResource(R.string.report_title), stringResource(R.string.help_report_summary), onReport)
        NavRow(Icons.Outlined.MailOutline, stringResource(R.string.help_feedback), stringResource(R.string.help_feedback_summary), onFeedback)
        NavRow(Icons.Outlined.Lock, stringResource(R.string.privacy_title), stringResource(R.string.help_privacy_summary), onPrivacy)
        SectionHeader(stringResource(R.string.help_faq_header))
        HelpTopic.entries.forEach { topic ->
            FaqItem(topic, expanded = open == topic, onToggle = { open = if (open == topic) null else topic })
        }
    }
}

@Composable
private fun FaqItem(topic: HelpTopic, expanded: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (expanded) R.string.help_expanded else R.string.help_collapsed)
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = state }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(topic.question), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
        }
        if (expanded) {
            Text(
                stringResource(topic.answer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}
