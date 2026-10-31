package com.example.accounting.core.persian

import java.time.LocalDate

/**
 * Solar Hijri (Jalali) calendar date. Leap years by the arithmetic 33-year rule:
 * year y is leap iff (25*y + 11) mod 33 < 8. Valid for 1 فروردین 1404 region and nearby years.
 */
data class JalaliDate(val year: Int, val month: Int, val day: Int) {
    init {
        require(month in 1..12) { "month out of range: $month" }
        require(day in 1..daysInMonth(year, month)) { "day out of range: $year/$month/$day" }
    }

    fun toGregorian(): LocalDate =
        LocalDate.ofEpochDay(ANCHOR_DATE.toEpochDay() + (dayNumber(year, month, day) - anchorNumber()))

    fun monthName(): String = MONTH_NAMES[month - 1]

    /** Persian digits, yyyy/mm/dd, e.g. ۱۴۰۴/۰۱/۰۱. */
    override fun toString(): String =
        PersianDigits.toPersian(String.format("%04d/%02d/%02d", year, month, day))

    companion object {
        val MONTH_NAMES: List<String> = listOf(
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
        )

        /** 1 فروردین 1404 = 2025-03-21 (anchor for day numbering). */
        private val ANCHOR_DATE: LocalDate = LocalDate.of(2025, 3, 21)
        private const val ANCHOR_YEAR = 1404

        /** LEAP_RESIDUE[k] = leap for any year y with y mod 33 == k. */
        private val LEAP_RESIDUE = BooleanArray(33) { k -> Math.floorMod(25 * k + 11, 33) < 8 }

        fun isLeapYear(year: Int): Boolean = LEAP_RESIDUE[Math.floorMod(year, 33)]

        fun daysInMonth(year: Int, month: Int): Int = when {
            month <= 6 -> 31
            month <= 11 -> 30
            isLeapYear(year) -> 30
            else -> 29
        }

        /** Gregorian date -> Jalali date. */
        fun from(date: LocalDate): JalaliDate {
            val n = date.toEpochDay() - ANCHOR_DATE.toEpochDay() + anchorNumber()
            var y = ANCHOR_YEAR + Math.floorDiv(n - yearStart(ANCHOR_YEAR), 365L).toInt()
            while (yearStart(y + 1) <= n) y++
            while (yearStart(y) > n) y--
            val doy = (n - yearStart(y)).toInt()
            val m = if (doy < 186) doy / 31 + 1 else (doy - 186) / 30 + 7
            val d = doy - monthOffset(m) + 1
            return JalaliDate(y, m, d)
        }

        /** Fiscal year Jalali [jalaliYear]: (1 فروردین, last day of اسفند) as Gregorian dates, inclusive. */
        fun fiscalYear(jalaliYear: Int): Pair<LocalDate, LocalDate> =
            JalaliDate(jalaliYear, 1, 1).toGregorian() to
                JalaliDate(jalaliYear, 12, daysInMonth(jalaliYear, 12)).toGregorian()

        /** Number of leap years in [0, year). Periodic with period 33; works for negative years. */
        private fun leapCountBefore(year: Int): Long {
            var c = 8L * Math.floorDiv(year, 33)
            for (k in 0 until Math.floorMod(year, 33)) if (LEAP_RESIDUE[k]) c++
            return c
        }

        /** Day index of 1 فروردین of [year] (days since an arbitrary origin, consistent across years). */
        private fun yearStart(year: Int): Long = 365L * year + leapCountBefore(year)

        private fun anchorNumber(): Long = yearStart(ANCHOR_YEAR)

        private fun monthOffset(m: Int): Int = if (m <= 6) 31 * (m - 1) else 186 + 30 * (m - 7)

        private fun dayNumber(y: Int, m: Int, d: Int): Long = yearStart(y) + monthOffset(m) + d - 1
    }
}
