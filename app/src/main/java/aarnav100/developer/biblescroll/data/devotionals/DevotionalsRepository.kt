package aarnav100.developer.biblescroll.data.devotionals

import aarnav100.developer.biblescroll.data.settings.ContentLanguage
import aarnav100.developer.biblescroll.core.content.DevotionalState
import aarnav100.developer.biblescroll.core.content.DevotionalMonthState
import aarnav100.developer.biblescroll.core.content.DevotionalRepository
import aarnav100.developer.biblescroll.core.content.model.Devotional
import aarnav100.developer.biblescroll.data.settings.SettingsRepository
import aarnav100.developer.biblescroll.data.streak.StreakRepository
import aarnav100.developer.biblescroll.domain.streak.StreakActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** H15-H17 data: content (core/content), what's been read (user.db), and the Hide AI studies choice. */
@Singleton
class DevotionalsRepository internal constructor(
    val content: DevotionalRepository,
    private val progress: DevotionalProgressDao,
    private val streaks: StreakRepository,
    private val settings: SettingsRepository,
    private val clock: () -> Long,
) {
    @Inject
    constructor(content: DevotionalRepository, progress: DevotionalProgressDao, streaks: StreakRepository, settings: SettingsRepository) :
        this(content, progress, streaks, settings, System::currentTimeMillis)

    /** One day's devotional ("MM-DD") in the app's language (English where it isn't translated). */
    fun devotional(date: String): Flow<DevotionalState> =
        combine(content.devotional(date), ContentLanguage.code) { state, lang ->
            if (state is DevotionalState.Available) DevotionalState.Available(state.devotional.localized(lang)) else state
        }

    /** A month ("01".."12") in the app's language. */
    fun month(month: String): Flow<DevotionalMonthState> =
        combine(content.month(month), ContentLanguage.code) { state, lang ->
            if (state is DevotionalMonthState.Available) {
                DevotionalMonthState.Available(state.devotionals.mapValues { it.value.localized(lang) })
            } else {
                state
            }
        }

    /** The local date, re-emitted at midnight and on time-zone or clock changes (shared with the streak). */
    fun today(): Flow<LocalDate> = streaks.today()

    /** Dates in [year] whose devotional was finished. */
    fun finished(year: Int): Flow<Set<LocalDate>> =
        progress.observeYear(year.toString()).map { rows -> rows.map(LocalDate::parse).toSet() }.distinctUntilChanged()

    /**
     * Devotionals are AI-assisted. With "Hide AI studies" on they're hidden behind a short note until the reader
     * chooses to show them (remembered). True = show them.
     */
    fun visible(): Flow<Boolean> = combine(settings.settings, settings.showDevotionalsWhenAiHidden) { s, anyway ->
        !s.hideAiStudies || anyway
    }.distinctUntilChanged()

    suspend fun showAnyway() = settings.setShowDevotionalsWhenAiHidden(true)

    /** H16 finished: saves progress and counts towards the streak, the first time only. Returns whether it was new. */
    suspend fun markFinished(date: LocalDate, devotional: Devotional): Boolean {
        val now = clock()
        val inserted = progress.insertIfAbsent(
            DevotionalProgressEntity(date.toString(), devotional.id, devotional.contentVersion, now, now),
        ) != -1L
        if (inserted) streaks.record(StreakActivity.DevotionalFinished)
        return inserted
    }
}
