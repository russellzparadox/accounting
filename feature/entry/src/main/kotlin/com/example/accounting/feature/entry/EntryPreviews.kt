package com.example.accounting.feature.entry

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.accounting.core.data.repo.CommittedDoc
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.domain.JournalLine
import com.example.accounting.core.domain.Money
import com.example.accounting.core.domain.PaidWith
import com.example.accounting.core.domain.Side
import com.example.accounting.core.domain.TemplateId
import com.example.accounting.core.domain.TransactionIntent
import com.example.accounting.core.domain.pipeline.ConfirmReason
import com.example.accounting.core.ui.theme.AccountingTheme
import java.time.LocalDate

// One preview per screen state (DESIGN.md 6). Preview-only data, not user-visible content.

private val previewDate: LocalDate = LocalDate.of(2026, 1, 10)

private fun previewEntry(rial: Long = 5_000_000L): JournalEntry = JournalEntry(
    id = "preview-entry",
    date = previewDate,
    templateId = TemplateId.PURCHASE_CASH,
    memo = "نوشابه",
    lines = listOf(
        JournalLine("1401", Side.DEBIT, Money(rial)),
        JournalLine("1101", Side.CREDIT, Money(rial)),
    ),
)

private fun previewDraft(mismatch: Boolean = false): ChatItem.DraftCard = ChatItem.DraftCard(
    id = "preview-draft",
    entry = previewEntry(),
    intent = TransactionIntent(
        type = TemplateId.PURCHASE_CASH,
        amountRial = 5_000_000L,
        date = previewDate,
        paidWith = PaidWith.CASH,
        memo = "نوشابه",
        confidence = 92,
    ),
    reasons = if (mismatch) listOf(ConfirmReason.AmountMismatch(llmRial = 4_000_000L, localRial = 5_000_000L)) else emptyList(),
    reasonTexts = if (mismatch) listOf("preview: مبلغ پیشنهادی با مبلغ متن فرق دارد.") else emptyList(),
    modelRial = 4_000_000L,
    committing = false,
    source = null,
)

@Preview(name = "idle", showBackground = true)
@Composable
private fun PreviewIdle() {
    AccountingTheme { EntryScreen(state = EntryUiState(), onAction = {}) }
}

@Preview(name = "loading", showBackground = true)
@Composable
private fun PreviewLoading() {
    AccountingTheme {
        EntryScreen(
            state = EntryUiState(items = listOf(ChatItem.UserText("u1", "نوشابه خریدم")), busy = true),
            onAction = {},
        )
    }
}

@Preview(name = "clarification", showBackground = true)
@Composable
private fun PreviewClarification() {
    AccountingTheme {
        EntryScreen(
            state = EntryUiState(
                items = listOf(
                    ChatItem.UserText("u1", "نوشابه خریدم"),
                    ChatItem.Clarification("c1", "مبلغ را به تومان بنویسید."),
                ),
            ),
            onAction = {},
        )
    }
}

@Preview(name = "draft", showBackground = true)
@Composable
private fun PreviewDraft() {
    AccountingTheme {
        EntryScreen(state = EntryUiState(items = listOf(previewDraft())), onAction = {})
    }
}

@Preview(name = "draft with amount mismatch", showBackground = true)
@Composable
private fun PreviewMismatch() {
    AccountingTheme {
        EntryScreen(state = EntryUiState(items = listOf(previewDraft(mismatch = true))), onAction = {})
    }
}

@Preview(name = "error", showBackground = true)
@Composable
private fun PreviewError() {
    AccountingTheme {
        EntryScreen(
            state = EntryUiState(
                items = listOf(
                    ChatItem.Error(
                        id = "e1",
                        kind = EntryErrorKind.OFFLINE,
                        title = "اتصال برقرار نشد",
                        message = "اینترنت را بررسی کنید و دوباره تلاش کنید. چیزی ثبت نشده است.",
                        retryText = "نوشابه خریدم",
                    ),
                ),
            ),
            onAction = {},
        )
    }
}

@Preview(name = "committed", showBackground = true)
@Composable
private fun PreviewCommitted() {
    AccountingTheme {
        EntryScreen(
            state = EntryUiState(
                items = listOf(
                    ChatItem.Committed(
                        "c1",
                        CommittedDoc(id = "preview-entry", fiscalYear = 1404, docNumber = 1, entry = previewEntry()),
                    ),
                ),
            ),
            onAction = {},
        )
    }
}

@Preview(name = "reversed", showBackground = true)
@Composable
private fun PreviewReversed() {
    AccountingTheme {
        EntryScreen(
            state = EntryUiState(
                items = listOf(
                    ChatItem.Reversed(
                        "r1",
                        originalDocId = "preview-entry",
                        doc = CommittedDoc(id = "preview-reversal", fiscalYear = 1404, docNumber = 2, entry = previewEntry()),
                    ),
                ),
            ),
            onAction = {},
        )
    }
}
