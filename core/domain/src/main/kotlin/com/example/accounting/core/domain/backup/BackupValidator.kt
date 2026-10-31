package com.example.accounting.core.domain.backup

/** Why a backup file is refused. Shown to the user as Persian text by feature:settings. */
enum class BackupReject { NOT_SQLITE, TOO_SMALL, CORRUPT, WRONG_SCHEMA_VERSION, MISSING_TABLES }

/** What the Android side read from the candidate file. Filled by BackupManager, judged by [BackupValidator]. */
data class BackupFacts(
    val headerIsSqlite: Boolean,
    val sizeBytes: Long,
    /** PRAGMA user_version. Room writes the schema version here. */
    val userVersion: Int,
    /** PRAGMA integrity_check returned exactly "ok". */
    val integrityOk: Boolean,
    /** Names of all tables in the file. */
    val tables: Set<String>,
)

sealed interface BackupVerdict {
    data object Accept : BackupVerdict
    data class Reject(val reason: BackupReject) : BackupVerdict
}

/**
 * Restore gate. Order matters: a foreign or damaged file is reported as such before its schema version is looked at.
 * The file is checked on a temp copy; nothing replaces the live database unless this returns [BackupVerdict.Accept].
 */
object BackupValidator {
    /** Table names of the v1 schema (`@Entity(tableName = ...)` in core:data). */
    val REQUIRED_TABLES: Set<String> = setOf(
        "account",
        "party",
        "fiscal_year",
        "doc_sequence",
        "journal_entry",
        "journal_line",
    )

    /** An empty SQLite file is one page of zeros at most; anything smaller cannot be a database. */
    const val MIN_SIZE_BYTES: Long = 512L

    fun check(facts: BackupFacts, expectedSchemaVersion: Int): BackupVerdict = when {
        !facts.headerIsSqlite -> BackupVerdict.Reject(BackupReject.NOT_SQLITE)
        facts.sizeBytes < MIN_SIZE_BYTES -> BackupVerdict.Reject(BackupReject.TOO_SMALL)
        !facts.integrityOk -> BackupVerdict.Reject(BackupReject.CORRUPT)
        facts.userVersion != expectedSchemaVersion -> BackupVerdict.Reject(BackupReject.WRONG_SCHEMA_VERSION)
        !facts.tables.containsAll(REQUIRED_TABLES) -> BackupVerdict.Reject(BackupReject.MISSING_TABLES)
        else -> BackupVerdict.Accept
    }

    /** First 16 bytes of every SQLite 3 database. */
    val SQLITE_MAGIC: ByteArray = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun isSqliteHeader(first16: ByteArray): Boolean =
        first16.size >= SQLITE_MAGIC.size && SQLITE_MAGIC.indices.all { first16[it] == SQLITE_MAGIC[it] }
}
