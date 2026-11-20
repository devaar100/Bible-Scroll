package aarnav100.developer.biblescroll.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing while scrubbing through Psalm 119 (176 verses) with the B1 verse scrubber. Each verse change is a
 * scrollToPage, so this measures composing pages under the finger plus the scrubber's own draw work.
 * Needs the scrubber on (S2 default "Always").
 */
@RunWith(AndroidJUnit4::class)
class ScrubBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrubPsalm119NoCompilation() = scrub(CompilationMode.None())

    @Test
    fun scrubPsalm119BaselineProfile() = scrub(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun scrub(mode: CompilationMode) = rule.measureRepeated(
        packageName = targetAppId,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = null, // as ScrollBenchmarks: a COLD mode would kill the process after setup
        iterations = 8,
        setupBlock = {
            pressHome()
            openPsalm119()
        },
    ) {
        scrubChapter()
    }
}
