package com.example.accounting.feature.reports

import androidx.test.core.app.ApplicationProvider
import com.example.accounting.core.domain.report.ReportCell
import com.example.accounting.core.domain.report.ReportColumn
import com.example.accounting.core.domain.report.ReportTable
import com.example.accounting.core.persian.AmountUnit
import com.example.accounting.feature.reports.export.PdfExporter
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PDF export layout on Robolectric. Text extraction and visual checks are NOT done here (see the handout):
 * this proves pagination and that bytes are produced. Persian glyph shaping needs a device check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfExporterTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun table(rows: Int, balanced: Boolean = true): ReportTable = ReportTable(
        title = "دفتر روزنامه",
        subtitle = "از ۱۴۰۵/۰۱/۰۱ تا ۱۴۰۵/۱۲/۲۹",
        unit = AmountUnit.TOMAN,
        columns = listOf(
            ReportColumn("تاریخ"), ReportColumn("شماره سند", true), ReportColumn("شرح"),
            ReportColumn("کد"), ReportColumn("نام حساب"), ReportColumn("بدهکار", true), ReportColumn("بستانکار", true),
        ),
        rows = List(rows) { i ->
            listOf(
                ReportCell.Date(LocalDate.of(2026, 3, 21).plusDays(i.toLong())),
                ReportCell.Count(i + 1),
                ReportCell.Text("خرید کالا برای فروشگاه شماره ${i + 1}"),
                ReportCell.Text("1100"),
                ReportCell.Text("صندوق"),
                ReportCell.Amount(5_000_000L),
                ReportCell.Amount(0L),
            )
        },
        totals = listOf(
            ReportCell.Text(""), ReportCell.Text(""), ReportCell.Text(""), ReportCell.Text(""),
            ReportCell.Text("جمع"), ReportCell.Amount(5_000_000L * rows), ReportCell.Amount(5_000_000L * rows),
        ),
        balanced = balanced,
    )

    @Test
    fun longReportBreaksIntoRepeatedHeaderPages() {
        val out = ByteArrayOutputStream()
        val pages = PdfExporter.render(context, table(rows = 120), out)
        assertTrue("expected several pages, got $pages", pages > 1)
        assertTrue(out.size() > 0)
    }

    @Test
    fun shortReportFitsOnOnePage() {
        val pages = PdfExporter.render(context, table(rows = 3), ByteArrayOutputStream())
        assertEquals(1, pages)
    }

    @Test
    fun unbalancedReportStillRendersWithWarning() {
        val out = ByteArrayOutputStream()
        assertTrue(PdfExporter.render(context, table(rows = 2, balanced = false), out) >= 1)
        assertTrue(out.size() > 0)
    }
}
