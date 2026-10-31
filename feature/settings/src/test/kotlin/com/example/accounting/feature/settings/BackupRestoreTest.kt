package com.example.accounting.feature.settings

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.accounting.core.data.db.AppDatabase
import com.example.accounting.core.data.repo.LedgerRepository
import com.example.accounting.core.data.seed.ChartSeeder
import com.example.accounting.core.domain.ChartOfAccounts
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.domain.JournalLine
import com.example.accounting.core.domain.Money
import com.example.accounting.core.domain.Side
import com.example.accounting.core.domain.TemplateId
import com.example.accounting.core.domain.backup.BackupReject
import com.example.accounting.feature.settings.backup.BackupManager
import com.example.accounting.feature.settings.backup.BackupResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Backup, modify, restore on a real file-backed Room database; and rejection of bad files. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRestoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val chart = ChartOfAccounts.standard()
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var manager: BackupManager

    @Before
    fun setUp() = runBlocking {
        context.deleteDatabase(AppDatabase.FILE_NAME)
        db = AppDatabase.file(context)
        ChartSeeder(db, chart).seed()
        ledger = LedgerRepository(db, chart)
        ledger.ensureFiscalYear(1404)
        manager = BackupManager(context, db)
    }

    @After
    fun tearDown() {
        runCatching { db.close() }
        context.deleteDatabase(AppDatabase.FILE_NAME)
    }

    private fun sale(rial: Long) = JournalEntry(
        id = UUID.randomUUID().toString(),
        date = LocalDate.of(2026, 1, 10),
        templateId = TemplateId.SALE_CASH,
        memo = null,
        lines = listOf(
            JournalLine("1100", Side.DEBIT, Money(rial)),
            JournalLine("4100", Side.CREDIT, Money(rial)),
        ),
    )

    /** Row count read through a fresh connection, so it sees what is on disk. */
    private fun entryCount(): Int {
        val fresh = AppDatabase.file(context)
        try {
            fresh.openHelper.readableDatabase.query("SELECT COUNT(*) FROM journal_entry").use {
                it.moveToFirst()
                return it.getInt(0)
            }
        } finally {
            fresh.close()
        }
    }

    private fun candidate(build: (SQLiteDatabase) -> Unit): ByteArray {
        val file = File(context.cacheDir, "candidate-${UUID.randomUUID()}.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { build(it) }
        return file.readBytes().also { file.delete() }
    }

    @Test
    fun backupThenModifyThenRestoreReturnsToTheBackedUpState() = runBlocking {
        ledger.commit(sale(1_000))
        val backup = ByteArrayOutputStream()
        assertEquals(BackupResult.Ok, manager.export { backup })

        ledger.commit(sale(2_000))
        assertEquals(2, entryCount())

        val result = manager.restore { ByteArrayInputStream(backup.toByteArray()) }

        assertEquals(BackupResult.Ok, result)
        assertEquals(1, entryCount())
        assertTrue(File(context.getDatabasePath(AppDatabase.FILE_NAME).parentFile, BackupManager.SAFETY_NAME).exists())
    }

    @Test
    fun foreignTextFileIsRejectedAndLedgerIsUntouched() = runBlocking {
        ledger.commit(sale(1_000))
        val result = manager.restore { "not a database at all ".repeat(200).byteInputStream() }
        assertEquals(BackupResult.Rejected(BackupReject.NOT_SQLITE), result)
        assertEquals(1, entryCount())
    }

    @Test
    fun sqliteFileWithoutLedgerTablesIsRejected() = runBlocking {
        ledger.commit(sale(1_000))
        val bytes = candidate { it.execSQL("CREATE TABLE notes (x INTEGER)") }
        assertEquals(BackupResult.Rejected(BackupReject.MISSING_TABLES), manager.restore { ByteArrayInputStream(bytes) })
        assertEquals(1, entryCount())
    }

    @Test
    fun ledgerTablesWithOtherSchemaVersionAreRejected() = runBlocking {
        ledger.commit(sale(1_000))
        val bytes = candidate {
            listOf("account", "party", "fiscal_year", "doc_sequence", "journal_entry", "journal_line")
                .forEach { table -> it.execSQL("CREATE TABLE $table (x INTEGER)") }
            it.version = 7
        }
        assertEquals(
            BackupResult.Rejected(BackupReject.WRONG_SCHEMA_VERSION),
            manager.restore { ByteArrayInputStream(bytes) },
        )
        assertEquals(1, entryCount())
    }

    @Test
    fun truncatedBackupIsRejectedAndLedgerIsUntouched() = runBlocking {
        ledger.commit(sale(1_000))
        val backup = ByteArrayOutputStream()
        manager.export { backup }
        val truncated = backup.toByteArray().copyOf(4096)

        val result = manager.restore { ByteArrayInputStream(truncated) }

        assertTrue("expected rejection, got $result", result is BackupResult.Rejected)
        assertEquals(1, entryCount())
    }
}
