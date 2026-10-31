package com.example.accounting.feature.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.accounting.core.domain.Side

/**
 * Document sheet host. Reads [LedgerViewModel.detail]; shows nothing when no document is open.
 * The only write offered here is «ابطال (سند اصلاحی)», behind a confirm dialog. No edit and no delete.
 */
@Composable
internal fun DocumentDetailHost(viewModel: LedgerViewModel) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val names by viewModel.accountNames.collectAsStateWithLifecycle()
    val current = detail ?: return
    DocumentDetailSheet(
        detail = current,
        accountName = { code -> names[code] ?: code },
        onOpen = viewModel::openDocument,
        onReverse = { viewModel.askReverse(true) },
        onConfirmReverse = viewModel::confirmReverse,
        onCancelReverse = { viewModel.askReverse(false) },
        onDismiss = viewModel::closeDocument,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentDetailSheet(
    detail: DocumentDetailUi,
    accountName: (String) -> String,
    onOpen: (String) -> Unit,
    onReverse: () -> Unit,
    onConfirmReverse: () -> Unit,
    onCancelReverse: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        DocumentDetailContent(
            detail = detail,
            accountName = accountName,
            onOpen = onOpen,
            onReverse = onReverse,
            onClose = onDismiss,
        )
    }
    if (detail.confirmReverse) {
        AlertDialog(
            onDismissRequest = onCancelReverse,
            title = { Text(stringResource(R.string.doc_reverse_dialog_title)) },
            text = { Text(stringResource(R.string.doc_reverse_dialog_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmReverse) { Text(stringResource(R.string.doc_reverse_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onCancelReverse) { Text(stringResource(R.string.doc_reverse_cancel)) }
            },
        )
    }
}

/** Sheet body. Separate from the sheet so it can be tested without the modal. */
@Composable
internal fun DocumentDetailContent(
    detail: DocumentDetailUi,
    accountName: (String) -> String,
    onOpen: (String) -> Unit,
    onReverse: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val doc = detail.doc
    val entry = doc.entry
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.doc_title, persianNumber(doc.docNumber)),
            style = MaterialTheme.typography.titleLarge,
            color = onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = jalaliText(entry.date),
            style = MaterialTheme.typography.bodyMedium,
            color = onSurfaceVariant,
        )
        Text(
            text = entry.memo?.takeIf { it.isNotBlank() } ?: stringResource(R.string.no_memo),
            style = MaterialTheme.typography.bodyLarge,
            color = onSurface,
        )
        HorizontalDivider()
        entry.lines.forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = persianNumber(line.accountCode) + " · " + accountName(line.accountCode),
                    style = MaterialTheme.typography.titleMedium,
                    color = onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.amount_side,
                        tomanText(line.amount.rial),
                        stringResource(if (line.side == Side.DEBIT) R.string.side_debit else R.string.side_credit),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurface,
                )
            }
        }
        HorizontalDivider()
        Text(
            text = stringResource(R.string.doc_total_debit, tomanText(entry.debitTotal.rial)),
            style = MaterialTheme.typography.bodyMedium,
            color = onSurface,
        )
        Text(
            text = stringResource(R.string.doc_total_credit, tomanText(entry.creditTotal.rial)),
            style = MaterialTheme.typography.bodyMedium,
            color = onSurface,
        )

        val original = detail.links.reversalOf
        if (original != null) {
            Text(
                text = stringResource(R.string.doc_is_reversal_of, persianNumber(original.docNumber)),
                style = MaterialTheme.typography.bodyMedium,
                color = onSurfaceVariant,
            )
            TextButton(onClick = { onOpen(original.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.doc_open_linked, persianNumber(original.docNumber)))
            }
        }
        val reversal = detail.links.reversedBy
        if (reversal != null) {
            Text(
                text = stringResource(R.string.doc_is_reversed_by, persianNumber(reversal.docNumber)),
                style = MaterialTheme.typography.bodyMedium,
                color = onSurfaceVariant,
            )
            TextButton(onClick = { onOpen(reversal.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.doc_open_linked, persianNumber(reversal.docNumber)))
            }
        }

        if (detail.canReverse) {
            Button(
                onClick = onReverse,
                enabled = !detail.reversing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.doc_reverse_action))
            }
        } else {
            Text(
                text = stringResource(
                    if (detail.isReversal) R.string.doc_cannot_reverse_reversal else R.string.doc_cannot_reverse_reversed,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = onSurfaceVariant,
            )
        }

        val reversedNumber = detail.reversedNumber
        if (reversedNumber != null) {
            Text(
                text = stringResource(R.string.doc_reversed_notice, persianNumber(reversedNumber)),
                style = MaterialTheme.typography.bodyMedium,
                color = onSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (detail.error == DetailError.REVERSE_FAILED) {
            Text(
                text = stringResource(R.string.doc_reverse_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.doc_close))
        }
    }
}
