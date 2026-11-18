package aarnav100.developer.biblescroll.data.library

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.user.LibraryCounts
import aarnav100.developer.biblescroll.data.user.NoteEntity
import aarnav100.developer.biblescroll.data.user.UserDao
import aarnav100.developer.biblescroll.data.user.VerseKey
import aarnav100.developer.biblescroll.data.user.VerseMarkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** H4-H7. H6 is one screen with a filter chip, but Liked and Bookmarked are separate lists. */
enum class LibraryList { SAVED_STUDIES, STUDIED, LIKED, BOOKMARKED, NOTES }

enum class LibrarySort { BIBLE, RECENT }

/** One row: the verse, when it was added (for "Most recent"), and the note text for H7. */
data class LibraryEntry(val ref: VerseRef, val addedAt: Long, val note: String? = null)

/** Everything needed to put a removed item back exactly as it was (Undo). */
sealed interface Removal {
    data class Flag(val list: LibraryList, val ref: VerseRef, val addedAt: Long?) : Removal
    data class Note(val note: NoteEntity) : Removal
}

/** The Library (H3-H7): live Room flows over the user DB, plus remove/undo. */
@Singleton
class LibraryRepository internal constructor(
    private val dao: UserDao,
    private val clock: () -> Long,
) {
    @Inject
    constructor(dao: UserDao) : this(dao, System::currentTimeMillis)

    fun counts(): Flow<LibraryCounts> = dao.observeLibraryCounts()

    /** [query] filters Notes by their text (case-insensitive for ASCII; ignored for other lists). */
    fun entries(list: LibraryList, sort: LibrarySort, query: String = ""): Flow<List<LibraryEntry>> {
        val rows: Flow<List<LibraryEntry>> = when (list) {
            LibraryList.NOTES -> {
                val notes = if (query.isBlank()) dao.observeNotes() else dao.searchNotes(escapeLike(query.trim()))
                notes.map { list -> list.map { LibraryEntry(VerseRef(it.bookId, it.chapter, it.verse), it.updatedAt, it.text) } }
            }
            else -> marks(list).map { marks -> marks.map { LibraryEntry(it.ref(), it.addedAt(list)) } }
        }
        return rows.map { it.sortedWith(comparator(sort)) }
    }

    /** Turn the item off (marks keep their row so the change can sync; notes are deleted). */
    suspend fun remove(list: LibraryList, ref: VerseRef): Removal? {
        val key = VerseKey(ref.bookId, ref.chapter, ref.verse)
        if (list == LibraryList.NOTES) {
            val note = dao.getNote(ref.bookId, ref.chapter, ref.verse) ?: return null
            dao.deleteNote(ref.bookId, ref.chapter, ref.verse)
            return Removal.Note(note)
        }
        val mark = dao.getMark(ref.bookId, ref.chapter, ref.verse) ?: return null
        if (!mark.has(list)) return null
        dao.updateMark(key, clock()) { it.with(list, on = false) }
        return Removal.Flag(list, ref, mark.addedAtRaw(list))
    }

    /** Put a removed item back, with its original date so it returns to the same place in "Most recent". */
    suspend fun undo(removal: Removal) {
        when (removal) {
            is Removal.Note -> dao.upsertNote(removal.note)
            is Removal.Flag -> {
                val r = removal.ref
                dao.updateMark(VerseKey(r.bookId, r.chapter, r.verse), clock()) {
                    it.with(removal.list, on = true, at = removal.addedAt)
                }
            }
        }
    }

    private fun marks(list: LibraryList): Flow<List<VerseMarkEntity>> = when (list) {
        LibraryList.SAVED_STUDIES -> dao.observeSavedStudies()
        LibraryList.STUDIED -> dao.observeStudied()
        LibraryList.LIKED -> dao.observeLiked()
        LibraryList.BOOKMARKED -> dao.observeBookmarked()
        LibraryList.NOTES -> error("notes aren't marks")
    }

    companion object {
        fun comparator(sort: LibrarySort): Comparator<LibraryEntry> = when (sort) {
            LibrarySort.BIBLE -> compareBy({ it.ref.bookId }, { it.ref.chapter }, { it.ref.verse })
            LibrarySort.RECENT -> compareByDescending<LibraryEntry> { it.addedAt }
                .thenBy { it.ref.bookId }.thenBy { it.ref.chapter }.thenBy { it.ref.verse }
        }

        /** Escape LIKE wildcards so a search for "50%" or "a_b" matches literally. */
        fun escapeLike(text: String): String =
            text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}

private fun VerseMarkEntity.ref() = VerseRef(bookId, chapter, verse)

private fun VerseMarkEntity.has(list: LibraryList) = when (list) {
    LibraryList.SAVED_STUDIES -> studySaved
    LibraryList.STUDIED -> studied
    LibraryList.LIKED -> liked
    LibraryList.BOOKMARKED -> bookmarked
    LibraryList.NOTES -> false
}

private fun VerseMarkEntity.addedAtRaw(list: LibraryList): Long? = when (list) {
    LibraryList.SAVED_STUDIES -> studySavedAt
    LibraryList.STUDIED -> studiedAt
    LibraryList.LIKED -> likedAt
    LibraryList.BOOKMARKED -> bookmarkedAt
    LibraryList.NOTES -> null
}

/** Rows from before per-flag dates existed fall back to the row's last update. */
private fun VerseMarkEntity.addedAt(list: LibraryList): Long = addedAtRaw(list) ?: updatedAt

private fun VerseMarkEntity.with(list: LibraryList, on: Boolean, at: Long? = addedAtRaw(list)): VerseMarkEntity = when (list) {
    LibraryList.SAVED_STUDIES -> copy(studySaved = on, studySavedAt = at)
    LibraryList.STUDIED -> copy(studied = on, studiedAt = at)
    LibraryList.LIKED -> copy(liked = on, likedAt = at)
    LibraryList.BOOKMARKED -> copy(bookmarked = on, bookmarkedAt = at)
    LibraryList.NOTES -> this
}
