package com.example.accounting.feature.ledger

import android.os.Looper
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * LedgerViewModel on an in-memory Room database (real SQLite). The ViewModel launches on the main looper, so
 * [awaitValue] pumps that looper until the derived state matches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LedgerViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private var nextId = 0

    /** 2026-01-10 is 1404/10/20. Fiscal year 1404 covers the test dates. */
    private val clock = Clock.fixed(Instant.parse("2026-01-10T08:00:00Z"), ZoneId.of("Asia/Tehran"))

    @Before
    fun setUp() {
        db = AppDatabase.inMemory(context)
        val chart = ChartOfAccounts.standard()
        ledger = LedgerRepository(db, chart, idGenerator = { "id-${++nextId}" })
        runBlocking {
            ChartSeeder(db, chart).seed()
            ledger.ensureFiscalYear(1404)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sale(id: String, date: LocalDate, rial: Long, memo: String): JournalEntry = JournalEntry(
        id = id,
        date = date,
        templateId = TemplateId.SALE_CASH,
        memo = memo,
        lines = listOf(
            JournalLine("1100", Side.DEBIT, Money(rial)),
            JournalLine("4100", Side.CREDIT, Money(rial)),
        ),
    )

    private fun commit(entry: JournalEntry) = runBlocking { ledger.commit(entry) }

    private fun <T> awaitValue(read: () -> T, ok: (T) -> Boolean): T {
        repeat(300) {
            shadowOf(Looper.getMainLooper()).idle()
            val value = read()
            if (ok(value)) return value
            Thread.sleep(10)
        }
        error("state did not settle")
    }

    @Test
    fun journalIsNewestFirstAndSearchFoldsArabicYeh() {
        commit(sale("a", LocalDate.of(2026, 1, 5), 1_000_000L, "فروش نقدی"))
        commit(sale("b", LocalDate.of(2026, 1, 8), 300_000L, "فروش به علي"))
        val vm = LedgerViewModel(ledger, clock)

        val rows = awaitValue({ vm.journal.value }) { it.size == 2 }
        assertEquals(listOf("b", "a"), rows.map { it.id })

        vm.onJournalQuery("علی")
        val filtered = awaitValue({ vm.journal.value }) { it.size == 1 }
        assertEquals("b", filtered.single().id)
    }

    @Test
    fun searchMatchesTomanAmountWrittenWithPersianDigits() {
        commit(sale("a", LocalDate.of(2026, 1, 5), 5_000_000L, "خرید"))
        commit(sale("b", LocalDate.of(2026, 1, 6), 1_200_000L, "خرید دیگر"))
        val vm = LedgerViewModel(ledger, clock)
        awaitValue({ vm.journal.value }) { it.size == 2 }

        vm.onJournalQuery("۵۰۰٬۰۰۰")
        val filtered = awaitValue({ vm.journal.value }) { it.size == 1 }
        assertEquals("a", filtered.single().id)
    }

    @Test
    fun reversalIsLinkedBothWaysAndOriginalCannotBeReversedTwice() {
        commit(sale("a", LocalDate.of(2026, 1, 5), 1_000_000L, "فروش"))
        val vm = LedgerViewModel(ledger, clock)
        awaitValue({ vm.journal.value }) { it.size == 1 }

        vm.openDocument("a")
        val opened = awaitValue({ vm.detail.value }) { it != null }!!
        assertTrue(opened.canReverse)

        vm.askReverse(true)
        assertTrue(vm.detail.value!!.confirmReverse)
        vm.confirmReverse()

        val after = awaitValue({ vm.detail.value }) { it?.reversedNumber != null }!!
        assertEquals(2, after.reversedNumber)
        assertFalse(after.canReverse)
        assertFalse(after.reversing)

        val rows = awaitValue({ vm.journal.value }) { it.size == 2 }
        val reversal = rows.first { it.isReversal }
        assertEquals(1, reversal.reversalOfNumber)
        assertTrue(rows.first { it.id == "a" }.isReversed)

        // Second confirm on the same original does nothing: the sheet no longer offers reversal.
        vm.confirmReverse()
        assertEquals(2, runBlocking { ledger.journal(1404).first().size })
    }

    @Test
    fun accountLedgerRunsBalanceAndTotalsMatchTheNet() {
        commit(sale("a", LocalDate.of(2026, 1, 5), 1_000_000L, "فروش"))
        commit(sale("b", LocalDate.of(2026, 1, 8), 300_000L, "فروش دوم"))
        val vm = LedgerViewModel(ledger, clock)

        vm.selectAccount("1100")
        val ui = awaitValue({ vm.accountLedger.value }) { it?.rows?.size == 2 }!!
        assertEquals(1_000_000L, ui.rows.first().balance)
        assertEquals(1_300_000L, ui.totalDebit)
        assertEquals(0L, ui.totalCredit)
        assertEquals(1_300_000L, ui.closing)

        val net = awaitValue({ vm.accountRows.value }) { rows ->
            rows.any { it.code == "1100" && it.netRial != 0L }
        }
        assertEquals(1_300_000L, net.first { it.code == "1100" }.netRial)
        assertEquals(
            runBlocking { ledger.trialBalance(1404).rows.first { it.code == "1100" }.debit },
            net.first { it.code == "1100" }.netRial,
        )
    }
}
