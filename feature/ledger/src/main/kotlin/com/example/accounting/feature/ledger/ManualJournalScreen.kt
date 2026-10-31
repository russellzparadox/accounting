package com.example.accounting.feature.ledger

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.accounting.core.domain.Account
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.domain.ManualIssue
import com.example.accounting.core.domain.PartyKind
import com.example.accounting.core.domain.Side
import com.example.accounting.core.persian.AmountUnit
import com.example.accounting.core.persian.JalaliDate
import com.example.accounting.core.persian.MoneyFormat
import com.example.accounting.core.ui.components.JalaliDatePickerDialog
import java.time.LocalDate
import kotlin.math.abs

/**
 * Free-form journal editor (سند دستی). [seed] prefills from an unconfirmed draft; null starts blank.
 * Restarts whenever [seedToken] changes. Back closes the account picker first, then the editor.
 */
@Composable
fun ManualJournalRoute(
    seed: JournalEntry?,
    seedToken: Long,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ManualJournalViewModel = hiltViewModel()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(seedToken) { viewModel.start(seed) }
    BackHandler {
        if (ui.pickerLine != null) viewModel.onAction(ManualAction.ClosePicker) else onClose()
    }
    ManualJournalScreen(ui = ui, onAction = viewModel::onAction, onClose = onClose, modifier = modifier)
}

/** Editor or account picker, depending on [ManualJournalUi.pickerLine]. Stateless. */
@Composable
fun ManualJournalScreen(
    ui: ManualJournalUi,
    onAction: (ManualAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ui.pickerLine != null) {
        ManualAccountPicker(ui = ui, onAction = onAction, modifier = modifier)
    } else {
        ManualForm(ui = ui, onAction = onAction, onClose = onClose, modifier = modifier)
    }
}

