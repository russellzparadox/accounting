package com.example.accounting.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.time.LocalDate

/** Template ids, identical to docs/ACCOUNTING_RULES.md section 8. REVERSAL is not an intent type (see PostingEngine.reverse). */
@Serializable
enum class TemplateId {
    PURCHASE_CASH, PURCHASE_CREDIT,
    SALE_CASH, SALE_CREDIT,
    EXPENSE_PAID, EXPENSE_PAYABLE,
    RECEIPT_FROM_PERSON, PAYMENT_TO_PERSON,
    BANK_TRANSFER,
    OWNER_CAPITAL, OWNER_WITHDRAWAL,
    PURCHASE_RETURN, SALE_RETURN,
    REVERSAL,
}

@Serializable
enum class PaidWith { CASH, BANK }

/** Expense categories -> 6100..6800 (ACCOUNTING_RULES section 7). */
@Serializable
enum class ExpenseCategory(val accountCode: String) {
    RENT("6100"),
    FREIGHT("6200"),
    PHONE("6300"),
    SALARY("6400"),
    UTILITIES("6500"),
    REPAIR("6600"),
    MISC("6700"),
    BANK_FEE("6800"),
}

@Serializable
data class Clarification(val question: String)

/**
 * LLM output: structure only. Money math and Dr/Cr choice happen in PostingEngine.
 * [confidence] is an integer percent 0..100 (no floating point in the domain).
 */
@Serializable
data class TransactionIntent(
    val type: TemplateId,
    @SerialName("amount_rial") val amountRial: Long,
    @Serializable(with = IsoDateSerializer::class) val date: LocalDate,
    @SerialName("paid_with") val paidWith: PaidWith? = null,
    @SerialName("bank_account") val bankAccount: String? = null,
    @SerialName("party_name") val partyName: String? = null,
    val category: ExpenseCategory? = null,
    val memo: String? = null,
    val confidence: Int,
    @SerialName("needs_clarification") val needsClarification: Clarification? = null,
) {
    init {
        require(amountRial > 0) { "amount_rial must be > 0" }
        require(confidence in 0..100) { "confidence must be 0..100" }
    }

    companion object {
        fun fromJson(text: String): TransactionIntent = domainJson.decodeFromString(text)
    }
}
