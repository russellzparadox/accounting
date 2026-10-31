package com.example.accounting.feature.ledger

import com.example.accounting.core.data.repo.CommittedDoc
import com.example.accounting.core.data.repo.DocumentLinks
import java.time.LocalDate

/** Shared filter: fiscal year and date range. Custom dates are kept as typed, [custom] only when valid. */
data class LedgerFilter(
    val year: Int,
    val preset: DatePreset = DatePreset.ALL,
    val custom: DateRange? = null,
    val fromText: String = "",
    val toText: String = "",
    val dateError: Boolean = false,
)

/** Error kinds only. The UI maps them to Persian text, so the ViewModel stays free of Android resources. */
enum class DetailError { REVERSE_FAILED }

/** Open document sheet. [reversedNumber] is set after a successful reversal (the new document number). */
data class DocumentDetailUi(
    val links: DocumentLinks,
    val reversing: Boolean = false,
    val confirmReverse: Boolean = false,
    val error: DetailError? = null,
    val reversedNumber: Int? = null,
) {
    val doc: CommittedDoc get() = links.doc

    /** A reversal document is never reversed again. */
    val isReversal: Boolean get() = doc.entry.reversalOfId != null

    /** Reversal action only for an original that has no reversal yet. */
    val canReverse: Boolean get() = !isReversal && links.reversedBy == null
}

/** One ledger row (per account). Debit or credit is zero on each row. [balance] is the running debit minus credit. */
data class LedgerRowUi(
    val entryId: String,
    val date: LocalDate,
    val docNumber: Int,
    val memo: String?,
    val debit: Long,
    val credit: Long,
    val balance: Long,
)

/** Account ledger for the applied range. [closing] is the running balance after the last row. */
data class AccountLedgerUi(
    val code: String,
    val name: String,
    val rows: List<LedgerRowUi>,
    val totalDebit: Long,
    val totalCredit: Long,
    val closing: Long,
)
