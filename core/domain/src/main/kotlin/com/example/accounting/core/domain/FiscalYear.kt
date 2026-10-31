package com.example.accounting.core.domain

import java.time.LocalDate

/** Fiscal year as inclusive Gregorian bounds. Jalali conversion is T05's job; callers pass the bounds in. */
data class FiscalYear(val start: LocalDate, val end: LocalDate) {
    init {
        require(!end.isBefore(start)) { "fiscal year ends before it starts" }
    }

    operator fun contains(date: LocalDate): Boolean = date >= start && date <= end
}
