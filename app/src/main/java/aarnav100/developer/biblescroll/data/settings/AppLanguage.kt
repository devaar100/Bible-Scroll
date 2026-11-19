package aarnav100.developer.biblescroll.data.settings

import aarnav100.developer.biblescroll.data.bible.References
import aarnav100.developer.biblescroll.data.bible.ReferencePatterns
import aarnav100.developer.biblescroll.R
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-app language (S2 → Language).
 *
 * - Android 13+: the system per-app language (LocaleManager). Android stores it, backs it up, applies it to every
 *   context, and offers it in system Settings → Apps → Bible Scroll → Language. Choices come from
 *   res/xml/locales_config.xml.
 * - Android 8–12: stored here in SharedPreferences, read synchronously before the activity inflates
 *   ([wrap] in MainActivity.attachBaseContext) and applied to the Application's resources ([applyToApplication]),
 *   so notifications, the widget and ViewModels speak the same language.
 *
 * Why not AppCompatDelegate.setApplicationLocales: below Android 13 it only takes effect in an AppCompatActivity.
 * MainActivity is a ComponentActivity with a framework theme, so this does the same job (and the same "store it
 * ourselves" trick as AppCompat's autoStoreLocales) without AppCompat. After an upgrade to Android 13 the stored
 * choice moves to the system once ([migrateToSystem]).
 */
@Singleton
class AppLanguage @Inject constructor(@ApplicationContext private val context: Context) {

    private val _locale = MutableStateFlow(uiLocale().also {
        References.patterns = ReferencePatterns.from(context.resources)
        ContentLanguage.set(it.language)
    })

    /** The locale the UI is in now: the chosen language, else the device's. Emits when it changes. */
    val locale: StateFlow<Locale> = _locale.asStateFlow()

    /** The chosen language tag ("es"), or null to follow the device. */
    fun selected(): String? = selected(context)

    /** Languages offered (res/values/arrays.xml `app_languages`, kept in step with locales_config.xml). */
    fun supported(): List<String> = context.resources.getStringArray(R.array.app_languages).toList()

    /**
     * Android 8–12 only (Android 13+ uses the system screen). Stores [tag] (null = device language) and applies it
     * to the Application. The caller recreates the activity so the open screen redraws.
     */
    fun select(tag: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            prefs(context).edit(commit = true) { if (tag == null) remove(KEY) else putString(KEY, tag) }
            applyToApplication(context)
        }
        refresh()
    }

    /** Re-read after a configuration change (language picked in system settings, or the device language changed). */
    fun refresh() {
        References.patterns = ReferencePatterns.from(context.resources)
        _locale.value = uiLocale()
        ContentLanguage.set(_locale.value.language)
    }

    /** Resources in the app's language, for text built outside a screen (notifications, widget, ViewModels). */
    fun resources(): Resources = context.resources

    /**
     * Resources in [locale], from a configuration context. Never `Resources(assets, metrics, config)`: that shares
     * the app's AssetManager and switches the app's own resources to [locale] too (Spanish book names came out English).
     */
    fun resourcesIn(locale: Locale): Resources =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) }).resources

    private fun uiLocale(): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

    companion object {
        private const val PREFS = "app_language"
        private const val KEY = "language_tag"

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun selected(context: Context): String? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java).applicationLocales
                    .takeUnless { it.isEmpty }?.get(0)?.toLanguageTag()
            } else {
                prefs(context).getString(KEY, null)
            }

        /** The locale list to use below Android 13: the stored choice, else the device's own. */
        private fun localesFor(context: Context): LocaleList =
            prefs(context).getString(KEY, null)?.let { LocaleList.forLanguageTags(it) }
                ?: Resources.getSystem().configuration.locales

        /** For Activity.attachBaseContext: below Android 13, a context in the chosen language. */
        fun wrap(base: Context): Context {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || prefs(base).getString(KEY, null) == null) return base
            val config = Configuration(base.resources.configuration).apply { setLocales(localesFor(base)) }
            return base.createConfigurationContext(config)
        }

        /**
         * Below Android 13: point the Application's resources at the chosen language. Called from Application.onCreate
         * and onConfigurationChanged (the framework resets them to the device language on every configuration change).
         */
        @Suppress("DEPRECATION") // updateConfiguration is the only way to localise an existing Application context.
        fun applyToApplication(app: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            val locales = localesFor(app)
            val resources = app.applicationContext.resources
            if (resources.configuration.locales == locales) return
            LocaleList.setDefault(locales)
            val config = Configuration(resources.configuration).apply { setLocales(locales) }
            resources.updateConfiguration(config, resources.displayMetrics)
        }

        /** After an upgrade to Android 13+: hand a choice made on Android 12 or lower to the system, once. */
        fun migrateToSystem(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            val stored = prefs(context).getString(KEY, null) ?: return
            val manager = context.getSystemService(LocaleManager::class.java)
            if (manager.applicationLocales.isEmpty) manager.applicationLocales = LocaleList.forLanguageTags(stored)
            prefs(context).edit { remove(KEY) }
        }
    }
}
