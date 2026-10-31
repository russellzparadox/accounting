package com.example.accounting.feature.entry

import com.example.accounting.core.persian.AmountInput
import com.example.accounting.core.persian.AmountUnit
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.accounting.core.domain.ExpenseCategory
import com.example.accounting.core.domain.PaidWith
import com.example.accounting.core.domain.TemplateId
import com.example.accounting.core.ui.money.LocalAmountUnit

/** Test tags for the instrumented QA flow (T13a). They attach no behaviour. */
const val MANUAL_AMOUNT_FIELD_TAG = "manual_amount_field"
const val MANUAL_SUBMIT_TAG = "manual_submit"

private val TEMPLATE_CHOICES = listOf(
    TemplateId.PURCHASE_CASH,
    TemplateId.PURCHASE_CREDIT,
    TemplateId.SALE_CASH,
    TemplateId.SALE_CREDIT,
    TemplateId.EXPENSE_PAID,
)

/**
 * Manual entry without the backend. Builds a [EntryAction.SubmitManual] and hands it up.
 * The amount is typed in the unit from LocalAmountUnit (settings): 500 is 500 rial or 500 toman accordingly.
 * Validation of the posting itself (party for credit, etc.) is done by the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntrySheet(
    onDismiss: () -> Unit,
    onSubmit: (EntryAction.SubmitManual) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ManualEntryForm(onDismiss = onDismiss, onSubmit = onSubmit)
    }
}

/** Sheet body without the modal, so it can be tested directly. */
@Composable
fun ManualEntryForm(
    onDismiss: () -> Unit,
    onSubmit: (EntryAction.SubmitManual) -> Unit,
) {
    val amountUnit = LocalAmountUnit.current
    var template by rememberSaveable { mutableStateOf(TemplateId.PURCHASE_CASH) }
    var paidWith by rememberSaveable { mutableStateOf(PaidWith.CASH) }
    var category by rememberSaveable { mutableStateOf(ExpenseCategory.MISC) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var party by rememberSaveable { mutableStateOf("") }
    var memo by rememberSaveable { mutableStateOf("") }

    val amountRial = AmountInput.parseRial(amountText, amountUnit)
    val needsParty = template == TemplateId.PURCHASE_CREDIT || template == TemplateId.SALE_CREDIT
    val hasPaidWith = template == TemplateId.PURCHASE_CASH ||
        template == TemplateId.SALE_CASH ||
        template == TemplateId.EXPENSE_PAID
    val isExpense = template == TemplateId.EXPENSE_PAID
    val canSubmit = amountRial != null && (!needsParty || party.isNotBlank())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.manual_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        SectionLabel(stringResource(R.string.manual_template_label))
        TEMPLATE_CHOICES.forEach { option ->
            RadioRow(
                label = stringResource(option.labelRes()),
                selected = template == option,
                onSelect = { template = option },
            )
        }
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            modifier = Modifier.fillMaxWidth().testTag(MANUAL_AMOUNT_FIELD_TAG),
            label = {
                Text(
                    stringResource(
                        if (amountUnit == AmountUnit.RIAL) R.string.amount_field_label_rial else R.string.amount_field_label,
                    ),
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        if (hasPaidWith) {
            SectionLabel(stringResource(R.string.manual_paid_label))
            RadioRow(
                label = stringResource(R.string.manual_paid_cash),
                selected = paidWith == PaidWith.CASH,
                onSelect = { paidWith = PaidWith.CASH },
            )
            RadioRow(
                label = stringResource(R.string.manual_paid_bank),
                selected = paidWith == PaidWith.BANK,
                onSelect = { paidWith = PaidWith.BANK },
            )
        }
        OutlinedTextField(
            value = party,
            onValueChange = { party = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.manual_party_label)) },
            supportingText = { if (needsParty) Text(stringResource(R.string.manual_party_hint)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
        )
        if (isExpense) {
            SectionLabel(stringResource(R.string.manual_category_label))
            ExpenseCategory.entries.forEach { option ->
                RadioRow(
                    label = stringResource(option.labelRes()),
                    selected = category == option,
                    onSelect = { category = option },
                )
            }
        }
        OutlinedTextField(
            value = memo,
            onValueChange = { memo = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.manual_memo_label)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
        )
        OutlinedButton(
            onClick = {
                amountRial?.let { rial ->
                    onSubmit(
                        EntryAction.SubmitManual(
                            templateId = template,
                            amountRial = rial,
                            paidWith = if (hasPaidWith) paidWith else null,
                            partyName = party.trim().ifBlank { null },
                            category = if (isExpense) category else null,
                            memo = memo.trim().ifBlank { null },
                        ),
                    )
                }
            },
            enabled = canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(MANUAL_SUBMIT_TAG),
        ) {
            Text(stringResource(R.string.manual_submit))
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.confirm_cancel_action))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One radio option. The whole row is the touch target (48dp minimum). */
@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun TemplateId.labelRes(): Int = when (this) {
    TemplateId.PURCHASE_CASH -> R.string.template_purchase_cash
    TemplateId.PURCHASE_CREDIT -> R.string.template_purchase_credit
    TemplateId.SALE_CASH -> R.string.template_sale_cash
    TemplateId.SALE_CREDIT -> R.string.template_sale_credit
    TemplateId.EXPENSE_PAID -> R.string.template_expense_paid
    else -> error("template not offered on the manual sheet: $this")
}

private fun ExpenseCategory.labelRes(): Int = when (this) {
    ExpenseCategory.RENT -> R.string.category_rent
    ExpenseCategory.FREIGHT -> R.string.category_freight
    ExpenseCategory.PHONE -> R.string.category_phone
    ExpenseCategory.SALARY -> R.string.category_salary
    ExpenseCategory.UTILITIES -> R.string.category_utilities
    ExpenseCategory.REPAIR -> R.string.category_repair
    ExpenseCategory.MISC -> R.string.category_misc
    ExpenseCategory.BANK_FEE -> R.string.category_bank_fee
}
