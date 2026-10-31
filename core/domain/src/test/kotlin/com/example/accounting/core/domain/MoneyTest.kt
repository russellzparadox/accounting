package com.example.accounting.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoneyTest {
    @Test
    fun integerRialArithmetic() {
        assertEquals(Money(7), Money(3) + Money(4))
        assertEquals(Money(-1), Money(3) - Money(4))
        assertEquals(Money.ZERO, Money(5) - Money(5))
        assertTrue(Money(2) < Money(3))
    }
}
