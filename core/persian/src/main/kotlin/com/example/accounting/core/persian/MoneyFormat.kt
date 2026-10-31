package com.example.accounting.core.persian

/** Display formatting for money stored as rial (Long). Persian digits, '٬' thousands separator. */
object MoneyFormat {

    private const val THOUSANDS = '٬' // ٬
    private const val DECIMAL = '٫'   // ٫

    /**
     * TOMAN: rial/10 shown with one decimal digit when rial is not a multiple of 10 (e.g. ۰٫۷ تومان).
     * RIAL: whole rial. Examples: 5_000_000 TOMAN -> "۵۰۰٬۰۰۰ تومان"; 123456789 RIAL -> "۱۲۳٬۴۵۶٬۷۸۹ ریال".
     */
    fun format(rial: Long, unit: AmountUnit): String {
        // String-based to stay safe for Long.MIN_VALUE.
        val negative = rial < 0
        val digits = rial.toString().removePrefix("-")
        val body = when (unit) {
            AmountUnit.RIAL -> group(digits)
            AmountUnit.TOMAN -> {
                val whole = if (digits.length > 1) digits.dropLast(1) else "0"
                val frac = digits.last()
                val grouped = group(whole)
                if (frac == '0') grouped else "$grouped$DECIMAL$frac"
            }
        }
        val suffix = when (unit) {
            AmountUnit.TOMAN -> " تومان"
            AmountUnit.RIAL -> " ریال"
        }
        val sign = if (negative) "-" else ""
        return PersianDigits.toPersian(sign + body) + suffix
    }

    private fun group(digits: String): String {
        val n = digits.length
        val sb = StringBuilder()
        for ((i, c) in digits.withIndex()) {
            if (i > 0 && (n - i) % 3 == 0) sb.append(THOUSANDS)
            sb.append(c)
        }
        return sb.toString()
    }
}
