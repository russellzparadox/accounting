package com.example.accounting.feature.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import com.example.accounting.core.ui.theme.AccountingTypography
import com.example.accounting.core.ui.money.amountText

/**
 * Account ledger (معین or کل): running balance per row, totals for the applied range, back to the picker.
 * Rows open the document sheet. Stateless: the ViewModel holds the data.
 */
@Composable
internal fun AccountLedgerScreen(
    ui: AccountLedgerUi?,
    years: List<Int>,
    filter: LedgerFilter,
    onYear: (Int) -> Unit,
    onPreset: (DatePreset) -> Unit,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.nav_back_cd),
                    )
                }
                Text(
                    text = if (ui != null) {
                        stringResource(R.string.ledger_title, ui.name)
                    } else {
                        stringResource(R.string.journal_title)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
            }
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
        if (ui == null) return@LazyColumn
        item(key = "totals") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.ledger_total_debit, amountText(ui.totalDebit)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.ledger_total_credit, amountText(ui.totalCredit)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.ledger_closing, balanceText(ui.closing)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (ui.rows.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.ledger_empty_title),
                    body = stringResource(R.string.ledger_empty_body),
                )
            }
        }
        items(items = ui.rows, key = { it.entryId }) { row ->
            LedgerRowItem(row = row, onOpen = onOpen)
        }
    }
}

@Composable
private fun LedgerRowItem(row: LedgerRowUi, onOpen: (String) -> Unit) {
    val isDebit = row.debit > 0L
    val movement = if (isDebit) {
        stringResource(R.string.amount_side, amountText(row.debit), stringResource(R.string.side_debit))
    } else {
        stringResource(R.string.amount_side, amountText(row.credit), stringResource(R.string.side_credit))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable { onOpen(row.entryId) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.journal_row_head, jalaliText(row.date), persianNumber(row.docNumber)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = row.memo?.takeIf { it.isNotBlank() } ?: stringResource(R.string.no_memo),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.ledger_balance_line, balanceText(row.balance)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = movement,
            style = AccountingTypography.amount,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
