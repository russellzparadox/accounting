package com.example.accounting.feature.reports.export

import android.content.Context
import android.net.Uri
import com.example.accounting.core.domain.report.CsvWriter
import com.example.accounting.core.domain.report.ReportTable

/** Writes [CsvWriter] output (UTF-8 with BOM, Latin digits) to cacheDir/reports. */
object CsvExporter {
    fun write(context: Context, table: ReportTable, baseName: String): Uri {
        val file = ReportFiles.fileFor(context, "$baseName.csv")
        file.writeBytes(CsvWriter.toUtf8Bytes(table))
        return ReportFiles.uriFor(context, file)
    }

    fun share(context: Context, uri: Uri) = ReportFiles.share(context, uri, "text/csv")
}
