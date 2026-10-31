package com.example.accounting.core.persian

/**
 * Whole toman typed by the user, Persian or Latin digits. Separators («,» «٬» «،») and spaces are ignored.
 * Returns null for empty input, zero, any other character, or more than 14 digits. Result is toman, not rial.
 */
object TomanInput {
    private const val MAX_DIGITS = 14
    private val separators = setOf(',', '٬', '،')

    fun parse(text: String): Long? {
        val cleaned = PersianDigits.toLatin(text).filterNot { it in separators || it.isWhitespace() }
        if (cleaned.isEmpty() || cleaned.length > MAX_DIGITS) return null
        if (!cleaned.all { it in '0'..'9' }) return null
        return cleaned.toLong().takeIf { it > 0L }
    }
}
