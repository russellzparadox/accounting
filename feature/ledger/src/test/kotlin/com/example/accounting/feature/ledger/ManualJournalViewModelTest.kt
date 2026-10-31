package com.example.accounting.feature.ledger

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.accounting.core.data.db.AppDatabase
import com.example.accounting.core.data.config.SettingsRepository
import com.example.accounting.core.data.repo.LedgerRepository
import com.example.accounting.core.persian.AmountUnit
import com.example.accounting.core.data.repo.PartyRepository
import com.example.accounting.core.data.seed.ChartSeeder
import com.example.accounting.core.domain.AccountLevel
import com.example.accounting.core.domain.ChartOfAccounts
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.domain.JournalLine
import com.example.accounting.core.domain.ManualIssue
import com.example.accounting.core.domain.Money
import com.example.accounting.core.domain.PartyKind
import com.example.accounting.core.domain.Side
import com.example.accounting.core.domain.TemplateId
import com.example.accounting.core.persian.JalaliDate
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
 * ManualJournalViewModel on an in-memory Room database (real SQLite). The ViewModel runs on the main looper, so
 * [awaitValue] pumps that looper until the derived state matches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ManualJournalViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var parties: PartyRepository
    private var nextId = 0

    /** 2026-01-10 is 1404/10/20; fiscal year 1404 is opened in setUp. */
    private val clock = Clock.fixed(Instant.parse("2026-01-10T08:00:00Z"), ZoneId.of("Asia/Tehran"))

    @Before
    fun setUp() {
        db = AppDatabase.inMemory(context)
        val chart = ChartOfAccounts.standard()
        ledger = LedgerRepository(db, chart, idGenerator = { "id-${++nextId}" })
        parties = PartyRepository(db)
        runBlocking {
            ChartSeeder(db, chart).seed()
            ledger.ensureFiscalYear(1404)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newVm() = ManualJournalViewModel(ledger, parties, clock, SettingsRepository(context))

    @Test
    fun rialUnitTypesRialAndAcceptsAmountThatIsNotWholeToman() {
        SettingsRepository(context).setAmountUnit(AmountUnit.RIAL)
        val vm = newVm()
        fillBalanced(vm, debit = "1100", credit = "4100", amount = "5000001")
        val ready = awaitValue({ vm.ui.value }) { it.canSave }
        assertEquals(AmountUnit.RIAL, ready.unit)
        assertEquals(5_000_001L, ready.check.debitRial)
    }

    @Test
    fun tomanUnitReadsTypedAmountAsTomanAndStoresRial() {
        SettingsRepository(context).setAmountUnit(AmountUnit.TOMAN)
        val vm = newVm()
        fillBalanced(vm, debit = "1100", credit = "4100", amount = "5000001")
        val ready = awaitValue({ vm.ui.value }) { it.canSave }
        assertEquals(AmountUnit.TOMAN, ready.unit)
        // 5 000 001 toman = 50 000 010 rial.
        assertEquals(50_000_010L, ready.check.debitRial)
    }

    private fun <T> awaitValue(read: () -> T, ok: (T) -> Boolean): T {
        repeat(300) {
            shadowOf(Looper.getMainLooper()).idle()
            val value = read()
            if (ok(value)) return value
            Thread.sleep(10)
        }
        error("state did not settle")
    }

    private fun awaitRows(vm: ManualJournalViewModel): List<ManualRowUi> =
        awaitValue({ vm.ui.value.rows }) { it.size == 2 }

    private fun fillBalanced(vm: ManualJournalViewModel, debit: String, credit: String, amount: String, memo: String = "") {
        val rows = awaitRows(vm)
        vm.onAction(ManualAction.SetMemo(memo))
        vm.onAction(ManualAction.OpenPicker(rows[0].id))
        vm.onAction(ManualAction.PickAccount(debit))
        vm.onAction(ManualAction.OpenPicker(rows[1].id))
        vm.onAction(ManualAction.PickAccount(credit))
        vm.onAction(ManualAction.SetAmount(rows[0].id, amount))
        vm.onAction(ManualAction.SetAmount(rows[1].id, amount))
    }

    private fun committedDocs(): List<com.example.accounting.core.data.repo.CommittedDoc> =
        runBlocking { ledger.journal(1404).first() }

    @Test
    fun balancedDocumentIsSavedOnceAsManualAndTrialBalanceStaysBalanced() {
        val vm = newVm()
        fillBalanced(vm, debit = "1100", credit = "4100", amount = "۵۰۰٬۰۰۰", memo = "  فروش نقدی ")
        val ready = awaitValue({ vm.ui.value }) { it.canSave }
        assertEquals(5_000_000L, ready.check.debitRial)

        vm.onAction(ManualAction.Save)
        vm.onAction(ManualAction.Save) // second tap: ignored while saving or after the save
        val saved = awaitValue({ vm.ui.value }) { it.savedDocNumber != null }
        assertEquals(1, saved.savedDocNumber)

        val docs = committedDocs()
        assertEquals(1, docs.size)
        assertEquals(TemplateId.MANUAL, docs.single().entry.templateId)
        assertEquals("فروش نقدی", docs.single().entry.memo)
        val trial = runBlocking { ledger.trialBalance(1404) }
        assertEquals(5_000_000L, trial.debitTotal)
        assertEquals(trial.debitTotal, trial.creditTotal)
    }

    @Test
    fun unbalancedDocumentCannotBeSaved() {
        val vm = newVm()
        fillBalanced(vm, debit = "1100", credit = "4100", amount = "500000")
        val rows = awaitRows(vm)
        vm.onAction(ManualAction.SetAmount(rows[1].id, "400000"))
        val ui = awaitValue({ vm.ui.value }) { ManualIssue.UNBALANCED in it.check.issues }

        assertFalse(ui.canSave)
        assertEquals(1_000_000L, ui.check.differenceRial)
        vm.onAction(ManualAction.Save)
        assertTrue(committedDocs().isEmpty())
        assertEquals(null, vm.ui.value.savedDocNumber)
    }

    @Test
    fun newCustomerIsCreatedOnlyWhenTheDocumentIsSaved() {
        val vm = newVm()
        val rows = awaitRows(vm)
        vm.onAction(ManualAction.OpenPicker(rows[0].id))
        vm.onAction(ManualAction.SetQuery("  علی   رضایی "))
        awaitValue({ vm.ui.value }) { it.canCreateParty }
        vm.onAction(ManualAction.PickNewParty(PartyKind.CUSTOMER))
        vm.onAction(ManualAction.OpenPicker(rows[1].id))
        vm.onAction(ManualAction.PickAccount("4100"))
        vm.onAction(ManualAction.SetAmount(rows[0].id, "300000"))
        vm.onAction(ManualAction.SetAmount(rows[1].id, "300000"))
        awaitValue({ vm.ui.value }) { it.canSave }

        // Nothing is persisted before the user saves. (The chart seeds a sample customer 1301 under 1300, so match by name.)
        assertTrue(runBlocking { ledger.accounts().first() }.none { it.nameFa == "علی رضایی" })

        vm.onAction(ManualAction.Save)
        val saved = awaitValue({ vm.ui.value }) { it.savedDocNumber != null }
        assertEquals(1, saved.savedDocNumber)
        val party = runBlocking { ledger.accounts().first() }.single { it.nameFa == "علی رضایی" }
        assertEquals("1300", party.parent)
        assertEquals(AccountLevel.DETAIL, party.level)
        assertTrue(committedDocs().single().entry.lines.any { it.accountCode == party.code && it.side == Side.DEBIT })
    }

    @Test
    fun draftPrefillKeepsLinesDateAndMemoAndIsNotPersisted() {
        val vm = newVm()
        val draft = JournalEntry(
            id = "draft-1",
            date = LocalDate.of(2026, 1, 5),
            templateId = TemplateId.SALE_CASH,
            memo = "فروش",
            lines = listOf(
                JournalLine("1100", Side.DEBIT, Money(5_000_000L)),
                JournalLine("4100", Side.CREDIT, Money(5_000_000L)),
            ),
        )
        vm.start(draft)
        val ui = awaitValue({ vm.ui.value }) { it.rows.size == 2 && it.rows[0].accountCode == "1100" }
        assertEquals(JalaliDate.from(LocalDate.of(2026, 1, 5)).toString(), ui.dateText)
        assertEquals("فروش", ui.memo)
        assertEquals(listOf("1100", "4100"), ui.rows.map { it.accountCode })
        assertEquals(listOf(Side.DEBIT, Side.CREDIT), ui.rows.map { it.side })
        awaitValue({ vm.ui.value }) { it.canSave }
        assertTrue(committedDocs().isEmpty())
    }

    @Test
    fun amountThatIsNotWholeTomanIsNotPrefilledAndBlocksSave() {
        val vm = newVm()
        val draft = JournalEntry(
            id = "draft-2",
            date = LocalDate.of(2026, 1, 5),
            templateId = TemplateId.SALE_CASH,
            memo = null,
            lines = listOf(
                JournalLine("1100", Side.DEBIT, Money(5_000_005L)),
                JournalLine("4100", Side.CREDIT, Money(5_000_005L)),
            ),
        )
        vm.start(draft)
        val ui = awaitValue({ vm.ui.value }) { it.rows.size == 2 && it.rows[0].accountCode == "1100" }
        assertEquals("", ui.rows[0].amountText)
        assertFalse(ui.rows[0].amountValid)
        assertFalse(ui.canSave)
    }

    @Test
    fun dateInUnopenedFiscalYearBlocksSave() {
        val vm = newVm()
        val rows = awaitRows(vm)
        fillBalanced(vm, debit = "1100", credit = "4100", amount = "500000")
        vm.onAction(ManualAction.SetDate("۱۴۰۵/۰۱/۰۱"))
        val ui = awaitValue({ vm.ui.value }) { !it.dateYearOpen }
        assertEquals(1405, ui.dateYear)
        assertFalse(ui.canSave)
        assertEquals(rows.size, ui.rows.size)
    }

    @Test
    fun pickerListsOnlyPostableAccounts() {
        val vm = newVm()
        val rows = awaitRows(vm)
        vm.onAction(ManualAction.OpenPicker(rows[0].id))
        val ui = awaitValue({ vm.ui.value }) { it.pickerAccounts.isNotEmpty() }
        val allParents = runBlocking { ledger.accounts().first() }.mapNotNull { it.parent }.toSet()
        assertTrue(ui.pickerAccounts.none { it.level == AccountLevel.GROUP })
        assertTrue(ui.pickerAccounts.none { it.code in allParents })
        assertTrue(ui.pickerAccounts.any { it.code == "1100" })
    }
}
