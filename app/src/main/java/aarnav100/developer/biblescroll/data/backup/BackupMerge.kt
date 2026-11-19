package aarnav100.developer.biblescroll.data.backup

/** What an import brought in, for "Added 12 notes, 40 likes". */
data class ImportSummary(
    val notesAdded: Int = 0,
    val notesUpdated: Int = 0,
    val likesAdded: Int = 0,
    val bookmarksAdded: Int = 0,
    val savedStudiesAdded: Int = 0,
    val chaptersReadAdded: Int = 0,
    val plansAdded: Int = 0,
    val planDaysAdded: Int = 0,
    val devotionalsAdded: Int = 0,
    val streakDaysAdded: Int = 0,
    /** Every row written, including changes the counts above don't name (e.g. a newer reading place). */
    val rowsWritten: Int = 0,
    /** Settings this phone hadn't set, taken from the file. */
    val settingsApplied: Int = 0,
) {
    val nothingNew: Boolean get() = rowsWritten == 0 && settingsApplied == 0
}

class MergeResult(val write: BackupTables, val summary: ImportSummary)

/**
 * Merge rules (S9 Import). For each row of [incoming], by primary key:
 * - not on this phone: add it;
 * - on this phone with an older updated_at: replace it with the file's row;
 * - on this phone with the same or a newer updated_at: keep this phone's row.
 * Nothing on this phone is ever removed. Turned-off flags and custom-plan tombstones are rows like any other,
 * so a newer "unliked" or "deleted" wins, and an older one doesn't undo a newer change.
 * Deleted notes leave no row, so a note deleted here comes back if the file still has it.
 */
fun mergeBackup(existing: BackupTables, incoming: BackupTables): MergeResult {
    val marks = pick(existing.verseMarks, incoming.verseMarks, { Triple(it.bookId, it.chapter, it.verse) }, { it.updatedAt })
    val positions = pick(existing.readingPositions, incoming.readingPositions, { it.bookId }, { it.updatedAt })
    val chapters = pick(existing.chapterReads, incoming.chapterReads, { it.bookId to it.chapter }, { it.updatedAt })
    val notes = pick(existing.notes, incoming.notes, { Triple(it.bookId, it.chapter, it.verse) }, { it.updatedAt })
    val streak = pick(existing.streakDays, incoming.streakDays, { it.date }, { it.updatedAt })
    val enrollments = pick(existing.planEnrollments, incoming.planEnrollments, { it.id }, { it.updatedAt })
    val days = pick(existing.planDayProgress, incoming.planDayProgress, { it.enrollmentId to it.dayNumber }, { it.updatedAt })
    val custom = pick(existing.customPlans, incoming.customPlans, { it.id }, { it.updatedAt })
    val devotionals = pick(existing.devotionalProgress, incoming.devotionalProgress, { it.readFor }, { it.updatedAt })

    val write = BackupTables(
        verseMarks = marks.written,
        readingPositions = positions.written,
        chapterReads = chapters.written,
        notes = notes.written,
        streakDays = streak.written,
        planEnrollments = enrollments.written,
        planDayProgress = days.written,
        customPlans = custom.written,
        devotionalProgress = devotionals.written,
    )
    val summary = ImportSummary(
        notesAdded = notes.added.size,
        notesUpdated = notes.replaced.count { (old, new) -> old.text != new.text },
        likesAdded = marks.turnedOn { it.liked },
        bookmarksAdded = marks.turnedOn { it.bookmarked },
        savedStudiesAdded = marks.turnedOn { it.studySaved },
        chaptersReadAdded = chapters.turnedOn { it.read },
        plansAdded = enrollments.added.size + custom.added.count { !it.deleted },
        planDaysAdded = days.turnedOn { it.completed },
        devotionalsAdded = devotionals.added.size,
        streakDaysAdded = streak.added.count { it.countedAt != null },
        rowsWritten = write.rowCount,
    )
    return MergeResult(write, summary)
}

private class Picked<T>(val added: List<T>, val replaced: List<Pair<T, T>>) {
    val written: List<T> get() = added + replaced.map { it.second }

    /** Rows where [flag] is on in the written row and wasn't on this phone before. */
    fun turnedOn(flag: (T) -> Boolean): Int =
        added.count(flag) + replaced.count { (old, new) -> flag(new) && !flag(old) }
}

private fun <T, K> pick(existing: List<T>, incoming: List<T>, key: (T) -> K, updatedAt: (T) -> Long): Picked<T> {
    val here = existing.associateBy(key)
    val added = mutableListOf<T>()
    val replaced = mutableListOf<Pair<T, T>>()
    for (row in incoming) {
        val old = here[key(row)]
        when {
            old == null -> added += row
            updatedAt(row) > updatedAt(old) -> replaced += old to row
        }
    }
    return Picked(added, replaced)
}
