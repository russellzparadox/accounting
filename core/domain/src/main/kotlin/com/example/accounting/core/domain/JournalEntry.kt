package com.example.accounting.core.domain

import java.time.LocalDate

/**
 * Committed, immutable double-entry document. Fixes are new reversal entries (see PostingEngine.reverse).
 * Construction fails unless the entry has >= 2 lines and total debit equals total credit.
 */
class JournalEntry(
    val id: String,
    val date: LocalDate,
    val templateId: TemplateId,
    val memo: String?,
    lines: List<JournalLine>,
    val reversalOfId: String? = null,
) {
    val lines: List<JournalLine> = lines.toList()
    val debitTotal: Money = total(Side.DEBIT)
    val creditTotal: Money = total(Side.CREDIT)

    init {
        require(id.isNotBlank()) { "entry id required" }
        require(this.lines.size >= 2) { "entry needs at least 2 lines" }
        require(debitTotal == creditTotal) {
            "unbalanced entry: debit ${debitTotal.rial} != credit ${creditTotal.rial}"
        }
    }

    private fun total(side: Side): Money =
        lines.filter { it.side == side }.fold(Money.ZERO) { acc, line -> acc + line.amount }
}
