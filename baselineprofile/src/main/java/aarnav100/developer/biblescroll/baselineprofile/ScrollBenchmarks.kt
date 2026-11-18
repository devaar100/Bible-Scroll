package aarnav100.developer.biblescroll.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Frame timing while flinging through the feed, without and with the Baseline Profile. */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun feedScrollNoCompilation() = scroll(CompilationMode.None())

    @Test
    fun feedScrollBaselineProfile() = scroll(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun scroll(mode: CompilationMode) = rule.measureRepeated(
        packageName = targetAppId,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        // No startup mode: a COLD mode kills the process after setupBlock, leaving nothing to scroll.
        startupMode = null,
        iterations = 8,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            passOnboardingIfShown()
            waitForFeed()
        },
    ) {
        flingFeed()
    }
}
