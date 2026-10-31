package com.example.accounting.feature.ledger
import com.example.accounting.core.ui.R as UiR
import com.example.accounting.core.ui.components.JALALI_DAY_TAG_PREFIX
import androidx.compose.ui.test.onNodeWithTag

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.accounting.core.domain.ManualCheck
import com.example.accounting.core.domain.ManualIssue
import com.example.accounting.core.domain.Side
import com.example.accounting.core.persian.AmountUnit
import com.example.accounting.core.ui.money.LocalAmountUnit
import androidx.compose.runtime.CompositionLocalProvider
import com.example.accounting.core.persian.MoneyFormat
import com.example.accounting.core.ui.theme.AccountingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stateless editor: totals, difference indicator, save gate and actions. Phone-sized window. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1200dp")
class ManualJournalScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun str(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun toman(rial: Long): String = MoneyFormat.format(rial, AmountUnit.TOMAN)

    private fun row(id: Long, code: String?, label: String?, side: Side, amount: String, valid: Boolean) = ManualRowUi(
        id = id,
        accountCode = code,
        accountLabel = label,
        newParty = null,
        side = side,
        amountText = amount,
        amountValid = valid,
    )

    private fun ui(check: ManualCheck, saving: Boolean = false) = ManualJournalUi(
        dateText = "۱۴۰۴/۱۰/۲۰",
        dateValid = true,
        dateYear = 1404,
        dateYearOpened = true,
        memo = "",
        rows = listOf(
            row(1, "1100", "صندوق", Side.DEBIT, "۵۰۰۰۰۰", valid = true),
            row(2, "4100", "فروش", Side.CREDIT, "۵۰۰۰۰۰", valid = true),
        ),
        check = check,
        pickerLine = null,
        query = "",
        pickerAccounts = emptyList(),
        canCreateParty = false,
        saving = saving,
        savedDocNumber = null,
        error = null,
    )

    @Test
    fun rialUnitShowsTotalsInRial() {
        val check = ManualCheck(emptySet(), debitRial = 5_000_000L, creditRial = 5_000_000L)
        compose.setContent {
            CompositionLocalProvider(LocalAmountUnit provides AmountUnit.RIAL) {
                ManualJournalScreen(ui = ui(check), onAction = {}, onClose = {})
            }
        }
        compose.onNodeWithText(str(R.string.manual_total_debit, MoneyFormat.format(5_000_000L, AmountUnit.RIAL)))
            .assertIsDisplayed()
    }

    @Test
    fun calendarPickerWritesTheChosenJalaliDateIntoTheField() {
        val check = ManualCheck(emptySet(), debitRial = 0L, creditRial = 0L)
        var picked: String? = null
        compose.setContent {
            ManualJournalScreen(
                ui = ui(check),
                onAction = { if (it is ManualAction.SetDate) picked = it.text },
                onClose = {},
            )
        }
        compose.onNodeWithText(str(R.string.manual_pick_date_button)).performClick()
        compose.onNodeWithTag(JALALI_DAY_TAG_PREFIX + "15").performClick()
        compose.onNodeWithText(str(UiR.string.picker_confirm)).performClick()
        assertEquals("۱۴۰۴/۱۰/۱۵", picked)
    }

    @Test
    fun unbalancedDocumentShowsDifferenceAndSaveIsDisabled() {
        val totals = ManualCheck(setOf(ManualIssue.UNBALANCED), debitRial = 5_000_000L, creditRial = 4_000_000L)
        compose.setContent {
            AccountingTheme {
                ManualJournalScreen(ui = ui(totals), onAction = {}, onClose = {})
            }
        }
        compose.onNodeWithText(str(R.string.manual_difference, toman(1_000_000L))).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.manual_save)).assertIsNotEnabled()
    }

    @Test
    fun balancedDocumentEnablesSaveAndRecordsTheAction() {
        val actions = mutableListOf<ManualAction>()
        val totals = ManualCheck(emptySet(), debitRial = 5_000_000L, creditRial = 5_000_000L)
        compose.setContent {
            AccountingTheme {
                ManualJournalScreen(ui = ui(totals), onAction = { actions += it }, onClose = {})
            }
        }
        compose.onNodeWithText(str(R.string.manual_balanced)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.manual_save)).performClick()
        compose.runOnIdle {
            assertEquals(listOf(ManualAction.Save), actions)
        }
    }

    @Test
    fun addLineButtonRecordsAddLineAction() {
        val actions = mutableListOf<ManualAction>()
        val totals = ManualCheck(emptySet(), debitRial = 0L, creditRial = 0L)
        compose.setContent {
            AccountingTheme {
                ManualJournalScreen(ui = ui(totals), onAction = { actions += it }, onClose = {})
            }
        }
        compose.onNodeWithText(str(R.string.manual_add_line)).performClick()
        compose.runOnIdle {
            assertEquals(listOf(ManualAction.AddLine), actions)
        }
    }
}
