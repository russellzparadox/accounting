package com.example.accounting.core.domain.report

import com.example.accounting.core.persian.AmountUnit
import com.example.accounting.core.persian.JalaliDate
import com.example.accounting.core.persian.MoneyFormat
import com.example.accounting.core.persian.PersianDigits
import java.time.LocalDate

/** Column header. [numeric] columns are right-aligned in the output (start/end, RTL aware in UI). */
data class ReportColumn(val title: String, val numeric: Boolean = false)

/** One report cell. Money is always rial (Long); the unit is applied by the renderer. */
sealed interface ReportCell {
    data class Text(val value: String) : ReportCell
    data class Amount(val rial: Long) : ReportCell
    data class Date(val date: LocalDate) : ReportCell
    data class Count(val value: Int) : ReportCell
}

/** Inclusive Gregorian bounds; null = open end. */
data class ReportRange(val from: LocalDate?, val to: LocalDate?) {
    init {
        if (from != null && to != null) require(!to.isBefore(from)) { "range ends before it starts" }
    }
}

/**
 * Renderer-neutral report. Built by the pure builders in this package, consumed by the on-screen preview,
 * the PDF exporter and [CsvWriter]. [balanced] is false when debit totals do not equal credit totals.
 */
data class ReportTable(
    val title: String,
    val subtitle: String,
    val unit: AmountUnit,
    val columns: List<ReportColumn>,
    val rows: List<List<ReportCell>>,
    val totals: List<ReportCell>?,
    val balanced: Boolean = true,
) {
    init {
        require(rows.all { it.size == columns.size }) { "every row must have ${columns.size} cells" }
        require(totals == null || totals.size == columns.size) { "totals must have ${columns.size} cells" }
    }
}

/** Display (Persian digits) and CSV (Latin digits) text for cells. */
object ReportFormat {
    /** Amount in [unit] without the unit word, Persian digits, grouped. Toman may show one decimal digit. */
    fun amountDisplay(rial: Long, unit: AmountUnit): String =
        MoneyFormat.format(rial, unit).substringBeforeLast(' ')

    /** Amount as a plain integer in [unit], Latin digits. TOMAN with a non-zero rial remainder keeps the exact
     *  decimal (one digit, '.'), never rounded. */
    fun amountCsv(rial: Long, unit: AmountUnit): String {
        require(rial != Long.MIN_VALUE) { "amount out of range" }
        return when (unit) {
            AmountUnit.RIAL -> rial.toString()
            AmountUnit.TOMAN -> {
                val sign = if (rial < 0) "-" else ""
                val a = Math.abs(rial)
                val whole = a / 10
                val frac = a % 10
                if (frac == 0L) "$sign$whole" else "$sign$whole.$frac"
            }
        }
    }

    fun dateDisplay(date: LocalDate): String = JalaliDate.from(date).toString()

    /** yyyy/MM/dd, Jalali, Latin digits. */
    fun dateCsv(date: LocalDate): String {
        val j = JalaliDate.from(date)
        return "${j.year.toString().padStart(4, '0')}/${j.month.toString().padStart(2, '0')}/" +
            j.day.toString().padStart(2, '0')
    }

    fun persianInt(value: Int): String = PersianDigits.toPersian(value.toString())

    fun display(cell: ReportCell, unit: AmountUnit): String = when (cell) {
        is ReportCell.Text -> cell.value
        is ReportCell.Amount -> amountDisplay(cell.rial, unit)
        is ReportCell.Date -> dateDisplay(cell.date)
        is ReportCell.Count -> persianInt(cell.value)
    }

    fun csv(cell: ReportCell, unit: AmountUnit): String = when (cell) {
        is ReportCell.Text -> cell.value
        is ReportCell.Amount -> amountCsv(cell.rial, unit)
        is ReportCell.Date -> dateCsv(cell.date)
        is ReportCell.Count -> cell.value.toString()
    }

    /** «از ۱۴۰۵/۰۱/۰۱ تا ۱۴۰۵/۰۱/۳۱»; «همه» for an open bound. */
    fun rangeText(range: ReportRange): String {
        val from = range.from?.let { dateDisplay(it) } ?: "همه"
        val to = range.to?.let { dateDisplay(it) } ?: "همه"
        return "از $from تا $to"
    }
}
