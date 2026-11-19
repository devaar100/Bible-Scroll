package aarnav100.developer.biblescroll.ui

import aarnav100.developer.biblescroll.R
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Shown instead of the app when user.db won't open (corrupt, or a migration failed). The file is never
 * deleted: the reader can save a copy of it (to send to support or keep), and try again.
 */
@Composable
fun DataErrorScreen(
    checking: Boolean,
    onRetry: () -> Unit,
    onSaveCopy: (Uri) -> Unit,
    savedResults: Flow<Boolean>,
) {
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(onSaveCopy)
    }
    LaunchedEffect(savedResults) {
        savedResults.collect { ok ->
            snackbar.showSnackbar(resources.getString(if (ok) R.string.data_error_saved else R.string.data_error_save_failed))
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.safeDrawingPadding()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    stringResource(R.string.data_error_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.data_error_body), style = MaterialTheme.typography.bodyLarge)
                Button(
                    onClick = { save.launch("bible-scroll-data-${LocalDate.now()}.zip") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.data_error_save_copy)) }
                OutlinedButton(onClick = onRetry, enabled = !checking, modifier = Modifier.fillMaxWidth()) {
                    if (checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.data_error_retry))
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}
