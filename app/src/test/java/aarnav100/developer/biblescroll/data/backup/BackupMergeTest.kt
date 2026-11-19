package aarnav100.developer.biblescroll.data.backup

import aarnav100.developer.biblescroll.testing.customPlan
import aarnav100.developer.biblescroll.testing.enrollment
import aarnav100.developer.biblescroll.testing.everyTable
import aarnav100.developer.biblescroll.testing.like
import aarnav100.developer.biblescroll.testing.note
import aarnav100.developer.biblescroll.testing.upsert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMergeTest {

    private fun mergedOnto(existing: BackupTables, incoming: BackupTables) =
        mergeBackup(existing, incoming).let { upsert(existing, it.write) to it.summary }

    @Test
    fun newRowsAreAdded() {
        val (after, summary) = mergedOnto(BackupTables(), everyTable())
        assertEquals(everyTable(), after)
        assertEquals(1, summary.notesAdded)
        assertEquals(1, summary.likesAdded)
        assertEquals(1, summary.bookmarksAdded)
        assertEquals(1, summary.savedStudiesAdded)
        assertEquals(1, summary.chaptersReadAdded)
        assertEquals(2, summary.plansAdded) // one started plan + one custom plan
        assertEquals(1, summary.planDaysAdded)
        assertEquals(1, summary.devotionalsAdded)
        assertEquals(1, summary.streakDaysAdded)
        assertEquals(everyTable().rowCount, summary.rowsWritten)
    }

    @Test
    fun newerRowFromFileReplacesOlderRowHere() {
        val here = BackupTables(notes = listOf(note(43, 3, 16, "old", at = 100)))
        val (after, summary) = mergedOnto(here, BackupTables(notes = listOf(note(43, 3, 16, "new", at = 200))))
        assertEquals("new", after.notes.single().text)
        assertEquals(1, summary.notesUpdated)
        assertEquals(0, summary.notesAdded)
    }

    @Test
    fun olderOrEqualRowFromFileKeepsThisPhonesRow() {
        val here = BackupTables(notes = listOf(note(43, 3, 16, "mine", at = 200), note(1, 1, 1, "tie here", at = 300)))
        val file = BackupTables(notes = listOf(note(43, 3, 16, "older", at = 100), note(1, 1, 1, "tie file", at = 300)))
        val (after, summary) = mergedOnto(here, file)
        assertEquals(setOf("mine", "tie here"), after.notes.map { it.text }.toSet())
        assertTrue(summary.nothingNew)
    }

    @Test
    fun neverRemovesRowsThatAreOnlyOnThisPhone() {
        val here = everyTable(at = 5_000)
        val (after, _) = mergedOnto(here, BackupTables(notes = listOf(note(1, 1, 1, "from file", at = 1))))
        assertEquals(here.rowCount + 1, after.rowCount)
        assertTrue(after.notes.containsAll(here.notes))
    }

    @Test
    fun newerUnlikeWinsButOlderUnlikeDoesNotUndoANewerLike() {
        val here = BackupTables(verseMarks = listOf(like(43, 3, 16, at = 100), like(1, 1, 1, at = 300)))
        val file = BackupTables(
            verseMarks = listOf(like(43, 3, 16, at = 200, liked = false), like(1, 1, 1, at = 200, liked = false)),
        )
        val (after, summary) = mergedOnto(here, file)
        val byVerse = after.verseMarks.associateBy { it.bookId }
        assertEquals(false, byVerse.getValue(43).liked)
        assertEquals(true, byVerse.getValue(1).liked)
        assertEquals(0, summary.likesAdded)
    }

    @Test
    fun likeCountsOnlyVersesThatWerentLikedHere() {
        val here = BackupTables(verseMarks = listOf(like(43, 3, 16, at = 100), like(1, 1, 1, at = 100, liked = false)))
        val file = BackupTables(verseMarks = listOf(like(43, 3, 16, at = 200), like(1, 1, 1, at = 200), like(19, 23, 1, at = 1)))
        assertEquals(2, mergeBackup(here, file).summary.likesAdded) // 1:1:1 turned on, 19:23:1 new; 43:3:16 already liked
    }

    @Test
    fun customPlanTombstoneFollowsTheNewerChange() {
        val here = BackupTables(customPlans = listOf(customPlan("custom-a", at = 100), customPlan("custom-b", at = 300, deleted = true)))
        val file = BackupTables(customPlans = listOf(customPlan("custom-a", at = 200, deleted = true), customPlan("custom-b", at = 200)))
        val (after, summary) = mergedOnto(here, file)
        val byId = after.customPlans.associateBy { it.id }
        assertTrue(byId.getValue("custom-a").deleted) // deleted later elsewhere
        assertTrue(byId.getValue("custom-b").deleted) // an older copy doesn't bring a deleted plan back
        assertEquals(0, summary.plansAdded)
    }

    @Test
    fun enrollmentsFromAnotherPhoneSitAlongsideThisPhonesOwn() {
        val here = BackupTables(planEnrollments = listOf(enrollment("here", at = 100)))
        val (after, summary) = mergedOnto(here, BackupTables(planEnrollments = listOf(enrollment("there", at = 50))))
        assertEquals(setOf("here", "there"), after.planEnrollments.map { it.id }.toSet())
        assertEquals(1, summary.plansAdded)
    }

    @Test
    fun importingTheSameFileTwiceChangesNothingTheSecondTime() {
        val (once, _) = mergedOnto(BackupTables(), everyTable())
        val (twice, summary) = mergedOnto(once, everyTable())
        assertEquals(once, twice)
        assertTrue(summary.nothingNew)
    }
}
