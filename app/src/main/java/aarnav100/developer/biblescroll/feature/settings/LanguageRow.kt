package aarnav100.developer.biblescroll.feature.settings

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.settings.AppLanguage
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LanguageViewModel @Inject constructor(private val language: AppLanguage) : ViewModel() {
    /** Emits on every language change, so the row's summary follows. */
    val locale = language.locale
    fun selected(): String? = language.selected()
    fun supported(): List<String> = language.supported()
    fun select(tag: String?) = language.select(tag)
}

/** A language's own name, capitalised for that language: "English", "Español". */
internal fun nativeName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
}

/**
 * S2 → Language. Android 13+ opens the system per-app language screen (Android keeps the choice); Android 8–12
 * shows an in-app dialog. The system screen falls back to the dialog if a device doesn't provide it.
 */
@Composable
fun LanguageRow(viewModel: LanguageViewModel = hiltViewModel()) {
    val context = LocalContext.current
    viewModel.locale.collectAsStateWithLifecycle().value // recompose when the language changes
    var choosing by rememberSaveable { mutableStateOf(false) }
    val selected = viewModel.selected()

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_language)) },
        supportingContent = { Text(selected?.let(::nativeName) ?: stringResource(R.string.settings_language_system)) },
        leadingContent = { Icon(Icons.Outlined.Language, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !openSystemLanguageScreen(context)) choosing = true
        },
    )

    if (choosing) {
        LanguageDialog(
            options = viewModel.supported(),
            selected = selected,
            onSelect = { tag ->
                choosing = false
                if (tag != selected) {
                    viewModel.select(tag)
                    // Below Android 13 nothing else redraws the open screens: recreate so they inflate in the new language.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) context.findActivity()?.recreate()
                }
            },
            onDismiss = { choosing = false },
        )
    }
}

private fun openSystemLanguageScreen(context: Context): Boolean = try {
    context.startActivity(
        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
    true
} catch (e: ActivityNotFoundException) {
    false
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun LanguageDialog(options: List<String>, selected: String?, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column(Modifier.selectableGroup()) {
                (listOf<String?>(null) + options).forEach { tag ->
                    ListItem(
                        headlineContent = { Text(tag?.let(::nativeName) ?: stringResource(R.string.settings_language_system)) },
                        leadingContent = { RadioButton(selected = tag == selected, onClick = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = tag == selected, role = Role.RadioButton, onClick = { onSelect(tag) })
                            .padding(vertical = 0.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.notifications_cancel)) } },
    )
}
