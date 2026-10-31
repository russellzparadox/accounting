package com.example.accounting.feature.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.accounting.core.domain.JournalLine
import com.example.accounting.core.domain.Side
import com.example.accounting.core.domain.pipeline.ConfirmReason
import com.example.accounting.core.persian.AmountUnit
import com.example.accounting.core.persian.JalaliDate
import com.example.accounting.core.persian.MoneyFormat
import com.example.accounting.core.persian.PersianDigits
import com.example.accounting.core.ui.theme.AccountingTypography

/**
 * Editable draft card: amount hero, date, reasons, lines, Dr/Cr totals, actions.
 * Lines are not edited by hand: the only edit is the amount (re-posted by the ViewModel).
 */
@Composable
fun ConfirmationCard(
    card: ChatItem.DraftCard,
    onAction: (EntryAction) -> Unit,
    accountName: (String) -> String,
    modifier: Modifier = Modifier,
) {
    var editing by rememberSaveable(card.id) { mutableStateOf(false) }
    val entry = card.entry
    val debit = MoneyFormat.format(entry.debitTotal.rial, AmountUnit.TOMAN)
    val credit = MoneyFormat.format(entry.creditTotal.rial, AmountUnit.TOMAN)
    val debitTotalText = stringResource(R.string.confirm_debit_total, debit)
    val creditTotalText = stringResource(R.string.confirm_credit_total, credit)
    val hasMismatch = card.reasons.any { it is ConfirmReason.AmountMismatch }

    ChatCard(modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(
            text = stringResource(R.string.confirm_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = debit,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = JalaliDate.from(entry.date).toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        entry.memo?.takeIf { it.isNotBlank() }?.let { memo ->
            Text(
                text = memo,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (card.reasonTexts.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    card.reasonTexts.forEach { text ->
                        Text(text = text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        HorizontalDivider()
        entry.lines.forEach { line ->
            LineRow(line = line, name = accountName(line.accountCode))
        }
        HorizontalDivider()
        Text(
            text = debitTotalText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = creditTotalText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (hasMismatch && !card.committing) {
            Button(
                onClick = { onAction(EntryAction.UseDetectedAmount(card.id)) },
                modifier = FullWidthButtonModifier,
            ) {
                Text(stringResource(R.string.action_use_detected))
            }
            OutlinedButton(
                onClick = { onAction(EntryAction.UseModelAmount(card.id)) },
                modifier = FullWidthButtonModifier,
            ) {
                Text(stringResource(R.string.action_use_model))
            }
        }

        Button(
            onClick = { onAction(EntryAction.Confirm(card.id)) },
            enabled = !card.committing,
            modifier = FullWidthButtonModifier,
        ) {
            if (card.committing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.loading_save),
                    modifier = Modifier.padding(start = 8.dp),
                )
            } else {
                Text(stringResource(R.string.confirm_action))
            }
        }
        OutlinedButton(
            onClick = { editing = true },
            enabled = !card.committing,
            modifier = FullWidthButtonModifier,
        ) {
            Text(stringResource(R.string.confirm_edit_action))
        }
        TextButton(
            onClick = { onAction(EntryAction.Discard(card.id)) },
            enabled = !card.committing,
            modifier = FullWidthButtonModifier,
        ) {
            Text(stringResource(R.string.confirm_cancel_action))
        }
    }

    if (editing) {
        EditAmountDialog(
            initialRial = entry.debitTotal.rial,
            onDismiss = { editing = false },
            onSave = { rial ->
                editing = false
                onAction(EntryAction.EditAmount(card.id, rial))
            },
        )
    }
}

@Composable
private fun LineRow(line: JournalLine, name: String) {
    val sideText = stringResource(if (line.side == Side.DEBIT) R.string.side_debit else R.string.side_credit)
    val amount = MoneyFormat.format(line.amount.rial, AmountUnit.TOMAN)
    val description = stringResource(R.string.amount_cd, amount, sideText)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = sideText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = amount,
            style = AccountingTypography.amount,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EditAmountDialog(initialRial: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by rememberSaveable { mutableStateOf(PersianDigits.toPersian((initialRial / 10L).toString())) }
    val parsed = parseTomanInput(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_amount_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.edit_amount_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.amount_field_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onSave) },
                enabled = parsed != null,
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm_cancel_action))
            }
        },
    )
}

/** Toman text (Persian or Latin digits, separators ignored) to rial. Null when empty, zero or too long. */
internal fun parseTomanInput(text: String): Long? {
    val digits = PersianDigits.toLatin(text.trim()).filter { it in '0'..'9' }
    if (digits.isEmpty() || digits.length > MAX_TOMAN_DIGITS) return null
    val toman = digits.toLongOrNull() ?: return null
    return if (toman > 0L) toman * 10L else null
}

private const val MAX_TOMAN_DIGITS = 14
