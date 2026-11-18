package aarnav100.developer.biblescroll.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates app/src/release/generated/baselineProfiles/ via
 * `./gradlew :app:generateBaselineProfile` on a connected API 33+ device (no root needed).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    /** Cold start into the feed (and onboarding on a fresh install). Also feeds the startup profile (dex layout). */
    @Test
    fun startup() = rule.collect(packageName = targetAppId, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        passOnboardingIfShown()
        waitForFeed()
    }

    /** The main journeys: fast feed scrolling, the book picker, and the compare sheet. */
    @Test
    fun feedJourneys() = rule.collect(packageName = targetAppId, includeInStartupProfile = false) {
        pressHome()
        startActivityAndWait()
        passOnboardingIfShown()
        waitForFeed()
        flingFeed()
        openAndCloseBookPicker()
        openAndCloseCompare()
    }
}
