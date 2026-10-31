package com.example.accounting.core.persian

import kotlin.test.Test
import kotlin.test.assertEquals

class PersianDigitsTest {

    @Test
    fun persianDigitsToLatin() {
        assertEquals("0123456789", PersianDigits.toLatin("۰۱۲۳۴۵۶۷۸۹"))
    }

    @Test
    fun arabicIndicDigitsToLatin() {
        assertEquals("0123456789", PersianDigits.toLatin("٠١٢٣٤٥٦٧٨٩"))
    }

    @Test
    fun latinToPersian() {
        assertEquals("۰۱۲۳۴۵۶۷۸۹", PersianDigits.toPersian("0123456789"))
    }

    @Test
    fun roundTripFromPersian() {
        val s = "۱۴۰۴/۰۱/۰۱"
        assertEquals(s, PersianDigits.toPersian(PersianDigits.toLatin(s)))
    }

    @Test
    fun roundTripFromArabicIndic() {
        assertEquals("1404", PersianDigits.toLatin("١٤٠٤"))
    }

    @Test
    fun lengthIsPreserved() {
        val s = "۵۰۰٬۰۰۰ تومان ٠١٢"
        assertEquals(s.length, PersianDigits.toLatin(s).length)
    }

    @Test
    fun nonDigitsUnchanged() {
        assertEquals("تومان ٬ ، ٫ abc", PersianDigits.toLatin("تومان ٬ ، ٫ abc"))
    }

    @Test
    fun toPersianLeavesSeparatorsAndWordsAlone() {
        assertEquals("۵۰۰٬۰۰۰ تومان", PersianDigits.toPersian("500٬000 تومان"))
    }
}
