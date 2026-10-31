package com.example.accounting.core.domain

import java.time.LocalDate

val testFiscalYear = FiscalYear(LocalDate.of(2026, 3, 21), LocalDate.of(2027, 3, 20))
val testToday: LocalDate = LocalDate.of(2026, 10, 31)

class FakeParties : PartyAccountResolver {
    val created = linkedMapOf<Pair<PartyKind, String>, String>()

    override fun resolve(kind: PartyKind, key: String, displayName: String): String =
        created.getOrPut(kind to key) { (kind.codes.first + created.keys.count { it.first == kind }).toString() }
}

fun testEngine(parties: PartyAccountResolver = FakeParties()): PostingEngine {
    var n = 0
    return PostingEngine(ChartOfAccounts.standard(), parties, testFiscalYear) { "E${++n}" }
}

fun intent(
    type: TemplateId,
    amount: Long = 5_000_000L,
    paidWith: PaidWith? = null,
    party: String? = null,
    category: ExpenseCategory? = null,
    bank: String? = null,
    memo: String? = null,
    needsClarification: Clarification? = null,
) = TransactionIntent(
    type = type,
    amountRial = amount,
    date = testToday,
    paidWith = paidWith,
    bankAccount = bank,
    partyName = party,
    category = category,
    memo = memo,
    confidence = 90,
    needsClarification = needsClarification,
)

fun JournalEntry.sides(): List<Pair<String, Side>> = lines.map { it.accountCode to it.side }
