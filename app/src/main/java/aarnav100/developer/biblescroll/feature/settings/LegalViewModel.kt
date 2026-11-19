package aarnav100.developer.biblescroll.feature.settings

import aarnav100.developer.biblescroll.BuildConfig
import aarnav100.developer.biblescroll.core.content.ContentInfo
import aarnav100.developer.biblescroll.data.bible.BibleDatabase
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.legal.LicenceText
import aarnav100.developer.biblescroll.data.legal.OpenSourceLibraries
import aarnav100.developer.biblescroll.data.legal.OpenSourceLibrary
import aarnav100.developer.biblescroll.data.legal.TranslationLicence
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

@Immutable
data class TranslationNotice(val id: String, val name: String, val licence: String, val attribution: String?)

@Immutable
data class LegalUiState(
    val translations: List<TranslationNotice> = emptyList(),
    /** Null while loading; empty if the build has no Licensee report. */
    val libraries: List<OpenSourceLibrary>? = null,
    val appVersion: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
    val bibleDbVersion: Int = BibleDatabase.VERSION,
    /** Last content release fetched; null if none yet. */
    val contentRelease: String? = null,
    val studyBooks: Int = 0,
)

/** S8 Legal: translations from bible.db, the build-time library list, licence texts and versions. */
@HiltViewModel
class LegalViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    bible: BibleRepository,
    private val content: ContentInfo,
) : ViewModel() {

    private val loaded = MutableStateFlow(LegalUiState())

    val uiState: StateFlow<LegalUiState> = combine(
        bible.translations().map { list ->
            list.map { t -> TranslationLicence.split(t.licence).let { (licence, attribution) -> TranslationNotice(t.id, t.name, licence, attribution) } }
        },
        loaded,
    ) { translations, rest -> rest.copy(translations = translations) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LegalUiState())

    private val _licenceText = MutableStateFlow<Pair<LicenceText, String>?>(null)
    val licenceText: StateFlow<Pair<LicenceText, String>?> = _licenceText

    init {
        viewModelScope.launch {
            val libraries = readAsset(OpenSourceLibraries.ASSET)?.let(OpenSourceLibraries::parse).orEmpty()
            loaded.value = loaded.value.copy(libraries = libraries, contentRelease = content.release(), studyBooks = content.storedBookCount())
        }
    }

    fun openLicence(text: LicenceText) {
        viewModelScope.launch { readAsset(text.asset)?.let { _licenceText.value = text to it } }
    }

    fun closeLicence() {
        _licenceText.value = null
    }

    private suspend fun readAsset(path: String): String? = withContext(Dispatchers.IO) {
        try {
            context.assets.open(path).use { it.readBytes().decodeToString() }
        } catch (e: IOException) {
            null
        }
    }
}
