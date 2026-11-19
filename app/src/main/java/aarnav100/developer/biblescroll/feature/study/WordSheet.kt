package aarnav100.developer.biblescroll.feature.study

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.core.content.model.Word
import aarnav100.developer.biblescroll.ui.theme.ScriptureTextStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp

/**
 * One original-language word: its dictionary form (lemma), pronunciation, meaning in this verse, grammar code and
 * Strong's number. Only data the study ships (STEPBible TAHOT/TAGNT); no lexicon definitions (pipeline LICENCES.md).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordSheet(word: Word, rtl: Boolean, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp),
        ) {
            Text(
                word.surface,
                style = ScriptureTextStyle.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textDirection = if (rtl) TextDirection.Rtl else TextDirection.Ltr,
                ),
                modifier = Modifier.semantics { heading() },
            )
            Detail(R.string.study_word_gloss, cleanGloss(word.gloss))
            Detail(R.string.study_word_lemma, word.lemma, scripture = true, rtl = rtl)
            Detail(R.string.study_word_transliteration, cleanTransliteration(word.transliteration))
            // Hebrew/Aramaic grammar codes come from ETCBC via TAHOT, whose licence terms are still being confirmed
            // (pipeline/LICENCES.md, FLAG): shown for Greek only until STEPBible confirms CC BY use.
            if (!rtl) Detail(R.string.study_word_morphology, word.morphology)
            Detail(R.string.study_word_strongs, word.strongs)
        }
    }
}

@Composable
private fun Detail(label: Int, value: String, scripture: Boolean = false, rtl: Boolean = false) {
    if (value.isBlank()) return
    Column(Modifier.padding(top = 16.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = if (scripture) {
                ScriptureTextStyle.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                    lineHeight = MaterialTheme.typography.titleLarge.lineHeight,
                    textDirection = if (rtl) TextDirection.Rtl else TextDirection.Ltr,
                )
            } else {
                MaterialTheme.typography.bodyLarge
            },
        )
    }
}
