package com.example.accounting.core.domain.pipeline

import com.example.accounting.core.persian.PersianDigits

/** Text normalization before the local parser and the backend. Keeps ZWNJ (meaningful in Persian). */
object InputNormalizer {
    private val whitespace = Regex("[\\s\\u00A0]+")

    fun normalize(raw: String): String = PersianDigits.toLatin(raw)
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace(whitespace, " ")
        .trim()
}
