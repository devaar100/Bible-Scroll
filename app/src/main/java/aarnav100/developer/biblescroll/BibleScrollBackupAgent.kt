package aarnav100.developer.biblescroll

import aarnav100.developer.biblescroll.data.backup.RestoreMarker
import aarnav100.developer.biblescroll.data.user.UserDatabase
import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.database.sqlite.SQLiteDatabase
import android.os.ParcelFileDescriptor
import android.util.Log

/**
 * Auto Backup with two additions (manifest: fullBackupOnly="true", so the XML rules still decide what's copied).
 * - Before a backup, checkpoint user.db's write-ahead log so the backed-up .db file holds everything. The app
 *   isn't running during backup, so the .db and -wal pair is consistent even if the checkpoint can't run.
 * - After a restore, leave a marker in noBackupFilesDir so the next launch can tidy up (RestoreFixups).
 *
 * Runs in a restricted process without Hilt or the app's Application class: keep it to plain file and SQLite work.
 */
class BibleScrollBackupAgent : BackupAgent() {

    override fun onFullBackup(data: FullBackupDataOutput) {
        checkpointUserDb()
        super.onFullBackup(data)
    }

    override fun onRestoreFinished() {
        RestoreMarker.set(this)
    }

    private fun checkpointUserDb() {
        val file = getDatabasePath(UserDatabase.FILE_NAME)
        if (!file.exists()) return
        try {
            // A no-op error handler: the default one would delete a database it thinks is corrupt.
            SQLiteDatabase.openDatabase(
                file.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING,
            ) { }.use { db ->
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            }
        } catch (e: Exception) {
            Log.w("BackupAgent", "WAL checkpoint skipped; backing up .db and -wal as they are", e)
        }
    }

    // Key-value backup isn't used (fullBackupOnly).
    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) = Unit
    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) = Unit
}
