package aarnav100.developer.biblescroll

import aarnav100.developer.biblescroll.data.settings.AppLanguage
import aarnav100.developer.biblescroll.feature.onboarding.OnboardingScreen
import aarnav100.developer.biblescroll.ui.BibleScrollAppUi
import aarnav100.developer.biblescroll.ui.DataErrorScreen
import aarnav100.developer.biblescroll.ui.navigation.PendingPlanLink
import aarnav100.developer.biblescroll.ui.navigation.PendingVerseLink
import aarnav100.developer.biblescroll.ui.navigation.PlanLinks
import aarnav100.developer.biblescroll.ui.navigation.VerseLinks
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import aarnav100.developer.biblescroll.ui.theme.isDark
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var pendingVerseLink: PendingVerseLink

    @Inject
    lateinit var pendingPlanLink: PendingPlanLink

    private val viewModel: MainViewModel by viewModels()

    /** Below Android 13, inflate in the language chosen in S2 (on 13+ the system does this). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash until settings load, so the first frame uses the saved theme (no flash).
        splash.setKeepOnScreenCondition {
            viewModel.settings.value == null || viewModel.onboardingComplete.value == null || viewModel.userData.value == null
        }
        enableEdgeToEdge()
        // Only on a fresh launch: after rotation the intent is the same one we already handled.
        if (savedInstanceState == null) handleVerseLink(intent)
        addOnNewIntentListener(::handleVerseLink)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val onboarded by viewModel.onboardingComplete.collectAsStateWithLifecycle()
            val current = settings ?: return@setContent // splash still showing
            val complete = onboarded ?: return@setContent
            val userData by viewModel.userData.collectAsStateWithLifecycle()
            val dataStatus = userData ?: return@setContent
            val appDark = current.themeMode.isDark()
            BibleScrollTheme(current) {
                if (!dataStatus.ok) {
                    // user.db won't open. Never start empty over it: explain, offer a copy, let them retry.
                    LaunchedEffect(appDark) {
                        enableEdgeToEdge(statusBarStyle = barStyle(appDark), navigationBarStyle = barStyle(appDark))
                    }
                    DataErrorScreen(
                        checking = dataStatus.checking,
                        onRetry = viewModel::retryUserData,
                        onSaveCopy = viewModel::saveRawCopy,
                        savedResults = viewModel.rawCopySaved,
                    )
                } else if (!complete) {
                    // O2-O8 sit on app chrome, so both bars follow the app's light/dark mode.
                    LaunchedEffect(appDark) {
                        enableEdgeToEdge(statusBarStyle = barStyle(appDark), navigationBarStyle = barStyle(appDark))
                    }
                    OnboardingScreen()
                } else BibleScrollAppUi(
                    pendingVerse = pendingVerseLink.ref,
                    pendingPlan = pendingPlanLink,
                    appDark = appDark,
                    onSystemBarsDark = { statusDark ->
                        // Status bar follows what's under it (the feed's reading background on the
                        // Bible tab); the navigation bar sits on app chrome, so it follows app mode.
                        enableEdgeToEdge(
                            statusBarStyle = barStyle(statusDark),
                            navigationBarStyle = barStyle(appDark),
                        )
                    },
                )
            }
        }
    }

    private fun barStyle(dark: Boolean) =
        if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)

    /** Widget taps arrive as biblescroll://verse/... links, plan reminders as biblescroll://plan/...; anything else is ignored. */
    private fun handleVerseLink(intent: Intent?) {
        VerseLinks.parse(intent?.dataString)?.let(pendingVerseLink::post)
        PlanLinks.parse(intent?.dataString)?.let(pendingPlanLink::post)
    }
}
