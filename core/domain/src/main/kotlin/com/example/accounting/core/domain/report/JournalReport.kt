package com.example.accounting.core.domain.report

import com.example.accounting.core.domain.Side
import com.example.accounting.core.persian.AmountUnit
import java.time.LocalDate

data class JournalDocLine(val accountCode: String, val accountName: String, val side: Side, val amount: Long)

/** A committed document (rial amounts). */
data class JournalDoc(
    val date: LocalDate,
    val docNumber: Int,
    val memo: String?,
    val lines: List<JournalDocLine>,
)

/** دفتر روزنامه: one row per document line, documents in date then number order. */
object JournalReport {
    fun build(range: ReportRange, docs: List<JournalDoc>, unit: AmountUnit): ReportTable {
        val sorted = docs.sortedWith(compareBy({ it.date }, { it.docNumber }))
        var debit = 0L
        var credit = 0L
        val rows = sorted.flatMap { doc ->
            doc.lines.map { line ->
                val isDebit = line.side == Side.DEBIT
                if (isDebit) debit += line.amount else credit += line.amount
                listOf(
                    ReportCell.Date(doc.date),
                    ReportCell.Count(doc.docNumber),
                    ReportCell.Text(doc.memo.orEmpty()),
                    ReportCell.Text(line.accountCode),
                    ReportCell.Text(line.accountName),
                    ReportCell.Amount(if (isDebit) line.amount else 0L),
                    ReportCell.Amount(if (isDebit) 0L else line.amount),
                )
            }
        }
        return ReportTable(
            title = "دفتر روزنامه",
            subtitle = ReportFormat.rangeText(range),
            unit = unit,
            columns = listOf(
                ReportColumn("تاریخ"),
                ReportColumn("شماره سند", numeric = true),
                ReportColumn("شرح"),
                ReportColumn("کد"),
                ReportColumn("نام حساب"),
                ReportColumn("بدهکار", numeric = true),
                ReportColumn("بستانکار", numeric = true),
            ),
            rows = rows,
            totals = listOf(
                ReportCell.Text(""), ReportCell.Text(""), ReportCell.Text(""), ReportCell.Text(""),
                ReportCell.Text("جمع"),
                ReportCell.Amount(debit),
                ReportCell.Amount(credit),
            ),
            balanced = debit == credit,
        )
    }
}
