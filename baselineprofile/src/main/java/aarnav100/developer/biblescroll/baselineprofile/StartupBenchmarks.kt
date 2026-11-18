package aarnav100.developer.biblescroll.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start to verses on screen, without and with the Baseline Profile.
 * Run: ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 *   -Pandroid.testInstrumentationRunnerArguments.class=aarnav100.developer.biblescroll.baselineprofile.StartupBenchmarks
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = targetAppId,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = {
            // Get past onboarding once (data persists across iterations), then start cold.
            startActivityAndWait()
            passOnboardingIfShown()
            pressHome()
        },
    ) {
        startActivityAndWait()
        waitForFeed()
    }
}
