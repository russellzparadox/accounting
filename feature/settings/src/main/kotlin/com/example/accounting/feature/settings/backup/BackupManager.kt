package com.example.accounting.feature.settings.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.example.accounting.core.data.db.AppDatabase
import com.example.accounting.core.domain.backup.BackupFacts
import com.example.accounting.core.domain.backup.BackupReject
import com.example.accounting.core.domain.backup.BackupValidator
import com.example.accounting.core.domain.backup.BackupVerdict
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where a backup operation failed. Used only to pick the Persian message. */
enum class BackupStage { READ, WRITE, SWAP }

sealed interface BackupResult {
    data object Ok : BackupResult
    data class Rejected(val reason: BackupReject) : BackupResult
    data class Failed(val stage: BackupStage) : BackupResult
}

/**
 * Export and restore of the whole ledger database file.
 *
 * Export: checkpoints the WAL so the main file holds every committed row, then copies it.
 * Restore: copies the candidate to a staging file in the same folder, validates it (header, integrity, schema
 * version, tables), and only then closes the live database, keeps a safety copy, and renames the staging file over
 * the live one (atomic on the same filesystem). Any failure during the swap puts the safety copy back.
 * The live database is closed after a restore; the caller must restart the app (no reopen in place).
 */
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
) {
    private val dbFile: File get() = context.getDatabasePath(AppDatabase.FILE_NAME)
    private val safetyFile: File get() = File(dbFile.parentFile, SAFETY_NAME)
    private val stagingFile: File get() = File(dbFile.parentFile, STAGING_NAME)

    /** [openOutput] is called once; the stream is closed here. Null means the target could not be opened. */
    suspend fun export(openOutput: () -> OutputStream?): BackupResult = withContext(Dispatchers.IO) {
        try {
            checkpoint()
            val out = openOutput() ?: return@withContext BackupResult.Failed(BackupStage.WRITE)
            out.use { stream ->
                dbFile.inputStream().use { it.copyTo(stream) }
                stream.flush()
            }
            BackupResult.Ok
        } catch (e: IOException) {
            BackupResult.Failed(BackupStage.WRITE)
        } catch (e: SQLiteException) {
            BackupResult.Failed(BackupStage.WRITE)
        }
    }

    /** [openInput] is called once; the stream is closed here. */
    suspend fun restore(openInput: () -> InputStream?): BackupResult = withContext(Dispatchers.IO) {
        val staged = stagingFile
        try {
            val input = openInput() ?: return@withContext BackupResult.Failed(BackupStage.READ)
            input.use { src -> staged.outputStream().use { dst -> src.copyTo(dst) } }
        } catch (e: IOException) {
            staged.delete()
            return@withContext BackupResult.Failed(BackupStage.READ)
        }
        val verdict = BackupValidator.check(readFacts(staged), AppDatabase.SCHEMA_VERSION)
        if (verdict is BackupVerdict.Reject) {
            deleteWithSidecars(staged)
            return@withContext BackupResult.Rejected(verdict.reason)
        }
        swap(staged)
    }

    private fun checkpoint() {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
    }

    /** Reads what the validator needs. Never writes to the file. */
    private fun readFacts(file: File): BackupFacts {
        val size = file.length()
        val header = ByteArray(HEADER_BYTES)
        val read = file.inputStream().use { it.read(header) }
        if (read < HEADER_BYTES || !BackupValidator.isSqliteHeader(header)) {
            return BackupFacts(headerIsSqlite = false, sizeBytes = size, userVersion = 0, integrityOk = false, tables = emptySet())
        }
        return try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
                val version = sqlite.rawQuery("PRAGMA user_version", null).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else -1
                }
                val integrity = sqlite.rawQuery("PRAGMA integrity_check", null).use { c ->
                    c.moveToFirst() && c.getString(0) == "ok"
                }
                val tables = sqlite.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                    buildSet { while (c.moveToNext()) add(c.getString(0)) }
                }
                BackupFacts(headerIsSqlite = true, sizeBytes = size, userVersion = version, integrityOk = integrity, tables = tables)
            }
        } catch (e: SQLiteException) {
            // Damaged pages can make even the open or the version query fail. Report as corrupt.
            BackupFacts(headerIsSqlite = true, sizeBytes = size, userVersion = -1, integrityOk = false, tables = emptySet())
        } finally {
            deleteSidecarsOnly(file)
        }
    }

    private fun swap(staged: File): BackupResult {
        db.close()
        try {
            dbFile.copyTo(safetyFile, overwrite = true)
            deleteWithSidecars(dbFile)
            Files.move(
                staged.toPath(),
                dbFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
            return BackupResult.Ok
        } catch (e: IOException) {
            // Roll back to the safety copy so the live file is not left half-replaced.
            try {
                safetyFile.copyTo(dbFile, overwrite = true)
            } catch (ignored: IOException) {
                // Safety copy stays in place for manual recovery; the error is reported as SWAP.
            }
            staged.delete()
            return BackupResult.Failed(BackupStage.SWAP)
        }
    }

    private fun deleteSidecarsOnly(file: File) {
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
    }

    private fun deleteWithSidecars(file: File) {
        file.delete()
        deleteSidecarsOnly(file)
    }

    companion object {
        /** Kept after every successful restore, inside the app's private database folder. */
        const val SAFETY_NAME = "accounting.db.before-restore"
        const val STAGING_NAME = "accounting.db.restore-staging"
        private const val HEADER_BYTES = 16
    }
}
