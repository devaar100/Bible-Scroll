package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.Devotional
import aarnav100.developer.biblescroll.core.content.net.NetworkMonitor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.json.Json

/** What a devotional screen should show. Never an exception: every failure is a state (as [StudyState]). */
sealed interface DevotionalState {
    data class Loading(val progress: DownloadProgress? = null) : DevotionalState
    data class Available(val devotional: Devotional) : DevotionalState

    /** The month isn't on the device yet. */
    data class NotDownloaded(val month: String, val reason: StudyState.NotDownloaded.Reason) : DevotionalState

    data class Unavailable(val reason: Reason) : DevotionalState {
        enum class Reason {
            /** Nothing published for this day (or month) yet. */
            NOT_PUBLISHED,
            APP_UPDATE_REQUIRED,
        }
    }
}

/** A whole month, for the archive (H17). */
sealed interface DevotionalMonthState {
    data class Loading(val progress: DownloadProgress? = null) : DevotionalMonthState
    /** Published days of the month, by date ("MM-DD"). May be partial: some days may not be published yet. */
    data class Available(val devotionals: Map<String, Devotional>) : DevotionalMonthState
    data class NotDownloaded(val reason: StudyState.NotDownloaded.Reason) : DevotionalMonthState
    data class Unavailable(val reason: DevotionalState.Unavailable.Reason) : DevotionalMonthState
}

/**
 * Devotionals by day and by month. Asking for a month that isn't on the device starts its download (when
 * online), like opening a study in a book that isn't downloaded.
 */
class DevotionalRepository(
    private val store: DevotionalStore,
    private val downloader: DevotionalDownloader,
    private val network: NetworkMonitor,
    private val json: Json,
    private val decodeDispatcher: CoroutineDispatcher,
) {
    /** [date] is "MM-DD". */
    fun devotional(date: String): Flow<DevotionalState> {
        val month = date.take(2)
        return combine(store.observeJson(date), month(month)) { json, monthState ->
            when {
                json != null -> decode(json)?.let { DevotionalState.Available(it) }
                    ?: DevotionalState.Unavailable(DevotionalState.Unavailable.Reason.NOT_PUBLISHED)
                else -> when (monthState) {
                    is DevotionalMonthState.Available -> DevotionalState.Unavailable(DevotionalState.Unavailable.Reason.NOT_PUBLISHED)
                    is DevotionalMonthState.Loading -> DevotionalState.Loading(monthState.progress)
                    is DevotionalMonthState.NotDownloaded -> DevotionalState.NotDownloaded(month, monthState.reason)
                    is DevotionalMonthState.Unavailable -> DevotionalState.Unavailable(monthState.reason)
                }
            }
        }.onStart { emit(DevotionalState.Loading()) }.distinctUntilChanged().flowOn(decodeDispatcher)
    }

    /** [month] is "01".."12". */
    fun month(month: String): Flow<DevotionalMonthState> = combine(
        store.observeMonth(month), store.observeMonthJson(month), downloader.state(month), network.isOnline,
    ) { stored, rows, download, online ->
        if (stored != null) {
            return@combine DevotionalMonthState.Available(rows.mapNotNull(::decode).associateBy { it.date })
        }
        when (download) {
            is DownloadState.Downloading -> DevotionalMonthState.Loading(download.progress)
            DownloadState.Done -> DevotionalMonthState.Loading()
            is DownloadState.Failed -> when (download.reason) {
                DownloadFailure.NOT_PUBLISHED -> DevotionalMonthState.Unavailable(DevotionalState.Unavailable.Reason.NOT_PUBLISHED)
                DownloadFailure.APP_UPDATE_REQUIRED -> DevotionalMonthState.Unavailable(DevotionalState.Unavailable.Reason.APP_UPDATE_REQUIRED)
                DownloadFailure.NETWORK, DownloadFailure.INTEGRITY -> DevotionalMonthState.NotDownloaded(
                    if (!online) StudyState.NotDownloaded.Reason.OFFLINE else StudyState.NotDownloaded.Reason.DOWNLOAD_FAILED,
                )
            }
            DownloadState.Idle -> if (online) {
                downloader.ensure(month)
                DevotionalMonthState.Loading(DownloadProgress(0, null))
            } else {
                DevotionalMonthState.NotDownloaded(StudyState.NotDownloaded.Reason.OFFLINE)
            }
        }
    }.distinctUntilChanged().flowOn(decodeDispatcher)

    fun retry(month: String) = downloader.retry(month)

    private fun decode(text: String): Devotional? = runCatching { json.decodeFromString(Devotional.serializer(), text) }.getOrNull()
}
