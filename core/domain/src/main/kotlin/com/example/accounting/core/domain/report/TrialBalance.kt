package com.example.accounting.core.domain.report

import com.example.accounting.core.persian.AmountUnit
import java.time.LocalDate

/** Gross debit and credit of one account (rial), as produced by the repository. */
data class TrialAccount(val code: String, val nameFa: String, val debit: Long, val credit: Long)

/** تراز آزمایشی. Accounts with no activity are left out. */
object TrialBalanceReport {
    fun build(
        fiscalYear: Int,
        asOf: LocalDate?,
        accounts: List<TrialAccount>,
        unit: AmountUnit,
    ): ReportTable {
        val active = accounts.filter { it.debit != 0L || it.credit != 0L }.sortedBy { it.code }
        val debit = active.sumOf { it.debit }
        val credit = active.sumOf { it.credit }
        val asOfText = asOf?.let { "تا ${ReportFormat.dateDisplay(it)}" } ?: "کل سال"
        return ReportTable(
            title = "تراز آزمایشی",
            subtitle = "سال مالی ${ReportFormat.persianInt(fiscalYear)} · $asOfText",
            unit = unit,
            columns = listOf(
                ReportColumn("کد"),
                ReportColumn("نام حساب"),
                ReportColumn("بدهکار", numeric = true),
                ReportColumn("بستانکار", numeric = true),
            ),
            rows = active.map { acc ->
                listOf(
                    ReportCell.Text(acc.code),
                    ReportCell.Text(acc.nameFa),
                    ReportCell.Amount(acc.debit),
                    ReportCell.Amount(acc.credit),
                )
            },
            totals = listOf(
                ReportCell.Text(""),
                ReportCell.Text("جمع"),
                ReportCell.Amount(debit),
                ReportCell.Amount(credit),
            ),
            balanced = debit == credit,
        )
    }
}
