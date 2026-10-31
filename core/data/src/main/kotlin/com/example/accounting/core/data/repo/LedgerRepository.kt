package com.example.accounting.core.data.repo

import androidx.room.withTransaction
import com.example.accounting.core.data.db.AccountEntity
import com.example.accounting.core.data.db.AppDatabase
import com.example.accounting.core.data.db.DocSequenceEntity
import com.example.accounting.core.data.db.EntryWithLines
import com.example.accounting.core.data.db.FiscalYearEntity
import com.example.accounting.core.data.db.JournalEntryEntity
import com.example.accounting.core.data.db.JournalLineEntity
import com.example.accounting.core.domain.Account
import com.example.accounting.core.domain.AccountLevel
import com.example.accounting.core.domain.AccountType
import com.example.accounting.core.domain.ChartOfAccounts
import com.example.accounting.core.domain.DomainException
import com.example.accounting.core.domain.FiscalYear
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.domain.JournalLine
import com.example.accounting.core.domain.Money
import com.example.accounting.core.domain.PartyAccountResolver
import com.example.accounting.core.domain.PartyKind
import com.example.accounting.core.domain.PostingEngine
import com.example.accounting.core.domain.Side
import com.example.accounting.core.domain.TemplateId
import com.example.accounting.core.persian.JalaliDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/** Committed journal entry with its document number inside the fiscal year. */
data class CommittedDoc(
    val id: String,
    val fiscalYear: Int,
    val docNumber: Int,
    val entry: JournalEntry,
)

/** One ledger row. [debit]/[credit] are rials for this line; [balance] is the running (debit minus credit). */
data class LedgerLine(
    val date: LocalDate,
    val fiscalYear: Int,
    val docNumber: Int,
    val entryId: String,
    val memo: String?,
    val debit: Long,
    val credit: Long,
    val balance: Long,
)

data class TrialRow(val code: String, val name: String, val debit: Long, val credit: Long)

/** [debitTotal] == [creditTotal] for any balanced ledger. */
data class TrialBalance(val rows: List<TrialRow>, val debitTotal: Long, val creditTotal: Long)

/** A document with the document it reverses ([reversalOf]) and the one that reverses it ([reversedBy]). */
data class DocumentLinks(val doc: CommittedDoc, val reversalOf: CommittedDoc?, val reversedBy: CommittedDoc?)

/** A reversal ([reversalId], [reversalNumber]) and the original it reverses ([originalId], [originalNumber]). */
data class ReversalLink(
    val originalId: String,
    val originalNumber: Int,
    val reversalId: String,
    val reversalNumber: Int,
)

/**
 * Persistence for journal documents. Fiscal year is the Jalali year of the entry date.
 * Committed rows are never updated or deleted; fixes go through [reverse].
 */
