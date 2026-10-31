package com.example.accounting.core.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupValidatorTest {
    private val good = BackupFacts(
        headerIsSqlite = true,
        sizeBytes = 8192,
        userVersion = 1,
        integrityOk = true,
        tables = BackupValidator.REQUIRED_TABLES + "room_master_table",
    )

    private fun reason(facts: BackupFacts, expected: Int = 1): BackupReject? =
        (BackupValidator.check(facts, expected) as? BackupVerdict.Reject)?.reason

    @Test
    fun ownSchemaFileIsAccepted() {
        assertEquals(BackupVerdict.Accept, BackupValidator.check(good, 1))
    }

    @Test
    fun foreignFileWithoutSqliteHeaderIsRejectedFirst() {
        assertEquals(BackupReject.NOT_SQLITE, reason(good.copy(headerIsSqlite = false, userVersion = 9)))
    }

    @Test
    fun tinyFileIsRejected() {
        assertEquals(BackupReject.TOO_SMALL, reason(good.copy(sizeBytes = 100)))
    }

    @Test
    fun failedIntegrityCheckIsCorrupt() {
        assertEquals(BackupReject.CORRUPT, reason(good.copy(integrityOk = false)))
    }

    @Test
    fun otherSchemaVersionIsRejectedBothWays() {
        assertEquals(BackupReject.WRONG_SCHEMA_VERSION, reason(good.copy(userVersion = 2)))
        assertEquals(BackupReject.WRONG_SCHEMA_VERSION, reason(good.copy(userVersion = 0)))
        assertEquals(BackupReject.WRONG_SCHEMA_VERSION, reason(good, expected = 2))
    }

    @Test
    fun sqliteFileWithoutLedgerTablesIsRejected() {
        val other = good.copy(tables = setOf("notes", "room_master_table"))
        assertEquals(BackupReject.MISSING_TABLES, reason(other))
    }

    @Test
    fun missingOneRequiredTableIsRejected() {
        val missing = good.copy(tables = BackupValidator.REQUIRED_TABLES - "journal_line")
        assertEquals(BackupReject.MISSING_TABLES, reason(missing))
    }

    @Test
    fun headerCheckMatchesSqliteMagic() {
        val header = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(84)
        assertTrue(BackupValidator.isSqliteHeader(header))
        assertFalse(BackupValidator.isSqliteHeader("PK\u0003\u0004 zip file header".toByteArray(Charsets.US_ASCII)))
        assertFalse(BackupValidator.isSqliteHeader(ByteArray(3)))
    }
}
