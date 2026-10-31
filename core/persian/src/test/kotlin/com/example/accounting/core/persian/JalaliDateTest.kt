package com.example.accounting.core.persian

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class JalaliDateTest {

    private data class Pair2(val g: LocalDate, val j: JalaliDate)

    // Anchors: Nowruz dates and Esfand 29/30 boundaries around known leap years.
    private val knownPairs = listOf(
        Pair2(LocalDate.of(2025, 3, 21), JalaliDate(1404, 1, 1)),
        Pair2(LocalDate.of(2026, 3, 21), JalaliDate(1405, 1, 1)),
        Pair2(LocalDate.of(2025, 3, 20), JalaliDate(1403, 12, 30)),
        Pair2(LocalDate.of(2024, 3, 20), JalaliDate(1403, 1, 1)),
        Pair2(LocalDate.of(2024, 3, 19), JalaliDate(1402, 12, 29)),
        Pair2(LocalDate.of(2023, 3, 21), JalaliDate(1402, 1, 1)),
        Pair2(LocalDate.of(2023, 3, 20), JalaliDate(1401, 12, 29)),
        Pair2(LocalDate.of(2022, 3, 21), JalaliDate(1401, 1, 1)),
        Pair2(LocalDate.of(2021, 3, 21), JalaliDate(1400, 1, 1)),
        Pair2(LocalDate.of(2020, 3, 20), JalaliDate(1399, 1, 1)),
        Pair2(LocalDate.of(2020, 3, 19), JalaliDate(1398, 12, 29)),
        Pair2(LocalDate.of(2026, 3, 20), JalaliDate(1404, 12, 29)),
        Pair2(LocalDate.of(2026, 10, 31), JalaliDate(1405, 8, 9)),
        Pair2(LocalDate.of(2025, 9, 23), JalaliDate(1404, 7, 1)),
    )

    @TestFactory
    fun knownGregorianJalaliPairs(): List<DynamicTest> = knownPairs.map { p ->
        DynamicTest.dynamicTest("${p.g} <-> ${p.j}") {
            assertEquals(p.j, JalaliDate.from(p.g), "from gregorian")
            assertEquals(p.g, p.j.toGregorian(), "to gregorian")
        }
    }

    @Test
    fun leapYear1399() = assertTrue(JalaliDate.isLeapYear(1399))

    @Test
    fun leapYear1403() = assertTrue(JalaliDate.isLeapYear(1403))

    @Test
    fun leapYear1408() = assertTrue(JalaliDate.isLeapYear(1408))

    @Test
    fun notLeapYear1404() = assertFalse(JalaliDate.isLeapYear(1404))

    @Test
    fun notLeapYear1400() = assertFalse(JalaliDate.isLeapYear(1400))

    @Test
    fun notLeapYear1402() = assertFalse(JalaliDate.isLeapYear(1402))

    @Test
    fun esfandLengthFollowsLeapYear() {
        assertEquals(30, JalaliDate.daysInMonth(1403, 12))
        assertEquals(29, JalaliDate.daysInMonth(1404, 12))
    }

    @Test
    fun esfand30RejectedInNonLeapYear() {
        assertFailsWith<IllegalArgumentException> { JalaliDate(1404, 12, 30) }
    }

    @Test
    fun esfand30AcceptedInLeapYear() {
        assertEquals(LocalDate.of(2025, 3, 20), JalaliDate(1403, 12, 30).toGregorian())
    }

    @Test
    fun invalidMonthRejected() {
        assertFailsWith<IllegalArgumentException> { JalaliDate(1404, 13, 1) }
    }

    @Test
    fun zeroDayRejected() {
        assertFailsWith<IllegalArgumentException> { JalaliDate(1404, 1, 0) }
    }

    @Test
    fun mehrHasThirtyDaysSoThirtyFirstRejected() {
        assertFailsWith<IllegalArgumentException> { JalaliDate(1404, 7, 31) }
    }

    @Test
    fun yearLengthsFollowLeapRule() {
        for (y in 1380..1430) {
            val len = JalaliDate(y + 1, 1, 1).toGregorian().toEpochDay() -
                JalaliDate(y, 1, 1).toGregorian().toEpochDay()
            assertEquals(if (JalaliDate.isLeapYear(y)) 366L else 365L, len, "year $y")
        }
    }

    @Test
    fun dailyRoundTripAcrossSeventyYears() {
        var d = LocalDate.of(1990, 1, 1)
        val end = LocalDate.of(2060, 12, 31)
        while (!d.isAfter(end)) {
            assertEquals(d, JalaliDate.from(d).toGregorian())
            d = d.plusDays(1)
        }
    }

    @Test
    fun fiscalYear1403() {
        assertEquals(
            LocalDate.of(2024, 3, 20) to LocalDate.of(2025, 3, 20),
            JalaliDate.fiscalYear(1403),
        )
    }

    @Test
    fun fiscalYear1404EndsOnTwentyNinthEsfand() {
        assertEquals(
            LocalDate.of(2025, 3, 21) to LocalDate.of(2026, 3, 20),
            JalaliDate.fiscalYear(1404),
        )
    }

    @Test
    fun toStringUsesPersianDigits() {
        assertEquals("۱۴۰۴/۰۱/۰۱", JalaliDate(1404, 1, 1).toString())
    }

    @Test
    fun toStringPadsMonthAndDay() {
        assertEquals("۱۴۰۳/۱۲/۳۰", JalaliDate(1403, 12, 30).toString())
    }

    @Test
    fun monthNames() {
        assertEquals("فروردین", JalaliDate(1404, 1, 5).monthName())
        assertEquals("اسفند", JalaliDate(1404, 12, 29).monthName())
        assertEquals(12, JalaliDate.MONTH_NAMES.size)
    }
}
