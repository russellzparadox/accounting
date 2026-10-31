package com.example.accounting.feature.ledger

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.accounting.core.data.repo.CommittedDoc
import com.example.accounting.core.data.repo.DocumentLinks
import com.example.accounting.core.domain.AccountLevel
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.domain.JournalLine
import com.example.accounting.core.domain.Money
import com.example.accounting.core.domain.Side
import com.example.accounting.core.domain.TemplateId
import com.example.accounting.core.persian.AmountUnit
import com.example.accounting.core.persian.MoneyFormat
import com.example.accounting.core.persian.PersianDigits
import com.example.accounting.core.ui.money.LocalAmountUnit
import com.example.accounting.core.ui.theme.AccountingTheme
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ledger UI on Robolectric. Stateless composables only, so no ViewModel is needed.
 * Phone-sized window, same as EntryScreenTest, so the sheet content is laid out in full.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1200dp")
class LedgerScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun str(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun doc(id: String, number: Int, reversalOf: String? = null): CommittedDoc = CommittedDoc(
        id = id,
        fiscalYear = 1404,
        docNumber = number,
        entry = JournalEntry(
            id = id,
            date = LocalDate.of(2026, 1, 5),
            templateId = TemplateId.SALE_CASH,
            memo = "فروش",
            lines = listOf(
                JournalLine("1100", Side.DEBIT, Money(1_000_000L)),
                JournalLine("4100", Side.CREDIT, Money(1_000_000L)),
            ),
            reversalOfId = reversalOf,
        ),
    )

    /**
     * Edit and delete never appear on these screens. Checked as substrings of every text node.
     * Scope is committed documents only: the «ویرایش مبلغ» action on an UNCONFIRMED entry draft card
     * (feature:entry ConfirmationCard) is allowed, lives in another module, and is not checked here.
     */
    private fun assertNoEditOrDeleteAction() {
        listOf("ویرایش", "حذف").forEach { word ->
            assertTrue(
                "found action text '$word'",
                compose.onAllNodesWithText(word, substring = true).fetchSemanticsNodes().isEmpty(),
            )
        }
    }

    @Test
    fun originalDocumentOffersReversalAndNoEditOrDelete() {
        val detail = DocumentDetailUi(DocumentLinks(doc("a", 1), null, null))
        compose.setContent {
            AccountingTheme {
                DocumentDetailContent(detail = detail, accountName = { it }, onOpen = {}, onReverse = {}, onClose = {})
            }
        }
        compose.onNodeWithText(str(R.string.doc_reverse_action)).assertExists()
        assertNoEditOrDeleteAction()
    }

    @Test
    fun reversedDocumentHasNoReversalAction() {
        val detail = DocumentDetailUi(DocumentLinks(doc("a", 1), null, doc("r", 2, reversalOf = "a")))
        compose.setContent {
            AccountingTheme {
                DocumentDetailContent(detail = detail, accountName = { it }, onOpen = {}, onReverse = {}, onClose = {})
            }
        }
        assertTrue(compose.onAllNodesWithText(str(R.string.doc_reverse_action)).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText(str(R.string.doc_is_reversed_by, "۲")).assertExists()
        compose.onNodeWithText(str(R.string.doc_cannot_reverse_reversed)).assertExists()
        assertNoEditOrDeleteAction()
    }

    @Test
    fun reversalDocumentLinksOriginalAndHasNoReversalAction() {
        val reversal = doc("r", 2, reversalOf = "a")
        val detail = DocumentDetailUi(DocumentLinks(reversal, doc("a", 1), null))
        compose.setContent {
            AccountingTheme {
                DocumentDetailContent(detail = detail, accountName = { it }, onOpen = {}, onReverse = {}, onClose = {})
            }
        }
        assertTrue(compose.onAllNodesWithText(str(R.string.doc_reverse_action)).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText(str(R.string.doc_is_reversal_of, "۱")).assertExists()
        compose.onNodeWithText(str(R.string.doc_cannot_reverse_reversal)).assertExists()
        assertNoEditOrDeleteAction()
    }

    /** Committed-data scope: account picker (tree view) has no edit or delete on accounts or balances. */
    @Test
    fun committedAccountPickerHasNoEditOrDelete() {
        val rows = listOf(
            AccountRow(
                code = "1100",
                nameFa = "صندوق",
                level = AccountLevel.DETAIL,
                depth = 0,
                netRial = 1_000_000L,
            ),
        )
        compose.setContent {
            AccountingTheme {
                AccountPicker(
                    years = listOf(1404),
                    selectedYear = 1404,
                    onYear = {},
                    rows = rows,
                    query = "",
                    onQuery = {},
                    hideZero = false,
                    onHideZero = {},
                    onPick = {},
                )
            }
        }
        // Positive control: the screen rendered, so the absence checks below are not vacuous.
        assertTrue(compose.onAllNodesWithText("صندوق", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertNoEditOrDeleteAction()
    }

    /** Committed-data scope: account ledger lists committed lines only, with no edit or delete on them. */
    @Test
    fun committedAccountLedgerHasNoEditOrDelete() {
        val ui = AccountLedgerUi(
            code = "1100",
            name = "صندوق",
            rows = listOf(
                LedgerRowUi(
                    entryId = "a",
                    date = LocalDate.of(2026, 1, 5),
                    docNumber = 1,
                    memo = "فروش",
                    debit = 1_000_000L,
                    credit = 0L,
                    balance = 1_000_000L,
                ),
            ),
            totalDebit = 1_000_000L,
            totalCredit = 0L,
            closing = 1_000_000L,
        )
        compose.setContent {
            AccountingTheme {
                AccountLedgerScreen(
                    ui = ui,
                    years = listOf(1404),
                    filter = LedgerFilter(year = 1404),
                    onYear = {},
                    onPreset = {},
                    onFromChange = {},
                    onToChange = {},
                    onBack = {},
                    onOpen = {},
                )
            }
        }
        assertTrue(compose.onAllNodesWithText("فروش", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertNoEditOrDeleteAction()
    }

    /** Touch target: every filter chip is at least 48dp tall (DESIGN 5.7). */
    @Test
    fun dateFilterChipsAreAtLeast48dpTall() {
        compose.setContent {
            AccountingTheme {
                DateFilterBar(
                    preset = DatePreset.ALL,
                    fromText = "",
                    toText = "",
                    dateError = false,
                    onPreset = {},
                    onFromChange = {},
                    onToChange = {},
                )
            }
        }
        compose.onNodeWithText(str(R.string.range_this_month)).assertHeightIsAtLeast(48.dp)
    }

    /** Touch target: fiscal year chips are at least 48dp tall (DESIGN 5.7). */
    @Test
    fun fiscalYearChipsAreAtLeast48dpTall() {
        compose.setContent {
            AccountingTheme {
                FiscalYearChips(years = listOf(1404), selected = 1404, onSelect = {})
            }
        }
        compose.onNodeWithText(PersianDigits.toPersian("1404")).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun journalListShowsReversedBadgeAndNoEditOrDelete() {
        val rows = listOf(
            JournalRow(
                id = "a",
                date = LocalDate.of(2026, 1, 5),
                docNumber = 1,
                memo = "فروش",
                totalRial = 1_000_000L,
                reversalOfNumber = null,
                isReversal = false,
                isReversed = true,
            ),
        )
        compose.setContent {
            AccountingTheme {
                JournalListScreen(
                    years = listOf(1404),
                    filter = LedgerFilter(year = 1404),
                    query = "",
                    rows = rows,
                    onYear = {},
                    onPreset = {},
                    onFromChange = {},
                    onToChange = {},
                    onQuery = {},
                    onOpen = {},
                )
            }
        }
        compose.onNodeWithText(str(R.string.status_reversed)).assertExists()
        assertNoEditOrDeleteAction()
    }

    private fun oneRowJournal(): List<JournalRow> = listOf(
        JournalRow(
            id = "a",
            date = LocalDate.of(2026, 1, 5),
            docNumber = 1,
            memo = "فروش",
            totalRial = 1_000_000L,
            reversalOfNumber = null,
            isReversal = false,
            isReversed = false,
        ),
    )

    /** Journal row amount follows the unit from LocalAmountUnit: 1,000,000 rial shows as 1,000,000 rial. */
    @Test
    fun journalRowAmountIsRialWhenSettingIsRial() {
        compose.setContent {
            AccountingTheme {
                CompositionLocalProvider(LocalAmountUnit provides AmountUnit.RIAL) {
                    JournalListScreen(
                        years = listOf(1404),
                        filter = LedgerFilter(year = 1404),
                        query = "",
                        rows = oneRowJournal(),
                        onYear = {},
                        onPreset = {},
                        onFromChange = {},
                        onToChange = {},
                        onQuery = {},
                        onOpen = {},
                    )
                }
            }
        }
        compose.onNodeWithText(MoneyFormat.format(1_000_000L, AmountUnit.RIAL)).assertIsDisplayed()
    }

    /** Same row in toman mode: 1,000,000 rial shows as 100,000 toman. */
    @Test
    fun journalRowAmountIsTomanWhenSettingIsToman() {
        compose.setContent {
            AccountingTheme {
                CompositionLocalProvider(LocalAmountUnit provides AmountUnit.TOMAN) {
                    JournalListScreen(
                        years = listOf(1404),
                        filter = LedgerFilter(year = 1404),
                        query = "",
                        rows = oneRowJournal(),
                        onYear = {},
                        onPreset = {},
                        onFromChange = {},
                        onToChange = {},
                        onQuery = {},
                        onOpen = {},
                    )
                }
            }
        }
        compose.onNodeWithText(MoneyFormat.format(1_000_000L, AmountUnit.TOMAN)).assertIsDisplayed()
    }

    /** Account ledger totals follow the unit from LocalAmountUnit. */
    @Test
    fun accountLedgerTotalsFollowTheSettingsUnit() {
        val ui = AccountLedgerUi(
            code = "1100",
            name = "صندوق",
            rows = listOf(
                LedgerRowUi(
                    entryId = "a",
                    date = LocalDate.of(2026, 1, 5),
                    docNumber = 1,
                    memo = "فروش",
                    debit = 1_000_000L,
                    credit = 0L,
                    balance = 1_000_000L,
                ),
            ),
            totalDebit = 1_000_000L,
            totalCredit = 0L,
            closing = 1_000_000L,
        )
        compose.setContent {
            AccountingTheme {
                CompositionLocalProvider(LocalAmountUnit provides AmountUnit.RIAL) {
                    AccountLedgerScreen(
                        ui = ui,
                        years = listOf(1404),
                        filter = LedgerFilter(year = 1404),
                        onYear = {},
                        onPreset = {},
                        onFromChange = {},
                        onToChange = {},
                        onBack = {},
                        onOpen = {},
                    )
                }
            }
        }
        compose.onNodeWithText(
            context.getString(R.string.ledger_total_debit, MoneyFormat.format(1_000_000L, AmountUnit.RIAL)),
        ).assertIsDisplayed()
    }
}
