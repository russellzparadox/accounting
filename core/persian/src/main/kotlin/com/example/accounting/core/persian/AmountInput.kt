package com.example.accounting.core.persian

/**
 * Amount typed by the user in the display unit (toman or rial) and back. Money stays Long rial.
 * Conversion uses only this object and [TomanInput]; no Float/Double anywhere.
 */
object AmountInput {
    /** 10^15 rial (10^14 toman). Same ceiling as the domain line limit. */
    const val MAX_RIAL: Long = 1_000_000_000_000_000L

    private const val MAX_RIAL_DIGITS = 16

    /**
     * Typed text to rial. Persian or Latin digits; separators («٬», «،», «,») and spaces are ignored.
     * TOMAN: whole toman times 10. RIAL: whole rial as typed.
     * Null for empty, zero, non-digit text, or a value above [MAX_RIAL].
     */
    fun parseRial(text: String, unit: AmountUnit): Long? = when (unit) {
        AmountUnit.TOMAN -> TomanInput.parse(text)?.takeIf { it <= MAX_RIAL / 10L }?.let { it * 10L }
        AmountUnit.RIAL -> {
            val cleaned = PersianDigits.toLatin(text).filterNot { it == ',' || it == '٬' || it == '،' || it.isWhitespace() }
            if (cleaned.isEmpty() || cleaned.length > MAX_RIAL_DIGITS || !cleaned.all { it in '0'..'9' }) {
                null
            } else {
                cleaned.toLong().takeIf { it in 1L..MAX_RIAL }
            }
        }
    }

    /**
     * Rial to the text shown in an input field for [unit] (Latin digits, the parser accepts both).
     * TOMAN returns null when the rial value is not a whole toman (its last digit is not 0): such a value cannot be
     * edited in toman without losing money, so the caller must keep it out of the toman field.
     */
    fun toInputText(rial: Long, unit: AmountUnit): String? = when (unit) {
        AmountUnit.TOMAN -> if (rial > 0L && rial % 10L == 0L) (rial / 10L).toString() else null
        AmountUnit.RIAL -> if (rial > 0L) rial.toString() else null
    }
}
