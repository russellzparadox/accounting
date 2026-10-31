package com.example.accounting.core.persian

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoneyFormatTest {

    @Test
    fun tomanWholeWithThousandsSeparator() {
        assertEquals("۵۰۰٬۰۰۰ تومان", MoneyFormat.format(5_000_000L, AmountUnit.TOMAN))
    }

    @Test
    fun tomanZero() {
        assertEquals("۰ تومان", MoneyFormat.format(0L, AmountUnit.TOMAN))
    }

    @Test
    fun tomanSingleRialShowsDecimal() {
        assertEquals("۰٫۷ تومان", MoneyFormat.format(7L, AmountUnit.TOMAN))
    }

    @Test
    fun tomanWithRialRemainder() {
        assertEquals("۵۰۰٬۰۰۰٫۵ تومان", MoneyFormat.format(5_000_005L, AmountUnit.TOMAN))
    }

    @Test
    fun tomanNegative() {
        assertEquals("-۱۰۰٬۰۰۰ تومان", MoneyFormat.format(-1_000_000L, AmountUnit.TOMAN))
    }

    @Test
    fun rialGrouped() {
        assertEquals("۱۲۳٬۴۵۶٬۷۸۹ ریال", MoneyFormat.format(123_456_789L, AmountUnit.RIAL))
    }

    @Test
    fun rialBelowThousandNotGrouped() {
        assertEquals("۹۹۹ ریال", MoneyFormat.format(999L, AmountUnit.RIAL))
    }

    @Test
    fun rialExactlyThousand() {
        assertEquals("۱٬۰۰۰ ریال", MoneyFormat.format(1_000L, AmountUnit.RIAL))
    }

    @Test
    fun longMinValueDoesNotOverflow() {
        assertEquals("-۹٬۲۲۳٬۳۷۲٬۰۳۶٬۸۵۴٬۷۷۵٬۸۰۸ ریال", MoneyFormat.format(Long.MIN_VALUE, AmountUnit.RIAL))
    }

    @Test
    fun outputHasNoLatinDigits() {
        val out = MoneyFormat.format(1_234_567_890L, AmountUnit.TOMAN)
        assertFalse(out.any { it in '0'..'9' }, out)
    }

    @Test
    fun formattedOutputParsesBackToSameAmount() {
        val out = MoneyFormat.format(50_000_000L, AmountUnit.TOMAN)
        val parsed = NumberWordParser.parseAmount(out)
        assertEquals(50_000_000L, parsed?.rial)
    }

    @Test
    fun separatorIsArabicThousandsSign() {
        assertTrue(MoneyFormat.format(1_000L, AmountUnit.RIAL).contains('٬'))
    }
}
