package aarnav100.developer.biblescroll.feature.share

import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ShareImageEvent {
    data class LaunchShare(val chooser: Intent) : ShareImageEvent
    data object Saved : ShareImageEvent
    data object Failed : ShareImageEvent
}

/** B10 file work. Lives in viewModelScope so a save isn't cut off if the sheet closes mid-write. */
@HiltViewModel
class ShareImageViewModel @Inject constructor(
    private val exporter: VerseImageExporter,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<ShareImageEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun share(bitmap: Bitmap, baseName: String, text: String) = run {
        val uri = exporter.writeShareFile(bitmap, baseName)
        ShareImageEvent.LaunchShare(exporter.shareIntent(uri, text))
    }

    fun save(bitmap: Bitmap, baseName: String) = run {
        exporter.saveToPictures(bitmap, baseName)
        ShareImageEvent.Saved
    }

    private fun run(block: suspend () -> ShareImageEvent) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val event = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ShareImageEvent.Failed
            } finally {
                _busy.value = false
            }
            _events.send(event)
        }
    }
}
