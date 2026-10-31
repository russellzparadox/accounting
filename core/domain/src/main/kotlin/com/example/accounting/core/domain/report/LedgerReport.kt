package com.example.accounting.core.domain.report

import com.example.accounting.core.persian.AmountUnit
import java.time.LocalDate

/**
 * One posted line of an account, rial amounts. Pass the FULL history of the account (all dates) so the
 * opening balance can be computed; [LedgerReport] applies the range itself.
 */
data class LedgerHistoryLine(
    val date: LocalDate,
    val fiscalYear: Int,
    val docNumber: Int,
    val memo: String?,
    val debit: Long,
    val credit: Long,
)

/** دفتر کل of one account. Balance = debit minus credit (same sign rule as the ledger screen). */
object LedgerReport {
    fun build(
        accountCode: String,
        accountName: String,
        range: ReportRange,
        history: List<LedgerHistoryLine>,
        unit: AmountUnit,
    ): ReportTable {
        val ordered = history.sortedWith(compareBy({ it.date }, { it.fiscalYear }, { it.docNumber }))
        val from = range.from
        val to = range.to
        val opening = ordered
            .filter { from != null && it.date.isBefore(from) }
            .sumOf { it.debit - it.credit }
        var running = opening
        var debitSum = 0L
        var creditSum = 0L
        val rows = mutableListOf<List<ReportCell>>()
        rows += listOf(
            ReportCell.Text(""), ReportCell.Text(""), ReportCell.Text("مانده اول دوره"),
            ReportCell.Text(""), ReportCell.Text(""), ReportCell.Amount(opening),
        )
        ordered.filter { inRange(it.date, from, to) }.forEach { line ->
            running += line.debit - line.credit
            debitSum += line.debit
            creditSum += line.credit
            rows += listOf(
                ReportCell.Date(line.date),
                ReportCell.Count(line.docNumber),
                ReportCell.Text(line.memo.orEmpty()),
                ReportCell.Amount(line.debit),
                ReportCell.Amount(line.credit),
                ReportCell.Amount(running),
            )
        }
        return ReportTable(
            title = "دفتر کل: $accountName",
            subtitle = "کد ${accountCode} · ${ReportFormat.rangeText(range)} · مانده = بدهکار منهای بستانکار",
            unit = unit,
            columns = listOf(
                ReportColumn("تاریخ"),
                ReportColumn("شماره سند", numeric = true),
                ReportColumn("شرح"),
                ReportColumn("بدهکار", numeric = true),
                ReportColumn("بستانکار", numeric = true),
                ReportColumn("مانده", numeric = true),
            ),
            rows = rows,
            totals = listOf(
                ReportCell.Text(""), ReportCell.Text(""), ReportCell.Text("جمع"),
                ReportCell.Amount(debitSum), ReportCell.Amount(creditSum), ReportCell.Amount(running),
            ),
        )
    }

    private fun inRange(date: LocalDate, from: LocalDate?, to: LocalDate?): Boolean =
        (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to))
}
