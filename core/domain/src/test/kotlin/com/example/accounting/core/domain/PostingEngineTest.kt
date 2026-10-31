package com.example.accounting.core.domain

import com.example.accounting.core.domain.PaidWith.BANK
import com.example.accounting.core.domain.PaidWith.CASH
import com.example.accounting.core.domain.Side.CREDIT
import com.example.accounting.core.domain.Side.DEBIT
import com.example.accounting.core.domain.TemplateId.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PostingEngineTest {
    private val customer = "علي رضایی"   // Arabic yeh on purpose, same key as the Persian spelling
    private val supplier = "تأمین‌کننده نمونه"

    @Test
    fun everyTemplateRowFromAccountingRules() {
        val cases = listOf(
            intent(PURCHASE_CASH, paidWith = CASH) to listOf("5100" to DEBIT, "1100" to CREDIT),
            intent(PURCHASE_CASH, paidWith = BANK) to listOf("5100" to DEBIT, "1201" to CREDIT),
            intent(PURCHASE_CREDIT, party = supplier) to listOf("5100" to DEBIT, "2101" to CREDIT),
            intent(SALE_CASH, paidWith = CASH) to listOf("1100" to DEBIT, "4100" to CREDIT),
            intent(SALE_CASH, paidWith = BANK) to listOf("1201" to DEBIT, "4100" to CREDIT),
            intent(SALE_CREDIT, party = customer) to listOf("1301" to DEBIT, "4100" to CREDIT),
            intent(EXPENSE_PAID, paidWith = CASH, category = ExpenseCategory.RENT) to listOf("6100" to DEBIT, "1100" to CREDIT),
            intent(EXPENSE_PAID, paidWith = BANK, category = ExpenseCategory.BANK_FEE) to listOf("6800" to DEBIT, "1201" to CREDIT),
            intent(EXPENSE_PAYABLE, party = supplier, category = ExpenseCategory.PHONE) to listOf("6300" to DEBIT, "2101" to CREDIT),
            intent(RECEIPT_FROM_PERSON, paidWith = CASH, party = customer) to listOf("1100" to DEBIT, "1301" to CREDIT),
            intent(RECEIPT_FROM_PERSON, paidWith = BANK, party = customer) to listOf("1201" to DEBIT, "1301" to CREDIT),
            intent(PAYMENT_TO_PERSON, paidWith = CASH, party = supplier) to listOf("2101" to DEBIT, "1100" to CREDIT),
            intent(PAYMENT_TO_PERSON, paidWith = BANK, party = supplier) to listOf("2101" to DEBIT, "1201" to CREDIT),
            intent(BANK_TRANSFER, paidWith = CASH) to listOf("1201" to DEBIT, "1100" to CREDIT),
            intent(BANK_TRANSFER, paidWith = BANK) to listOf("1100" to DEBIT, "1201" to CREDIT),
            intent(OWNER_CAPITAL, paidWith = CASH) to listOf("1100" to DEBIT, "3100" to CREDIT),
            intent(OWNER_CAPITAL, paidWith = BANK) to listOf("1201" to DEBIT, "3100" to CREDIT),
            intent(OWNER_WITHDRAWAL, paidWith = CASH) to listOf("3200" to DEBIT, "1100" to CREDIT),
            intent(OWNER_WITHDRAWAL, paidWith = BANK) to listOf("3200" to DEBIT, "1201" to CREDIT),
            intent(PURCHASE_RETURN, party = supplier) to listOf("2101" to DEBIT, "5200" to CREDIT),
            intent(PURCHASE_RETURN, paidWith = CASH) to listOf("1100" to DEBIT, "5200" to CREDIT),
            intent(PURCHASE_RETURN, paidWith = BANK) to listOf("1201" to DEBIT, "5200" to CREDIT),
            intent(SALE_RETURN, party = customer) to listOf("4200" to DEBIT, "1301" to CREDIT),
            intent(SALE_RETURN, paidWith = CASH) to listOf("4200" to DEBIT, "1100" to CREDIT),
            intent(SALE_RETURN, paidWith = BANK) to listOf("4200" to DEBIT, "1201" to CREDIT),
        )
        cases.forEach { (intent, expected) ->
            val entry = testEngine().post(intent)
            assertEquals(expected, entry.sides(), "${intent.type} ${intent.paidWith}")
            assertEquals(Money(intent.amountRial), entry.debitTotal)
            assertEquals(entry.debitTotal, entry.creditTotal)
            assertEquals(intent.type, entry.templateId)
            assertEquals(null, entry.reversalOfId)
        }
    }

    @Test
    fun templateIdsAreTheFourteenFromRulesPlusManual() {
        // 13 intent ids + REVERSAL (rules section 8) + MANUAL (free-form document, never an intent).
        assertEquals(15, TemplateId.entries.size)
        assertTrue(MANUAL in TemplateId.entries)
    }

    @Test
    fun manualIsRejectedAsIntent() {
        val manual = intent(MANUAL, paidWith = CASH)
        assertFailsWith<DomainException> { testEngine().post(manual) }
    }

    @Test
    fun invalidFieldCombinationsAreRejected() {
        val bad = listOf(
            intent(PURCHASE_CASH),                                              // paid_with missing
            intent(PURCHASE_CASH, paidWith = CASH, party = customer),           // party not allowed
            intent(PURCHASE_CREDIT, paidWith = CASH, party = supplier),         // paid_with not allowed
            intent(PURCHASE_CREDIT),                                            // party missing
            intent(EXPENSE_PAID, paidWith = CASH),                              // category missing
            intent(SALE_CASH, paidWith = CASH, category = ExpenseCategory.RENT), // category not allowed
            intent(PURCHASE_CASH, paidWith = CASH, bank = "1201"),              // bank_account with CASH
            intent(PURCHASE_CASH, paidWith = BANK, bank = "1100"),              // not a 1200 detail account
            intent(PURCHASE_CASH, paidWith = BANK, bank = "1300"),
            intent(REVERSAL),                                                   // use reverse()
            intent(PURCHASE_CASH, paidWith = CASH, needsClarification = Clarification("کدام صندوق؟")),
        )
        bad.forEach { assertFailsWith<DomainException>("$it") { testEngine().post(it) } }
    }

    @Test
    fun entryOutsideFiscalYearIsRejected() {
        val engine = testEngine()
        assertFailsWith<DomainException> {
            engine.post(intent(SALE_CASH, paidWith = CASH).copy(date = java.time.LocalDate.of(2025, 12, 31)))
        }
    }

    @Test
    fun partiesFindOrCreateByNormalizedKey() {
        val parties = FakeParties()
        val engine = testEngine(parties)
        val first = engine.post(intent(SALE_CREDIT, party = customer))
        val second = engine.post(intent(SALE_CREDIT, party = "علی  رضایی"))
        assertEquals(first.sides(), second.sides())
        assertEquals(1, parties.created.size)
    }

    @Test
    fun partyKeyFoldsArabicLettersAndDropsZwnjAndSpaces() {
        assertEquals(partyKey("علی رضایی"), partyKey("علي  رضایی"))
        assertEquals("میخرم", partyKey("می‌خرم"))
        assertEquals("کتاب", partyKey("كتاب"))
    }

    @Test
    fun resolverCodeOutsidePartyRangeIsRejected() {
        val bad = object : PartyAccountResolver {
            override fun resolve(kind: PartyKind, key: String, displayName: String) = "1350"
            override fun find(kind: PartyKind, key: String): String? = null
        }
        assertFailsWith<DomainException> { testEngine(bad).post(intent(SALE_CREDIT, party = customer)) }
    }

    @Test
    fun reversalIsNewEntryWithSwappedSides() {
        val engine = testEngine()
        val original = engine.post(intent(PURCHASE_CASH, paidWith = CASH, memo = "خرید"))
        val reversal = engine.reverse(original, testToday, "برگشت")

        assertNotEquals(original.id, reversal.id)
        assertEquals(original.id, reversal.reversalOfId)
        assertEquals(REVERSAL, reversal.templateId)
        assertEquals(original.lines.map { it.accountCode }, reversal.lines.map { it.accountCode })
        assertEquals(original.lines.map { it.side.opposite() }, reversal.lines.map { it.side })
        assertEquals(original.debitTotal, reversal.creditTotal)
        assertEquals(DEBIT, original.lines[0].side) // original untouched
        assertTrue(original.reversalOfId == null)
    }

    @Test
    fun reversalDateMustBeInFiscalYear() {
        val engine = testEngine()
        val original = engine.post(intent(OWNER_CAPITAL, paidWith = CASH))
        assertFailsWith<DomainException> { engine.reverse(original, java.time.LocalDate.of(2027, 4, 1)) }
    }
}
