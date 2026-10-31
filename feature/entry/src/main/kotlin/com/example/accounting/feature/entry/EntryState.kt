package com.example.accounting.feature.entry

import com.example.accounting.core.data.repo.CommittedDoc
import com.example.accounting.core.domain.ExpenseCategory
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.domain.PaidWith
import com.example.accounting.core.domain.TemplateId
import com.example.accounting.core.domain.TransactionIntent
import com.example.accounting.core.domain.pipeline.ConfirmReason
import com.example.accounting.core.domain.pipeline.PipelineError
import com.example.accounting.core.domain.pipeline.PipelineResult

/** Everything the UI can ask for. Routed through [EntryViewModel.onAction]. */
sealed interface EntryAction {
    data class Submit(val text: String) : EntryAction
    data class AnswerClarification(val answer: String) : EntryAction
    /** Re-runs the text stored on an [ChatItem.Error] that offers [ErrorAction.RETRY]. */
    data class Retry(val errorId: String) : EntryAction
    data class Confirm(val draftId: String) : EntryAction
    data class Discard(val draftId: String) : EntryAction
    /** Mismatch only: re-posts the draft with the locally detected amount. */
    data class UseDetectedAmount(val draftId: String) : EntryAction
    /** Re-posts the draft with the amount the backend proposed. */
    data class UseModelAmount(val draftId: String) : EntryAction
    /** [docId] is the committed entry id (CommittedDoc.id). Creates a reversal; never deletes. */
    data class Undo(val docId: String) : EntryAction
    data class SubmitManual(
        val templateId: TemplateId,
        val amountRial: Long,
        val paidWith: PaidWith?,
        val partyName: String? = null,
        val category: ExpenseCategory? = null,
        val memo: String? = null,
    ) : EntryAction
}

/** Failure classes shown to the user. Persian text is chosen in the ViewModel (needs resources). */
enum class EntryErrorKind {
    OFFLINE,
    UNAUTHORIZED,
    RATE_LIMITED,
    SERVER,
    BAD_RESPONSE,
    EMPTY_INPUT,
    AMOUNT_MISSING,
    DATE_OUTSIDE_FISCAL_YEAR,
    NOT_CONFIGURED,
    MANUAL_INVALID,
    LEDGER_FAILED,
}

/** Buttons offered under an error. */
enum class ErrorAction { RETRY, CONFIGURE_SERVER, MANUAL_ENTRY }

fun PipelineError.toErrorKind(): EntryErrorKind = when (this) {
    PipelineError.Offline -> EntryErrorKind.OFFLINE
    PipelineError.Unauthorized -> EntryErrorKind.UNAUTHORIZED
    is PipelineError.RateLimited -> EntryErrorKind.RATE_LIMITED
    is PipelineError.ServerError -> EntryErrorKind.SERVER
    is PipelineError.BadResponse -> EntryErrorKind.BAD_RESPONSE
    PipelineError.EmptyInput -> EntryErrorKind.EMPTY_INPUT
    PipelineError.AmountMissing -> EntryErrorKind.AMOUNT_MISSING
    is PipelineError.DateOutsideFiscalYear -> EntryErrorKind.DATE_OUTSIDE_FISCAL_YEAR
}

fun EntryErrorKind.actions(): List<ErrorAction> = when (this) {
    EntryErrorKind.OFFLINE, EntryErrorKind.RATE_LIMITED, EntryErrorKind.SERVER ->
        listOf(ErrorAction.RETRY, ErrorAction.MANUAL_ENTRY)
    EntryErrorKind.UNAUTHORIZED, EntryErrorKind.NOT_CONFIGURED ->
        listOf(ErrorAction.CONFIGURE_SERVER, ErrorAction.MANUAL_ENTRY)
    EntryErrorKind.BAD_RESPONSE, EntryErrorKind.AMOUNT_MISSING, EntryErrorKind.DATE_OUTSIDE_FISCAL_YEAR ->
        listOf(ErrorAction.MANUAL_ENTRY)
    EntryErrorKind.EMPTY_INPUT, EntryErrorKind.MANUAL_INVALID, EntryErrorKind.LEDGER_FAILED -> emptyList()
}

/** One row of the chat-style list. [id] is stable; a DraftCard's id is the draftId used by actions. */
sealed interface ChatItem {
    val id: String

    data class UserText(override val id: String, val text: String) : ChatItem

    /**
     * Unposted, uncommitted entry. [reasons] non-empty means the user must look before confirming.
     * [modelRial] is the backend amount, kept so "use model amount" still works after a local switch.
     * [source] is the pipeline draft (null for manual entries); needed by withAmount.
     * [committing] is true while a confirm is in flight.
     */
    data class DraftCard(
        override val id: String,
        val entry: JournalEntry,
        val intent: TransactionIntent,
        val reasons: List<ConfirmReason>,
        val reasonTexts: List<String>,
        val modelRial: Long,
        val committing: Boolean,
        val source: PipelineResult.Draft?,
    ) : ChatItem

    data class Clarification(override val id: String, val question: String) : ChatItem

    /** [retryText] is the text to re-run, or null when a retry is meaningless. */
    data class Error(
        override val id: String,
        val kind: EntryErrorKind,
        val title: String,
        val message: String,
        val retryText: String?,
    ) : ChatItem {
        val actions: List<ErrorAction> get() = kind.actions()
    }

    data class Committed(override val id: String, val doc: CommittedDoc, val reversed: Boolean = false) : ChatItem

    /** Reversal document created for [originalDocId]. */
    data class Reversed(override val id: String, val originalDocId: String, val doc: CommittedDoc) : ChatItem
}

data class EntryUiState(
    val items: List<ChatItem> = emptyList(),
    val busy: Boolean = false,
    /** Original text waiting for the user's answer to a clarification question. */
    val pendingClarification: String? = null,
)

fun EntryUiState.draft(id: String): ChatItem.DraftCard? = items.firstOrNull { it.id == id } as? ChatItem.DraftCard

fun EntryUiState.append(item: ChatItem): EntryUiState = copy(items = items + item)

fun EntryUiState.replace(id: String, item: ChatItem): EntryUiState =
    copy(items = items.map { if (it.id == id) item else it })

fun EntryUiState.remove(id: String): EntryUiState = copy(items = items.filterNot { it.id == id })

/** Rule for answering a clarification: original text, one space, the answer. Blank parts are dropped. */
fun combineClarification(original: String, answer: String): String =
    listOf(original, answer).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
