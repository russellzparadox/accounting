package com.example.accounting.core.domain

import java.time.LocalDate
import java.util.UUID

/** Control accounts whose معین (detail) accounts are created per party, and the code range they may use. */
enum class PartyKind(val controlCode: String, val codes: IntRange) {
    CUSTOMER("1300", 1301..1349),
    SUPPLIER("2100", 2101..2199),
}

/** Find-or-create the معین for a party. Persistence implements this (T04). */
interface PartyAccountResolver {
    /**
     * [key] comes from [partyKey]; [displayName] is the trimmed original spelling.
     * Must return a 4-digit code inside [PartyKind.codes].
     */
    fun resolve(kind: PartyKind, key: String, displayName: String): String
}

/** Matching key for party names: Persian ي/ك folded to ی/ک, ZWNJ and all whitespace removed. */
fun partyKey(name: String): String = name
    .replace('ي', 'ی')
    .replace('ك', 'ک')
    .replace("‌", "")
    .replace(Regex("\\s+"), "")

private const val CASH_ACCOUNT = "1100"
private const val BANK_CONTROL = "1200"
private const val SALES = "4100"
private const val SALES_RETURNS = "4200"
private const val PURCHASES = "5100"
private const val PURCHASE_RETURNS = "5200"
private const val OWNER_CAPITAL = "3100"
private const val OWNER_DRAWINGS = "3200"

/**
 * Builds balanced journal entries from [TransactionIntent]s (ACCOUNTING_RULES section 8).
 * Purely deterministic. Nothing is persisted; the caller stores the returned entry.
 * Each intent field combination is validated; invalid input throws [DomainException].
 * Callers must check [TransactionIntent.needsClarification] first. The engine rejects such intents.
 */
