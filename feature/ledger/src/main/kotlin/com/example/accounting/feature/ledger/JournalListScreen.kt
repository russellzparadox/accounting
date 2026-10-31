package com.example.accounting.feature.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.accounting.core.ui.theme.AccountingTypography

/** Journal tab entry point. Collects the shared ViewModel and forwards actions. */
@Composable
fun JournalRoute(modifier: Modifier = Modifier, onManual: () -> Unit = {}) {
    val viewModel: LedgerViewModel = hiltViewModel()
    val years by viewModel.fiscalYears.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val query by viewModel.journalQuery.collectAsStateWithLifecycle()
    val rows by viewModel.journal.collectAsStateWithLifecycle()
    Box(modifier = modifier.fillMaxSize()) {
        JournalListScreen(
            years = years,
            filter = filter,
            query = query,
            rows = rows,
            onYear = viewModel::selectYear,
            onPreset = viewModel::selectPreset,
            onFromChange = viewModel::onFromTextChange,
            onToChange = viewModel::onToTextChange,
            onQuery = viewModel::onJournalQuery,
            onOpen = viewModel::openDocument,
            onManual = onManual,
        )
        DocumentDetailHost(viewModel)
    }
}

/**
 * Journal list, newest first. Stateless. Rows open the document sheet; there is no edit or delete action.
 * A reversal row names the document it reverses; an original row shows a «معکوس شده» badge.
 */
@Composable
internal fun JournalListScreen(
    years: List<Int>,
    filter: LedgerFilter,
    query: String,
    rows: List<JournalRow>,
    onYear: (Int) -> Unit,
    onPreset: (DatePreset) -> Unit,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    onQuery: (String) -> Unit,
    onOpen: (String) -> Unit,
    onManual: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") {
            Text(
                text = stringResource(R.string.journal_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        }
        item(key = "manual") {
            Button(
                onClick = onManual,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.manual_open_action))
            }
        }
        item(key = "search") {
            SearchField(
                value = query,
                onValueChange = onQuery,
                label = stringResource(R.string.journal_search_label),
            )
        }
        item(key = "years") {
            FiscalYearChips(years = years, selected = filter.year, onSelect = onYear)
        }
        item(key = "range") {
            DateFilterBar(
                preset = filter.preset,
                fromText = filter.fromText,
                toText = filter.toText,
                dateError = filter.dateError,
                onPreset = onPreset,
                onFromChange = onFromChange,
                onToChange = onToChange,
            )
        }
        if (rows.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.journal_empty_title),
                    body = stringResource(R.string.journal_empty_body),
                )
            }
        }
        items(items = rows, key = { it.id }) { row ->
            JournalRowItem(row = row, onOpen = onOpen)
        }
    }
}

@Composable
private fun JournalRowItem(row: JournalRow, onOpen: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable { onOpen(row.id) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(
                    R.string.journal_row_head,
                    jalaliText(row.date),
                    persianNumber(row.docNumber),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = row.memo?.takeIf { it.isNotBlank() } ?: stringResource(R.string.no_memo),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (row.isReversal) {
                val origin = row.reversalOfNumber
                Text(
                    text = if (origin != null) {
                        stringResource(R.string.journal_row_reversal_of, persianNumber(origin))
                    } else {
                        stringResource(R.string.journal_row_reversal)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.isReversed) {
                StatusBadge(stringResource(R.string.status_reversed))
            }
        }
        Text(
            text = tomanText(row.totalRial),
            style = AccountingTypography.amount,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
