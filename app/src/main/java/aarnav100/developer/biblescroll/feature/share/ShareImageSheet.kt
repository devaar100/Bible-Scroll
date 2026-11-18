package aarnav100.developer.biblescroll.feature.share

import aarnav100.developer.biblescroll.ui.components.SheetDragHandle
import aarnav100.developer.biblescroll.R
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * B10: live preview of a verse card plus Share / Save. The exported image is rendered from a
 * separate full-resolution copy of the card into a GraphicsLayer, not captured from the preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareImageSheet(
    content: CardContent,
    /** The plain copied-verse text, sent alongside the image. */
    shareText: String,
    onDismiss: () -> Unit,
    viewModel: ShareImageViewModel = hiltViewModel(),
) {
    var format by rememberSaveable { mutableStateOf(CardFormat.Square) }
    var style by rememberSaveable { mutableStateOf(CardStyle.Paper) }
    val fit = rememberVerseFit(content.verseText, format)
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val exportLayer = rememberGraphicsLayer()
    val baseName = content.reference

    val savedMsg = stringResource(R.string.share_saved, stringResource(R.string.app_name))
    val failedMsg = stringResource(R.string.share_failed)
    val permissionMsg = stringResource(R.string.share_permission_needed)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ShareImageEvent.LaunchShare -> context.startActivity(event.chooser)
                ShareImageEvent.Saved -> snackbar.showSnackbar(savedMsg)
                ShareImageEvent.Failed -> snackbar.showSnackbar(failedMsg)
            }
        }
    }

    fun withBitmap(action: (Bitmap) -> Unit) {
        scope.launch { action(exportLayer.toSoftwareBitmap()) }
    }

    // API 26-28 only: ask for storage at the moment the user taps Save, never up front.
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) withBitmap { viewModel.save(it, baseName) }
        else scope.launch { snackbar.showSnackbar(permissionMsg) }
    }
    val onSave = {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else withBitmap { viewModel.save(it, baseName) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { SheetDragHandle() },
    ) {
        Box {
            // Full-size export copy: laid out at exactly widthPx x heightPx and recorded into
            // exportLayer, but never drawn, so it's invisible and takes no space.
            Box(Modifier.size(0.dp)) {
                val density = LocalDensity.current
                VerseCard(
                    content = content,
                    format = format,
                    style = style,
                    fit = fit,
                    modifier = Modifier
                        .requiredSize(
                            with(density) { format.widthPx.toDp() },
                            with(density) { format.heightPx.toDp() },
                        )
                        .drawWithContent { exportLayer.record { this@drawWithContent.drawContent() } },
                )
            }

            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.actions_share_image),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))

                VerseCard(
                    content = content,
                    format = format,
                    style = style,
                    fit = fit,
                    modifier = Modifier
                        .fillMaxWidth(if (format == CardFormat.Story) 0.56f else 0.86f)
                        .aspectRatio(format.widthPx.toFloat() / format.heightPx)
                        .clip(RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.height(20.dp))

                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    CardFormat.entries.forEachIndexed { i, f ->
                        SegmentedButton(
                            selected = f == format,
                            onClick = { format = f },
                            shape = SegmentedButtonDefaults.itemShape(i, CardFormat.entries.size),
                        ) {
                            Text(stringResource(if (f == CardFormat.Square) R.string.share_square else R.string.share_story))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CardStyle.entries.forEach { s ->
                        StyleSwatch(s, selected = s == style, onClick = { style = s })
                    }
                }
                Spacer(Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onSave, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.share_save))
                    }
                    Button(
                        onClick = { withBitmap { viewModel.share(it, baseName, shareText) } },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.share_share))
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun StyleSwatch(style: CardStyle, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(style.label)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            border = BorderStroke(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = label
                    role = Role.RadioButton
                    this.selected = selected
                },
        ) {
            Box(Modifier.fillMaxSize().padding(if (selected) 5.dp else 0.dp).clip(CircleShape).background(style.background))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

/** GraphicsLayer snapshots can be HARDWARE bitmaps, which PNG encoding can't always read. */
private suspend fun GraphicsLayer.toSoftwareBitmap(): Bitmap {
    val bitmap = toImageBitmap().asAndroidBitmap()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        bitmap
    }
}
