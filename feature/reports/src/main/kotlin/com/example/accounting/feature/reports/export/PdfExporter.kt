package com.example.accounting.feature.reports.export

import android.content.Context
import android.graphics.Canvas
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
import com.example.accounting.feature.reports.export.PdfLayout.MARGIN
import com.example.accounting.feature.reports.export.PdfLayout.PAGE_H
import com.example.accounting.feature.reports.export.PdfLayout.PAGE_W
import java.io.OutputStream
import com.example.accounting.core.ui.R as UiR

/**
 * A4 portrait PDF with android.graphics.pdf.PdfDocument. Geometry and page breaks come from [PdfLayout] (pure,
 * unit-tested). RTL: column 0 at the right margin; each cell is a StaticLayout with RTL direction (right-aligned).
 * Vazirmatn (core:ui) for all text. Header repeats on every page; each page gets a page-number footer.
 * Rendering itself is NOT exercised by any CI test (Robolectric cannot back PdfDocument here); see the handout.
 */
object PdfExporter {
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

        // Measure first: widths, cell layouts, row heights. Then break pages with PdfLayout.
        val widths = PdfLayout.columnWidths(columnWeights(table), PAGE_W - 2 * MARGIN)
        fun layoutsFor(cells: List<String>, paint: TextPaint): List<StaticLayout> =
            cells.mapIndexed { i, text ->
                val t = text.ifEmpty { " " }
                StaticLayout.Builder.obtain(t, 0, t.length, paint, (widths[i] - 2 * PAD).toInt().coerceAtLeast(1))
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setTextDirection(TextDirectionHeuristics.RTL)
                    .setIncludePad(false)
                    .build()
            }
        fun heightOf(layouts: List<StaticLayout>): Float = (layouts.maxOfOrNull { it.height } ?: 0) + 2 * PAD

        val headerTexts = table.columns.map { it.title }
        val headerLayouts = layoutsFor(headerTexts, bodyBold)
        val headerH = heightOf(headerLayouts)
        val bodyRows = mutableListOf<Pair<List<String>, TextPaint>>()
        table.rows.forEach { row -> bodyRows += row.map { ReportFormat.display(it, table.unit) } to body }
        table.totals?.let { totals -> bodyRows += totals.map { ReportFormat.display(it, table.unit) } to bodyBold }
        val rowLayouts = bodyRows.map { (cells, paint) -> layoutsFor(cells, paint) }
        val rowHeights = rowLayouts.map { heightOf(it) }

        val warningH = if (table.balanced) 0f else BODY_SP + 8f
        val titleBlock = TITLE_SP + 6f + BODY_SP + 8f + warningH
        val starts = PdfLayout.pageStarts(rowHeights, MARGIN + titleBlock + headerH, headerH)

        val pdf = PdfDocument()
        starts.forEachIndexed { p, from ->
            val to = starts.getOrNull(p + 1) ?: rowLayouts.size
            val pageNo = p + 1
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageNo).create())
            val canvas = page.canvas
            var y = MARGIN
            if (p == 0) {
                y = drawTitleBlock(canvas, context, table, titlePaint, subtitlePaint, y)
            }
            y = drawRow(canvas, widths, headerLayouts, y, headerH, rule)
            for (r in from until to) {
                y = drawRow(canvas, widths, rowLayouts[r], y, rowHeights[r], rule)
            }
            val label = context.getString(R.string.report_page, PersianDigits.toPersian(pageNo.toString()))
            canvas.drawText(label, PAGE_W / 2f, PAGE_H - MARGIN / 2, footerPaint)
            pdf.finishPage(page)
        }
        pdf.writeTo(out)
        pdf.close()
        return starts.size
    }

    private fun drawTitleBlock(
        canvas: Canvas,
        context: Context,
        table: ReportTable,
        titlePaint: Paint,
        subtitlePaint: Paint,
        top: Float,
    ): Float {
        var y = top
        canvas.drawText(table.title, PAGE_W - MARGIN, y + TITLE_SP, titlePaint)
        y += TITLE_SP + 6f
        canvas.drawText(table.subtitle, PAGE_W - MARGIN, y + BODY_SP, subtitlePaint)
        y += BODY_SP + 8f
        if (!table.balanced) {
            canvas.drawText(context.getString(R.string.report_unbalanced), PAGE_W - MARGIN, y + BODY_SP, subtitlePaint)
            y += BODY_SP + 8f
        }
        return y
    }

    /** Draws one row at [top]; returns the y below it. */
    private fun drawRow(
        canvas: Canvas,
        widths: List<Float>,
        layouts: List<StaticLayout>,
        top: Float,
        height: Float,
        rule: Paint,
    ): Float {
        layouts.forEachIndexed { i, layout ->
            canvas.save()
            canvas.translate(PdfLayout.columnLeft(widths, i) + PAD, top + PAD)
            layout.draw(canvas)
            canvas.restore()
        }
        canvas.drawLine(MARGIN, top + height, PAGE_W - MARGIN, top + height, rule)
        return top + height
    }

    /** Relative width per column: longer text gets more room, numbers stay compact. */
    private fun columnWeights(table: ReportTable): List<Float> = table.columns.indices.map { i ->
        val lengths = table.rows.map { ReportFormat.display(it[i], table.unit).length }
        val avg = if (lengths.isEmpty()) 0.0 else lengths.average()
        (avg / 6.0).coerceIn(1.0, 3.0).toFloat()
    }
}
