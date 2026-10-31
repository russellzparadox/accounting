package com.example.accounting.feature.ledger

import com.example.accounting.core.data.repo.CommittedDoc
import com.example.accounting.core.data.repo.ReversalLink
import com.example.accounting.core.domain.Account
import com.example.accounting.core.domain.AccountLevel
import com.example.accounting.core.domain.AccountType
import com.example.accounting.core.domain.JournalEntry
import com.example.accounting.core.domain.JournalLine
import com.example.accounting.core.domain.Money
import com.example.accounting.core.domain.Side
import com.example.accounting.core.domain.TemplateId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure search, row and tree rules. No Android, no database. */
class LedgerSearchTest {
    private val names = mapOf("1100" to "صندوق", "4100" to "فروش", "1302" to "علی احمدی")

    private fun doc(
        id: String,
        number: Int,
        date: LocalDate,
        memo: String?,
        rial: Long,
        reversalOf: String? = null,
    ): CommittedDoc = CommittedDoc(
        id = id,
        fiscalYear = 1404,
        docNumber = number,
        entry = JournalEntry(
            id = id,
            date = date,
            templateId = TemplateId.SALE_CASH,
            memo = memo,
            lines = listOf(
                JournalLine("1100", Side.DEBIT, Money(rial)),
                JournalLine("4100", Side.CREDIT, Money(rial)),
            ),
            reversalOfId = reversalOf,
        ),
    )

    @Test
    fun normalizeFoldsYehKafDigitsSeparatorsAndZwnj() {
        assertEquals(LedgerSearch.normalize("علی"), LedgerSearch.normalize("علي"))
        assertEquals("کتاب", LedgerSearch.normalize("ك‌تاب"))
        assertEquals("500000", LedgerSearch.normalize("۵۰۰٬۰۰۰"))
        assertEquals("500000", LedgerSearch.normalize("500,000"))
    }

    @Test
    fun amountMatchesRialOrWholeTomanOnly() {
        assertTrue(LedgerSearch.amountMatches("500000", 5_000_000L))
        assertTrue(LedgerSearch.amountMatches("5000000", 5_000_000L))
        assertFalse(LedgerSearch.amountMatches("500", 5_000_000L))
        assertFalse(LedgerSearch.amountMatches("1234", 12_345L))
        assertFalse(LedgerSearch.amountMatches("فروش", 5_000_000L))
    }

    @Test
    fun docMatchesMemoAccountNameAndAmount() {
        val d = doc("a", 1, LocalDate.of(2026, 1, 5), "فروش به علي", 5_000_000L)
        val name: (String) -> String = { names[it] ?: it }
        assertTrue(LedgerSearch.matchesDoc(d, "علی", name))
        assertTrue(LedgerSearch.matchesDoc(d, "صندوق", name))
        assertTrue(LedgerSearch.matchesDoc(d, "۵۰۰٬۰۰۰", name))
        assertTrue(LedgerSearch.matchesDoc(d, "", name))
        assertFalse(LedgerSearch.matchesDoc(d, "خرید", name))
    }

    @Test
    fun journalIsNewestFirstAndLinksReversalPair() {
        val a = doc("a", 1, LocalDate.of(2026, 1, 5), "فروش", 1_000_000L)
        val b = doc("b", 2, LocalDate.of(2026, 1, 8), "فروش دوم", 300_000L)
        val r = doc("r", 3, LocalDate.of(2026, 1, 9), "اصلاح", 1_000_000L, reversalOf = "a")
        val links = listOf(ReversalLink(originalId = "a", originalNumber = 1, reversalId = "r", reversalNumber = 3))
        val rows = buildJournalRows(listOf(a, b, r), "", links) { it }
        assertEquals(listOf("r", "b", "a"), rows.map { it.id })
        val reversal = rows.first { it.id == "r" }
        assertTrue(reversal.isReversal)
        assertEquals(1, reversal.reversalOfNumber)
        assertTrue(rows.first { it.id == "a" }.isReversed)
        assertFalse(rows.first { it.id == "b" }.isReversed)
    }

    @Test
    fun reversalLinkHoldsWhenTheOriginalIsOutsideTheListedDocs() {
        val r = doc("r", 3, LocalDate.of(2026, 1, 9), "اصلاح", 1_000_000L, reversalOf = "a")
        val links = listOf(ReversalLink(originalId = "a", originalNumber = 1, reversalId = "r", reversalNumber = 3))
        val reversal = buildJournalRows(listOf(r), "", links) { it }.single()
        assertEquals(1, reversal.reversalOfNumber)
        assertTrue(reversal.isReversal)
        assertFalse(reversal.isReversed)
    }

    @Test
    fun originalOutsideTheListedDocsShowsReversedBadge() {
        val a = doc("a", 1, LocalDate.of(2025, 12, 5), "فروش", 1_000_000L)
        val links = listOf(ReversalLink(originalId = "a", originalNumber = 1, reversalId = "r", reversalNumber = 3))
        val original = buildJournalRows(listOf(a), "", links) { it }.single()
        assertTrue(original.isReversed)
        assertEquals(null, original.reversalOfNumber)
    }

    private fun account(code: String, name: String, level: AccountLevel, parent: String?) = Account(
        code = code,
        nameFa = name,
        level = level,
        parent = parent,
        type = AccountType.ASSET,
        normalBalance = Side.DEBIT,
    )

    private val chart = listOf(
        account("1000", "دارایی", AccountLevel.GROUP, null),
        account("1100", "صندوق", AccountLevel.GENERAL, "1000"),
        account("1101", "صندوق مرکزی", AccountLevel.DETAIL, "1100"),
        account("1102", "صندوق فرعی", AccountLevel.DETAIL, "1100"),
        account("2000", "بدهی", AccountLevel.GROUP, null),
        account("2100", "بستانکاران", AccountLevel.GENERAL, "2000"),
    )

    @Test
    fun accountTreeIsOrderedWithDepth() {
        val rows = buildAccountRows(chart, emptyMap(), "", hideZero = false)
        assertEquals(listOf("1000", "1100", "1101", "1102", "2000", "2100"), rows.map { it.code })
        assertEquals(listOf(0, 1, 2, 2, 0, 1), rows.map { it.depth })
    }

    @Test
    fun hideZeroKeepsNonZeroAccountsAndTheirAncestors() {
        val net = mapOf("1101" to 500_000L)
        val rows = buildAccountRows(chart, net, "", hideZero = true)
        assertEquals(listOf("1000", "1100", "1101"), rows.map { it.code })
    }

    @Test
    fun searchMatchesCodeOrPersianNameAsFlatList() {
        val byName = buildAccountRows(chart, emptyMap(), "مرکزی", hideZero = false)
        assertEquals(listOf("1101"), byName.map { it.code })
        val byCode = buildAccountRows(chart, emptyMap(), "۱۱۰۲", hideZero = false)
        assertEquals(listOf("1102"), byCode.map { it.code })
        assertEquals(0, byCode.single().depth)
    }
}
