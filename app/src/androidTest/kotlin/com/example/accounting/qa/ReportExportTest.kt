package com.example.accounting.qa

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.accounting.core.data.db.AppDatabase
import com.example.accounting.core.data.repo.LedgerRepository
import com.example.accounting.core.data.seed.ChartSeeder
import com.example.accounting.core.domain.ChartOfAccounts
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.domain.JournalLine
import com.example.accounting.core.domain.Money
import com.example.accounting.core.domain.Side
import com.example.accounting.core.domain.TemplateId
import com.example.accounting.core.domain.report.ReportTable
import com.example.accounting.core.domain.report.TrialAccount
import com.example.accounting.core.domain.report.TrialBalanceReport
import com.example.accounting.core.persian.AmountUnit
import com.example.accounting.core.persian.JalaliDate
import com.example.accounting.feature.reports.export.CsvExporter
import com.example.accounting.feature.reports.export.PdfExporter
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Report exports from a populated database (in-memory Room, not the app's file, so it is order-independent).
 * Uses the real PdfExporter / CsvExporter write paths (cacheDir/reports, FileProvider Uri, read back through the
 * content resolver). Outputs are copied to the app's external `qa/` folder for inspection.
 */
@RunWith(AndroidJUnit4::class)
class ReportExportTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase

    @Before
    fun openEmptyDatabase() {
        db = AppDatabase.inMemory(context)
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    /** Two cash/bank purchases, one of them reversed: 12,000,000 rial gross per side, balanced. */
    private fun populatedTrialTable(): ReportTable = runBlocking {
        val chart = ChartOfAccounts.standard()
        ChartSeeder(db, chart).seed()
        val ledger = LedgerRepository(db, chart)
        val today = LocalDate.now(ZoneId.of("Asia/Tehran"))
        ledger.ensureFiscalYear(JalaliDate.from(today).year)

        val cashPurchase = ledger.commit(
            JournalEntry(
                id = UUID.randomUUID().toString(),
                date = today,
                templateId = TemplateId.PURCHASE_CASH,
                memo = "خرید نقدی",
                lines = listOf(
                    JournalLine("5100", Side.DEBIT, Money(5_000_000L)),
                    JournalLine("1100", Side.CREDIT, Money(5_000_000L)),
                ),
            ),
        )
        ledger.commit(
            JournalEntry(
                id = UUID.randomUUID().toString(),
                date = today,
                templateId = TemplateId.PURCHASE_CASH,
                memo = "خرید بانکی",
                lines = listOf(
                    JournalLine("5100", Side.DEBIT, Money(2_000_000L)),
                    JournalLine("1201", Side.CREDIT, Money(2_000_000L)),
                ),
            ),
        )
        ledger.reverse(cashPurchase.id, today, "ابطال")

        val balance = ledger.trialBalance(JalaliDate.from(today).year)
        TrialBalanceReport.build(
            JalaliDate.from(today).year,
            null,
            balance.rows.map { TrialAccount(it.code, it.name, it.debit, it.credit) },
            AmountUnit.TOMAN,
        )
    }

    @Test
    fun trialBalancePdfExportsAndRendersNonBlankPages() {
        val table = populatedTrialTable()
        assertTrue("fixture must be balanced", table.balanced)

        val uri = PdfExporter.write(context, table, "trial-balance")
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        assertTrue("PDF header", bytes.size > 1000 && String(bytes, 0, 5, Charsets.US_ASCII) == "%PDF-")

        val qa = QaOutput.dir(context)
        val pdf = File(qa, "trial-balance.pdf").apply { writeBytes(bytes) }
        assertTrue("PDF file exists", pdf.exists() && pdf.length() > 0L)

        val renderer = PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY))
        try {
            val pages = renderer.pageCount
            assertTrue("page count > 0, was $pages", pages > 0)
            for (index in 0 until pages) {
                val page = renderer.openPage(index)
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                try {
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                } finally {
                    page.close()
                }
                assertNotBlank(bitmap, "page ${index + 1}")
                FileOutputStream(File(qa, "trial-balance-page-${index + 1}.png")).use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "PNG write failed" }
                }
            }
        } finally {
            renderer.close()
        }
    }

    @Test
    fun trialBalanceCsvHasUtf8BomAndPersianHeaders() {
        val table = populatedTrialTable()
        val uri = CsvExporter.write(context, table, "trial-balance")
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }

        assertArrayEquals(
            "UTF-8 BOM at the start of the file",
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
            bytes.copyOfRange(0, 3),
        )
        val text = bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        val header = text.lineSequence().first()
        table.columns.forEach { column ->
            assertTrue("header contains '${column.title}' in: $header", header.contains(column.title))
        }
        File(QaOutput.dir(context), "trial-balance.csv").writeBytes(bytes)
    }

    /** Fails when every pixel is the same colour (blank page) or when there is almost no ink. */
    private fun assertNotBlank(bitmap: Bitmap, label: String) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val ink = pixels.count { it != Color.WHITE }
        val distinct = pixels.toSet().size
        assertTrue("$label is blank (ink=$ink, colours=$distinct)", ink > 500 && distinct > 2)
    }
}
