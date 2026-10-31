package com.example.accounting.feature.reports.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.accounting.feature.reports.R
import java.io.File

/** Export files live in cacheDir/reports and are shared through FileProvider (authority in the module manifest). */
internal object ReportFiles {
    const val AUTHORITY = "com.example.accounting.reportfiles"

    fun fileFor(context: Context, name: String): File =
        File(File(context.cacheDir, "reports").apply { mkdirs() }, name)

    fun uriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(context, AUTHORITY, file)

    fun share(context: Context, uri: Uri, mime: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.report_share_title))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
