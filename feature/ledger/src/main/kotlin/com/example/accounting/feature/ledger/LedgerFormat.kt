package com.example.accounting.feature.ledger

import com.example.accounting.core.persian.JalaliDate
import com.example.accounting.core.persian.PersianDigits
import java.time.LocalDate

/** Persian digits for anything shown to the user. DB values and account codes stay Latin in storage. */
internal fun persianNumber(value: Any): String = PersianDigits.toPersian(value.toString())

/** Jalali date with Persian digits, e.g. ۱۴۰۴/۱۰/۲۰. */
internal fun jalaliText(date: LocalDate): String = JalaliDate.from(date).toString()
