package com.example.accounting.core.persian

/** Digit mapping between Persian/Arabic-Indic digits and ASCII. Length-preserving. */
object PersianDigits {

    /** Maps ۰-۹ (U+06F0..U+06F9) and ٠-٩ (U+0660..U+0669) to ASCII 0-9. Other chars unchanged. */
    fun toLatin(text: String): String = buildString(text.length) {
        for (c in text) {
            append(
                when (c) {
                    in '۰'..'۹' -> '0' + (c - '۰')
                    in '٠'..'٩' -> '0' + (c - '٠')
                    else -> c
                }
            )
        }
    }

    /** Maps ASCII 0-9 to ۰-۹ (U+06F0..U+06F9). Other chars unchanged. */
    fun toPersian(text: String): String = buildString(text.length) {
        for (c in text) {
            append(if (c in '0'..'9') '۰' + (c - '0') else c)
        }
    }
}
