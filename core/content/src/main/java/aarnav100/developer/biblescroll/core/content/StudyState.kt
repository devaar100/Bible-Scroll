package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.Study

/** What a verse's study screen should show. Never an exception: every failure is a state. */
sealed interface StudyState {
    /** Reading local storage ([progress] null), or downloading the verse's book ([progress] set). */
    data class Loading(val progress: DownloadProgress? = null) : StudyState

    data class Available(val study: Study) : StudyState

    /** The book isn't on the device yet. */
    data class NotDownloaded(val bookId: Int, val reason: Reason) : StudyState {
        enum class Reason {
            /** No connection: "not available offline". Downloads start by themselves once online. */
            OFFLINE,
            /** The download failed (server error or a file that didn't verify). Offer retry. */
            DOWNLOAD_FAILED,
        }
    }

    /** There's nothing to show for this verse. */
    data class Unavailable(val reason: Reason) : StudyState {
        enum class Reason {
            /** No published study: the verse or its book isn't written, checked or reviewed yet. */
            NO_STUDY,
            /** Published content is in a newer format than this app version reads. */
            APP_UPDATE_REQUIRED,
        }
    }
}
