package com.example.accounting.core.persian

/**
 * Pure state of the Jalali calendar picker (year, month, day). Android-free so the leap and clamping rules run on the JVM.
 * Every change keeps the day inside the month: moving to Esfand of a non-leap year turns 30 into 29.
 */
data class JalaliPickerState(val year: Int, val month: Int, val day: Int) {
    init {
        require(year in MIN_YEAR..MAX_YEAR) { "year out of picker range: $year" }
        require(month in 1..12) { "month out of range: $month" }
        require(day in 1..JalaliDate.daysInMonth(year, month)) { "day out of range: $year/$month/$day" }
    }

    val daysInMonth: Int get() = JalaliDate.daysInMonth(year, month)

    fun withYear(newYear: Int): JalaliPickerState {
        val y = newYear.coerceIn(MIN_YEAR, MAX_YEAR)
        return JalaliPickerState(y, month, day.coerceAtMost(JalaliDate.daysInMonth(y, month)))
    }

    fun withMonth(newMonth: Int): JalaliPickerState {
        val m = newMonth.coerceIn(1, 12)
        return JalaliPickerState(year, m, day.coerceAtMost(JalaliDate.daysInMonth(year, m)))
    }

    fun withDay(newDay: Int): JalaliPickerState = JalaliPickerState(year, month, newDay.coerceIn(1, daysInMonth))

    /** Next Jalali month. Esfand of year Y goes to Farvardin of Y+1. Null past [MAX_YEAR]. */
    fun nextMonth(): JalaliPickerState? = when {
        month < 12 -> withMonth(month + 1)
        year < MAX_YEAR -> JalaliPickerState(year + 1, 1, day.coerceAtMost(JalaliDate.daysInMonth(year + 1, 1)))
        else -> null
    }

    /** Previous Jalali month. Farvardin of Y goes to Esfand of Y-1. Null before [MIN_YEAR]. */
    fun previousMonth(): JalaliPickerState? = when {
        month > 1 -> withMonth(month - 1)
        year > MIN_YEAR -> JalaliPickerState(year - 1, 12, day.coerceAtMost(JalaliDate.daysInMonth(year - 1, 12)))
        else -> null
    }

    fun toDate(): JalaliDate = JalaliDate(year, month, day)

    /** Years offered in the year list, centred on [year] and clamped to the picker range. */
    fun yearOptions(radius: Int = YEAR_RADIUS): List<Int> =
        (year - radius..year + radius).filter { it in MIN_YEAR..MAX_YEAR }

    companion object {
        const val MIN_YEAR = 1300
        const val MAX_YEAR = 1500
        const val YEAR_RADIUS = 12

        fun of(date: JalaliDate): JalaliPickerState = JalaliPickerState(date.year, date.month, date.day)
    }
}
