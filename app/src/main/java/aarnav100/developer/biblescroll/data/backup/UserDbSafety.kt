package aarnav100.developer.biblescroll.data.backup

import aarnav100.developer.biblescroll.data.user.UserDatabase
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** user.db and its write-ahead log. -shm is an index SQLite rebuilds, so it's never copied. */
fun userDbFiles(context: Context): List<File> {
    val db = context.getDatabasePath(UserDatabase.FILE_NAME)
    return listOf(db, File(db.path + "-wal"))
}

/**
 * Opens user.db like the default factory, except that a corrupt file is never deleted. SQLite's default
 * handler deletes a database it finds corrupt and starts empty; here the files are copied to
 * noBackupFilesDir/user-db-rescue/ and left in place, so the open fails and [UserDbHealth] shows the error
 * screen (with "Save a copy") instead of an empty app.
 */
class NoDeleteOpenHelperFactory(
    private val rescueDir: File,
    private val delegate: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory(),
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val inner = configuration.callback
        val callback = object : SupportSQLiteOpenHelper.Callback(inner.version) {
            override fun onConfigure(db: SupportSQLiteDatabase) = inner.onConfigure(db)
            override fun onCreate(db: SupportSQLiteDatabase) = inner.onCreate(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                inner.onUpgrade(db, oldVersion, newVersion)
            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                inner.onDowngrade(db, oldVersion, newVersion)
            override fun onOpen(db: SupportSQLiteDatabase) = inner.onOpen(db)

            override fun onCorruption(db: SupportSQLiteDatabase) {
                Log.e(TAG, "user.db reported corrupt; keeping it and saving a rescue copy")
                db.path?.let { rescue(File(it)) }
                // Deliberately no super call: the default deletes the files.
            }
        }
        return delegate.create(
            SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name)
                .callback(callback)
                .noBackupDirectory(configuration.useNoBackupDirectory)
                .allowDataLossOnRecovery(false)
                .build(),
        )
    }

    private fun rescue(db: File) {
        try {
            val dir = File(rescueDir, System.currentTimeMillis().toString()).apply { mkdirs() }
            listOf(db, File(db.path + "-wal"), File(db.path + "-shm"))
                .filter { it.exists() }
                .forEach { it.copyTo(File(dir, it.name), overwrite = true) }
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't save a rescue copy of user.db", e)
        }
    }

    companion object {
        private const val TAG = "UserDb"
        fun rescueDir(context: Context) = File(context.noBackupFilesDir, "user-db-rescue")
    }
}

/** Whether user.db opens (and migrates). Checked once at start, before any screen reads it. */
@Singleton
class UserDbHealth @Inject constructor(
    private val db: UserDatabase,
    @ApplicationContext private val context: Context,
) {
    suspend fun opens(): Boolean = withContext(Dispatchers.IO) {
        try {
            db.openHelper.writableDatabase.query("SELECT count(*) FROM sqlite_master").use { it.moveToFirst() }
            true
        } catch (e: Exception) {
            // Room keeps the files: there's no destructive fallback, and corruption doesn't delete (above).
            Log.e("UserDb", "user.db failed to open", e)
            false
        }
    }

    /** Zips user.db and its WAL as they are on disk into [uri] (the "Save a copy" on the error screen). */
    suspend fun saveRawCopy(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val out = context.contentResolver.openOutputStream(uri) ?: return@withContext false
            ZipOutputStream(out).use { zip ->
                userDbFiles(context).filter { it.exists() }.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            true
        } catch (e: Exception) {
            Log.e("UserDb", "Couldn't save a copy of user.db", e)
            false
        }
    }
}
