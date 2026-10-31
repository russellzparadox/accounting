package com.example.accounting.core.domain

import java.time.LocalDate

/**
 * Account of one manual line. [NewParty] is a customer or supplier معین that does not exist yet; it is created
 * only when the document is saved (see the manual editor), so [ManualJournal.check] never needs a chart entry for it.
 */
sealed interface ManualAccount {
    data object Missing : ManualAccount
    data class Existing(val code: String) : ManualAccount
    data class NewParty(val kind: PartyKind, val name: String) : ManualAccount
}

/** One line as typed by the user. [rial] is null when the amount is empty or not valid. */
data class ManualLine(val account: ManualAccount, val side: Side, val rial: Long?)

/** Why a manual document cannot be saved. The UI maps each to Persian text. */
enum class ManualIssue {
    TOO_FEW_LINES,
    TOO_MANY_LINES,
    ACCOUNT_MISSING,
    ACCOUNT_NOT_POSTABLE,
    AMOUNT_INVALID,
    PARTY_NAME_MISSING,
    UNBALANCED,
}

/** Live result for the editor: [debitRial] and [creditRial] are sums of valid amounts only. */
data class ManualCheck(val issues: Set<ManualIssue>, val debitRial: Long, val creditRial: Long) {
    val differenceRial: Long get() = debitRial - creditRial
    val isValid: Boolean get() = issues.isEmpty()
}

/**
 * Free-form journal document (template [TemplateId.MANUAL]). Pure logic, no Android, no money floats.
 * The caller supplies the account list (committed chart plus party معین already in the database).
 */
object ManualJournal {
    const val MAX_LINES = 50

    /** 10^15 rial (10^14 toman). Keeps sums of MAX_LINES lines far below Long.MAX_VALUE. */
    const val MAX_LINE_RIAL = 1_000_000_000_000_000L

    /** Typed whole toman to rial. Null when not in 1..MAX_LINE_RIAL/10. */
    fun tomanToRial(toman: Long): Long? = if (toman in 1L..(MAX_LINE_RIAL / 10L)) toman * 10L else null

    /** Rial to whole toman, only when the rial value is an exact multiple of 10. Null otherwise. */
    fun rialToWholeToman(rial: Long): Long? = if (rial > 0L && rial % 10L == 0L) rial / 10L else null

    /** Trimmed name with single spaces, the same normalization PostingEngine uses. Null when blank. */
    fun partyDisplayName(raw: String): String? = raw.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }

    /**
     * A postable account exists in [accounts], is not گروه, and has no children (معین rule). Party accounts are
     * ordinary DETAIL rows in [accounts], so a party معین is postable.
     */
    /** Accounts a line may use: not گروه, and no children. */
    fun postableAccounts(accounts: List<Account>): List<Account> {
        val parentCodes = accounts.mapNotNullTo(HashSet()) { it.parent }
        return accounts.filter { it.level != AccountLevel.GROUP && it.code !in parentCodes }
    }

    fun check(lines: List<ManualLine>, accounts: List<Account>): ManualCheck {
        val postable = postableAccounts(accounts).mapTo(HashSet()) { it.code }
        val issues = mutableSetOf<ManualIssue>()
        if (lines.size < 2) issues += ManualIssue.TOO_FEW_LINES
        if (lines.size > MAX_LINES) issues += ManualIssue.TOO_MANY_LINES
        lines.forEach { line ->
            when (val account = line.account) {
                ManualAccount.Missing -> issues += ManualIssue.ACCOUNT_MISSING
                is ManualAccount.Existing -> {
                    if (account.code !in postable) issues += ManualIssue.ACCOUNT_NOT_POSTABLE
                }
                is ManualAccount.NewParty -> {
                    if (partyDisplayName(account.name) == null) issues += ManualIssue.PARTY_NAME_MISSING
                }
            }
            if (validRial(line.rial) == null) issues += ManualIssue.AMOUNT_INVALID
        }
        val debit = lines.filter { it.side == Side.DEBIT }.sumOf { validRial(it.rial) ?: 0L }
        val credit = lines.filter { it.side == Side.CREDIT }.sumOf { validRial(it.rial) ?: 0L }
        if (debit != credit || debit == 0L) issues += ManualIssue.UNBALANCED
        return ManualCheck(issues, debit, credit)
    }

    /**
     * Builds the committed-shape entry. Throws [DomainException] unless [check] is valid and every line is
     * [ManualAccount.Existing] (resolve new parties first). Never throws for a valid document.
     */
    fun build(
        id: String,
        date: LocalDate,
        memo: String?,
        lines: List<ManualLine>,
        accounts: List<Account>,
    ): JournalEntry {
        val result = check(lines, accounts)
        if (!result.isValid) throw DomainException("manual document invalid: ${result.issues.map { it.name }.sorted()}")
        val journalLines = lines.map { line ->
            val code = (line.account as? ManualAccount.Existing)?.code
                ?: throw DomainException("manual line has no existing account (resolve new parties first)")
            JournalLine(code, line.side, Money(requireNotNull(validRial(line.rial))))
        }
        return JournalEntry(
            id = id,
            date = date,
            templateId = TemplateId.MANUAL,
            memo = memo?.trim()?.takeIf { it.isNotEmpty() },
            lines = journalLines,
        )
    }

    private fun validRial(rial: Long?): Long? = rial?.takeIf { it in 1L..MAX_LINE_RIAL }
}
