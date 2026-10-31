package com.example.accounting.core.persian

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class JalaliPickerTest {
    @Test
    fun leapEsfandHasThirtyDaysAndCommonEsfandTwentyNine() {
        // 1399 and 1403 are leap years; 1404 is not (arithmetic 33-year rule, same as JalaliDate).
        assertEquals(30, JalaliPickerState(1399, 12, 30).daysInMonth)
        assertEquals(30, JalaliPickerState(1403, 12, 1).daysInMonth)
        assertEquals(29, JalaliPickerState(1404, 12, 1).daysInMonth)
    }

    @Test
    fun changingYearClampsDayToTheNewMonthLength() {
        val leap = JalaliPickerState(1403, 12, 30)
        assertEquals(JalaliPickerState(1404, 12, 29), leap.withYear(1404))
        // 1399 is leap, so 29 Esfand stays 29 (it does not jump back to 30).
        assertEquals(JalaliPickerState(1399, 12, 29), JalaliPickerState(1404, 12, 29).withYear(1399))
    }

    @Test
    fun changingMonthClampsDayAndKeepsItInsideTheMonth() {
        // Day 31 in Shahrivar (6, 31 days) moves to Mehr (7, 30 days) as day 30.
        assertEquals(JalaliPickerState(1404, 7, 30), JalaliPickerState(1404, 6, 31).withMonth(7))
        assertEquals(JalaliPickerState(1404, 1, 31), JalaliPickerState(1404, 6, 31).withMonth(1))
    }

    @Test
    fun withDayCoercesIntoTheMonth() {
        assertEquals(JalaliPickerState(1404, 1, 31), JalaliPickerState(1404, 1, 5).withDay(40))
        assertEquals(JalaliPickerState(1404, 1, 1), JalaliPickerState(1404, 1, 5).withDay(-3))
    }

    @Test
    fun nextAndPreviousMonthCrossYearBoundaries() {
        assertEquals(JalaliPickerState(1405, 1, 29), JalaliPickerState(1404, 12, 29).nextMonth())
        assertEquals(JalaliPickerState(1404, 12, 29), JalaliPickerState(1405, 1, 31).previousMonth())
        assertEquals(JalaliPickerState(1404, 2, 31), JalaliPickerState(1404, 1, 31).nextMonth())
    }

    @Test
    fun monthStepStopsAtPickerRange() {
        assertNull(JalaliPickerState(JalaliPickerState.MAX_YEAR, 12, 1).nextMonth())
        assertNull(JalaliPickerState(JalaliPickerState.MIN_YEAR, 1, 1).previousMonth())
    }

    @Test
    fun yearOptionsAreCentredAndClamped() {
        assertEquals((1392..1416).toList(), JalaliPickerState(1404, 1, 1).yearOptions())
        assertEquals((1300..1312).toList(), JalaliPickerState(1300, 1, 1).yearOptions())
        assertEquals((1488..1500).toList(), JalaliPickerState(1500, 1, 1).yearOptions())
    }

    @Test
    fun outOfRangeStateIsRejected() {
        assertFailsWith<IllegalArgumentException> { JalaliPickerState(1299, 1, 1) }
        assertFailsWith<IllegalArgumentException> { JalaliPickerState(1404, 12, 30) }
    }

    @Test
    fun ofMatchesDate() {
        assertEquals(JalaliPickerState(1404, 7, 15), JalaliPickerState.of(JalaliDate(1404, 7, 15)))
        assertEquals(JalaliDate(1404, 7, 15), JalaliPickerState(1404, 7, 15).toDate())
    }

    @Test
    fun leadingBlanksCountFromSaturdayWeekStart() {
        // 1 فروردین 1404 = Friday 2025-03-21: six blank cells before it in a Saturday-first grid.
        assertEquals(LocalDate.of(2025, 3, 21).dayOfWeek.value, 5)
        assertEquals(6, JalaliPickerState(1404, 1, 1).leadingBlanks())
        // 1 فروردین 1405 = Saturday 2026-03-21 (leap-free arithmetic, checked against JalaliDate.toGregorian): no blanks.
        assertEquals(0, JalaliPickerState(1405, 1, 1).leadingBlanks())
    }
}
