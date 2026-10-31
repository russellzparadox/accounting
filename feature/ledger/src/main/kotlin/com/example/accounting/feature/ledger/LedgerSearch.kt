package com.example.accounting.feature.ledger

import com.example.accounting.core.data.repo.CommittedDoc
import com.example.accounting.core.domain.Account
import com.example.accounting.core.domain.AccountLevel
import com.example.accounting.core.domain.partyKey
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
     * A digits-only query matches a line amount when it equals the rial value or the whole-toman value
     * (rial / 10, only when divisible). Text queries never match amounts.
     */
    fun amountMatches(query: String, rial: Long): Boolean {
        if (query.isEmpty() || !query.all { it in '0'..'9' }) return false
        val toman = if (rial % 10L == 0L) (rial / 10L).toString() else null
        return query == rial.toString() || query == toman
    }

    /** Matches memo, account code, account name (includes party names) and amount. Empty query matches all. */
    fun matchesDoc(doc: CommittedDoc, query: String, accountName: (String) -> String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return true
        val memo = doc.entry.memo
        if (memo != null && normalize(memo).contains(q)) return true
        return doc.entry.lines.any { line ->
            normalize(line.accountCode).contains(q) ||
                normalize(accountName(line.accountCode)).contains(q) ||
                amountMatches(q, line.amount.rial)
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
 * A reversal shows the number of the document it reverses. An original shows [JournalRow.isReversed] when a
 * reversal exists in [docs]. Cross fiscal-year links are not visible here (documented limit).
 */
fun buildJournalRows(
    docs: List<CommittedDoc>,
    query: String,
    accountName: (String) -> String,
): List<JournalRow> {
    val numberById = docs.associate { it.id to it.docNumber }
    val reversedIds = docs.mapNotNullTo(HashSet()) { it.entry.reversalOfId }
    return docs
        .filter { LedgerSearch.matchesDoc(it, query, accountName) }
        .sortedWith(compareByDescending<CommittedDoc> { it.entry.date }.thenByDescending { it.docNumber })
        .map { doc ->
            val originalId = doc.entry.reversalOfId
            JournalRow(
                id = doc.id,
                date = doc.entry.date,
                docNumber = doc.docNumber,
                memo = doc.entry.memo,
                totalRial = doc.entry.debitTotal.rial,
                reversalOfNumber = originalId?.let { numberById[it] },
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
