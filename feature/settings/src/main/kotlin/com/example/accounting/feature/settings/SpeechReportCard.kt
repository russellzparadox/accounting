package com.example.accounting.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.accounting.core.data.speech.PackStatus
import com.example.accounting.core.data.speech.SpeechEngine
import com.example.accounting.core.data.speech.SpeechErrorKind
import com.example.accounting.core.data.speech.SpeechReport
import com.example.accounting.core.persian.PersianDigits

/**
 * Settings «تشخیص گفتار» card: what the phone offers for fa-IR speech and the last voice error.
 * [report] is null while the check runs. The copy button puts [speechReportText] on the clipboard.
 */
@Composable
fun SpeechReportCard(report: SpeechReport?, onRefresh: () -> Unit) {
    val context = LocalContext.current
    var copied by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.speech_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.speech_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (report == null) {
            Text(
                text = stringResource(R.string.speech_checking),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            speechReportLines(context, report).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (report != null) {
                        copySpeechReport(context, report)
                        copied = true
                    }
                },
                enabled = report != null,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.speech_copy_action)) }
            OutlinedButton(
                onClick = {
                    copied = false
                    onRefresh()
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.speech_refresh_action)) }
        }
        if (copied) {
            Text(
                text = stringResource(R.string.speech_copied),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Report lines in display order, without the title. Persian labels and digits; no device model, no account data. */
fun speechReportLines(context: Context, report: SpeechReport): List<String> {
    val res = context.resources
    fun fa(value: Any) = PersianDigits.toPersian(value.toString())
    val lines = mutableListOf<String>()
    lines += res.getString(R.string.speech_api_level, fa(report.apiLevel))
    lines += res.getString(if (report.recognitionAvailable) R.string.speech_service_yes else R.string.speech_service_no)
    lines += res.getString(if (report.onDeviceAvailable) R.string.speech_on_device_yes else R.string.speech_on_device_no)
    val services = if (report.servicePackages.isEmpty()) {
        res.getString(R.string.speech_services_none)
    } else {
        report.servicePackages.joinToString("، ")
    }
    lines += res.getString(R.string.speech_services, services)
    lines += res.getString(R.string.speech_fa_pack, res.getString(faStateRes(report.faPack)))
    val last = report.lastError
    if (last == null) {
        lines += res.getString(R.string.speech_last_error_none)
    } else {
        val kind = res.getString(errorLabelRes(last.kind))
        val described = last.code?.let { res.getString(R.string.speech_error_code, kind, fa(it)) } ?: kind
        lines += res.getString(R.string.speech_last_error, described)
        last.engine?.let { lines += res.getString(R.string.speech_engine, res.getString(engineRes(it))) }
    }
    return lines
}

/** The whole report as plain text: title, then [speechReportLines]. */
fun speechReportText(context: Context, report: SpeechReport): String =
    (listOf(context.getString(R.string.speech_report_title)) + speechReportLines(context, report)).joinToString("\n")

/** Puts the report on the system clipboard as plain text. */
fun copySpeechReport(context: Context, report: SpeechReport) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.speech_report_title), speechReportText(context, report)))
}

private fun faStateRes(status: PackStatus?): Int = when (status) {
    PackStatus.INSTALLED -> R.string.speech_fa_installed
    PackStatus.NOT_INSTALLED -> R.string.speech_fa_not_installed
    PackStatus.PENDING -> R.string.speech_fa_pending
    PackStatus.UNSUPPORTED_API -> R.string.speech_fa_unsupported_api
    PackStatus.UNKNOWN, null -> R.string.speech_fa_unknown
}

private fun errorLabelRes(kind: SpeechErrorKind): Int = when (kind) {
    SpeechErrorKind.NO_MATCH -> R.string.speech_err_no_match
    SpeechErrorKind.NO_RECOGNIZER_INSTALLED -> R.string.speech_err_no_recognizer
    SpeechErrorKind.LANGUAGE_PACK_MISSING -> R.string.speech_err_language_pack
    SpeechErrorKind.BUSY -> R.string.speech_err_busy
    SpeechErrorKind.NO_PERMISSION -> R.string.speech_err_no_permission
    SpeechErrorKind.NETWORK -> R.string.speech_err_network
    SpeechErrorKind.UNSUPPORTED -> R.string.speech_err_unsupported
    SpeechErrorKind.OTHER -> R.string.speech_err_other
}

private fun engineRes(engine: SpeechEngine): Int = when (engine) {
    SpeechEngine.ON_DEVICE -> R.string.speech_engine_on_device
    SpeechEngine.SYSTEM -> R.string.speech_engine_system
}
