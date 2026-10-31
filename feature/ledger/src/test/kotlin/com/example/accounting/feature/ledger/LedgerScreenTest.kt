package com.example.accounting.feature.ledger

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.accounting.core.data.repo.CommittedDoc
import com.example.accounting.core.data.repo.DocumentLinks
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.domain.JournalLine
import com.example.accounting.core.domain.Money
import com.example.accounting.core.domain.Side
import com.example.accounting.core.domain.TemplateId
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

    /** Edit and delete never appear anywhere on these screens. Checked as substrings of every text node. */
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
}