class LedgerRepository(
    private val db: AppDatabase,
    private val chart: ChartOfAccounts = ChartOfAccounts.standard(),
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    /** Creates the fiscal year (and its doc-number counter) if missing. Idempotent. */
    suspend fun ensureFiscalYear(jalaliYear: Int): FiscalYear = db.withTransaction {
        val (start, end) = JalaliDate.fiscalYear(jalaliYear)
        db.fiscalYearDao().insertIgnore(FiscalYearEntity(jalaliYear, start.toString(), end.toString()))
        db.docSequenceDao().insertIgnore(DocSequenceEntity(jalaliYear, 0))
        val row = checkNotNull(db.fiscalYearDao().find(jalaliYear))
        FiscalYear(LocalDate.parse(row.startDate), LocalDate.parse(row.endDate))
    }

    /**
     * Persists a balanced entry under the next doc number of its fiscal year.
     * Allocation and inserts share one transaction, so a failed commit leaves no gap.
     * Throws [DomainException] for an unopened fiscal year, a date outside it, a missing or already-reversed
     * reversal target, or a non-postable account.
     */
    suspend fun commit(entry: JournalEntry): CommittedDoc = db.withTransaction { commitInTx(entry) }

    /** New reversing entry for [docId] dated [date]. Original is untouched. A document can be reversed once. */
    suspend fun reverse(docId: String, date: LocalDate, memo: String? = null): CommittedDoc = db.withTransaction {
        val original = loadEntry(docId) ?: throw DomainException("document $docId not found")
        val fiscal = openFiscalYear(JalaliDate.from(date).year, date)
        val engine = PostingEngine(chart, NoPartyResolver, fiscal, idGenerator)
        commitInTx(engine.reverse(original, date, memo))
    }

    /** Entries of [fiscalYear], optionally restricted to [from]..[to] (inclusive). Re-emits on any journal change. */
    fun journal(fiscalYear: Int, from: LocalDate? = null, to: LocalDate? = null): Flow<List<CommittedDoc>> =
        db.journalDao().observeEntries(fiscalYear, from?.toString(), to?.toString())
            .map { rows -> rows.map { it.toCommittedDoc() } }

    /**
     * Lines of [accountCode] in [from]..[to], with running balance. Opening balance is everything dated before
     * [from] across all fiscal years (0 when [from] is null).
     */
    fun ledger(accountCode: String, from: LocalDate? = null, to: LocalDate? = null): Flow<List<LedgerLine>> {
        val fromText = from?.toString()
        val toText = to?.toString()
        return combine(
            db.journalDao().observeOpeningNet(accountCode, fromText),
            db.journalDao().observeLedgerRows(accountCode, fromText, toText),
        ) { opening, rows ->
            var running = if (from == null) 0L else opening
            rows.map { row ->
                val debit = if (row.side == Side.DEBIT.name) row.amount else 0L
                val credit = if (row.side == Side.CREDIT.name) row.amount else 0L
                running += debit - credit
                LedgerLine(
                    date = LocalDate.parse(row.entryDate),
                    fiscalYear = row.fiscalYear,
                    docNumber = row.docNumber,
                    entryId = row.entryId,
                    memo = row.memo,
                    debit = debit,
                    credit = credit,
                    balance = running,
                )
            }
        }
    }

    /** Gross debit and credit per account for [fiscalYear], dated up to [asOf] when given. */
    suspend fun trialBalance(fiscalYear: Int, asOf: LocalDate? = null): TrialBalance {
        val rows = db.journalDao().trialRows(fiscalYear, asOf?.toString())
            .map { TrialRow(it.code, it.name, it.debit, it.credit) }
        return TrialBalance(rows, rows.sumOf { it.debit }, rows.sumOf { it.credit })
    }

    fun accounts(): Flow<List<Account>> = db.accountDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    /** Fiscal years that exist (opened), ascending. */
    fun fiscalYears(): Flow<List<Int>> = db.fiscalYearDao().observeAll().map { rows -> rows.map { it.jalaliYear } }

    /**
     * All reversal links, unfiltered by date or fiscal year, so a list window never hides the other half of a pair.
     * Re-emits on any journal change.
     */
    fun reversalLinks(): Flow<List<ReversalLink>> = db.journalDao().observeReversalLinks().map { rows ->
        rows.map { ReversalLink(it.originalId, it.originalNumber, it.reversalId, it.reversalNumber) }
    }

    /** One committed document with its reversal links. Null when [docId] does not exist. */
    suspend fun document(docId: String): DocumentLinks? {
        val doc = loadDoc(docId) ?: return null
        val reversalOf = doc.entry.reversalOfId?.let { loadDoc(it) }
        val reversedBy = db.journalDao().reversalIdOf(docId)?.let { loadDoc(it) }
        return DocumentLinks(doc, reversalOf, reversedBy)
    }

    private suspend fun loadDoc(id: String): CommittedDoc? = db.journalDao().findWithLines(id)?.toCommittedDoc()

    private suspend fun commitInTx(entry: JournalEntry): CommittedDoc {
        val year = JalaliDate.from(entry.date).year
        openFiscalYear(year, entry.date)
        entry.reversalOfId?.let { originalId ->
            if (db.journalDao().findEntry(originalId) == null) {
                throw DomainException("reversal target $originalId not found")
            }
            if (db.journalDao().reversalCount(originalId) > 0) {
                throw DomainException("document $originalId is already reversed")
            }
        }
        entry.lines.forEach { line ->
            if (db.accountDao().isPostable(line.accountCode) == 0) {
                throw DomainException("account ${line.accountCode} is not postable")
            }
        }

        db.docSequenceDao().insertIgnore(DocSequenceEntity(year, 0))
        val docNumber = (db.docSequenceDao().lastNumber(year) ?: 0) + 1
        db.docSequenceDao().setLastNumber(year, docNumber)

        db.journalDao().insertEntry(
            JournalEntryEntity(
                id = entry.id,
                entryDate = entry.date.toString(),
                fiscalYear = year,
                docNumber = docNumber,
                templateId = entry.templateId.name,
                memo = entry.memo,
                reversalOfId = entry.reversalOfId,
            ),
        )
        db.journalDao().insertLines(
            entry.lines.map { line ->
                JournalLineEntity(
                    entryId = entry.id,
                    accountCode = line.accountCode,
                    side = line.side.name,
                    amount = line.amount.rial,
                )
            },
        )
        return CommittedDoc(entry.id, year, docNumber, entry)
    }

    private suspend fun openFiscalYear(year: Int, date: LocalDate): FiscalYear {
        val row = db.fiscalYearDao().find(year)
            ?: throw DomainException("fiscal year $year is not open (date $date)")
        val fiscal = FiscalYear(LocalDate.parse(row.startDate), LocalDate.parse(row.endDate))
        if (date !in fiscal) throw DomainException("date $date outside fiscal year $year")
        return fiscal
    }

    private suspend fun loadEntry(id: String): JournalEntry? {
        val entry = db.journalDao().findEntry(id) ?: return null
        return toDomain(entry, db.journalDao().linesOf(id))
    }

    private fun EntryWithLines.toCommittedDoc(): CommittedDoc =
        CommittedDoc(entry.id, entry.fiscalYear, entry.docNumber, toDomain(entry, lines))

    private fun AccountEntity.toDomain(): Account = Account(
        code = code,
        nameFa = nameFa,
        level = AccountLevel.valueOf(level),
        parent = parent,
        type = AccountType.valueOf(type),
        normalBalance = Side.valueOf(normalBalance),
    )

    private companion object {
        fun toDomain(entry: JournalEntryEntity, lines: List<JournalLineEntity>): JournalEntry = JournalEntry(
            id = entry.id,
            date = LocalDate.parse(entry.entryDate),
            templateId = TemplateId.valueOf(entry.templateId),
            memo = entry.memo,
            lines = lines.sortedBy { it.id }.map {
                JournalLine(it.accountCode, Side.valueOf(it.side), Money(it.amount))
            },
            reversalOfId = entry.reversalOfId,
        )
    }
}

/** Reversals never create parties. Any call is a bug in the caller. */
private object NoPartyResolver : PartyAccountResolver {
    override fun resolve(kind: PartyKind, key: String, displayName: String): String =
        throw IllegalStateException("party resolution is not used by reverse()")
}
