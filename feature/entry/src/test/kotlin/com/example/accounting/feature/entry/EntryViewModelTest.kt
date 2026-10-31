package com.example.accounting.feature.entry

import androidx.test.core.app.ApplicationProvider
import com.example.accounting.core.data.config.BackendConfig
import com.example.accounting.core.data.config.ConfigStore
import com.example.accounting.core.data.db.AppDatabase
import com.example.accounting.core.data.nlp.NlParserProvider
import com.example.accounting.core.data.repo.LedgerRepository
import com.example.accounting.core.data.repo.PartyRepository
import com.example.accounting.core.data.seed.ChartSeeder
import com.example.accounting.core.domain.ChartOfAccounts
import com.example.accounting.core.domain.ExpenseCategory
import com.example.accounting.core.domain.PaidWith
import com.example.accounting.core.domain.TemplateId
import com.example.accounting.core.domain.TransactionIntent
import com.example.accounting.core.domain.pipeline.ConfirmReason
import com.example.accounting.core.domain.pipeline.NlParser
import com.example.accounting.core.domain.pipeline.ParsedIntent
import com.example.accounting.core.domain.pipeline.ParserResult
import com.example.accounting.core.domain.pipeline.PipelineError
import com.example.accounting.core.persian.AmountUnit
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ViewModel flows against an in-memory Room database and scripted parser. No network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntryViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var db: AppDatabase
    private lateinit var chart: ChartOfAccounts
    private lateinit var ledger: LedgerRepository
    private lateinit var parties: PartyRepository
    private lateinit var configStore: ConfigStore

    /** 2026-01-10 is in Jalali 1404 (fiscal year 2025-03-21..2026-03-20). */
    private val clock = Clock.fixed(Instant.parse("2026-01-10T08:00:00Z"), ZoneId.of("Asia/Tehran"))
    private val jalaliYear = 1404

    @Before
    fun setUp() {
        db = AppDatabase.inMemory(context)
        chart = ChartOfAccounts.standard()
        ledger = LedgerRepository(db, chart)
        parties = PartyRepository(db)
        configStore = ConfigStore(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun viewModel(parser: NlParser = ScriptedParser()): EntryViewModel = EntryViewModel(
        context = context,
        ledger = ledger,
        chart = chart,
        parties = parties,
        seeder = ChartSeeder(db, chart),
        configStore = configStore,
        parserProvider = FixedProvider(parser),
        clock = clock,
    )

    private fun configure() = configStore.save(BackendConfig("https://backend.test", "test-token"))

    private fun sodaIntent(rial: Long = SODA_RIAL, confidence: Int = 92) = ParserResult.Intent(
        ParsedIntent(
            intent = TransactionIntent(
                type = TemplateId.PURCHASE_CASH,
                amountRial = rial,
                date = LocalDate.of(2026, 1, 10),
                paidWith = PaidWith.CASH,
                memo = "نوشابه",
                confidence = confidence,
            ),
            amountText = "500٬000 تومان",
            amountValue = 500_000L,
            amountUnit = AmountUnit.TOMAN,
        ),
    )

    @Test
    fun sodaExampleConfirmsIntoBalancedLedger() = runBlocking {
        configure()
        val vm = viewModel(ScriptedParser(sodaIntent()))

        vm.handle(EntryAction.Submit(SODA_TEXT))
        val card = vm.state.value.items.filterIsInstance<ChatItem.DraftCard>().single()
        assertTrue(card.reasons.isEmpty())
        assertEquals(SODA_RIAL, card.entry.debitTotal.rial)

        vm.handle(EntryAction.Confirm(card.id))

        val committed = vm.state.value.items.filterIsInstance<ChatItem.Committed>().single()
        assertEquals(card.entry.id, committed.doc.entry.id)
        val trial = ledger.trialBalance(jalaliYear)
        assertEquals(SODA_RIAL, trial.debitTotal)
        assertEquals(trial.debitTotal, trial.creditTotal)
        assertEquals(1, ledger.journal(jalaliYear).first().size)
    }

    @Test
    fun amountMismatchOffersDetectedOrModelAmount() = runBlocking {
        configure()
        val vm = viewModel(ScriptedParser(sodaIntent(rial = 4_000_000L)))

        vm.handle(EntryAction.Submit(SODA_TEXT))
        val card = vm.state.value.items.filterIsInstance<ChatItem.DraftCard>().single()
        val mismatch = card.reasons.single() as ConfirmReason.AmountMismatch
        assertEquals(4_000_000L, mismatch.llmRial)
        assertEquals(SODA_RIAL, mismatch.localRial)
        assertEquals(1, card.reasonTexts.size)

        vm.handle(EntryAction.UseDetectedAmount(card.id))
        val detected = vm.state.value.draft(card.id)!!
        assertEquals(SODA_RIAL, detected.intent.amountRial)
        assertTrue(detected.reasons.isEmpty())

        vm.handle(EntryAction.UseModelAmount(card.id))
        val model = vm.state.value.draft(card.id)!!
        assertEquals(4_000_000L, model.intent.amountRial)
        assertTrue(model.reasons.isEmpty())
    }

    @Test
    fun clarificationIsAnsweredWithOriginalPlusAnswer() = runBlocking {
        configure()
        val parser = ScriptedParser(
            ParserResult.Clarification("مبلغ چقدر بود؟"),
            sodaIntent(),
        )
        val vm = viewModel(parser)

        vm.handle(EntryAction.Submit("قوطی نوشابه خریدم"))
        assertTrue(vm.state.value.items.last() is ChatItem.Clarification)
        assertEquals("قوطی نوشابه خریدم", vm.state.value.pendingClarification)

        vm.handle(EntryAction.AnswerClarification("۵۰۰٬۰۰۰ تومان"))

        assertTrue(parser.requests[1].contains("قوطی نوشابه خریدم"))
        assertTrue(parser.requests[1].contains("500٬000 تومان"))
        assertNull(vm.state.value.pendingClarification)
        assertTrue(vm.state.value.items.last() is ChatItem.DraftCard)
    }

    @Test
    fun offlineErrorIsRetriedWithSameText() = runBlocking {
        configure()
        val vm = viewModel(ScriptedParser(ParserResult.Error(PipelineError.Offline), sodaIntent()))

        vm.handle(EntryAction.Submit(SODA_TEXT))
        val error = vm.state.value.items.last() as ChatItem.Error
        assertEquals(EntryErrorKind.OFFLINE, error.kind)
        assertTrue(ErrorAction.RETRY in error.actions)

        vm.handle(EntryAction.Retry(error.id))

        assertTrue(vm.state.value.items.none { it is ChatItem.Error })
        assertTrue(vm.state.value.items.last() is ChatItem.DraftCard)
    }

    @Test
    fun notConfiguredShowsServerSetupAndManualStillWorks() = runBlocking {
        val vm = viewModel(ScriptedParser())

        vm.handle(EntryAction.Submit(SODA_TEXT))
        val error = vm.state.value.items.last() as ChatItem.Error
        assertEquals(EntryErrorKind.NOT_CONFIGURED, error.kind)
        assertTrue(ErrorAction.CONFIGURE_SERVER in error.actions)

        vm.handle(
            EntryAction.SubmitManual(
                templateId = TemplateId.EXPENSE_PAID,
                amountRial = 1_000_000L,
                paidWith = PaidWith.CASH,
                category = ExpenseCategory.RENT,
            ),
        )
        val card = vm.state.value.items.last() as ChatItem.DraftCard
        vm.handle(EntryAction.Confirm(card.id))

        assertTrue(vm.state.value.items.last() is ChatItem.Committed)
        val trial = ledger.trialBalance(jalaliYear)
        assertEquals(1_000_000L, trial.debitTotal)
        assertEquals(trial.debitTotal, trial.creditTotal)
    }

    @Test
    fun manualPartyTemplateWithoutPartyIsRejected() = runBlocking {
        val vm = viewModel()

        vm.handle(EntryAction.SubmitManual(TemplateId.PURCHASE_CREDIT, 100_000L, paidWith = null))

        val error = vm.state.value.items.last() as ChatItem.Error
        assertEquals(EntryErrorKind.MANUAL_INVALID, error.kind)
        assertTrue(error.actions.isEmpty())
    }

    @Test
    fun undoCreatesReversalAndKeepsLedgerBalanced() = runBlocking {
        configure()
        val vm = viewModel(ScriptedParser(sodaIntent()))
        vm.handle(EntryAction.Submit(SODA_TEXT))
        val card = vm.state.value.items.filterIsInstance<ChatItem.DraftCard>().single()
        vm.handle(EntryAction.Confirm(card.id))
        val committed = vm.state.value.items.filterIsInstance<ChatItem.Committed>().single()

        vm.handle(EntryAction.Undo(committed.doc.id))

        val reversal = vm.state.value.items.filterIsInstance<ChatItem.Reversed>().single()
        assertEquals(committed.doc.id, reversal.originalDocId)
        assertEquals(committed.doc.id, reversal.doc.entry.reversalOfId)
        assertTrue(vm.state.value.items.filterIsInstance<ChatItem.Committed>().single().reversed)
        val trial = ledger.trialBalance(jalaliYear)
        assertEquals(trial.debitTotal, trial.creditTotal)
        assertEquals(2, ledger.journal(jalaliYear).first().size)

        vm.handle(EntryAction.Undo(committed.doc.id))
        assertEquals(2, ledger.journal(jalaliYear).first().size)
    }

    @Test
    fun doubleConfirmCommitsOnce() = runBlocking {
        configure()
        val vm = viewModel(ScriptedParser(sodaIntent()))
        vm.handle(EntryAction.Submit(SODA_TEXT))
        val card = vm.state.value.items.filterIsInstance<ChatItem.DraftCard>().single()

        vm.handle(EntryAction.Confirm(card.id))
        vm.handle(EntryAction.Confirm(card.id))

        assertEquals(1, ledger.journal(jalaliYear).first().size)
    }

    @Test
    fun discardRemovesDraftWithoutCommitting() = runBlocking {
        configure()
        val vm = viewModel(ScriptedParser(sodaIntent()))
        vm.handle(EntryAction.Submit(SODA_TEXT))
        val card = vm.state.value.items.filterIsInstance<ChatItem.DraftCard>().single()

        vm.handle(EntryAction.Discard(card.id))

        assertNull(vm.state.value.draft(card.id))
        assertTrue(ledger.journal(jalaliYear).first().isEmpty())
    }

    /** Scripted [NlParser]: returns queued results in order and records every text it received. */
    private class ScriptedParser(vararg responses: ParserResult) : NlParser {
        private val queue = ArrayDeque(responses.toList())
        val requests = mutableListOf<String>()

        override suspend fun parse(text: String): ParserResult {
            requests += text
            return checkNotNull(queue.removeFirstOrNull()) { "ScriptedParser: no response left" }
        }
    }

    /** Hands out [parser] when the config is complete, nothing otherwise. */
    private class FixedProvider(private val parser: NlParser) : NlParserProvider {
        override fun parserFor(config: BackendConfig): NlParser? = if (config.isConfigured) parser else null
    }

    private companion object {
        const val SODA_TEXT = "۴ قوطی نوشابه روی هم ۵۰۰٬۰۰۰ تومان خریدم"

        /** 500 000 toman = 5 000 000 rial. */
        const val SODA_RIAL = 5_000_000L
    }
}
