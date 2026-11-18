package aarnav100.developer.biblescroll.feature.onboarding

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import androidx.annotation.StringRes
import java.time.LocalTime

/** O2-O8. O1 is the system splash, held by MainActivity. */
enum class OnboardingStep { Welcome, Tour, Goal, GoalResponse, Timing, Plan, Permission }

/** O4 answer. [verse] is shown on O5 and becomes the first verse in the feed. Public-domain text from the bundled DB. */
enum class Goal(
    @StringRes val label: Int,
    @StringRes val detail: Int,
    @StringRes val responseTitle: Int,
    @StringRes val planPhrase: Int,
    val verse: VerseRef,
) {
    Peace(
        R.string.onb_goal_peace, R.string.onb_goal_peace_detail, R.string.onb_response_peace_title,
        R.string.onb_plan_phrase_peace, VerseRef(43, 14, 27), // John 14:27
    ),
    Understanding(
        R.string.onb_goal_understanding, R.string.onb_goal_understanding_detail, R.string.onb_response_understanding_title,
        R.string.onb_plan_phrase_understanding, VerseRef(19, 119, 105), // Psalm 119:105
    ),
    Routine(
        R.string.onb_goal_routine, R.string.onb_goal_routine_detail, R.string.onb_response_routine_title,
        R.string.onb_plan_phrase_routine, VerseRef(25, 3, 23), // Lamentations 3:23
    ),
}

/** O6 answer, mapped to O7's default reminder time. Null time = no reminder. */
enum class Timing(@StringRes val label: Int, @StringRes val phrase: Int, val defaultTime: LocalTime?) {
    Morning(R.string.onb_timing_morning, R.string.onb_when_morning, LocalTime.of(8, 0)),
    Break(R.string.onb_timing_break, R.string.onb_when_break, LocalTime.of(12, 30)),
    Bedtime(R.string.onb_timing_bed, R.string.onb_when_bed, LocalTime.of(21, 30)),
    Whenever(R.string.onb_timing_whenever, R.string.onb_when_whenever, null),
}