class PostingEngine(
    private val chart: ChartOfAccounts,
    private val parties: PartyAccountResolver,
    private val fiscalYear: FiscalYear,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    fun post(intent: TransactionIntent): JournalEntry {
        intent.needsClarification?.let { throw DomainException("needs clarification: ${it.question}") }
        requireInFiscalYear(intent.date)
        val a = Money(intent.amountRial)
        val lines = when (intent.type) {
            TemplateId.PURCHASE_CASH -> {
                intent.expect(needPaid = true, needParty = false, needCategory = false)
                two(PURCHASES, moneyLeaf(intent), a)
            }
            TemplateId.PURCHASE_CREDIT -> {
                intent.expect(needPaid = false, needParty = true, needCategory = false)
                two(PURCHASES, partyLeaf(PartyKind.SUPPLIER, intent.partyName), a)
            }
            TemplateId.SALE_CASH -> {
                intent.expect(needPaid = true, needParty = false, needCategory = false)
                two(moneyLeaf(intent), SALES, a)
            }
            TemplateId.SALE_CREDIT -> {
                intent.expect(needPaid = false, needParty = true, needCategory = false)
                two(partyLeaf(PartyKind.CUSTOMER, intent.partyName), SALES, a)
            }
            TemplateId.EXPENSE_PAID -> {
                intent.expect(needPaid = true, needParty = false, needCategory = true)
                two(expenseLeaf(intent), moneyLeaf(intent), a)
            }
            TemplateId.EXPENSE_PAYABLE -> {
                intent.expect(needPaid = false, needParty = true, needCategory = true)
                two(expenseLeaf(intent), partyLeaf(PartyKind.SUPPLIER, intent.partyName), a)
            }
            TemplateId.RECEIPT_FROM_PERSON -> {
                intent.expect(needPaid = true, needParty = true, needCategory = false)
                two(moneyLeaf(intent), partyLeaf(PartyKind.CUSTOMER, intent.partyName), a)
            }
            TemplateId.PAYMENT_TO_PERSON -> {
                intent.expect(needPaid = true, needParty = true, needCategory = false)
                two(partyLeaf(PartyKind.SUPPLIER, intent.partyName), moneyLeaf(intent), a)
            }
            TemplateId.BANK_TRANSFER -> {
                // paid_with = source account. Destination is the other side (cash <-> bank).
                intent.expect(needPaid = true, needParty = false, needCategory = false)
                val source = moneyLeaf(intent)
                val destination = if (intent.paidWith == PaidWith.CASH) bankLeaf(intent.bankAccount) else CASH_ACCOUNT
                two(destination, source, a)
            }
            TemplateId.OWNER_CAPITAL -> {
                intent.expect(needPaid = true, needParty = false, needCategory = false)
                two(moneyLeaf(intent), OWNER_CAPITAL, a)
            }
            TemplateId.OWNER_WITHDRAWAL -> {
                intent.expect(needPaid = true, needParty = false, needCategory = false)
                two(OWNER_DRAWINGS, moneyLeaf(intent), a)
            }
            TemplateId.PURCHASE_RETURN -> {
                if (intent.paidWith == null) {
                    intent.expect(needPaid = false, needParty = true, needCategory = false)
                    two(partyLeaf(PartyKind.SUPPLIER, intent.partyName), PURCHASE_RETURNS, a)
                } else {
                    intent.expect(needPaid = true, needParty = false, needCategory = false)
                    two(moneyLeaf(intent), PURCHASE_RETURNS, a)
                }
            }
            TemplateId.SALE_RETURN -> {
                if (intent.paidWith == null) {
                    intent.expect(needPaid = false, needParty = true, needCategory = false)
                    two(SALES_RETURNS, partyLeaf(PartyKind.CUSTOMER, intent.partyName), a)
                } else {
                    intent.expect(needPaid = true, needParty = false, needCategory = false)
                    two(SALES_RETURNS, moneyLeaf(intent), a)
                }
            }
            TemplateId.REVERSAL -> throw DomainException("REVERSAL is not an intent; use PostingEngine.reverse()")
        }
        return JournalEntry(
            id = idGenerator(),
            date = intent.date,
            templateId = intent.type,
            memo = intent.memo,
            lines = lines,
        )
    }

    /** New entry that mirrors [original] line by line with Dr/Cr swapped. Original is not modified. */
    fun reverse(original: JournalEntry, date: LocalDate, memo: String? = null): JournalEntry {
        requireInFiscalYear(date)
        return JournalEntry(
            id = idGenerator(),
            date = date,
            templateId = TemplateId.REVERSAL,
            memo = memo,
            lines = original.lines.map { it.flipped() },
            reversalOfId = original.id,
        )
    }

    private fun requireInFiscalYear(date: LocalDate) {
        if (date !in fiscalYear) {
            throw DomainException("date $date outside fiscal year ${fiscalYear.start}..${fiscalYear.end}")
        }
    }

    /** Two-line template body. Codes are already validated by their leaf helpers or by the chart test. */
    private fun two(debit: String, credit: String, amount: Money): List<JournalLine> =
        listOf(JournalLine(debit, Side.DEBIT, amount), JournalLine(credit, Side.CREDIT, amount))

    /** Validates which optional fields this template uses. paid_with and bank_account rules included. */
    private fun TransactionIntent.expect(needPaid: Boolean, needParty: Boolean, needCategory: Boolean) {
        fun fail(message: String): Nothing = throw DomainException("$type: $message")
        if (needPaid != (paidWith != null)) fail(if (needPaid) "paid_with required" else "paid_with not allowed")
        if (needParty != (partyName != null)) fail(if (needParty) "party_name required" else "party_name not allowed")
        if (needCategory != (category != null)) fail(if (needCategory) "category required" else "category not allowed")
        if (bankAccount != null && paidWith != PaidWith.BANK && type != TemplateId.BANK_TRANSFER) {
            fail("bank_account needs paid_with BANK")
        }
    }

    /** The cash or bank leg selected by paid_with. */
    private fun moneyLeaf(intent: TransactionIntent): String = when (intent.paidWith) {
        PaidWith.CASH -> {
            chart.requirePostable(CASH_ACCOUNT)
            CASH_ACCOUNT
        }
        PaidWith.BANK -> bankLeaf(intent.bankAccount)
        null -> throw DomainException("${intent.type}: paid_with required")
    }

    /** Bank leg must be a معین under 1200. If omitted and there is exactly one, that one is used. */
    private fun bankLeaf(bankAccount: String?): String {
        val banks = chart.childrenOf(BANK_CONTROL).map { it.code }
        val code = bankAccount ?: banks.singleOrNull()
            ?: throw DomainException("bank_account required (${banks.size} bank accounts)")
        if (code !in banks) throw DomainException("bank_account $code is not a bank detail account")
        return code
    }

    private fun expenseLeaf(intent: TransactionIntent): String {
        val code = requireNotNull(intent.category) { "category required" }.accountCode
        chart.requirePostable(code)
        return code
    }

    private fun partyLeaf(kind: PartyKind, name: String?): String {
        val display = name?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }
            ?: throw DomainException("party_name required")
        val code = parties.resolve(kind, partyKey(display), display)
        val number = code.takeIf { it.length == 4 && it.all { c -> c in '0'..'9' } }?.toInt()
        if (number == null || number !in kind.codes) {
            throw DomainException("party account $code outside ${kind.codes.first}..${kind.codes.last}")
        }
        return code
    }
}
