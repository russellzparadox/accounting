package com.example.accounting.core.domain.pipeline

import com.example.accounting.core.domain.TransactionIntent
import com.example.accounting.core.persian.AmountUnit
import java.time.LocalDate

/**
 * Port for the LLM backend (T06 `POST /v1/parse`). Implementations must not throw for
 * transport or HTTP failures; they return [ParserResult.Error] instead.
 * [text] is already normalized by [InputNormalizer]. Implementations must never log it.
 */
interface NlParser {
    suspend fun parse(text: String): ParserResult
}

/** Backend output after wire decoding. Money is never computed here. */
sealed interface ParserResult {
    data class Intent(val parsed: ParsedIntent) : ParserResult

    data class Clarification(val question: String) : ParserResult

    data class Error(val error: PipelineError) : ParserResult
}

/**
 * [intent] is the T03 shape (amount_rial authoritative). The extras are the T06 cross-check fields:
 * [amountText] must be a verbatim substring of the input; [amountValue]/[amountUnit] are as stated.
 */
data class ParsedIntent(
    val intent: TransactionIntent,
    val amountText: String?,
    val amountValue: Long?,
    val amountUnit: AmountUnit?,
)

/** Typed failures. Never carries user text, tokens or raw bodies. */
sealed interface PipelineError {
    /** No network, DNS failure, connect or read timeout. */
    data object Offline : PipelineError

    /** HTTP 401. Token missing or wrong. */
    data object Unauthorized : PipelineError

    /** HTTP 429. [retryAfterSeconds] from the Retry-After header, if present and numeric. */
    data class RateLimited(val retryAfterSeconds: Long?) : PipelineError

    /** HTTP 5xx except the 502 `invalid_llm_output` case (that is [BadResponse]). */
    data class ServerError(val status: Int) : PipelineError

    /** Malformed JSON, schema violation, unexpected 4xx, or a rule failure in PostingEngine. */
    data class BadResponse(val reason: String) : PipelineError

    /** Input is blank after normalization. Parser is not called. */
    data object EmptyInput : PipelineError

    /** Neither the local parser nor the backend's amount_text grounds an amount in the input. */
    data object AmountMissing : PipelineError

    /** Intent date is outside the open fiscal year. */
    data class DateOutsideFiscalYear(val date: LocalDate) : PipelineError
}
