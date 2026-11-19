package aarnav100.developer.biblescroll.data.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * The language plans and devotionals are shown in: the app's UI language code ("en", "es"). Process-wide, like
 * Locale.getDefault(); AppLanguage updates it at start and on every language change, and content flows combine
 * with [code] so open screens switch without a restart. Content without that language falls back to English.
 */
object ContentLanguage {
    private val _code = MutableStateFlow(Locale.getDefault().language)
    val code: StateFlow<String> = _code.asStateFlow()

    fun set(code: String) {
        _code.value = code
    }
}
