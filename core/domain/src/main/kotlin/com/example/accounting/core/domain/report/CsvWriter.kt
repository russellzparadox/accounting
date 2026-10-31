package com.example.accounting.core.domain.report

/**
 * RFC 4180 CSV for a [ReportTable]: comma separator, CRLF records, UTF-8 with BOM (so Excel reads Persian).
 * Generated numbers and dates use Latin digits; user text (memo, names) is written as typed.
 * Header row = column titles; then data rows; then the totals row when present.
 */
object CsvWriter {
    const val BOM = '﻿'

    fun write(table: ReportTable): String {
        val sb = StringBuilder().append(BOM)
        fun record(cells: List<String>) {
            sb.append(cells.joinToString(",") { escape(it) }).append("\r\n")
        }
        record(table.columns.map { it.title })
        table.rows.forEach { row -> record(row.map { ReportFormat.csv(it, table.unit) }) }
        table.totals?.let { totals -> record(totals.map { ReportFormat.csv(it, table.unit) }) }
        return sb.toString()
    }

    fun toUtf8Bytes(table: ReportTable): ByteArray = write(table).toByteArray(Charsets.UTF_8)

    internal fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
