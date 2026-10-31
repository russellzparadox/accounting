package com.example.accounting.core.domain

/** Amount in rials (IRR). Integer only. Toman (rial / 10) is display-only, done in UI. */
@JvmInline
value class Money(val rial: Long) : Comparable<Money> {
    operator fun plus(other: Money): Money = Money(rial + other.rial)

    operator fun minus(other: Money): Money = Money(rial - other.rial)

    override fun compareTo(other: Money): Int = rial.compareTo(other.rial)

    companion object {
        val ZERO = Money(0)
    }
}
