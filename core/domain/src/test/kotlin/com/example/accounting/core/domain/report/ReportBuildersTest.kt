package com.example.accounting.core.domain.report

import com.example.accounting.core.domain.ChartOfAccounts
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.domain.PaidWith
import com.example.accounting.core.domain.Side
import com.example.accounting.core.domain.TemplateId
import com.example.accounting.core.domain.TransactionIntent
import com.example.accounting.core.domain.testEngine
import com.example.accounting.core.persian.AmountUnit
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportBuildersTest {
    private val chart = ChartOfAccounts.standard()

    private fun amountsOf(table: ReportTable, col: Int): List<Long> =
        table.rows.map { (it[col] as ReportCell.Amount).rial }

    @Test
    fun trialBalanceFixtureOfOverHundredEntriesBalances() {
        val entries = fixtureEntries(count = 150, seed = 20261031)
        assertTrue(entries.size >= 100)

        // Aggregate gross debit/credit per account, the same way LedgerRepository.trialRows does.
        val byCode = linkedMapOf<String, LongArray>()
        entries.forEach { e ->
            e.lines.forEach { l ->
                val acc = byCode.getOrPut(l.accountCode) { LongArray(2) }
                if (l.side == Side.DEBIT) acc[0] += l.amount.rial else acc[1] += l.amount.rial
            }
        }
        val accounts = byCode.map { (code, v) ->
            TrialAccount(code, chart.account(code)?.nameFa ?: code, v[0], v[1])
        }
        val table = TrialBalanceReport.build(1405, null, accounts, AmountUnit.TOMAN)

        val expected = entries.sumOf { it.debitTotal.rial }
        assertTrue(table.balanced)
        assertEquals(expected, (table.totals!![2] as ReportCell.Amount).rial)
        assertEquals(expected, (table.totals!![3] as ReportCell.Amount).rial)
        assertEquals(accounts.size, table.rows.size)
    }

    @Test
    fun trialBalanceReportsImbalanceInsteadOfHidingIt() {
        val table = TrialBalanceReport.build(
            1405, null,
            listOf(TrialAccount("1100", "صندوق", 500, 0), TrialAccount("3100", "سرمایه", 0, 400)),
            AmountUnit.RIAL,
        )
        assertFalse(table.balanced)
    }

    @Test
    fun journalRowsAreDocumentLinesInDateThenNumberOrder() {
        val docs = listOf(
            JournalDoc(LocalDate.of(2025, 4, 2), 2, "دوم", listOf(
                JournalDocLine("1100", "صندوق", Side.CREDIT, 700),
                JournalDocLine("6100", "اجاره", Side.DEBIT, 700),
            )),
            JournalDoc(LocalDate.of(2025, 4, 1), 1, null, listOf(
                JournalDocLine("1100", "صندوق", Side.DEBIT, 1000),
                JournalDocLine("3100", "سرمایه", Side.CREDIT, 1000),
            )),
        )
        val table = JournalReport.build(ReportRange(null, null), docs, AmountUnit.RIAL)
        assertEquals(4, table.rows.size)
        assertEquals(listOf(1, 1, 2, 2), table.rows.map { (it[1] as ReportCell.Count).value })
        assertEquals(listOf(1000L, 0L, 700L, 0L), amountsOf(table, 5))
        assertEquals(listOf(0L, 1000L, 0L, 700L), amountsOf(table, 6))
        assertEquals(1700L, (table.totals!![5] as ReportCell.Amount).rial)
        assertTrue(table.balanced)
    }

    @Test
    fun ledgerComputesOpeningAndClosingForRange() {
        val history = listOf(
            LedgerHistoryLine(LocalDate.of(2025, 3, 25), 1404, 1, "اول", 1000, 0),
            LedgerHistoryLine(LocalDate.of(2025, 4, 10), 1404, 2, "دوم", 0, 300),
            LedgerHistoryLine(LocalDate.of(2025, 5, 1), 1404, 3, "سوم", 200, 0),
            LedgerHistoryLine(LocalDate.of(2025, 6, 1), 1404, 4, "چهارم", 0, 50),
        )
        val range = ReportRange(LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31))
        val table = LedgerReport.build("1100", "صندوق", range, history, AmountUnit.RIAL)

        // opening = 1000 (only the 3/25 line is before 4/1); window holds 4/10 and 5/1
        assertEquals(3, table.rows.size) // opening row + 2 lines
        assertEquals(1000L, (table.rows[0][5] as ReportCell.Amount).rial)
        assertEquals(listOf(700L, 900L), listOf(3, 4).map { (table.rows[it - 2][5] as ReportCell.Amount).rial })
        assertEquals(900L, (table.totals!![5] as ReportCell.Amount).rial) // closing
        assertEquals(200L, (table.totals!![3] as ReportCell.Amount).rial) // debit in window
        assertEquals(300L, (table.totals!![4] as ReportCell.Amount).rial) // credit in window
    }

    @Test
    fun ledgerWithNoOpenRangeStartsAtZero() {
        val history = listOf(LedgerHistoryLine(LocalDate.of(2025, 4, 1), 1404, 1, null, 500, 0))
        val table = LedgerReport.build("1100", "صندوق", ReportRange(null, null), history, AmountUnit.RIAL)
        assertEquals(0L, (table.rows[0][5] as ReportCell.Amount).rial)
        assertEquals(500L, (table.totals!![5] as ReportCell.Amount).rial)
    }

    @Test
    fun rangeMustNotEndBeforeItStarts() {
        val bad = runCatching { ReportRange(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 4, 1)) }
        assertTrue(bad.isFailure)
    }

    @Test
    fun amountDisplayUsesUnitAndPersianDigits() {
        assertEquals("۵۰۰٬۰۰۰", ReportFormat.amountDisplay(5_000_000, AmountUnit.TOMAN))
        assertEquals("۱۲۳٬۴۵۶٬۷۸۹", ReportFormat.amountDisplay(123_456_789, AmountUnit.RIAL))
    }

    /** Random valid entries through the real PostingEngine. Deterministic seed. */
    private fun fixtureEntries(count: Int, seed: Long): List<JournalEntry> {
        val engine = testEngine()
        val rnd = Random(seed)
        val parties = listOf("علی رضایی", "شرکت نور", "احمد")
        val types = listOf(
            TemplateId.PURCHASE_CASH, TemplateId.SALE_CASH, TemplateId.PURCHASE_CREDIT,
            TemplateId.SALE_CREDIT, TemplateId.BANK_TRANSFER, TemplateId.OWNER_CAPITAL,
            TemplateId.OWNER_WITHDRAWAL, TemplateId.RECEIPT_FROM_PERSON, TemplateId.PAYMENT_TO_PERSON,
        )
        return List(count) {
            val type = types.random(rnd)
            val credit = type == TemplateId.PURCHASE_CREDIT || type == TemplateId.SALE_CREDIT
            val party = if (credit || type == TemplateId.RECEIPT_FROM_PERSON || type == TemplateId.PAYMENT_TO_PERSON) {
                parties.random(rnd)
            } else {
                null
            }
            engine.post(
                TransactionIntent(
                    type = type,
                    amountRial = rnd.nextLong(1_000, 5_000_000_000L),
                    date = LocalDate.of(2026, 3, 21).plusDays(rnd.nextLong(0, 365)),
                    paidWith = if (credit) null else if (rnd.nextBoolean()) PaidWith.CASH else PaidWith.BANK,
                    partyName = party,
                    confidence = 90,
                ),
            )
        }
    }
}
