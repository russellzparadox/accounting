package com.example.accounting.core.data

import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import com.example.accounting.core.data.db.AppDatabase
import com.example.accounting.core.data.repo.LedgerRepository
import com.example.accounting.core.data.repo.PartyRepository
import com.example.accounting.core.data.seed.ChartSeeder
import com.example.accounting.core.domain.ChartOfAccounts
import com.example.accounting.core.domain.DomainException
import com.example.accounting.core.domain.FiscalYear
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.domain.JournalLine
import com.example.accounting.core.domain.Money
import com.example.accounting.core.domain.PostingEngine
import com.example.accounting.core.domain.Side
import com.example.accounting.core.domain.TemplateId
import com.example.accounting.core.domain.TransactionIntent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID

/** Jalali 1405 covers 2026-10-31. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LedgerRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository
    private lateinit var fiscal: FiscalYear

    private val oct1 = LocalDate.of(2026, 10, 1)

    @Before
    fun setUp() = runBlocking {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        ChartSeeder(db).seed()
        repo = LedgerRepository(db)
        fiscal = repo.ensureFiscalYear(1405)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun cashSale(date: LocalDate, rial: Long): JournalEntry = JournalEntry(
        id = UUID.randomUUID().toString(),
        date = date,
        templateId = TemplateId.SALE_CASH,
        memo = null,
        lines = listOf(
            JournalLine("1100", Side.DEBIT, Money(rial)),
            JournalLine("4100", Side.CREDIT, Money(rial)),
        ),
    )

    @Test
    fun docNumbersAreSequentialWithoutGaps() = runBlocking {
        val numbers = (0 until 30).map { repo.commit(cashSale(oct1.plusDays(it.toLong()), 1000)).docNumber }
        assertEquals((1..30).toList(), numbers)
    }

    @Test
    fun failedCommitsConsumeNoDocNumber() = runBlocking {
        val first = cashSale(oct1, 1000)
        repo.commit(first)

        // Rejected before allocation: group account 1000 is not postable.
        val onGroup = JournalEntry(
            UUID.randomUUID().toString(), oct1, TemplateId.SALE_CASH, null,
            listOf(JournalLine("1000", Side.DEBIT, Money(5)), JournalLine("4100", Side.CREDIT, Money(5))),
        )
        assertThrows(DomainException::class.java) { runBlocking { repo.commit(onGroup) } }

        // Rejected inside the transaction: duplicate primary key after the number was allocated.
        assertThrows(SQLiteException::class.java) { runBlocking { repo.commit(first) } }

        assertEquals(2, repo.commit(cashSale(oct1, 1000)).docNumber)
    }

    /**
     * Jalali 1404 and 1406 are not opened. A commit dated in them opens the year lazily (no error), the first
     * document of that year is number 1, and each year numbers its own documents without gaps.
     */
    @Test
    fun committingIntoAnUnopenedFiscalYearOpensItAndNumbersFromOne() = runBlocking {
        val previous = repo.commit(cashSale(LocalDate.of(2025, 12, 1), 1000)) // 1404
        assertEquals(1404, previous.fiscalYear)
        assertEquals(1, previous.docNumber)
        assertEquals(2, repo.commit(cashSale(LocalDate.of(2025, 12, 2), 1000)).docNumber)

        val later = repo.commit(cashSale(LocalDate.of(2027, 6, 1), 1000)) // 1406
        assertEquals(1406, later.fiscalYear)
        assertEquals(1, later.docNumber)

        // 1405 keeps its own counter.
        assertEquals(1, repo.commit(cashSale(oct1, 1000)).docNumber)
        assertEquals(listOf(1404, 1405, 1406), repo.fiscalYears().first())
    }

    @Test
    fun committedRowsCannotBeUpdatedOrDeleted() = runBlocking {
        val doc = repo.commit(cashSale(oct1, 1000))
        val raw = db.openHelper.writableDatabase

        assertThrows(SQLiteException::class.java) { raw.execSQL("UPDATE journal_entry SET memo = 'x'") }
        assertThrows(SQLiteException::class.java) { raw.execSQL("DELETE FROM journal_entry") }
        assertThrows(SQLiteException::class.java) { raw.execSQL("UPDATE journal_line SET amount = 1") }
        assertThrows(SQLiteException::class.java) { raw.execSQL("DELETE FROM journal_line") }

        val lines = db.journalDao().linesOf(doc.id)
        assertEquals(listOf(1000L, 1000L), lines.map { it.amount })
    }

    @Test
    fun ledgerRunningBalanceReversalAndTrialBalance() = runBlocking {
        val first = cashSale(oct1, 1000)
        repo.commit(first)
        repo.commit(cashSale(oct1.plusDays(1), 500))
        val reversal = repo.reverse(first.id, oct1.plusDays(2), "undo first")
        assertEquals(3, reversal.docNumber)

        val cash = repo.ledger("1100").first()
        assertEquals(listOf(1000L, 1500L, 500L), cash.map { it.balance })
        assertEquals(1000L, cash[2].credit)

        val trial = repo.trialBalance(1405)
        assertEquals(2500L, trial.debitTotal)
        assertEquals(trial.debitTotal, trial.creditTotal)
        val cashRow = trial.rows.single { it.code == "1100" }
        assertEquals(1500L, cashRow.debit)
        assertEquals(1000L, cashRow.credit)
    }

    @Test
    fun documentCanBeReversedOnlyOnce() {
        runBlocking {
            val first = cashSale(oct1, 1000)
            repo.commit(first)
            repo.reverse(first.id, oct1)
            assertThrows(DomainException::class.java) { runBlocking { repo.reverse(first.id, oct1) } }
        }
    }

    @Test
    fun postedPartyEntryUsesResolvedMeinAccount() = runBlocking {
        val engine = PostingEngine(ChartOfAccounts.standard(), PartyRepository(db), fiscal)
        val intent = TransactionIntent.fromJson(
            """{"type":"PURCHASE_CREDIT","amount_rial":3000,"date":"2026-10-05","paid_with":null,
               "bank_account":null,"party_name":"ابرام","category":null,"memo":null,
               "confidence":90,"needs_clarification":null}""",
        )
        val doc = repo.commit(engine.post(intent))

        assertEquals(setOf("2102", "5100"), doc.entry.lines.map { it.accountCode }.toSet())
        val supplier = repo.ledger("2102").first().single()
        assertEquals(3000L, supplier.credit)
        assertEquals(-3000L, supplier.balance)
    }

    @Test
    fun reversalLinksAreNotLimitedByTheJournalWindow() = runBlocking {
        val first = cashSale(oct1, 1000)
        repo.commit(first)
        val reversal = repo.reverse(first.id, oct1.plusDays(30))

        // The window excludes the original, so the journal list cannot see it.
        val windowed = repo.journal(1405, from = oct1.plusDays(29)).first()
        assertEquals(listOf(reversal.docNumber), windowed.map { it.docNumber })

        // The link query has no window and still pairs them.
        val link = repo.reversalLinks().first().single()
        assertEquals(first.id, link.originalId)
        assertEquals(1, link.originalNumber)
        assertEquals(reversal.id, link.reversalId)
        assertEquals(reversal.docNumber, link.reversalNumber)
    }

    @Test
    fun journalEmitsEntriesWithLines() = runBlocking {
        repo.commit(cashSale(oct1, 1000))
        repo.commit(cashSale(oct1.plusDays(1), 700))

        val docs = repo.journal(1405).first()
        assertEquals(listOf(1, 2), docs.map { it.docNumber })
        assertEquals(2, docs.first().entry.lines.size)

        val fromSecond = repo.journal(1405, from = oct1.plusDays(1)).first()
        assertEquals(listOf(2), fromSecond.map { it.docNumber })
    }
}
