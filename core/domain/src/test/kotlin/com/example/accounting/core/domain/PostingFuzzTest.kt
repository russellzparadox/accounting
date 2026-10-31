package com.example.accounting.core.domain

import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostingFuzzTest {
    private val chart = ChartOfAccounts.standard()
    private val names = listOf("علی رضایی", "علي رضایی", "شرکت  نور", "میوه‌فروشی", "احمد")
    private val returns = setOf(TemplateId.PURCHASE_RETURN, TemplateId.SALE_RETURN)
    /** Templates with no cash/bank leg (paid_with must be null). */
    private val creditTemplates = setOf(TemplateId.PURCHASE_CREDIT, TemplateId.SALE_CREDIT, TemplateId.EXPENSE_PAYABLE)
    /** Templates whose party_name is always required. */
    private val alwaysPartyTemplates = creditTemplates + setOf(TemplateId.RECEIPT_FROM_PERSON, TemplateId.PAYMENT_TO_PERSON)
    private val categoryTemplates = setOf(TemplateId.EXPENSE_PAID, TemplateId.EXPENSE_PAYABLE)

    private fun randomValid(rnd: Random): TransactionIntent {
        val type = TemplateId.entries.filter { it != TemplateId.REVERSAL }.random(rnd)
        val paid = if (rnd.nextBoolean()) PaidWith.CASH else PaidWith.BANK
        val paidWith = when (type) {
            in creditTemplates -> null
            in returns -> if (rnd.nextBoolean()) null else paid
            else -> paid
        }
        val party = if (type in alwaysPartyTemplates || (type in returns && paidWith == null)) {
            names.random(rnd)
        } else {
            null
        }
        val category = if (type in categoryTemplates) ExpenseCategory.entries.random(rnd) else null
        return TransactionIntent(
            type = type,
            amountRial = rnd.nextLong(1, 10_000_000_000_000L),
            date = LocalDate.of(2026, 3, 21).plusDays(rnd.nextLong(0, 365)),
            paidWith = paidWith,
            bankAccount = null,
            partyName = party,
            category = category,
            memo = null,
            confidence = rnd.nextInt(0, 101),
        )
    }

    @Test
    fun randomValidIntentsPostBalancedEntriesAndReverseCleanly() {
        val engine = testEngine()
        val rnd = Random(20261031)
        repeat(5_000) {
            val intent = randomValid(rnd)
            val entry = engine.post(intent)

            assertEquals(entry.debitTotal, entry.creditTotal, "$intent")
            assertEquals(Money(intent.amountRial), entry.debitTotal, "$intent")
            entry.lines.forEach { line ->
                assertTrue(line.amount > Money.ZERO)
                val code = line.accountCode.toInt()
                val known = line.accountCode in chart.codes ||
                    code in PartyKind.CUSTOMER.codes || code in PartyKind.SUPPLIER.codes
                assertTrue(known, "unknown code ${line.accountCode} for $intent")
            }

            val reversal = engine.reverse(entry, testToday)
            assertEquals(entry.debitTotal, reversal.creditTotal)
            assertEquals(entry.creditTotal, reversal.debitTotal)
        }
    }
}
