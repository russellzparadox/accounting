package com.example.accounting.feature.reports.export

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.accounting.core.domain.report.ReportFormat
import com.example.accounting.core.domain.report.ReportTable
import com.example.accounting.core.persian.PersianDigits
import com.example.accounting.feature.reports.R
import java.io.OutputStream
import com.example.accounting.core.ui.R as UiR

/**
 * A4 portrait PDF with android.graphics.pdf.PdfDocument.
 * RTL: column 0 sits at the right margin; each cell is a StaticLayout with an RTL direction (right-aligned).
 * Vazirmatn (core:ui) for all text. The table header repeats on every page; each page gets a page-number footer.
 */
object PdfExporter {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 36f
    private const val PAD = 4f
    private const val BODY_SP = 9f
    private const val TITLE_SP = 15f

    fun write(context: Context, table: ReportTable, baseName: String): Uri {
        val file = ReportFiles.fileFor(context, "$baseName.pdf")
        file.outputStream().use { render(context, table, it) }
        return ReportFiles.uriFor(context, file)
    }

    fun share(context: Context, uri: Uri) = ReportFiles.share(context, uri, "application/pdf")

    /** Writes the PDF to [out] and returns the number of pages. */
    fun render(context: Context, table: ReportTable, out: OutputStream): Int {
        val regular = ResourcesCompat.getFont(context, UiR.font.vazirmatn_regular) ?: Typeface.DEFAULT
        val bold = ResourcesCompat.getFont(context, UiR.font.vazirmatn_bold) ?: Typeface.DEFAULT_BOLD
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regular; textSize = BODY_SP }
        val bodyBold = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = BODY_SP }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold; textSize = TITLE_SP; textAlign = Paint.Align.RIGHT
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regular; textSize = BODY_SP + 1; textAlign = Paint.Align.RIGHT
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regular; textSize = BODY_SP; textAlign = Paint.Align.CENTER
        }
        val rule = Paint().apply { strokeWidth = 0.75f }

        val widths = columnWidths(table)
        fun colLeft(i: Int): Float = PAGE_W - MARGIN - widths.take(i + 1).sum()

        val pdf = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun startPage() {
            pageNo++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            y = MARGIN
        }

        fun endPage() {
            val p = page ?: return
            val label = context.getString(R.string.report_page, PersianDigits.toPersian(pageNo.toString()))
            p.canvas.drawText(label, PAGE_W / 2f, PAGE_H - MARGIN / 2, footerPaint)
            pdf.finishPage(p)
            page = null
        }

        fun layoutOf(text: String, width: Float, paint: TextPaint): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setIncludePad(false)
                .build()

        fun drawRow(cells: List<String>, paint: TextPaint, repeatHeader: List<String>?) {
            val layouts = cells.mapIndexed { i, text ->
                layoutOf(text.ifEmpty { " " }, widths[i] - 2 * PAD, paint)
            }
            val h = (layouts.maxOfOrNull { it.height } ?: 0) + 2 * PAD
            if (y + h > PAGE_H - MARGIN) {
                endPage()
                startPage()
                if (repeatHeader != null) drawRow(repeatHeader, bodyBold, null)
            }
            val canvas = checkNotNull(page).canvas
            layouts.forEachIndexed { i, layout ->
                canvas.save()
                canvas.translate(colLeft(i) + PAD, y + PAD)
                layout.draw(canvas)
                canvas.restore()
            }
            canvas.drawLine(MARGIN, y + h, PAGE_W - MARGIN, y + h, rule)
            y += h
        }

        startPage()
        val canvas = checkNotNull(page).canvas
        canvas.drawText(table.title, PAGE_W - MARGIN, y + TITLE_SP, titlePaint)
        y += TITLE_SP + 6f
        canvas.drawText(table.subtitle, PAGE_W - MARGIN, y + BODY_SP, subtitlePaint)
        y += BODY_SP + 8f
        if (!table.balanced) {
            canvas.drawText(context.getString(R.string.report_unbalanced), PAGE_W - MARGIN, y + BODY_SP, subtitlePaint)
            y += BODY_SP + 8f
        }
        val header = table.columns.map { it.title }
        drawRow(header, bodyBold, null)
        table.rows.forEach { row -> drawRow(row.map { ReportFormat.display(it, table.unit) }, body, header) }
        table.totals?.let { totals -> drawRow(totals.map { ReportFormat.display(it, table.unit) }, bodyBold, header) }
        endPage()

        pdf.writeTo(out)
        pdf.close()
        return pageNo
    }

    /** Column widths in points, summing to the usable page width. Index 0 is the rightmost column (RTL). */
    private fun columnWidths(table: ReportTable): List<Float> {
        val usable = PAGE_W - 2 * MARGIN
        val weights = table.columns.indices.map { i ->
            val lengths = table.rows.map { ReportFormat.display(it[i], table.unit).length }
            val avg = if (lengths.isEmpty()) 0.0 else lengths.average()
            (avg / 6.0).coerceIn(1.0, 3.0).toFloat()
        }
        val total = weights.sum()
        return weights.map { usable * it / total }
    }
}
