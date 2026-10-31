package com.example.accounting.core.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// No @Update or @Delete on journal DAOs: committed documents are append-only.

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(accounts: List<AccountEntity>)

    @Insert
    suspend fun insert(account: AccountEntity)

    @Query("SELECT * FROM account ORDER BY code")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account WHERE code = :code")
    suspend fun find(code: String): AccountEntity?

    @Query("SELECT MAX(code) FROM account WHERE code BETWEEN :from AND :to")
    suspend fun maxCodeInRange(from: String, to: String): String?

    /** 1 when [code] is an existing non-group account with no معین children, else 0. */
    @Query(
        """SELECT COUNT(*) FROM account a
           WHERE a.code = :code AND a.level <> 'GROUP'
           AND NOT EXISTS (SELECT 1 FROM account c WHERE c.parent = a.code)""",
    )
    suspend fun isPostable(code: String): Int

    @Query("SELECT COUNT(*) FROM account")
    suspend fun count(): Int
}

@Dao
interface PartyDao {
    @Query("SELECT * FROM party WHERE kind = :kind AND party_key = :key")
    suspend fun find(kind: String, key: String): PartyEntity?

    @Insert
    suspend fun insert(party: PartyEntity)

    @Query("SELECT COUNT(*) FROM party")
    suspend fun count(): Int
}

@Dao
interface FiscalYearDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(year: FiscalYearEntity)

    @Query("SELECT * FROM fiscal_year WHERE jalali_year = :year")
    suspend fun find(year: Int): FiscalYearEntity?

    @Query("SELECT * FROM fiscal_year ORDER BY jalali_year")
    fun observeAll(): Flow<List<FiscalYearEntity>>
}

@Dao
interface DocSequenceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(sequence: DocSequenceEntity)

    @Query("SELECT last_number FROM doc_sequence WHERE fiscal_year = :year")
    suspend fun lastNumber(year: Int): Int?

    @Query("UPDATE doc_sequence SET last_number = :value WHERE fiscal_year = :year")
    suspend fun setLastNumber(year: Int, value: Int)
}

/** Entry with its lines, for Room @Relation. */
data class EntryWithLines(
    @Embedded val entry: JournalEntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entry_id")
    val lines: List<JournalLineEntity>,
)

data class LedgerRowTuple(
    val entryDate: String,
    val fiscalYear: Int,
    val docNumber: Int,
    val entryId: String,
    val memo: String?,
    val side: String,
    val amount: Long,
)

data class TrialTuple(
    val code: String,
    val name: String,
    val debit: Long,
    val credit: Long,
)

@Dao
interface JournalDao {
    @Insert
    suspend fun insertEntry(entry: JournalEntryEntity)

    @Insert
    suspend fun insertLines(lines: List<JournalLineEntity>)

    @Query("SELECT * FROM journal_entry WHERE id = :id")
    suspend fun findEntry(id: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_line WHERE entry_id = :id ORDER BY id")
    suspend fun linesOf(id: String): List<JournalLineEntity>

    @Query("SELECT COUNT(*) FROM journal_entry WHERE reversal_of_id = :id")
    suspend fun reversalCount(id: String): Int

    @Transaction
    @Query(
        """SELECT * FROM journal_entry
           WHERE fiscal_year = :year
           AND (:from IS NULL OR entry_date >= :from)
           AND (:to IS NULL OR entry_date <= :to)
           ORDER BY entry_date, doc_number""",
    )
    fun observeEntries(year: Int, from: String?, to: String?): Flow<List<EntryWithLines>>

    @Query(
        """SELECT e.entry_date AS entryDate, e.fiscal_year AS fiscalYear, e.doc_number AS docNumber,
                  e.id AS entryId, e.memo AS memo, l.side AS side, l.amount AS amount
           FROM journal_line l
           INNER JOIN journal_entry e ON e.id = l.entry_id
           WHERE l.account_code = :code
           AND (:from IS NULL OR e.entry_date >= :from)
           AND (:to IS NULL OR e.entry_date <= :to)
           ORDER BY e.entry_date, e.fiscal_year, e.doc_number, l.id""",
    )
    fun observeLedgerRows(code: String, from: String?, to: String?): Flow<List<LedgerRowTuple>>

    /** Debit minus credit of all lines dated before [from] (all fiscal years). 0 when [from] is null. */
    @Query(
        """SELECT COALESCE(SUM(CASE WHEN l.side = 'DEBIT' THEN l.amount ELSE -l.amount END), 0)
           FROM journal_line l
           INNER JOIN journal_entry e ON e.id = l.entry_id
           WHERE l.account_code = :code AND e.entry_date < :from""",
    )
    fun observeOpeningNet(code: String, from: String?): Flow<Long>

    @Query(
        """SELECT l.account_code AS code, a.name_fa AS name,
                  COALESCE(SUM(CASE WHEN l.side = 'DEBIT' THEN l.amount END), 0) AS debit,
                  COALESCE(SUM(CASE WHEN l.side = 'CREDIT' THEN l.amount END), 0) AS credit
           FROM journal_line l
           INNER JOIN journal_entry e ON e.id = l.entry_id
           INNER JOIN account a ON a.code = l.account_code
           WHERE e.fiscal_year = :year
           AND (:asOf IS NULL OR e.entry_date <= :asOf)
           GROUP BY l.account_code, a.name_fa
           ORDER BY l.account_code""",
    )
    suspend fun trialRows(year: Int, asOf: String?): List<TrialTuple>
}
