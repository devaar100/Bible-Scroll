package aarnav100.developer.biblescroll.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/** App id under test, passed by the Gradle config (see build.gradle.kts). */
val targetAppId: String
    get() = InstrumentationRegistry.getArguments().getString("targetAppId")
        ?: error("targetAppId not passed as instrumentation runner arg")

// Tags from app/.../ui/components/TestTags.kt, exposed as resource ids.
private const val FEED_PAGER = "feed_pager"
private const val BOOK_PICKER_BUTTON = "feed_book_picker"
private const val BOOK_PICKER = "book_picker"
private const val COMPARE_SHEET = "compare_sheet"

private const val TIMEOUT = 10_000L

/**
 * First launch shows onboarding. Walk through it the shortest way (Get started -> Skip -> reminder off
 * -> Continue), so no notification permission dialog appears. A no-op once onboarding is done.
 */
fun MacrobenchmarkScope.passOnboardingIfShown() {
    val deadline = System.currentTimeMillis() + TIMEOUT
    while (System.currentTimeMillis() < deadline) {
        if (device.hasObject(By.res(FEED_PAGER))) return
        device.findObject(By.text("Get started"))?.let { getStarted ->
            getStarted.click()
            device.wait(Until.findObject(By.text("Skip")), TIMEOUT).click()
            device.wait(Until.findObject(By.text("Remind me daily")), TIMEOUT).click() // on by default after Skip
            device.findObject(By.text("Continue")).click()
            waitForFeed()
            return
        }
        Thread.sleep(100)
    }
    error("Neither the feed nor onboarding appeared")
}

fun MacrobenchmarkScope.waitForFeed() {
    check(device.wait(Until.hasObject(By.res(FEED_PAGER)), TIMEOUT)) { "Feed did not appear" }
    // Verses load just after the pager; the book pill only shows once a verse is on screen.
    check(device.wait(Until.hasObject(By.res(BOOK_PICKER_BUTTON)), TIMEOUT)) { "Verses did not load" }
}

/** Fast swipes up through the feed, one verse per fling. */
fun MacrobenchmarkScope.flingFeed(times: Int = 12) {
    val feed = checkNotNull(device.wait(Until.findObject(By.res(FEED_PAGER)), TIMEOUT)) { "Feed not on screen" }
    // Keep clear of the system gesture areas at the top and bottom.
    feed.setGestureMargin(device.displayHeight / 5)
    repeat(times) {
        feed.fling(Direction.DOWN, FLING_SPEED)
        device.waitForIdle(500)
    }
}

/** B3: open the book picker, then close it. */
fun MacrobenchmarkScope.openAndCloseBookPicker() {
    device.findObject(By.res(BOOK_PICKER_BUTTON)).click()
    check(device.wait(Until.hasObject(By.res(BOOK_PICKER)), TIMEOUT)) { "Book picker did not open" }
    device.waitForIdle()
    device.pressBack()
    device.wait(Until.gone(By.res(BOOK_PICKER)), TIMEOUT)
}

/** B7 -> B9: Verse actions, then Compare versions, then close. */
fun MacrobenchmarkScope.openAndCloseCompare() {
    device.wait(Until.findObject(By.text("Verse actions")), TIMEOUT).click()
    device.wait(Until.findObject(By.text("Compare versions")), TIMEOUT).click()
    check(device.wait(Until.hasObject(By.res(COMPARE_SHEET)), TIMEOUT)) { "Compare sheet did not open" }
    device.waitForIdle()
    device.pressBack()
    device.wait(Until.gone(By.res(COMPARE_SHEET)), TIMEOUT)
}

/** px/s; well above a casual swipe, like someone skimming. */
private const val FLING_SPEED = 12_000

private const val VERSE_SCRUBBER = "verse_scrubber"

/** Opens the feed on Psalm 119:1 (176 verses, the longest chapter) through the app's own verse link. */
fun MacrobenchmarkScope.openPsalm119() {
    startActivityAndWait { intent ->
        intent.data = android.net.Uri.parse("biblescroll://verse/19/119/1")
    }
    passOnboardingIfShown()
    waitForFeed()
    check(device.wait(Until.hasObject(By.res(VERSE_SCRUBBER)), TIMEOUT)) { "Verse scrubber not shown (setting Off?)" }
}

/** Grabs the active capsule (verse 1, at the top) and drags it to the bottom, then back up: every one of Psalm 119's verses goes past. */
fun MacrobenchmarkScope.scrubChapter(passes: Int = 2) {
    val scrubber = checkNotNull(device.wait(Until.findObject(By.res(VERSE_SCRUBBER)), TIMEOUT)) { "Scrubber not on screen" }
    val bounds = scrubber.visibleBounds
    val x = bounds.centerX()
    val top = bounds.top + 4
    val bottom = bounds.bottom - 4
    repeat(passes) {
        // ~5 ms per step: about 1.2 s per sweep, a brisk human scrub.
        device.swipe(x, top, x, bottom, 240)
        device.waitForIdle(500)
        device.swipe(x, bottom, x, top, 240)
        device.waitForIdle(500)
    }
}
