package com.example.accounting.feature.reports

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.example.accounting.core.data.config.SettingsRepository
import com.example.accounting.core.data.db.AppDatabase
import com.example.accounting.core.data.repo.LedgerRepository
import com.example.accounting.core.data.seed.ChartSeeder
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.domain.JournalLine
import com.example.accounting.core.domain.Money
import com.example.accounting.core.domain.Side
import com.example.accounting.core.domain.TemplateId
import com.example.accounting.core.domain.report.ReportFormat
import com.example.accounting.core.persian.AmountUnit
import com.example.accounting.core.ui.theme.AccountingTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reports screen on Robolectric: the default trial balance renders its totals in the unit chosen in Settings, and
 * switching the unit in Settings re-renders them. In-memory Room, real ViewModel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1200dp")
class ReportsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var vm: ReportsViewModel
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-31T07:30:00Z"), ZoneId.of("Asia/Tehran"))

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.inMemory(context)
        ChartSeeder(db).seed()
        val ledger = LedgerRepository(db)
        ledger.ensureFiscalYear(1405)
        ledger.commit(cashSale(LocalDate.of(2026, 10, 1), 1_000))
        ledger.commit(cashSale(LocalDate.of(2026, 10, 15), 300))
        settings = SettingsRepository(context)
        vm = ReportsViewModel(ledger, clock, settings)
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

    /** Trial balance is the default report: 1,300 rial is 130 toman, and 1,300 rial once the unit is rial. */
    @Test
    fun trialReportTotalsFollowTheUnitChosenInSettings() {
        compose.setContent {
            AccountingTheme {
                ReportsRoute(onClose = {}, vm = vm)
            }
        }
        val toman = ReportFormat.amountDisplay(1_300L, AmountUnit.TOMAN)
        compose.waitUntil(10_000) { compose.onAllNodesWithText(toman).fetchSemanticsNodes().isNotEmpty() }

        settings.setAmountUnit(AmountUnit.RIAL)
        val rial = ReportFormat.amountDisplay(1_300L, AmountUnit.RIAL)
        compose.waitUntil(10_000) { compose.onAllNodesWithText(rial).fetchSemanticsNodes().isNotEmpty() }
    }
}
