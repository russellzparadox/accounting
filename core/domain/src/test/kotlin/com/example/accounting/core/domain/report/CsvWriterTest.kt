package com.example.accounting.core.domain.report

import com.example.accounting.core.persian.AmountUnit
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvWriterTest {
    private fun table(unit: AmountUnit, rows: List<List<ReportCell>>) = ReportTable(
        title = "t", subtitle = "s", unit = unit,
        columns = listOf(ReportColumn("تاریخ"), ReportColumn("شرح"), ReportColumn("مبلغ", numeric = true)),
        rows = rows, totals = null,
    )

    @Test
    fun startsWithUtf8ByteOrderMarkAndPersianHeader() {
        val bytes = CsvWriter.toUtf8Bytes(table(AmountUnit.TOMAN, emptyList()))
        assertEquals(0xEF.toByte(), bytes[0])
        assertEquals(0xBB.toByte(), bytes[1])
        assertEquals(0xBF.toByte(), bytes[2])
        val text = String(bytes, Charsets.UTF_8)
        assertEquals("﻿تاریخ,شرح,مبلغ\r\n", text)
    }

    @Test
    fun quotesFieldsWithCommaQuoteOrNewline() {
        assertEquals("\"a,b\"", CsvWriter.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.escape("say \"hi\""))
        assertEquals("\"x\ny\"", CsvWriter.escape("x\ny"))
        assertEquals("plain", CsvWriter.escape("plain"))
    }

    @Test
    fun datesAreLatinJalaliAndAmountsAreLatinIntegers() {
        // 2025-03-21 = 1 فروردین 1404
        val text = CsvWriter.write(
            table(AmountUnit.TOMAN, listOf(listOf(
                ReportCell.Date(LocalDate.of(2025, 3, 21)),
                ReportCell.Text("خرید، کاغذ"),
                ReportCell.Amount(5_000_000),
            ))),
        )
        assertTrue(text.contains("1404/01/01,\"خرید، کاغذ\",500000\r\n"), text)
    }

    @Test
    fun tomanKeepsExactDecimalWhenRialNotDivisibleByTen() {
        assertEquals("123456.7", ReportFormat.amountCsv(1_234_567, AmountUnit.TOMAN))
        assertEquals("-1.5", ReportFormat.amountCsv(-15, AmountUnit.TOMAN))
        assertEquals("1234567", ReportFormat.amountCsv(1_234_567, AmountUnit.RIAL))
    }

    @Test
    fun totalsRowIsWrittenLast() {
        val t = table(AmountUnit.RIAL, listOf(listOf(
            ReportCell.Date(LocalDate.of(2025, 3, 21)), ReportCell.Text("a"), ReportCell.Amount(1),
        ))).copy(totals = listOf(ReportCell.Text(""), ReportCell.Text("جمع"), ReportCell.Amount(1)))
        val lines = CsvWriter.write(t).removePrefix("﻿").split("\r\n")
        assertEquals("جمع", lines[lines.size - 2].split(",")[1])
    }
}
