package com.example.accounting.core.domain

import com.example.accounting.core.domain.Side.CREDIT
import com.example.accounting.core.domain.Side.DEBIT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JournalEntryTest {
    private fun line(code: String, side: Side, amount: Long) = JournalLine(code, side, Money(amount))

    private fun entry(lines: List<JournalLine>) =
        JournalEntry("E1", testToday, TemplateId.PURCHASE_CASH, null, lines)

    @Test
    fun balancedEntryIsAccepted() {
        val e = entry(listOf(line("5100", DEBIT, 10), line("1100", CREDIT, 6), line("1200", CREDIT, 4)))
        assertEquals(Money(10), e.debitTotal)
        assertEquals(Money(10), e.creditTotal)
    }

    @Test
    fun unbalancedEntryIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            entry(listOf(line("5100", DEBIT, 10), line("1100", CREDIT, 9)))
        }
    }

    @Test
    fun entryNeedsTwoLines() {
        assertFailsWith<IllegalArgumentException> { entry(listOf(line("5100", DEBIT, 10))) }
    }

    @Test
    fun lineAmountMustBePositive() {
        assertFailsWith<IllegalArgumentException> { line("5100", DEBIT, 0) }
        assertFailsWith<IllegalArgumentException> { line("5100", DEBIT, -1) }
    }

    @Test
    fun linesAreCopiedSoCallerCannotMutateEntry() {
        val source = mutableListOf(line("5100", DEBIT, 10), line("1100", CREDIT, 10))
        val e = entry(source)
        source.clear()
        assertEquals(2, e.lines.size)
    }
}
