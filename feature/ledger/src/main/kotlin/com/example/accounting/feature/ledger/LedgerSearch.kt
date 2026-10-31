package com.example.accounting.feature.ledger

import com.example.accounting.core.data.repo.CommittedDoc
import com.example.accounting.core.data.repo.ReversalLink
import com.example.accounting.core.domain.Account
import com.example.accounting.core.domain.AccountLevel
import com.example.accounting.core.domain.partyKey
import com.example.accounting.core.persian.AmountUnit
import com.example.accounting.core.persian.PersianDigits
import java.time.LocalDate

/** One journal list row. [totalRial] is the debit total (= credit total). */
data class JournalRow(
    val id: String,
    val date: LocalDate,
    val docNumber: Int,
    val memo: String?,
    val totalRial: Long,
    val reversalOfNumber: Int?,
    val isReversal: Boolean,
    val isReversed: Boolean,
)

/** One account picker row. Groups (گروه) are headers only. [netRial] is debit minus credit for the fiscal year. */
data class AccountRow(
    val code: String,
    val nameFa: String,
    val level: AccountLevel,
    val depth: Int,
    val netRial: Long,
)

/**
 * Search normalization for the ledger (T10). Applied to both query and haystack:
 * Persian and Arabic digits to Latin, ي/ك to ی/ک, ZWNJ and whitespace removed, grouping separators (٬ , ،)
 * dropped, lowercase. Built on [partyKey] so the rule matches party-name matching in the domain.
 */
object LedgerSearch {
    private val groupingSeparators = Regex("[,٬،]")

    fun normalize(text: String): String =
        partyKey(PersianDigits.toLatin(text).replace(groupingSeparators, "")).lowercase()

    /**
     * A digits-only query matches a line amount written in [unit] (the setting): the rial value in RIAL, the
     * whole-toman value in TOMAN (only when the rial value is divisible by 10). Text queries never match amounts.
     */
    fun amountMatches(query: String, rial: Long, unit: AmountUnit): Boolean {
        if (query.isEmpty() || !query.all { it in '0'..'9' }) return false
        return when (unit) {
            AmountUnit.RIAL -> query == rial.toString()
            AmountUnit.TOMAN -> rial % 10L == 0L && query == (rial / 10L).toString()
        }
    }

    /** Matches memo, account code, account name (includes party names) and amount in [unit]. Empty query matches all. */
    fun matchesDoc(doc: CommittedDoc, query: String, unit: AmountUnit, accountName: (String) -> String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return true
        val memo = doc.entry.memo
        if (memo != null && normalize(memo).contains(q)) return true
        return doc.entry.lines.any { line ->
            normalize(line.accountCode).contains(q) ||
                normalize(accountName(line.accountCode)).contains(q) ||
                amountMatches(q, line.amount.rial, unit)
        }
    }

    fun matchesAccount(account: Account, query: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return true
        return normalize(account.code).contains(q) || normalize(account.nameFa).contains(q)
    }
}

/**
 * Journal rows: newest first (date, then document number), filtered by [query].
 * [links] must be the unfiltered ledger links ([LedgerRepository.reversalLinks]), not derived from [docs]: the
 * other half of a pair can sit outside the date range or fiscal year being shown. A reversal shows the number of
 * the document it reverses; an original shows [JournalRow.isReversed] when any reversal links to it.
 */
fun buildJournalRows(
    docs: List<CommittedDoc>,
    query: String,
    links: List<ReversalLink>,
    unit: AmountUnit,
    accountName: (String) -> String,
): List<JournalRow> {
    val originalNumberByReversalId = links.associate { it.reversalId to it.originalNumber }
    val reversedIds = links.mapTo(HashSet()) { it.originalId }
    return docs
        .filter { LedgerSearch.matchesDoc(it, query, unit, accountName) }
        .sortedWith(compareByDescending<CommittedDoc> { it.entry.date }.thenByDescending { it.docNumber })
        .map { doc ->
            val originalId = doc.entry.reversalOfId
            JournalRow(
                id = doc.id,
                date = doc.entry.date,
                docNumber = doc.docNumber,
                memo = doc.entry.memo,
                totalRial = doc.entry.debitTotal.rial,
                reversalOfNumber = if (originalId != null) originalNumberByReversalId[doc.id] else null,
                isReversal = originalId != null,
                isReversed = doc.id in reversedIds,
            )
        }
}

/**
 * Account picker rows. Without a query: the chart as a tree (code order, depth = nesting). With a query:
 * a flat list of matches by code or Persian name. [hideZero] keeps accounts with a non-zero net and every
 * ancestor of one, so a header never hides a visible child.
 */
fun buildAccountRows(
    accounts: List<Account>,
    netByCode: Map<String, Long>,
    query: String,
    hideZero: Boolean,
): List<AccountRow> {
    val ordered: List<Pair<Account, Int>> = if (LedgerSearch.normalize(query).isEmpty()) {
        chartTree(accounts)
    } else {
        accounts.sortedBy { it.code }.filter { LedgerSearch.matchesAccount(it, query) }.map { it to 0 }
    }
    val parentOf = accounts.associate { it.code to it.parent }
    val visible = mutableSetOf<String>()
    accounts.forEach { account ->
        if ((netByCode[account.code] ?: 0L) != 0L) {
            var code: String? = account.code
            while (code != null && visible.add(code)) {
                code = parentOf[code]
            }
        }
    }
    return ordered
        .map { (account, depth) ->
            AccountRow(
                code = account.code,
                nameFa = account.nameFa,
                level = account.level,
                depth = depth,
                netRial = netByCode[account.code] ?: 0L,
            )
        }
        .filter { !hideZero || it.code in visible }
}

private fun chartTree(accounts: List<Account>): List<Pair<Account, Int>> {
    val sorted = accounts.sortedBy { it.code }
    val childrenOf: Map<String, List<Account>> = sorted
        .filter { it.parent != null }
        .groupBy { checkNotNull(it.parent) }
    val out = mutableListOf<Pair<Account, Int>>()
    fun visit(account: Account, depth: Int) {
        out += account to depth
        childrenOf[account.code].orEmpty().forEach { visit(it, depth + 1) }
    }
    sorted.filter { it.parent == null }.forEach { visit(it, 0) }
    return out
}
