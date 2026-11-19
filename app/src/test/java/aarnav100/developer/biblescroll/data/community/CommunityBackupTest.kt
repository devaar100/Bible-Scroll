package aarnav100.developer.biblescroll.data.community

import aarnav100.developer.biblescroll.data.backup.BACKUP_FORMAT
import aarnav100.developer.biblescroll.data.backup.BackupFile
import aarnav100.developer.biblescroll.data.backup.BackupJson
import aarnav100.developer.biblescroll.data.backup.BackupTables
import aarnav100.developer.biblescroll.data.backup.BackupValidator
import aarnav100.developer.biblescroll.data.backup.CommunityVoteRow
import aarnav100.developer.biblescroll.data.backup.Validated
import aarnav100.developer.biblescroll.data.backup.mergeBackup
import aarnav100.developer.biblescroll.data.backup.toEntity
import aarnav100.developer.biblescroll.data.backup.toRow
import aarnav100.developer.biblescroll.data.user.UserDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Community votes in S9 export/import: one per month and final, so import only adds months this phone hasn't voted in. */
class CommunityBackupTest {

    private fun row(month: String, ministry: String, updatedAt: Long, sent: Boolean = true) =
        CommunityVoteRow(month, ministry, votedAt = updatedAt, sent = sent, updatedAt = updatedAt)

    @Test
    fun importAddsMissingMonthsAndNeverReplacesAVoteHere() {
        val here = BackupTables(communityVotes = listOf(row("2026-12", "river", 100)))
        val file = BackupTables(communityVotes = listOf(row("2026-12", "harbor", 999), row("2026-11", "harbor", 50)))

        val write = mergeBackup(here, file).write.communityVotes

        // December keeps this phone's vote even though the file's is newer; November is added.
        assertEquals(listOf(row("2026-11", "harbor", 50)), write)
    }

    @Test
    fun importingTheSameFileTwiceAddsNothingTheSecondTime() {
        val file = BackupTables(communityVotes = listOf(row("2026-11", "harbor", 50)))
        val first = mergeBackup(BackupTables(), file).write
        assertEquals(1, first.communityVotes.size)
        assertTrue(mergeBackup(first, file).write.communityVotes.isEmpty())
    }

    @Test
    fun rowsRoundTripThroughTheEntity() {
        val r = row("2026-12", "river", 7, sent = false)
        assertEquals(r, r.toEntity().toRow())
    }

    private fun validate(vararg votes: CommunityVoteRow): Validated = BackupValidator.validate(
        BackupJson.encodeToString(
            BackupFile.serializer(),
            BackupFile(BACKUP_FORMAT, UserDatabase.VERSION, "1.0", "2026-12-01T00:00:00Z", BackupTables(communityVotes = votes.toList())),
        ),
    )

    @Test
    fun validatorChecksMonthIdAndDuplicates() {
        assertTrue(validate(row("2026-12", "river", 1)) is Validated.Ok)
        for (bad in listOf(row("2026-13", "river", 1), row("12-2026", "river", 1), row("2026-12", "../x", 1), row("2026-12", "river", -1))) {
            assertTrue("$bad should be rejected", validate(bad) is Validated.Rejected)
        }
        assertTrue(validate(row("2026-12", "river", 1), row("2026-12", "harbor", 2)) is Validated.Rejected)
    }
}
