package com.example.accounting.feature.entry

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accounting.core.data.config.BackendConfig
import com.example.accounting.core.data.config.ConfigStore
import com.example.accounting.core.data.nlp.NlParserProvider
import com.example.accounting.core.data.repo.LedgerRepository
import com.example.accounting.core.data.seed.ChartSeeder
import com.example.accounting.core.domain.ChartOfAccounts
import com.example.accounting.core.domain.FiscalYear
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.domain.PartyAccountResolver
import com.example.accounting.core.domain.PostingEngine
import com.example.accounting.core.domain.TransactionIntent
import com.example.accounting.core.domain.pipeline.ConfirmReason
import com.example.accounting.core.domain.pipeline.NlPipeline
import com.example.accounting.core.domain.pipeline.PipelineResult
import com.example.accounting.core.persian.AmountUnit
import com.example.accounting.core.persian.JalaliDate
import com.example.accounting.core.persian.MoneyFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * State layer for the entry screen. The UI only sends [EntryAction]s and renders [state].
 * It never touches [LedgerRepository] directly. Blocking work (party lookup, Room) runs on Dispatchers.IO.
 * Drafts are not committed until [EntryAction.Confirm].
 */
@HiltViewModel
class EntryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ledger: LedgerRepository,
    private val chart: ChartOfAccounts,
    private val parties: PartyAccountResolver,
    private val seeder: ChartSeeder,
    private val configStore: ConfigStore,
    private val parserProvider: NlParserProvider,
    private val clock: Clock,
) : ViewModel() {

    private val mutableState = MutableStateFlow(EntryUiState())
    val state: StateFlow<EntryUiState> = mutableState.asStateFlow()

    private val claimLock = Mutex()
    private val setupLock = Mutex()
    private var seeded = false

    /** UI entry point. Work runs in [viewModelScope]. */
    fun onAction(action: EntryAction) {
        viewModelScope.launch { handle(action) }
    }

    /** Dispatch body. Internal so tests can await it directly. */
    internal suspend fun handle(action: EntryAction) {
        when (action) {
            is EntryAction.Submit -> submit(action.text)
            is EntryAction.AnswerClarification -> answerClarification(action.answer)
            is EntryAction.Retry -> retry(action.errorId)
            is EntryAction.Confirm -> confirm(action.draftId)
            is EntryAction.Discard -> discard(action.draftId)
            is EntryAction.UseDetectedAmount -> useDetectedAmount(action.draftId)
            is EntryAction.UseModelAmount -> useModelAmount(action.draftId)
            is EntryAction.EditAmount -> editAmount(action.draftId, action.amountRial)
            is EntryAction.SaveBackendConfig -> saveBackendConfig(action.baseUrl, action.token)
            is EntryAction.Undo -> undo(action.docId)
            is EntryAction.SubmitManual -> submitManual(action)
        }
    }

    private suspend fun submit(text: String) {
        mutableState.update { it.copy(pendingClarification = null) }
        process(userText = text, pipelineText = text.trim())
    }

    private suspend fun answerClarification(answer: String) {
        val original = mutableState.value.pendingClarification ?: return
        if (answer.isBlank()) return
        process(userText = answer.trim(), pipelineText = combineClarification(original, answer))
    }

    private suspend fun retry(errorId: String) {
        val error = mutableState.value.items.firstOrNull { it.id == errorId } as? ChatItem.Error ?: return
        val text = error.retryText ?: return
        mutableState.update { it.remove(errorId) }
        process(userText = null, pipelineText = text)
    }

    /** Runs the NL pipeline for [pipelineText] and appends the outcome. */
    private suspend fun process(userText: String?, pipelineText: String) {
        if (!userText.isNullOrBlank()) appendItem(ChatItem.UserText(newId(), userText))
        if (pipelineText.isBlank()) {
            appendItem(errorItem(EntryErrorKind.EMPTY_INPUT, null))
            return
        }
        setBusy(true)
        val step = try {
            withContext(Dispatchers.IO) { runPipeline(pipelineText) }
        } catch (e: CancellationException) {
            setBusy(false)
            throw e
        } catch (e: Exception) {
            Step(errorItem(EntryErrorKind.LEDGER_FAILED, null), pending = null)
        }
        mutableState.update { it.append(step.item).copy(busy = false, pendingClarification = step.pending) }
    }

    /** Called on IO. [Step.pending] is the text to answer later, set only for a clarification. */
    private suspend fun runPipeline(text: String): Step {
        val parser = parserProvider.parserFor(configStore.config.first())
            ?: return Step(errorItem(EntryErrorKind.NOT_CONFIGURED, text), pending = null)
        val fiscal = openFiscalYear()
        val pipeline = NlPipeline(parser, PostingEngine(chart, parties, fiscal), fiscal)
        return when (val result = pipeline.process(text)) {
            is PipelineResult.Draft -> Step(draftCard(newId(), result, result.intent.amountRial), pending = null)
            is PipelineResult.NeedsClarification ->
                Step(ChatItem.Clarification(newId(), result.question), pending = text)
            is PipelineResult.Failure -> Step(errorItem(result.error.toErrorKind(), text), pending = null)
        }
    }

    /**
     * Commits the draft once. The first confirm marks the card as committing under [claimLock];
     * any later confirm for the same draft finds the flag and does nothing.
     * Party lookup already happened when the draft was built (PostingEngine.post), on IO.
     */
    private suspend fun confirm(draftId: String) {
        val card = claimLock.withLock {
            val current = mutableState.value.draft(draftId) ?: return
            if (current.committing) return
            mutableState.update { it.replace(draftId, current.copy(committing = true)) }
            current
        }
        val doc = try {
            withContext(Dispatchers.IO) { ledger.commit(card.entry) }
        } catch (e: CancellationException) {
            release(card)
            throw e
        } catch (e: Exception) {
            release(card)
            appendItem(errorItem(EntryErrorKind.LEDGER_FAILED, null))
            return
        }
        mutableState.update { it.replace(draftId, ChatItem.Committed(draftId, doc)) }
    }

    private fun release(card: ChatItem.DraftCard) {
        mutableState.update { it.replace(card.id, card.copy(committing = false)) }
    }

    private fun discard(draftId: String) {
        mutableState.update { s ->
            val card = s.draft(draftId)
            if (card == null || card.committing) s else s.remove(draftId)
        }
    }

    /** Mismatch only: the local parser's amount, when it differs from the backend's. */
    private suspend fun useDetectedAmount(draftId: String) {
        val card = mutableState.value.draft(draftId) ?: return
        val local = card.reasons.filterIsInstance<ConfirmReason.AmountMismatch>().firstOrNull()?.localRial ?: return
        rebase(card, local)
    }

    private suspend fun useModelAmount(draftId: String) {
        val card = mutableState.value.draft(draftId) ?: return
        rebase(card, card.modelRial)
    }

    private suspend fun editAmount(draftId: String, rial: Long) {
        val card = mutableState.value.draft(draftId) ?: return
        if (rial <= 0L) {
            appendItem(errorItem(EntryErrorKind.BAD_RESPONSE, null))
            return
        }
        rebase(card, rial)
    }

    private fun saveBackendConfig(baseUrl: String, token: String) {
        configStore.save(BackendConfig(baseUrl, token))
        mutableState.update { s ->
            s.copy(items = s.items.filterNot { it is ChatItem.Error && it.kind == EntryErrorKind.NOT_CONFIGURED })
        }
    }

    /** Display name for an account code. Party accounts (معین) are not in the chart, so the code is shown. */
    fun accountName(code: String): String = chart.account(code)?.nameFa ?: code

    private suspend fun rebase(card: ChatItem.DraftCard, rial: Long) {
        val source = card.source ?: return
        if (card.committing) return
        val updated = try {
            withContext(Dispatchers.IO) { source.withAmount(rial) }
        } catch (e: IllegalArgumentException) {
            appendItem(errorItem(EntryErrorKind.BAD_RESPONSE, null))
            return
        }
        mutableState.update { it.replace(card.id, draftCard(card.id, updated, card.modelRial)) }
    }

    /** Reversal only: [docId] is the committed entry id. The original document is never changed. */
    private suspend fun undo(docId: String) {
        val committed = mutableState.value.items
            .filterIsInstance<ChatItem.Committed>()
            .firstOrNull { it.doc.id == docId }
        if (committed?.reversed == true) return
        val reversal = try {
            withContext(Dispatchers.IO) {
                openFiscalYear()
                ledger.reverse(docId, LocalDate.now(clock))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            appendItem(errorItem(EntryErrorKind.LEDGER_FAILED, null))
            return
        }
        mutableState.update { s ->
            val marked = if (committed == null) s else s.replace(committed.id, committed.copy(reversed = true))
            marked.append(ChatItem.Reversed(newId(), docId, reversal))
        }
    }

    /** Manual path: no backend needed. Builds the intent here and posts it directly. */
    private suspend fun submitManual(action: EntryAction.SubmitManual) {
        val item: ChatItem = try {
            withContext(Dispatchers.IO) {
                val fiscal = openFiscalYear()
                val intent = TransactionIntent(
                    type = action.templateId,
                    amountRial = action.amountRial,
                    date = LocalDate.now(clock),
                    paidWith = action.paidWith,
                    partyName = action.partyName,
                    category = action.category,
                    memo = action.memo,
                    confidence = MANUAL_CONFIDENCE,
                )
                val entry = PostingEngine(chart, parties, fiscal).post(intent)
                manualCard(newId(), entry, intent)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            errorItem(EntryErrorKind.MANUAL_INVALID, null)
        } catch (e: Exception) {
            errorItem(EntryErrorKind.LEDGER_FAILED, null)
        }
        appendItem(item)
    }

    /** First-run setup (chart seed) runs once; the fiscal year for today is ensured on every call (idempotent). */
    private suspend fun openFiscalYear(): FiscalYear {
        setupLock.withLock {
            if (!seeded) {
                seeder.seed()
                seeded = true
            }
        }
        return ledger.ensureFiscalYear(JalaliDate.from(LocalDate.now(clock)).year)
    }

    private fun draftCard(id: String, draft: PipelineResult.Draft, modelRial: Long): ChatItem.DraftCard =
        ChatItem.DraftCard(
            id = id,
            entry = draft.entry,
            intent = draft.intent,
            reasons = draft.reasons,
            reasonTexts = draft.reasons.map { reasonText(it) },
            modelRial = modelRial,
            committing = false,
            source = draft,
        )

    private fun manualCard(id: String, entry: JournalEntry, intent: TransactionIntent): ChatItem.DraftCard =
        ChatItem.DraftCard(
            id = id,
            entry = entry,
            intent = intent,
            reasons = emptyList(),
            reasonTexts = emptyList(),
            modelRial = intent.amountRial,
            committing = false,
            source = null,
        )

    private fun reasonText(reason: ConfirmReason): String = when (reason) {
        is ConfirmReason.AmountMismatch ->
            string(R.string.reason_amount_mismatch, money(reason.llmRial), money(reason.localRial))
        is ConfirmReason.AmountUnverified -> string(R.string.reason_amount_unverified)
        is ConfirmReason.AmountTextUnverified -> string(R.string.reason_amount_text_unverified)
        is ConfirmReason.LowConfidence -> string(R.string.reason_low_confidence)
    }

    private fun errorItem(kind: EntryErrorKind, retryText: String?): ChatItem.Error = ChatItem.Error(
        id = newId(),
        kind = kind,
        title = string(titleRes(kind)),
        message = string(messageRes(kind)),
        retryText = retryText,
    )

    private fun titleRes(kind: EntryErrorKind): Int = when (kind) {
        EntryErrorKind.OFFLINE -> R.string.error_network_title
        EntryErrorKind.AMOUNT_MISSING, EntryErrorKind.EMPTY_INPUT -> R.string.error_parse_title
        EntryErrorKind.NOT_CONFIGURED -> R.string.error_not_configured_title
        else -> R.string.error_generic_title
    }

    private fun messageRes(kind: EntryErrorKind): Int = when (kind) {
        EntryErrorKind.OFFLINE -> R.string.error_network_body
        EntryErrorKind.UNAUTHORIZED -> R.string.error_unauthorized_body
        EntryErrorKind.RATE_LIMITED -> R.string.error_rate_limited_body
        EntryErrorKind.SERVER -> R.string.error_server_body
        EntryErrorKind.BAD_RESPONSE -> R.string.error_bad_response_body
        EntryErrorKind.EMPTY_INPUT -> R.string.error_empty_body
        EntryErrorKind.AMOUNT_MISSING -> R.string.error_parse_body
        EntryErrorKind.DATE_OUTSIDE_FISCAL_YEAR -> R.string.error_date_outside_body
        EntryErrorKind.NOT_CONFIGURED -> R.string.error_not_configured_body
        EntryErrorKind.MANUAL_INVALID -> R.string.error_manual_invalid_body
        EntryErrorKind.LEDGER_FAILED -> R.string.error_ledger_body
    }

    private fun money(rial: Long): String = MoneyFormat.format(rial, AmountUnit.TOMAN)

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun appendItem(item: ChatItem) = mutableState.update { it.append(item) }

    private fun setBusy(value: Boolean) = mutableState.update { it.copy(busy = value) }

    private fun newId(): String = UUID.randomUUID().toString()

    /** Result of one pipeline run. [pending] is the original text waiting for a clarification answer. */
    private class Step(val item: ChatItem, val pending: String?)

    private companion object {
        /** Manual entries are typed by the user, so the model-confidence check does not apply. */
        const val MANUAL_CONFIDENCE = 100
    }
}
