package com.example.accounting.core.domain

/** Rule violation in an intent, the chart, or a posting request. */
class DomainException(message: String) : IllegalArgumentException(message)