@Composable
private fun ManualForm(
    ui: ManualJournalUi,
    onAction: (ManualAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.manual_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.manual_close))
                }
            }
        }
        item(key = "date") {
            val message = when {
                !ui.dateValid -> stringResource(R.string.manual_date_invalid)
                !ui.dateYearOpen -> stringResource(R.string.manual_year_closed, persianNumber(ui.dateYear ?: 0))
                else -> stringResource(R.string.manual_date_hint)
            }
            var showDatePicker by rememberSaveable { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = ui.dateText,
                    onValueChange = { onAction(ManualAction.SetDate(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.manual_date_label)) },
                    singleLine = true,
                    isError = !ui.dateValid || !ui.dateYearOpen,
                    supportingText = { Text(message) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                // Text stays the primary input; the calendar is an alternative that writes the same text.
                TextButton(onClick = { showDatePicker = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.manual_pick_date_button))
                }
            }
            if (showDatePicker) {
                JalaliDatePickerDialog(
                    initial = parseJalaliInput(ui.dateText)?.let { JalaliDate.from(it) }
                        ?: JalaliDate.from(LocalDate.now()),
                    title = stringResource(R.string.manual_date_picker_title),
                    onConfirm = {
                        onAction(ManualAction.SetDate(it.toString()))
                        showDatePicker = false
                    },
                    onDismiss = { showDatePicker = false },
                )
            }
        }
        item(key = "memo") {
            OutlinedTextField(
                value = ui.memo,
                onValueChange = { onAction(ManualAction.SetMemo(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.manual_memo_label)) },
                singleLine = true,
            )
        }
        itemsIndexed(items = ui.rows, key = { _, row -> row.id }) { index, row ->
            ManualLineRow(index = index, row = row, unit = ui.unit, onAction = onAction)
        }
        item(key = "add") {
            OutlinedButton(
                onClick = { onAction(ManualAction.AddLine) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.manual_add_line))
            }
        }
        item(key = "totals") {
            val balanced = ManualIssue.UNBALANCED !in ui.check.issues
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.manual_total_debit, moneyText(ui.check.debitRial, ui.unit)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.manual_total_credit, moneyText(ui.check.creditRial, ui.unit)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (balanced) {
                        stringResource(R.string.manual_balanced)
                    } else {
                        stringResource(R.string.manual_difference, moneyText(abs(ui.check.differenceRial), ui.unit))
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        val problems = ui.check.issues.sortedBy { it.ordinal }
        items(items = problems, key = { "issue-${it.name}" }) { issue ->
            Text(
                text = stringResource(issueText(issue)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(key = "save") {
            Button(
                onClick = { onAction(ManualAction.Save) },
                enabled = ui.canSave,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(if (ui.saving) R.string.manual_saving else R.string.manual_save))
            }
        }
        ui.savedDocNumber?.let { number ->
            item(key = "saved") {
                Text(
                    text = stringResource(R.string.manual_saved, persianNumber(number)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        ui.error?.let { error ->
            item(key = "error") {
                Text(
                    text = stringResource(
                        if (error == ManualError.PARTY_FAILED) R.string.manual_error_party else R.string.manual_error_save,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun ManualLineRow(index: Int, row: ManualRowUi, unit: AmountUnit, onAction: (ManualAction) -> Unit) {
    val amountProblem = row.amountText.isNotBlank() && !row.amountValid
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.manual_line_title, persianNumber(index + 1)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = { onAction(ManualAction.RemoveLine(row.id)) }) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.manual_remove_line_cd),
                )
            }
        }
        OutlinedButton(
            onClick = { onAction(ManualAction.OpenPicker(row.id)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(text = accountButtonText(row))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = row.side == Side.DEBIT,
                onClick = { onAction(ManualAction.SetSide(row.id, Side.DEBIT)) },
                label = { Text(stringResource(R.string.side_debit)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            FilterChip(
                selected = row.side == Side.CREDIT,
                onClick = { onAction(ManualAction.SetSide(row.id, Side.CREDIT)) },
                label = { Text(stringResource(R.string.side_credit)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        OutlinedTextField(
            value = row.amountText,
            onValueChange = { onAction(ManualAction.SetAmount(row.id, it)) },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    stringResource(
                        if (unit == AmountUnit.RIAL) R.string.manual_amount_label_rial else R.string.manual_amount_label,
                    ),
                )
            },
            singleLine = true,
            isError = amountProblem,
            supportingText = {
                Text(
                    stringResource(
                        when {
                            amountProblem && unit == AmountUnit.RIAL -> R.string.manual_amount_invalid_rial
                            amountProblem -> R.string.manual_amount_invalid
                            unit == AmountUnit.RIAL -> R.string.manual_amount_hint_rial
                            else -> R.string.manual_amount_hint
                        },
                    ),
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@Composable
private fun accountButtonText(row: ManualRowUi): String = when {
    row.newParty != null -> stringResource(
        R.string.manual_new_party_line,
        stringResource(if (row.newParty == PartyKind.CUSTOMER) R.string.manual_kind_customer else R.string.manual_kind_supplier),
        row.accountLabel.orEmpty(),
    )
    row.accountCode != null -> stringResource(
        R.string.manual_account_line,
        persianNumber(row.accountCode),
        row.accountLabel.orEmpty(),
    )
    else -> stringResource(R.string.manual_pick_account)
}

@Composable
private fun ManualAccountPicker(
    ui: ManualJournalUi,
    onAction: (ManualAction) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.manual_picker_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                TextButton(
                    onClick = { onAction(ManualAction.ClosePicker) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.manual_picker_back))
                }
            }
        }
        item(key = "search") {
            SearchField(
                value = ui.query,
                onValueChange = { onAction(ManualAction.SetQuery(it)) },
                label = stringResource(R.string.manual_picker_search_label),
            )
        }
        if (ui.canCreateParty) {
            item(key = "new-customer") {
                OutlinedButton(
                    onClick = { onAction(ManualAction.PickNewParty(PartyKind.CUSTOMER)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.manual_add_customer, ui.query.trim()))
                }
            }
            item(key = "new-supplier") {
                OutlinedButton(
                    onClick = { onAction(ManualAction.PickNewParty(PartyKind.SUPPLIER)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.manual_add_supplier, ui.query.trim()))
                }
            }
        }
        if (ui.pickerAccounts.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.manual_picker_empty_title),
                    body = stringResource(R.string.manual_picker_empty_body),
                )
            }
        }
        items(items = ui.pickerAccounts, key = { it.code }) { account ->
            PickerRow(account = account, onPick = { onAction(ManualAction.PickAccount(it)) })
        }
    }
}

@Composable
private fun PickerRow(account: Account, onPick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onPick(account.code) }
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = account.nameFa,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = persianNumber(account.code),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun issueText(issue: ManualIssue): Int = when (issue) {
    ManualIssue.TOO_FEW_LINES -> R.string.manual_issue_too_few
    ManualIssue.TOO_MANY_LINES -> R.string.manual_issue_too_many
    ManualIssue.ACCOUNT_MISSING -> R.string.manual_issue_account_missing
    ManualIssue.ACCOUNT_NOT_POSTABLE -> R.string.manual_issue_not_postable
    ManualIssue.AMOUNT_INVALID -> R.string.manual_issue_amount
    ManualIssue.PARTY_NAME_MISSING -> R.string.manual_issue_party_name
    ManualIssue.UNBALANCED -> R.string.manual_issue_unbalanced
}

/** Money text in the unit from settings (toman by default). Stateless. */
private fun moneyText(rial: Long, unit: AmountUnit): String = MoneyFormat.format(rial, unit)
