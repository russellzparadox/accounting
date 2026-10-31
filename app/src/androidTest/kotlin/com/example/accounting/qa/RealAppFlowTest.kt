package com.example.accounting.qa

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.accounting.MainActivity
import com.example.accounting.R as AppR
import com.example.accounting.core.domain.ChartOfAccounts
import com.example.accounting.core.domain.report.ReportFormat
import com.example.accounting.core.persian.AmountUnit
import com.example.accounting.core.persian.MoneyFormat
import com.example.accounting.core.persian.PersianDigits
import com.example.accounting.feature.entry.MANUAL_AMOUNT_FIELD_TAG
import com.example.accounting.feature.entry.MANUAL_SUBMIT_TAG
import com.example.accounting.feature.entry.R as EntryR
import com.example.accounting.feature.ledger.R as LedgerR
import com.example.accounting.feature.reports.R as ReportsR
import com.example.accounting.feature.settings.R as SettingsR
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One real-app run on the emulator: MainActivity with the real Room file, Hilt graph and navigation.
 * Single test method on purpose: the app's Room singleton lives for the whole process, so a second
 * method would see the first one's data. Each step asserts before it moves on; a failure names the step.
 *
 * Flow: accept onboarding -> quick manual cash purchase 500,000 toman -> journal row (Persian digits)
 * -> document detail -> cash account ledger -> reverse the document -> trial balance preview
 * -> settings unit rial (amounts re-render) -> manual journal editor.
 * Screenshots go to the app's external `qa/` folder.
 */
@RunWith(AndroidJUnit4::class)
class RealAppFlowTest {
    @get:Rule(order = 0)
    val cleanState = CleanAppStateRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun str(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun waitForNode(matcher: SemanticsMatcher, timeoutMs: Long = 15_000L) {
        composeRule.waitUntil(timeoutMs) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun clickNode(matcher: SemanticsMatcher) {
        waitForNode(matcher)
        composeRule.onAllNodes(matcher).onFirst().performClick()
    }

    private fun screenshot(name: String) {
        composeRule.waitForIdle()
        QaOutput.screenshot(context, name)
    }

    private fun tap(text: String, substring: Boolean = false) =
        hasText(text, substring = substring) and hasClickAction()

    @Test
    fun postReverseAndRialMode() {
        val journalTab = str(AppR.string.nav_journal)
        val docLabel = "سند شماره " + PersianDigits.toPersian("1")
        val cashName = ChartOfAccounts.standard().account("1100")!!.nameFa
        val tomanTotal = MoneyFormat.format(5_000_000L, AmountUnit.TOMAN)

        // 0. Onboarding gate: accept the privacy disclosure through the UI.
        clickNode(hasText(str(SettingsR.string.onboarding_accept)))

        // 1. Quick manual entry: cash purchase, 500,000 toman, no backend.
        clickNode(tap(str(EntryR.string.action_manual_entry)))
        composeRule.onNode(hasTestTag(MANUAL_AMOUNT_FIELD_TAG)).performTextReplacement("500000")
        clickNode(hasTestTag(MANUAL_SUBMIT_TAG))
        val committed = str(EntryR.string.committed_notice, PersianDigits.toPersian("1"))
        waitForNode(hasText(committed))
        screenshot("01-entry-committed")

        // 2. Journal tab: the document is listed with Persian digits.
        clickNode(tap(journalTab))
        waitForNode(hasText(docLabel, substring = true))
        screenshot("02-journal")

        // 3. Document detail: totals in the unit from settings (toman by default).
        clickNode(tap(docLabel, substring = true))
        waitForNode(hasText(str(LedgerR.string.doc_total_debit, tomanTotal)))
        screenshot("03-document-detail")
        clickNode(tap(str(LedgerR.string.doc_close)))

        // 4. Accounts tab -> cash account ledger, then back to the account list.
        clickNode(tap(str(AppR.string.nav_accounts)))
        waitForNode(tap(cashName, substring = true))
        screenshot("04-accounts")
        clickNode(tap(cashName, substring = true))
        waitForNode(hasText(str(LedgerR.string.ledger_title, cashName)))
        screenshot("05-account-ledger")
        UiDevice.getInstance(instrumentation).pressBack()

        // 5. Reverse the document from the journal. Reversal is a new document; the original stays.
        clickNode(tap(journalTab))
        clickNode(tap(docLabel, substring = true))
        clickNode(tap(str(LedgerR.string.doc_reverse_action)))
        clickNode(tap(str(LedgerR.string.doc_reverse_confirm)))
        clickNode(tap(str(LedgerR.string.doc_close)))
        waitForNode(hasText(str(LedgerR.string.journal_row_reversal_of, PersianDigits.toPersian("1"))))
        screenshot("06-journal-reversed")

        // 6. Trial balance preview: 500,000 toman posted and reversed, so gross debit = gross credit = 1,000,000 toman.
        clickNode(tap(str(AppR.string.nav_reports)))
        val grossTotal = ReportFormat.amountDisplay(10_000_000L, AmountUnit.TOMAN)
        composeRule.waitUntil(15_000L) {
            composeRule.onAllNodes(hasText(grossTotal)).fetchSemanticsNodes().size >= 2
        }
        composeRule.onAllNodes(hasText(grossTotal)).assertCountEquals(2)
        composeRule.onAllNodes(hasText(str(ReportsR.string.report_unbalanced))).assertCountEquals(0)
        screenshot("07-reports-trial")
        clickNode(hasContentDescription(str(ReportsR.string.report_back_cd)))

        // 7. Settings: switch to rial, then the journal amounts must re-render in rial.
        clickNode(tap(str(AppR.string.nav_settings)))
        waitForNode(hasText(str(SettingsR.string.section_unit)))
        screenshot("08-settings")
        clickNode(tap(str(SettingsR.string.unit_rial)))
        clickNode(tap(str(SettingsR.string.settings_back_cd)))
        clickNode(tap(journalTab))
        val rialTotal = MoneyFormat.format(5_000_000L, AmountUnit.RIAL)
        waitForNode(hasText(rialTotal))
        composeRule.onAllNodes(hasText(tomanTotal)).assertCountEquals(0)
        screenshot("09-journal-rial")

        // 8. Manual journal editor (last: it replaces the tabs while open).
        clickNode(tap(str(LedgerR.string.manual_open_action)))
        composeRule.waitForIdle()
        screenshot("10-manual-editor")
    }
}
