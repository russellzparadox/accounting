package com.example.accounting.feature.reports

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.accounting.core.data.db.AppDatabase
import com.example.accounting.core.data.repo.LedgerRepository
import com.example.accounting.core.data.seed.ChartSeeder
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.domain.JournalLine
import com.example.accounting.core.domain.Money
import com.example.accounting.core.domain.Side
import com.example.accounting.core.domain.TemplateId
import com.example.accounting.core.domain.report.ReportCell
import com.example.accounting.core.persian.JalaliDate
import com.example.accounting.feature.ledger.DatePreset
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** ReportsViewModel on in-memory Room (real SQLite). Pumps the main looper until the state settles. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReportsViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var vm: ReportsViewModel
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-31T07:30:00Z"), ZoneId.of("Asia/Tehran"))

    private val oct1 = LocalDate.of(2026, 10, 1)
    private val oct15 = LocalDate.of(2026, 10, 15)

    @Before
    fun setUp() = runBlocking {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        ChartSeeder(db).seed()
        ledger = LedgerRepository(db)
        ledger.ensureFiscalYear(1405)
        ledger.commit(cashSale(oct1, 1_000))
        ledger.commit(cashSale(oct15, 300))
        vm = ReportsViewModel(ledger, clock)
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

    private fun awaitUi(predicate: (ReportsUi) -> Boolean): ReportsUi {
        repeat(300) {
            shadowOf(Looper.getMainLooper()).idle()
            val state = vm.ui.value
            if (!state.busy && predicate(state)) return state
            Thread.sleep(10)
        }
        error("state did not settle: ${vm.ui.value}")
    }

    private fun amount(cell: ReportCell): Long = (cell as ReportCell.Amount).rial

    @Test
    fun trialReportIsBalancedWithMatchingTotals() {
        val state = awaitUi { it.table != null }
        val table = requireNotNull(state.table)
        assertTrue(table.balanced)
        assertEquals(1_300L, amount(requireNotNull(table.totals)[2]))
        assertEquals(1_300L, amount(table.totals!![3]))
    }

    @Test
    fun journalListsOnlyDocumentsInTheTypedJalaliRange() {
        vm.selectKind(ReportKind.JOURNAL)
        vm.onFromText(JalaliDate.from(oct1.plusDays(10)).toString())
        vm.onToText(JalaliDate.from(LocalDate.of(2026, 10, 31)).toString())
        val state = awaitUi { it.table != null && it.table.rows.isNotEmpty() }
        val table = requireNotNull(state.table)
        // Only the 15 Oct sale (two lines) falls in the range.
        assertEquals(2, table.rows.size)
        assertEquals(300L, amount(table.rows[0][5]))
    }

    @Test
    fun ledgerOpeningComesFromEarlierHistoryWhenRangeStartsLater() {
        vm.selectKind(ReportKind.LEDGER)
        vm.onAccountCode("۱۱۰۰")
        vm.onFromText(JalaliDate.from(oct15).toString())
        vm.onToText(JalaliDate.from(LocalDate.of(2026, 10, 31)).toString())
        val state = awaitUi { it.table != null && it.table.rows.size >= 2 }
        val table = requireNotNull(state.table)
        assertEquals(1_000L, amount(table.rows[0][5]))   // opening: the 1 Oct sale
        assertEquals(1_300L, amount(table.totals!![5]))  // closing
    }

    @Test
    fun unknownLedgerAccountShowsMessageAndNoTable() {
        vm.selectKind(ReportKind.LEDGER)
        vm.onAccountCode("9999")
        val state = awaitUi { it.message != null }
        assertNull(state.table)
        assertEquals(R.string.report_account_not_found, state.message)
    }

    @Test
    fun presetAllUsesTheWholeFiscalYear() {
        vm.selectPreset(DatePreset.ALL)
        val state = awaitUi { it.table != null }
        assertTrue(state.table!!.subtitle.contains("۱۴۰۵"))
    }
}
