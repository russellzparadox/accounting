package com.example.accounting.feature.ledger

import com.example.accounting.core.domain.Account
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.domain.ManualAccount
import com.example.accounting.core.domain.ManualCheck
import com.example.accounting.core.domain.ManualJournal
import com.example.accounting.core.domain.ManualLine
import com.example.accounting.core.domain.PartyKind
import com.example.accounting.core.domain.Side
import com.example.accounting.core.persian.AmountInput
import com.example.accounting.core.persian.AmountUnit
import com.example.accounting.core.persian.JalaliDate
import com.example.accounting.core.persian.PersianDigits

/**
 * One line as typed. [id] is stable (Compose keys). [amountText] is typed in the display unit from settings
 * (toman or rial), Persian or Latin digits. Conversion to rial is [AmountInput] only.
 */
data class ManualLineDraft(
    val id: Long,
    val account: ManualAccount = ManualAccount.Missing,
    val side: Side = Side.DEBIT,
    val amountText: String = "",
) {
    /** Rial for this line in [unit]. Null when the amount is empty, zero, or invalid for that unit. */
    fun rial(unit: AmountUnit): Long? = AmountInput.parseRial(amountText, unit)

    fun toDomain(unit: AmountUnit): ManualLine = ManualLine(account, side, rial(unit))
}

/** Editable document. Nothing here is persisted until the user saves. */
data class ManualDraft(
    val dateText: String,
    val memo: String = "",
    val lines: List<ManualLineDraft> = emptyList(),
    /** Line whose account is being picked. Null when the form is shown. */
    val pickerLine: Long? = null,
    val query: String = "",
    val saving: Boolean = false,
    val savedDocNumber: Int? = null,
    val error: ManualError? = null,
)

/** Save failures shown as Persian text. */
enum class ManualError { SAVE_FAILED, PARTY_FAILED }

/** One row as shown. [accountLabel] is the account name, or the typed name of a new party. */
data class ManualRowUi(
    val id: Long,
    val accountCode: String?,
    val accountLabel: String?,
    val newParty: PartyKind?,
    val side: Side,
    val amountText: String,
    val amountValid: Boolean,
)

/** Everything the screen shows. Derived from [ManualDraft], the account list and the opened fiscal years. */
data class ManualJournalUi(
    val dateText: String,
    val dateValid: Boolean,
    val dateYear: Int?,
    /** True when the typed year is already open. A closed year is opened by the save; it does not block it. */
    val dateYearOpened: Boolean,
    val memo: String,
    val rows: List<ManualRowUi>,
    val check: ManualCheck,
    val pickerLine: Long?,
    val query: String,
    val pickerAccounts: List<Account>,
    val canCreateParty: Boolean,
    val saving: Boolean,
    val savedDocNumber: Int?,
    val error: ManualError?,
    /** Unit the amounts are typed and shown in (settings). */
    val unit: AmountUnit = AmountUnit.TOMAN,
) {
    /** Save is possible for a balanced, valid document with a valid date, once. */
    val canSave: Boolean get() = check.isValid && dateValid && !saving && savedDocNumber == null
}

/** Everything the editor can ask for. Routed through ManualJournalViewModel.onAction. */
sealed interface ManualAction {
    data class SetDate(val text: String) : ManualAction
    data class SetMemo(val text: String) : ManualAction
    data object AddLine : ManualAction
    data class RemoveLine(val id: Long) : ManualAction
    data class SetSide(val id: Long, val side: Side) : ManualAction
    data class SetAmount(val id: Long, val text: String) : ManualAction
    data class OpenPicker(val id: Long) : ManualAction
    data object ClosePicker : ManualAction
    data class SetQuery(val text: String) : ManualAction
    data class PickAccount(val code: String) : ManualAction
    /** Uses the picker query as the new party name. Nothing is created until save. */
    data class PickNewParty(val kind: PartyKind) : ManualAction
    data object Save : ManualAction
}

/** Derives the screen state. Pure, so it is testable without Android. */
internal fun buildManualUi(
    draft: ManualDraft,
    accounts: List<Account>,
    openYears: List<Int>,
    unit: AmountUnit = AmountUnit.TOMAN,
): ManualJournalUi {
    val date = parseJalaliInput(draft.dateText)
    val year = date?.let { JalaliDate.from(it).year }
    val names = accounts.associate { it.code to it.nameFa }
    val rows = draft.lines.map { line ->
        val account = line.account
        ManualRowUi(
            id = line.id,
            accountCode = (account as? ManualAccount.Existing)?.code,
            accountLabel = when (account) {
                ManualAccount.Missing -> null
                is ManualAccount.Existing -> names[account.code] ?: account.code
                is ManualAccount.NewParty -> ManualJournal.partyDisplayName(account.name) ?: account.name
            },
            newParty = (account as? ManualAccount.NewParty)?.kind,
            side = line.side,
            amountText = line.amountText,
            amountValid = line.rial(unit) != null,
        )
    }
    val pickerAccounts = if (draft.pickerLine == null) {
        emptyList()
    } else {
        val needle = LedgerSearch.normalize(draft.query)
        ManualJournal.postableAccounts(accounts)
            .filter { acc ->
                needle.isEmpty() ||
                    LedgerSearch.normalize(acc.code).contains(needle) ||
                    LedgerSearch.normalize(acc.nameFa).contains(needle)
            }
            .sortedBy { it.code }
    }
    return ManualJournalUi(
        dateText = draft.dateText,
        dateValid = date != null,
        dateYear = year,
        dateYearOpened = year != null && year in openYears,
        memo = draft.memo,
        rows = rows,
        check = ManualJournal.check(draft.lines.map { it.toDomain(unit) }, accounts),
        pickerLine = draft.pickerLine,
        query = draft.query,
        pickerAccounts = pickerAccounts,
        canCreateParty = ManualJournal.partyDisplayName(draft.query) != null,
        saving = draft.saving,
        savedDocNumber = draft.savedDocNumber,
        error = draft.error,
        unit = unit,
    )
}

/**
 * Prefill from an unconfirmed entry (its lines, date and memo). A line whose amount is not a whole toman
 * amount keeps an empty amount, so the user must type it again instead of the value changing silently.
 */
internal fun seededDraft(entry: JournalEntry, nextId: () -> Long, unit: AmountUnit = AmountUnit.TOMAN): ManualDraft = ManualDraft(
    dateText = JalaliDate.from(entry.date).toString(),
    memo = entry.memo.orEmpty(),
    lines = entry.lines.map { line ->
        ManualLineDraft(
            id = nextId(),
            account = ManualAccount.Existing(line.accountCode),
            side = line.side,
            amountText = AmountInput.toInputText(line.amount.rial, unit)
                ?.let { PersianDigits.toPersian(it) }
                .orEmpty(),
        )
    },
)
