package com.example.accounting.feature.reports

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.accounting.core.domain.report.ReportCell
import com.example.accounting.core.domain.report.ReportFormat
import com.example.accounting.core.domain.report.ReportTable
import com.example.accounting.core.persian.PersianDigits
import com.example.accounting.feature.ledger.DateFilterBar
import com.example.accounting.feature.reports.export.CsvExporter
import com.example.accounting.feature.reports.export.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reports screen (گزارش‌ها). Opened from the journal and accounts tabs; closes back to them. */
@Composable
fun ReportsRoute(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ReportsViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onClose)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.report_back_cd))
            }
            Text(
                stringResource(R.string.reports_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReportKind.entries.forEach { kind ->
                FilterChip(
                    selected = ui.kind == kind,
                    onClick = { vm.selectKind(kind) },
                    label = { Text(stringResource(kind.labelRes)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (ui.years + ui.year).distinct().sorted().forEach { year ->
                FilterChip(
                    selected = year == ui.year,
                    onClick = { vm.selectYear(year) },
                    label = { Text(PersianDigits.toPersian(year.toString())) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        DateFilterBar(
            preset = ui.preset,
            fromText = ui.fromText,
            toText = ui.toText,
            dateError = ui.dateError,
            onPreset = vm::selectPreset,
            onFromChange = vm::onFromText,
            onToChange = vm::onToText,
        )

        if (ui.kind == ReportKind.LEDGER) {
            OutlinedTextField(
                value = ui.accountCode,
                onValueChange = vm::onAccountCode,
                label = { Text(stringResource(R.string.report_account_code)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ui.message?.let { message ->
            Text(
                stringResource(message),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        val table = ui.table
        val baseName = "${ui.kind.name.lowercase()}-${ui.year}"
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = table != null && !ui.busy,
                modifier = Modifier.heightIn(min = 48.dp),
                onClick = {
                    val t = table ?: return@Button
                    scope.launch {
                        val uri = runCatching {
                            withContext(Dispatchers.IO) { CsvExporter.write(context, t, baseName) }
                        }.getOrNull()
                        if (uri == null) {
                            Toast.makeText(context, R.string.report_export_error, Toast.LENGTH_SHORT).show()
                        } else {
                            CsvExporter.share(context, uri)
                        }
                    }
                },
            ) { Text(stringResource(R.string.report_csv)) }
            OutlinedButton(
                enabled = table != null && !ui.busy,
                modifier = Modifier.heightIn(min = 48.dp),
                onClick = {
                    val t = table ?: return@OutlinedButton
                    scope.launch {
                        val uri = runCatching {
                            withContext(Dispatchers.IO) { PdfExporter.write(context, t, baseName) }
                        }.getOrNull()
                        if (uri == null) {
                            Toast.makeText(context, R.string.report_export_error, Toast.LENGTH_SHORT).show()
                        } else {
                            PdfExporter.share(context, uri)
                        }
                    }
                },
            ) { Text(stringResource(R.string.report_pdf)) }
        }

        if (table != null) ReportPreview(table)
    }
}

/** On-screen preview of the same table that the exports use. Horizontally scrollable, not lazy. */
@Composable
private fun ReportPreview(table: ReportTable) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(table.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(table.subtitle, style = MaterialTheme.typography.bodySmall)
        if (!table.balanced) {
            Text(stringResource(R.string.report_unbalanced), color = MaterialTheme.colorScheme.error)
        }
        fun text(cells: List<ReportCell>): List<String> = cells.map { ReportFormat.display(it, table.unit) }
        Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            PreviewRow(table.columns.map { it.title }, bold = true)
            table.rows.forEach { row -> PreviewRow(text(row), bold = false) }
            table.totals?.let { PreviewRow(text(it), bold = true) }
        }
    }
}

@Composable
private fun PreviewRow(texts: List<String>, bold: Boolean) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        texts.forEach { text ->
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.width(112.dp).padding(horizontal = 4.dp),
            )
        }
    }
}
