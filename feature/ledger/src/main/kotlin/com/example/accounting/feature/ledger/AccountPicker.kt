package com.example.accounting.feature.ledger

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.accounting.core.domain.AccountLevel
import com.example.accounting.core.ui.theme.AccountingTypography
import kotlin.math.abs

/**
 * Accounts tab. Shows the picker, or the account ledger once an account is picked.
 * Back returns from the ledger to the picker (system back, handled here).
 */
@Composable
fun AccountsRoute(modifier: Modifier = Modifier) {
    val viewModel: LedgerViewModel = hiltViewModel()
    val years by viewModel.fiscalYears.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val rows by viewModel.accountRows.collectAsStateWithLifecycle()
    val query by viewModel.accountQuery.collectAsStateWithLifecycle()
    val hideZero by viewModel.hideZeroBalances.collectAsStateWithLifecycle()
    val selected by viewModel.selectedAccount.collectAsStateWithLifecycle()
    val ledgerUi by viewModel.accountLedger.collectAsStateWithLifecycle()

    // Balances come from a trial balance (not a Flow). Reload each time this tab opens.
    LaunchedEffect(Unit) { viewModel.refresh() }
    BackHandler(enabled = selected != null) { viewModel.selectAccount(null) }

    Box(modifier = modifier.fillMaxSize()) {
        val code = selected
        if (code == null) {
            AccountPicker(
                years = years,
                selectedYear = filter.year,
                onYear = viewModel::selectYear,
                rows = rows,
                query = query,
                onQuery = viewModel::onAccountQuery,
                hideZero = hideZero,
                onHideZero = viewModel::setHideZeroBalances,
                onPick = viewModel::selectAccount,
            )
        } else {
            AccountLedgerScreen(
                ui = ledgerUi?.takeIf { it.code == code },
                years = years,
                filter = filter,
                onYear = viewModel::selectYear,
                onPreset = viewModel::selectPreset,
                onFromChange = viewModel::onFromTextChange,
                onToChange = viewModel::onToTextChange,
                onBack = { viewModel.selectAccount(null) },
                onOpen = viewModel::openDocument,
            )
        }
        DocumentDetailHost(viewModel)
    }
}

/**
 * Account picker: search by code or Persian name, hide-zero switch, fiscal year chips.
 * Groups (گروه) are headers. Other rows open the account ledger. Balances are net for the year, shown as a side word.
 */
@Composable
internal fun AccountPicker(
    years: List<Int>,
    selectedYear: Int,
    onYear: (Int) -> Unit,
    rows: List<AccountRow>,
    query: String,
    onQuery: (String) -> Unit,
    hideZero: Boolean,
    onHideZero: (Boolean) -> Unit,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") {
            Text(
                text = stringResource(R.string.accounts_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        }
        item(key = "search") {
            SearchField(
                value = query,
                onValueChange = onQuery,
                label = stringResource(R.string.accounts_search_label),
            )
        }
        item(key = "years") {
            FiscalYearChips(years = years, selected = selectedYear, onSelect = onYear)
        }
        item(key = "zero") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = hideZero, role = Role.Switch, onValueChange = onHideZero),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.accounts_hide_zero),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Switch(checked = hideZero, onCheckedChange = null)
            }
        }
        if (rows.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.accounts_empty_title),
                    body = stringResource(R.string.accounts_empty_body),
                )
            }
        }
        items(items = rows, key = { it.code }) { row ->
            AccountRowItem(row = row, onPick = onPick)
        }
    }
}

@Composable
private fun AccountRowItem(row: AccountRow, onPick: (String) -> Unit) {
    val isGroup = row.level == AccountLevel.GROUP
    val base = Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .padding(start = (row.depth * 16).dp)
    val rowModifier = if (isGroup) base else base.clickable { onPick(row.code) }
    val levelLabel = stringResource(
        when (row.level) {
            AccountLevel.GROUP -> R.string.level_group
            AccountLevel.GENERAL -> R.string.level_general
            AccountLevel.DETAIL -> R.string.level_detail
        },
    )
    Row(
        modifier = rowModifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.nameFa,
                style = if (isGroup) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = persianNumber(row.code) + " · " + levelLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!isGroup) {
            Text(
                text = balanceText(row.netRial),
                style = AccountingTypography.amount,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Net balance as «X تومان، بدهکار» / «X تومان، بستانکار», or «بدون مانده». Never a minus sign. */
@Composable
internal fun balanceText(netRial: Long): String = when {
    netRial == 0L -> stringResource(R.string.balance_zero)
    netRial > 0L -> stringResource(R.string.amount_side, tomanText(netRial), stringResource(R.string.side_debit))
    else -> stringResource(R.string.amount_side, tomanText(abs(netRial)), stringResource(R.string.side_credit))
}
