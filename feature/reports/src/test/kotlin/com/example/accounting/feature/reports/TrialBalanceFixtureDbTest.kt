package com.example.accounting.feature.reports

import androidx.test.core.app.ApplicationProvider
import com.example.accounting.core.data.db.AppDatabase
import com.example.accounting.core.data.repo.LedgerRepository
import com.example.accounting.core.data.seed.ChartSeeder
import com.example.accounting.core.domain.ChartOfAccounts
import com.example.accounting.core.domain.ExpenseCategory
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.domain.PaidWith
import com.example.accounting.core.domain.PartyAccountResolver
import com.example.accounting.core.domain.PartyKind
import com.example.accounting.core.domain.PostingEngine
import com.example.accounting.core.domain.TemplateId
import com.example.accounting.core.domain.TransactionIntent
import com.example.accounting.core.domain.report.ReportCell
import com.example.accounting.core.domain.report.TrialAccount
import com.example.accounting.core.domain.report.TrialBalanceReport
import com.example.accounting.core.domain.FiscalYear
import com.example.accounting.core.persian.AmountUnit
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 120 committed entries through PostingEngine and the real Room repository (in-memory SQLite). The DB trial
 * balance must balance, and the pure TrialBalanceReport built from it must agree with the posted amounts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrialBalanceFixtureDbTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository
    private lateinit var fiscal: FiscalYear

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

    /** No party templates: the resolver must never be asked for a party. */
    private val noParties = object : PartyAccountResolver {
        override fun resolve(kind: PartyKind, key: String, displayName: String): String =
            error("fixture uses no party templates")
        override fun find(kind: PartyKind, key: String): String? = error("fixture uses no party templates")
    }

    @Test
    fun trialBalanceOfOverHundredCommittedEntriesBalances(): Unit = runBlocking<Unit> {
        val engine = PostingEngine(ChartOfAccounts.standard(), noParties, fiscal)
        val rnd = Random(20261031)
        val types = listOf(
            TemplateId.PURCHASE_CASH, TemplateId.SALE_CASH, TemplateId.BANK_TRANSFER,
            TemplateId.OWNER_CAPITAL, TemplateId.OWNER_WITHDRAWAL, TemplateId.EXPENSE_PAID,
        )
        var postedDebit = 0L
        repeat(120) {
            val type = types.random(rnd)
            val entry: JournalEntry = engine.post(
                TransactionIntent(
                    type = type,
                    amountRial = rnd.nextLong(1_000, 5_000_000_000L),
                    date = LocalDate.of(2026, 3, 21).plusDays(rnd.nextLong(0, 200)),
                    paidWith = if (rnd.nextBoolean()) PaidWith.CASH else PaidWith.BANK,
                    category = if (type == TemplateId.EXPENSE_PAID) ExpenseCategory.RENT else null,
                    confidence = 90,
                ),
            )
            repo.commit(entry)
            postedDebit += entry.debitTotal.rial
        }

        val tb = repo.trialBalance(1405)
        assertEquals(tb.debitTotal, tb.creditTotal)
        assertEquals(postedDebit, tb.debitTotal)

        val report = TrialBalanceReport.build(
            1405,
            null,
            tb.rows.map { TrialAccount(it.code, it.name, it.debit, it.credit) },
            AmountUnit.TOMAN,
        )
        assertTrue(report.balanced)
        assertEquals(postedDebit, (report.totals!![2] as ReportCell.Amount).rial)
        assertEquals(postedDebit, (report.totals!![3] as ReportCell.Amount).rial)
    }
}
