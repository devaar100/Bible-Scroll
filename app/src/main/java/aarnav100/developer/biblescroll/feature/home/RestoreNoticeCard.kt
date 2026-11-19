package aarnav100.developer.biblescroll.feature.home

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.core.content.BookDownloader
import aarnav100.developer.biblescroll.core.content.ContentStorage
import aarnav100.developer.biblescroll.data.backup.BackupNotices
import aarnav100.developer.biblescroll.data.plans.PlanProgressDao
import aarnav100.developer.biblescroll.data.reminder.NotificationAccess
import aarnav100.developer.biblescroll.data.reminder.ReminderRepository
import aarnav100.developer.biblescroll.data.user.UserDao
import aarnav100.developer.biblescroll.data.user.VerseMarkEntity
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class RestoreNoticeState(
    val visible: Boolean = false,
    /** Reminders are set but this phone hasn't allowed notifications (not part of a backup). */
    val askNotifications: Boolean = false,
    /** Books with studies the reader opened or saved that aren't on this phone (downloads aren't backed up). */
    val missingStudyBooks: List<Int> = emptyList(),
)

/** "Welcome back" on Home, once, after Android restored a backup (RestoreFixups raises it). */
@HiltViewModel
class RestoreNoticeViewModel @Inject constructor(
    private val notices: BackupNotices,
    private val access: NotificationAccess,
    private val reminders: ReminderRepository,
    private val plans: PlanProgressDao,
    private val userDao: UserDao,
    private val storage: ContentStorage,
    private val downloader: BookDownloader,
) : ViewModel() {

    private val needsPermission = MutableStateFlow(false)

    val state: StateFlow<RestoreNoticeState> = combine(
        notices.restoreNotice,
        needsPermission,
        userDao.observeStudied(),
        userDao.observeSavedStudies(),
        storage.books,
    ) { notice, ask, studied, saved, stored ->
        if (!notice) return@combine RestoreNoticeState()
        val missing = missingBooks(studied + saved, stored.map { it.bookId })
        RestoreNoticeState(visible = ask || missing.isNotEmpty(), askNotifications = ask, missingStudyBooks = missing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RestoreNoticeState())

    /**
     * Re-checked on every resume, so granting from system settings hides the line. Once nothing is left to do
     * (permission granted or not needed, studies back), the notice is cleared for good.
     */
    fun refreshPermission() {
        viewModelScope.launch {
            if (!notices.restoreNotice.first()) return@launch
            val anyReminder = reminders.settings.first().enabled || plans.activeWithReminders().isNotEmpty()
            val ask = anyReminder && access.needsRuntimePermission()
            needsPermission.value = ask
            val marks = userDao.observeStudied().first() + userDao.observeSavedStudies().first()
            if (!ask && missingBooks(marks, storage.books.first().map { it.bookId }).isEmpty()) notices.dismissRestoreNotice()
        }
    }

    private fun missingBooks(marks: List<VerseMarkEntity>, onPhone: List<Int>): List<Int> =
        marks.map { it.bookId }.distinct().filter { it !in onPhone && it !in BUNDLED_STUDY_BOOKS }.sorted()

    fun downloadStudies() {
        state.value.missingStudyBooks.forEach(downloader::ensure)
    }

    fun dismiss() {
        viewModelScope.launch { notices.dismissRestoreNotice() }
    }

    private companion object {
        /**
         * Psalms and the Gospels ship in the app (pipeline `bundle`) and re-import on first launch, so they're
         * never "missing" even if that import hasn't finished yet.
         */
        val BUNDLED_STUDY_BOOKS = setOf(19, 40, 41, 42, 43)
    }
}

@Composable
fun RestoreNoticeCard(viewModel: RestoreNoticeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermission() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshPermission()
    }
    if (!state.visible) return
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 8.dp)) {
            Text(
                stringResource(R.string.restore_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.restore_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            )
            if (state.askNotifications) {
                Text(
                    stringResource(R.string.restore_notifications),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                )
                TextButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text(stringResource(R.string.restore_allow_notifications))
                }
            }
            if (state.missingStudyBooks.isNotEmpty()) {
                val n = state.missingStudyBooks.size
                Text(
                    pluralStringResource(R.plurals.restore_studies, n, n),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                )
                TextButton(onClick = viewModel::downloadStudies) { Text(stringResource(R.string.restore_download_studies)) }
            }
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.tip_dismiss)) }
            }
        }
    }
}
