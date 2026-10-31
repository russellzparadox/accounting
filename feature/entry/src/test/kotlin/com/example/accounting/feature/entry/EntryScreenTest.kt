package com.example.accounting.feature.entry

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.accounting.core.data.config.BackendConfig
import com.example.accounting.core.data.config.ConfigStore
import com.example.accounting.core.data.db.AppDatabase
import com.example.accounting.core.data.nlp.NlParserProvider
import com.example.accounting.core.data.repo.CommittedDoc
import com.example.accounting.core.data.repo.LedgerRepository
import com.example.accounting.core.data.repo.PartyRepository
import com.example.accounting.core.data.seed.ChartSeeder
import com.example.accounting.core.domain.ChartOfAccounts
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.domain.JournalLine
import com.example.accounting.core.domain.Money
import com.example.accounting.core.domain.PaidWith
import com.example.accounting.core.domain.Side
import com.example.accounting.core.domain.TemplateId
import com.example.accounting.core.domain.TransactionIntent
import com.example.accounting.core.domain.pipeline.NlParser
import com.example.accounting.core.domain.pipeline.ParsedIntent
import com.example.accounting.core.domain.pipeline.ParserResult
import com.example.accounting.core.persian.AmountUnit
import com.example.accounting.core.data.config.SettingsRepository
import com.example.accounting.core.persian.MoneyFormat
import com.example.accounting.core.ui.money.LocalAmountUnit
import com.example.accounting.core.ui.theme.AccountingTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.example.accounting.core.data.speech.SpeechErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests on Robolectric (JVM). Stateless checks first, then one run through the real ViewModel.
 * Window is phone-sized (411x1200dp): the default Robolectric 320x470dp window is shorter than a
 * confirmation card, so its Dr/Cr totals and Confirm button would be off-screen (not displayed, not clickable).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1200dp")
class EntryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun str(id: Int, vararg args: Any): String = context.getString(id, *args)

    /**
     * The Confirm button only. Its label is the same string as the top-bar title (entry_title), so a
     * plain text query matches two nodes; the click action picks the button.
     */
    private fun confirmButton(): SemanticsMatcher =
        hasText(str(R.string.confirm_action)) and hasClickAction()

    @Test
    fun layoutIsRtlAndInputBarHasDescriptions() {
        compose.setContent {
            AccountingTheme {
                Text(text = LocalLayoutDirection.current.name, modifier = Modifier.testTag("dir"))
                EntryScreen(state = EntryUiState(), onAction = {})
            }
        }
        compose.onNodeWithTag("dir").assertTextEquals("Rtl")
        compose.onNodeWithContentDescription(str(R.string.input_send_cd)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(str(R.string.input_mic_cd)).assertIsEnabled()
    }

    @Test
    fun micListeningShowsStopLabelAndLocksSend() {
        compose.setContent {
            AccountingTheme {
                EntryScreen(state = EntryUiState(), onAction = {}, voice = VoiceUiState(listening = true))
            }
        }
        compose.onNodeWithContentDescription(str(R.string.input_mic_stop_cd)).assertIsEnabled()
        compose.onNodeWithText(str(R.string.input_listening)).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.input_send_cd)).assertIsNotEnabled()
    }

    @Test
    fun voiceErrorIsShownAsText() {
        compose.setContent {
            AccountingTheme {
                EntryScreen(
                    state = EntryUiState(),
                    onAction = {},
                    voice = VoiceUiState(error = SpeechErrorKind.NO_MATCH),
                )
            }
        }
        compose.onNodeWithText(str(R.string.input_voice_unclear)).assertIsDisplayed()
    }

    @Test
    fun micWithoutPermissionOpensRationaleAndDoesNotStartListening() {
        val voiceActions = mutableListOf<VoiceAction>()
        compose.setContent {
            AccountingTheme {
                EntryScreen(state = EntryUiState(), onAction = {}, onVoiceAction = { voiceActions += it })
            }
        }
        compose.onNodeWithContentDescription(str(R.string.input_mic_cd)).performClick()
        compose.onNodeWithText(str(R.string.mic_permission_body)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.mic_permission_action)).assertIsDisplayed()
        assertTrue(voiceActions.isEmpty())
    }

    @Test
    fun sendDispatchesSubmitWithTypedText() {
        val actions = mutableListOf<EntryAction>()
        compose.setContent {
            AccountingTheme {
                var text by remember { mutableStateOf("") }
                EntryScreen(
                    state = EntryUiState(),
                    onAction = { actions += it },
                    input = text,
                    onInputChange = { text = it },
                )
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput(SODA_TEXT)
        compose.onNodeWithContentDescription(str(R.string.input_send_cd)).assertIsEnabled().performClick()
        assertEquals(listOf<EntryAction>(EntryAction.Submit(SODA_TEXT)), actions)
    }

    /** Leaving the entry tab removes the screen from composition. The unsent text must come back unchanged. */
    @Test
    fun unsentTextSurvivesLeavingTheEntryTab() {
        val db = AppDatabase.inMemory(context)
        try {
            val chart = ChartOfAccounts.standard()
            val vm = EntryViewModel(
                context = context,
                ledger = LedgerRepository(db, chart),
                chart = chart,
                parties = PartyRepository(db),
                seeder = ChartSeeder(db, chart),
                configStore = ConfigStore(context, FakeTokenCipher),
                parserProvider = FixedProvider(StubParser(sodaResult())),
                clock = clock,
                settings = SettingsRepository(context),
            )
            val onEntryTab = mutableStateOf(true)
            compose.setContent {
                AccountingTheme {
                    if (onEntryTab.value) {
                        val input by vm.input.collectAsState()
                        EntryScreen(
                            state = EntryUiState(),
                            onAction = vm::onAction,
                            input = input,
                            onInputChange = vm::onInputChange,
                        )
                    }
                }
            }

            compose.onNode(hasSetTextAction()).performTextInput(SODA_TEXT)
            compose.runOnIdle { onEntryTab.value = false }
            compose.runOnIdle { onEntryTab.value = true }
            compose.onNode(hasSetTextAction()).assertTextEquals(SODA_TEXT)
        } finally {
            db.close()
        }
    }

    /** Flipping the setting changes the draft card's amounts: same entry, rial text instead of toman. */
    @Test
    fun draftShowsTotalsInRialWhenSettingIsRial() {
        val card = testDraft()
        val rialText = MoneyFormat.format(SODA_RIAL, AmountUnit.RIAL)
        compose.setContent {
            AccountingTheme {
                CompositionLocalProvider(LocalAmountUnit provides AmountUnit.RIAL) {
                    EntryScreen(state = EntryUiState(items = listOf(card)), onAction = {})
                }
            }
        }
        compose.onNodeWithText(str(R.string.confirm_debit_total, rialText)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.confirm_credit_total, rialText)).assertIsDisplayed()
    }

    /** Quick manual sheet: 500 typed in rial mode posts 500 rial. */
    @Test
    fun manualFormPostsTypedAmountAsRialInRialMode() {
        assertManualAmount(AmountUnit.RIAL, expectedRial = 500L)
    }

    /** Quick manual sheet: 500 typed in toman mode posts 5000 rial. */
    @Test
    fun manualFormPostsTypedAmountAsTomanInTomanMode() {
        assertManualAmount(AmountUnit.TOMAN, expectedRial = 5_000L)
    }

    private fun assertManualAmount(unit: AmountUnit, expectedRial: Long) {
        val actions = mutableListOf<EntryAction>()
        compose.setContent {
            AccountingTheme {
                CompositionLocalProvider(LocalAmountUnit provides unit) {
                    ManualEntryForm(onDismiss = {}, onSubmit = { actions += it })
                }
            }
        }
        val label = if (unit == AmountUnit.RIAL) R.string.amount_field_label_rial else R.string.amount_field_label
        compose.onNodeWithText(str(label)).assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("500")
        compose.onNodeWithText(str(R.string.manual_submit)).performScrollTo().performClick()
        val submitted = actions.single() as EntryAction.SubmitManual
        assertEquals(expectedRial, submitted.amountRial)
        assertEquals(TemplateId.PURCHASE_CASH, submitted.templateId)
    }

    /** NOT_CONFIGURED offers the server settings action (no dialog); the screen hands it to the Settings callback. */
    @Test
    fun notConfiguredErrorOpensBackendSettingsInsteadOfADialog() {
        val error = ChatItem.Error(
            id = "err-nc",
            kind = EntryErrorKind.NOT_CONFIGURED,
            title = str(R.string.error_not_configured_title),
            message = str(R.string.error_not_configured_body),
            retryText = null,
        )
        val actions = mutableListOf<EntryAction>()
        var opened = 0
        compose.setContent {
            AccountingTheme {
                EntryScreen(
                    state = EntryUiState(items = listOf(error)),
                    onAction = { actions += it },
                    onOpenBackendSettings = { opened++ },
                )
            }
        }
        compose.onNodeWithText(str(R.string.action_configure_server)).performClick()
        assertEquals(1, opened)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun draftShowsTotalsAndConfirmDispatches() {
        val card = testDraft()
        val actions = mutableListOf<EntryAction>()
        compose.setContent {
            AccountingTheme {
                EntryScreen(state = EntryUiState(items = listOf(card)), onAction = { actions += it })
            }
        }
        compose.onNodeWithText(str(R.string.confirm_title)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.confirm_debit_total, SODA_TOMAN_TEXT)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.confirm_credit_total, SODA_TOMAN_TEXT)).assertIsDisplayed()
        compose.onNode(confirmButton()).performClick()
        assertEquals(listOf<EntryAction>(EntryAction.Confirm(card.id)), actions)
    }

    @Test
    fun committedShowsReverseAndDispatchesUndo() {
        val committed = ChatItem.Committed(
            id = "draft-1",
            doc = CommittedDoc(id = "entry-1", fiscalYear = 1404, docNumber = 1, entry = testEntry()),
        )
        val actions = mutableListOf<EntryAction>()
        compose.setContent {
            AccountingTheme {
                EntryScreen(state = EntryUiState(items = listOf(committed)), onAction = { actions += it })
            }
        }
        compose.onNodeWithText(str(R.string.reverse_action)).performClick()
        assertEquals(listOf<EntryAction>(EntryAction.Undo("entry-1")), actions)
    }

    @Test
    fun offlineErrorOffersRetryWithSameText() {
        val error = ChatItem.Error(
            id = "err-1",
            kind = EntryErrorKind.OFFLINE,
            title = str(R.string.error_network_title),
            message = str(R.string.error_network_body),
            retryText = SODA_TEXT,
        )
        val actions = mutableListOf<EntryAction>()
        compose.setContent {
            AccountingTheme {
                EntryScreen(state = EntryUiState(items = listOf(error)), onAction = { actions += it })
            }
        }
        compose.onNodeWithText(str(R.string.action_retry)).performClick()
        assertEquals(listOf<EntryAction>(EntryAction.Retry("err-1")), actions)
    }

    @Test
    fun sendThenConfirmThroughViewModelCommits() {
        ConfigStore(context, FakeTokenCipher).save(BackendConfig("https://backend.test", "test-token"))
        val db = AppDatabase.inMemory(context)
        try {
            val chart = ChartOfAccounts.standard()
            val ledger = LedgerRepository(db, chart)
            val vm = EntryViewModel(
                context = context,
                ledger = ledger,
                chart = chart,
                parties = PartyRepository(db),
                seeder = ChartSeeder(db, chart),
                configStore = ConfigStore(context, FakeTokenCipher),
                parserProvider = FixedProvider(StubParser(sodaResult())),
                clock = clock,
                settings = SettingsRepository(context),
            )
            compose.setContent {
                AccountingTheme {
                    val state by vm.state.collectAsState()
                    val input by vm.input.collectAsState()
                    EntryScreen(
                        state = state,
                        onAction = vm::onAction,
                        accountName = vm::accountName,
                        input = input,
                        onInputChange = vm::onInputChange,
                    )
                }
            }

            compose.onNode(hasSetTextAction()).performTextInput(SODA_TEXT)
            compose.onNodeWithContentDescription(str(R.string.input_send_cd)).performClick()
            compose.waitUntil(TIMEOUT_MS) {
                compose.onAllNodes(confirmButton()).fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNode(confirmButton()).performClick()
            compose.waitUntil(TIMEOUT_MS) {
                compose.onAllNodesWithText(str(R.string.reverse_action)).fetchSemanticsNodes().isNotEmpty()
            }

            val journal = runBlocking { ledger.journal(JALALI_YEAR).first() }
            assertEquals(1, journal.size)
        } finally {
            db.close()
        }
    }

    private fun testEntry(id: String = "entry-1"): JournalEntry = JournalEntry(
        id = id,
        date = LocalDate.of(2026, 1, 10),
        templateId = TemplateId.PURCHASE_CASH,
        memo = "نوشابه",
        lines = listOf(
            JournalLine("1401", Side.DEBIT, Money(SODA_RIAL)),
            JournalLine("1101", Side.CREDIT, Money(SODA_RIAL)),
        ),
    )

    private fun testDraft(): ChatItem.DraftCard = ChatItem.DraftCard(
        id = "draft-1",
        entry = testEntry(id = "entry-draft"),
        intent = TransactionIntent(
            type = TemplateId.PURCHASE_CASH,
            amountRial = SODA_RIAL,
            date = LocalDate.of(2026, 1, 10),
            paidWith = PaidWith.CASH,
            memo = "نوشابه",
            confidence = 92,
        ),
        reasons = emptyList(),
        reasonTexts = emptyList(),
        modelRial = SODA_RIAL,
        committing = false,
        source = null,
    )

    private fun sodaResult(): ParserResult = ParserResult.Intent(
        ParsedIntent(
            intent = TransactionIntent(
                type = TemplateId.PURCHASE_CASH,
                amountRial = SODA_RIAL,
                date = LocalDate.of(2026, 1, 10),
                paidWith = PaidWith.CASH,
                memo = "نوشابه",
                confidence = 92,
            ),
            amountText = "500٬000 تومان",
            amountValue = 500_000L,
            amountUnit = AmountUnit.TOMAN,
        ),
    )

    /** Returns one fixed result for any text. */
    private class StubParser(private val result: ParserResult) : NlParser {
        override suspend fun parse(text: String): ParserResult = result
    }

    /** Hands out [parser] only when the backend config is complete. */
    private class FixedProvider(private val parser: NlParser) : NlParserProvider {
        override fun parserFor(config: BackendConfig): NlParser? = if (config.isConfigured) parser else null
    }

    private companion object {
        const val SODA_TEXT = "۴ قوطی نوشابه روی هم ۵۰۰٬۰۰۰ تومان خریدم"
        const val SODA_RIAL = 5_000_000L
        const val JALALI_YEAR = 1404
        const val TIMEOUT_MS = 10_000L
        val SODA_TOMAN_TEXT: String = MoneyFormat.format(SODA_RIAL, AmountUnit.TOMAN)
        val clock: Clock = Clock.fixed(Instant.parse("2026-01-10T08:00:00Z"), ZoneId.of("Asia/Tehran"))
    }
}
