package com.example.accounting.feature.ledger

import com.example.accounting.core.persian.JalaliDate
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Jalali input parsing and preset ranges. Pure JVM. */
class DateFilterTest {
    @Test
    fun parsesPersianAndLatinDigitsAndSeparators() {
        val start = JalaliDate.fiscalYear(1405).first
        assertEquals(start, parseJalaliInput("۱۴۰۵/۰۱/۰۱"))
        assertEquals(start, parseJalaliInput("1405/1/1"))
        assertEquals(start, parseJalaliInput("1405-01-01"))
        assertEquals(start, parseJalaliInput(" ۱۴۰۵/۱/۱ "))
    }

    @Test
    fun rejectsInvalidDates() {
        assertNull(parseJalaliInput(""))
        assertNull(parseJalaliInput("abc"))
        assertNull(parseJalaliInput("۱۴۰۵/۱۳/۰۱"))
        assertNull(parseJalaliInput("۱۴۰۵/۰۷/۳۱")) // مهر has 30 days
        assertNull(parseJalaliInput("۱۴۰۵/۰۱"))
    }

    @Test
    fun fullYearPresetIsFiscalYear() {
        assertEquals(fiscalRange(1405), rangeFor(DatePreset.ALL, 1405, LocalDate.of(2026, 1, 10), null))
    }

    @Test
    fun thisMonthIsTheJalaliMonthOfToday() {
        // 2026-01-10 is 1404/10/20 (دی, 30 days). Range checked against the Jalali endpoints.
        val range = rangeFor(DatePreset.THIS_MONTH, 1404, LocalDate.of(2026, 1, 10), null)!!
        assertEquals(JalaliDate(1404, 10, 1).toGregorian(), range.from)
        assertEquals(JalaliDate(1404, 10, 30).toGregorian(), range.to)
    }

    @Test
    fun previousMonthWrapsToEsfandOfPreviousYear() {
        // 2026-04-01 is 1405/01/12 (فروردین), so the previous month is اسفند 1404.
        val range = rangeFor(DatePreset.PREVIOUS_MONTH, 1405, LocalDate.of(2026, 4, 1), null)!!
        assertEquals(JalaliDate(1404, 12, 1).toGregorian(), range.from)
        assertEquals(JalaliDate(1404, 12, JalaliDate.daysInMonth(1404, 12)).toGregorian(), range.to)
    }

    @Test
    fun customUsesTypedRangeOrNothing() {
        val custom = DateRange(LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30))
        assertEquals(custom, rangeFor(DatePreset.CUSTOM, 1404, LocalDate.of(2026, 1, 10), custom))
        assertNull(rangeFor(DatePreset.CUSTOM, 1404, LocalDate.of(2026, 1, 10), null))
    }
}
