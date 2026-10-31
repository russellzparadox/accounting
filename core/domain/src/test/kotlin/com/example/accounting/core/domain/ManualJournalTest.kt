package com.example.accounting.core.domain

import com.example.accounting.core.domain.Side.CREDIT
import com.example.accounting.core.domain.Side.DEBIT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ManualJournalTest {
    /** Small chart: 1000 گروه; 1100 معین; 1200 کل with معین 1201; 1300 کل with معین 1301; 9000 کل without children. */
    private val accounts = listOf(
        account("1000", AccountLevel.GROUP, null),
        account("1100", AccountLevel.DETAIL, "1000"),
        account("1200", AccountLevel.GENERAL, "1000"),
        account("1201", AccountLevel.DETAIL, "1200"),
        account("1300", AccountLevel.GENERAL, "1000"),
        account("1301", AccountLevel.DETAIL, "1300"),
        account("5100", AccountLevel.DETAIL, null),
        account("9000", AccountLevel.GENERAL, null),
    )

    private fun account(code: String, level: AccountLevel, parent: String?) = Account(
        code = code,
        nameFa = "حساب $code",
        level = level,
        parent = parent,
        type = AccountType.ASSET,
        normalBalance = DEBIT,
    )

    private fun line(code: String, side: Side, rial: Long?) = ManualLine(ManualAccount.Existing(code), side, rial)

    private fun balanced() = listOf(line("1100", DEBIT, 5_000_000L), line("5100", CREDIT, 5_000_000L))

    @Test
    fun balancedDocumentBuildsAsManual() {
        val entry = ManualJournal.build("M1", testToday, "  خرید نقدی  ", balanced(), accounts)
        assertEquals(TemplateId.MANUAL, entry.templateId)
        assertEquals("خرید نقدی", entry.memo)
        assertEquals(Money(5_000_000L), entry.debitTotal)
        assertEquals(entry.debitTotal, entry.creditTotal)
        assertEquals(null, entry.reversalOfId)
    }

    @Test
    fun blankMemoBecomesNull() {
        val entry = ManualJournal.build("M2", testToday, "   ", balanced(), accounts)
        assertNull(entry.memo)
    }

    @Test
    fun unbalancedDocumentIsNotValidAndCannotBeBuilt() {
        val lines = listOf(line("1100", DEBIT, 5_000_000L), line("5100", CREDIT, 4_000_000L))
        val check = ManualJournal.check(lines, accounts)
        assertTrue(ManualIssue.UNBALANCED in check.issues)
        assertEquals(1_000_000L, check.differenceRial)
        assertFailsWith<DomainException> { ManualJournal.build("M3", testToday, null, lines, accounts) }
    }

    @Test
    fun oneSideOnlyIsUnbalanced() {
        val lines = listOf(line("1100", DEBIT, 10L), line("5100", DEBIT, 10L))
        assertTrue(ManualIssue.UNBALANCED in ManualJournal.check(lines, accounts).issues)
    }

    @Test
    fun emptyAmountsAreInvalidAndUnbalanced() {
        val lines = listOf(line("1100", DEBIT, null), line("5100", CREDIT, null))
        val issues = ManualJournal.check(lines, accounts).issues
        assertTrue(ManualIssue.AMOUNT_INVALID in issues)
        assertTrue(ManualIssue.UNBALANCED in issues)
    }

    @Test
    fun zeroAndOverLimitAmountsAreInvalid() {
        val zero = listOf(line("1100", DEBIT, 0L), line("5100", CREDIT, 0L))
        assertTrue(ManualIssue.AMOUNT_INVALID in ManualJournal.check(zero, accounts).issues)
        val over = listOf(
            line("1100", DEBIT, ManualJournal.MAX_LINE_RIAL + 1),
            line("5100", CREDIT, ManualJournal.MAX_LINE_RIAL + 1),
        )
        assertTrue(ManualIssue.AMOUNT_INVALID in ManualJournal.check(over, accounts).issues)
    }

    @Test
    fun needsAtLeastTwoLines() {
        val one = listOf(line("1100", DEBIT, 10L))
        assertTrue(ManualIssue.TOO_FEW_LINES in ManualJournal.check(one, accounts).issues)
        assertTrue(ManualIssue.TOO_FEW_LINES in ManualJournal.check(emptyList(), accounts).issues)
    }

    @Test
    fun atMostFiftyLines() {
        val many = List(ManualJournal.MAX_LINES + 1) { line("1100", if (it % 2 == 0) DEBIT else CREDIT, 10L) }
        assertTrue(ManualIssue.TOO_MANY_LINES in ManualJournal.check(many, accounts).issues)
    }

    @Test
    fun missingAccountIsReported() {
        val lines = listOf(ManualLine(ManualAccount.Missing, DEBIT, 10L), line("5100", CREDIT, 10L))
        assertTrue(ManualIssue.ACCOUNT_MISSING in ManualJournal.check(lines, accounts).issues)
    }

    @Test
    fun groupAndControlWithChildrenAreNotPostable() {
        listOf("1000", "1200", "7777").forEach { code ->
            val lines = listOf(line(code, DEBIT, 10L), line("5100", CREDIT, 10L))
            assertTrue(ManualIssue.ACCOUNT_NOT_POSTABLE in ManualJournal.check(lines, accounts).issues, code)
        }
    }

    @Test
    fun detailAndChildlessControlArePostable() {
        val lines = listOf(line("1201", DEBIT, 10L), line("9000", CREDIT, 10L))
        assertFalse(ManualJournal.check(lines, accounts).issues.any { it == ManualIssue.ACCOUNT_NOT_POSTABLE })
        assertTrue(ManualJournal.check(lines, accounts).isValid)
    }

    @Test
    fun newPartyNeedsNameAndCountsInTotals() {
        val blank = listOf(ManualLine(ManualAccount.NewParty(PartyKind.CUSTOMER, "  "), DEBIT, 10L), line("5100", CREDIT, 10L))
        assertTrue(ManualIssue.PARTY_NAME_MISSING in ManualJournal.check(blank, accounts).issues)

        val named = listOf(
            ManualLine(ManualAccount.NewParty(PartyKind.CUSTOMER, "علی رضایی"), DEBIT, 10L),
            line("5100", CREDIT, 10L),
        )
        val check = ManualJournal.check(named, accounts)
        assertTrue(check.isValid)
        assertEquals(10L, check.debitRial)
    }

    @Test
    fun buildRefusesUnresolvedNewParty() {
        val lines = listOf(
            ManualLine(ManualAccount.NewParty(PartyKind.SUPPLIER, "تأمین"), CREDIT, 10L),
            line("5100", DEBIT, 10L),
        )
        assertFailsWith<DomainException> { ManualJournal.build("M4", testToday, null, lines, accounts) }
    }

    @Test
    fun tomanToRialIsExactAndBounded() {
        assertEquals(10L, ManualJournal.tomanToRial(1L))
        assertEquals(5_000_000L, ManualJournal.tomanToRial(500_000L))
        assertNull(ManualJournal.tomanToRial(0L))
        assertNull(ManualJournal.tomanToRial(-5L))
        assertEquals(ManualJournal.MAX_LINE_RIAL, ManualJournal.tomanToRial(ManualJournal.MAX_LINE_RIAL / 10L))
        assertNull(ManualJournal.tomanToRial(ManualJournal.MAX_LINE_RIAL / 10L + 1L))
    }

    @Test
    fun rialToWholeTomanOnlyWhenDivisibleByTen() {
        assertEquals(500_000L, ManualJournal.rialToWholeToman(5_000_000L))
        assertNull(ManualJournal.rialToWholeToman(5_000_005L))
        assertNull(ManualJournal.rialToWholeToman(0L))
    }

    @Test
    fun partyDisplayNameNormalizesSpaces() {
        assertEquals("علی رضایی", ManualJournal.partyDisplayName("  علی   رضایی "))
        assertNull(ManualJournal.partyDisplayName(" \t "))
    }
}
