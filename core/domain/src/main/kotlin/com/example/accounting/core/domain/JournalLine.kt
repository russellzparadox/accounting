package com.example.accounting.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Side {
    @SerialName("debit") DEBIT,
    @SerialName("credit") CREDIT;

    fun opposite(): Side = if (this == DEBIT) CREDIT else DEBIT
}

/** One side of a journal entry. Amount is always positive. */
data class JournalLine(val accountCode: String, val side: Side, val amount: Money) {
    init {
        require(amount > Money.ZERO) { "line amount must be > 0" }
    }

    fun flipped(): JournalLine = copy(side = side.opposite())
}
