package com.example.accounting.core.persian

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TomanInputTest {
    @Test
    fun persianAndLatinDigitsParse() {
        assertEquals(500_000L, TomanInput.parse("۵۰۰٬۰۰۰"))
        assertEquals(500_000L, TomanInput.parse("500,000"))
        assertEquals(500_000L, TomanInput.parse(" ۵۰۰ ۰۰۰ "))
        assertEquals(1L, TomanInput.parse("۱"))
    }

    @Test
    fun invalidInputIsNull() {
        assertNull(TomanInput.parse(""))
        assertNull(TomanInput.parse("   "))
        assertNull(TomanInput.parse("۰"))
        assertNull(TomanInput.parse("۰۰۰"))
        assertNull(TomanInput.parse("۵٫۵"))
        assertNull(TomanInput.parse("-۵"))
        assertNull(TomanInput.parse("۵۰ تومان"))
        assertNull(TomanInput.parse("۱۲۳۴۵۶۷۸۹۰۱۲۳۴۵"))
    }
}
